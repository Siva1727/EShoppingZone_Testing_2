package com.eshoppingzone.order.saga.orchestrator;

import com.eshoppingzone.order.client.CartClient;
import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentMethod;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.exception.IdempotencyConflictException;
import com.eshoppingzone.order.exception.InsufficientStockException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.exception.PaymentException;
import com.eshoppingzone.order.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class CheckoutSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSagaOrchestrator.class);

    private static final Map<SagaStatus, Set<SagaStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(SagaStatus.STARTED, Set.of(SagaStatus.INVENTORY_RESERVED, SagaStatus.FAILED)),
            Map.entry(SagaStatus.INVENTORY_RESERVED, Set.of(SagaStatus.PAYMENT_COMPLETED, SagaStatus.COD_PAYMENT_PENDING,
                    SagaStatus.COMPENSATING, SagaStatus.PAYMENT_RECONCILIATION_REQUIRED)),
            Map.entry(SagaStatus.PAYMENT_COMPLETED, Set.of(SagaStatus.INVENTORY_CONFIRMED, SagaStatus.INVENTORY_CONFIRMATION_FAILED)),
            Map.entry(SagaStatus.COD_PAYMENT_PENDING, Set.of(SagaStatus.INVENTORY_CONFIRMED, SagaStatus.INVENTORY_CONFIRMATION_FAILED)),
            Map.entry(SagaStatus.INVENTORY_CONFIRMED, Set.of(SagaStatus.CART_CLEARED, SagaStatus.CART_CLEAR_FAILED, SagaStatus.COMPLETED)),
            Map.entry(SagaStatus.INVENTORY_CONFIRMATION_FAILED, Set.of(SagaStatus.CART_CLEARED, SagaStatus.CART_CLEAR_FAILED, SagaStatus.COMPLETED, SagaStatus.INVENTORY_CONFIRMED)),
            Map.entry(SagaStatus.CART_CLEARED, Set.of(SagaStatus.COMPLETED)),
            Map.entry(SagaStatus.CART_CLEAR_FAILED, Set.of(SagaStatus.COMPLETED, SagaStatus.CART_CLEARED)),
            Map.entry(SagaStatus.COMPENSATING, Set.of(SagaStatus.COMPENSATED, SagaStatus.FAILED)),
            Map.entry(SagaStatus.COMPENSATED, Set.of(SagaStatus.FAILED)),
            Map.entry(SagaStatus.PAYMENT_RECONCILIATION_REQUIRED, Set.of(SagaStatus.PAYMENT_COMPLETED, SagaStatus.COMPENSATING, SagaStatus.FAILED)),
            Map.entry(SagaStatus.COMPLETED, EnumSet.noneOf(SagaStatus.class)),
            Map.entry(SagaStatus.FAILED, EnumSet.noneOf(SagaStatus.class))
    );

    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final CartClient cartClient;
    private final SagaStateService sagaStateService;
    private final RabbitTemplate rabbitTemplate;

    public CheckoutSagaOrchestrator(InventoryClient inventoryClient,
                                    PaymentClient paymentClient,
                                    CartClient cartClient,
                                    SagaStateService sagaStateService,
                                    RabbitTemplate rabbitTemplate) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.cartClient = cartClient;
        this.sagaStateService = sagaStateService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public OrderDto executeSaga(CheckoutSagaContext context) {
        String sagaId = context.getSagaId();
        String idempotencyKey = context.getIdempotencyKey();
        Order order = context.getOrder();
        Long customerId = context.getCustomerId();
        PaymentMethod paymentMethod = context.getCheckoutRequest().getPaymentMethod();

        log.info("Starting Checkout Saga [{}] for Order [{}] by Customer [{}] with Method [{}]",
                sagaId, order.getOrderNumber(), customerId, paymentMethod);

        // Initialize persistent Saga State (Tx 1)
        try {
            sagaStateService.initSagaState(sagaId, idempotencyKey, context.getRequestFingerprint(), order, SagaStep.RESERVE_INVENTORY);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate checkout detected for idempotencyKey: [{}]. Handling gracefully.", idempotencyKey);
            try {
                sagaStateService.deleteOrderById(order.getId());
            } catch (Exception ex) {
                log.warn("Failed to clean up draft order ID [{}]: {}", order.getId(), ex.getMessage());
            }
            OrderSagaState winningSaga = sagaStateService.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Saga state should exist after unique collision"));
            if (!winningSaga.getCustomerId().equals(customerId)) {
                throw new InvalidOrderStateException("Idempotency key belongs to another customer or request");
            }
            if (winningSaga.getRequestFingerprint() != null && !winningSaga.getRequestFingerprint().equals(context.getRequestFingerprint())) {
                throw new IdempotencyConflictException("Idempotency key reused with different checkout request parameters");
            }
            if (winningSaga.getStatus() == SagaStatus.FAILED) {
                throw new PaymentException("Previous checkout with this idempotency key failed: " + winningSaga.getFailureReason());
            }
            Order winningOrder = sagaStateService.findOrderById(winningSaga.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + winningSaga.getOrderId()));
            return OrderDto.fromEntity(winningOrder);
        }
        SagaStatus currentStatus = SagaStatus.STARTED;

        StockReservationRequest stockReq = new StockReservationRequest(order.getOrderNumber(), context.getReservationItems());

        // Step 1: Reserve Inventory (Remote Call 1)
        try {
            ApiResponse<Void> stockRes = inventoryClient.reserveStock(stockReq);
            if (stockRes != null && !stockRes.isSuccess()) {
                throw new InsufficientStockException("Inventory reservation failed for order items");
            }
            currentStatus = transition(sagaId, currentStatus, SagaStep.PROCESS_PAYMENT,
                    SagaStatus.INVENTORY_RESERVED, SagaStep.RESERVE_INVENTORY);
        } catch (Exception e) {
            log.warn("Saga [{}] Step 1 (RESERVE_INVENTORY) failed: {}", sagaId, e.getMessage());
            transition(sagaId, currentStatus, SagaStep.RESERVE_INVENTORY, SagaStatus.FAILED, null);
            sagaStateService.updateOrderStatus(order.getId(), OrderStatus.FAILED, PaymentStatus.FAILED);
            publishOrderEvent(order, "ORDER_FAILED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
            throw new InsufficientStockException("Failed to reserve inventory: " + e.getMessage());
        }

        // Step 2: Process Payment (Remote Call 2)
        if (paymentMethod == PaymentMethod.WALLET) {
            currentStatus = executeWalletPaymentStep(sagaId, currentStatus, order, customerId, stockReq);
        } else if (paymentMethod == PaymentMethod.COD) {
            currentStatus = executeCodPaymentStep(sagaId, currentStatus, order, customerId);
        }

        // Step 3: Confirm Inventory Consumption (Remote Call 3)
        try {
            inventoryClient.confirmStock(stockReq);
            currentStatus = transition(sagaId, currentStatus, SagaStep.CLEAR_CART,
                    SagaStatus.INVENTORY_CONFIRMED, SagaStep.CONFIRM_INVENTORY);
        } catch (Exception e) {
            log.warn("Saga [{}] Step 3 (CONFIRM_INVENTORY) non-fatal failure: {}", sagaId, e.getMessage());
            currentStatus = transition(sagaId, currentStatus, SagaStep.CLEAR_CART,
                    SagaStatus.INVENTORY_CONFIRMATION_FAILED, null);
        }

        // Step 4: Clear Customer Cart (Remote Call 4)
        try {
            cartClient.clearCustomerCart(customerId);
            currentStatus = transition(sagaId, currentStatus, SagaStep.FINALIZE_ORDER,
                    SagaStatus.CART_CLEARED, SagaStep.CLEAR_CART);
        } catch (Exception e) {
            log.warn("Saga [{}] Step 4 (CLEAR_CART) non-fatal failure: {}", sagaId, e.getMessage());
            currentStatus = transition(sagaId, currentStatus, SagaStep.FINALIZE_ORDER,
                    SagaStatus.CART_CLEAR_FAILED, null);
        }

        // Step 5: Finalize Order Status & Complete Saga (Tx 4)
        if (paymentMethod == PaymentMethod.WALLET) {
            sagaStateService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.SUCCESS);
        } else {
            sagaStateService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED, PaymentStatus.PENDING);
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        transition(sagaId, currentStatus, SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED, SagaStep.FINALIZE_ORDER);
        publishOrderEvent(order, "ORDER_CONFIRMED", RabbitMQConfig.ORDER_CONFIRMED_ROUTING_KEY);

        log.info("Checkout Saga [{}] COMPLETED successfully for Order [{}]", sagaId, order.getOrderNumber());
        return OrderDto.fromEntity(order);
    }

    private SagaStatus executeWalletPaymentStep(String sagaId, SagaStatus currentStatus, Order order,
                                                Long customerId, StockReservationRequest stockReq) {
        ProcessPaymentRequest payReq = new ProcessPaymentRequest(
                order.getId(),
                customerId,
                order.getTotalAmount(),
                PaymentMethod.WALLET
        );

        boolean paymentSuccess = false;
        boolean paymentDefinitivelyFailed = false;

        try {
            ApiResponse<PaymentResponseDto> payRes = paymentClient.processPayment(payReq);
            if (payRes != null && payRes.isSuccess() && payRes.getData() != null && "SUCCESS".equalsIgnoreCase(payRes.getData().getStatus())) {
                paymentSuccess = true;
            } else {
                paymentDefinitivelyFailed = true;
            }
        } catch (Exception e) {
            log.warn("Saga [{}] Step 2 (PROCESS_PAYMENT) call threw exception: {}. Querying payment status truth...", sagaId, e.getMessage());
            // Financial safety: query payment status to verify truth before deciding compensation
            try {
                ApiResponse<PaymentResponseDto> lookupRes = paymentClient.getPaymentByOrderId(order.getId());
                if (lookupRes != null && lookupRes.getData() != null) {
                    String status = lookupRes.getData().getStatus();
                    if ("SUCCESS".equalsIgnoreCase(status)) {
                        log.info("Saga [{}] verified payment truth: SUCCESS", sagaId);
                        paymentSuccess = true;
                    } else if ("FAILED".equalsIgnoreCase(status)) {
                        log.info("Saga [{}] verified payment truth: FAILED", sagaId);
                        paymentDefinitivelyFailed = true;
                    }
                }
            } catch (Exception lookupEx) {
                log.warn("Saga [{}] payment lookup also unreachable: {}", sagaId, lookupEx.getMessage());
            }
        }

        if (paymentSuccess) {
            return transition(sagaId, currentStatus, SagaStep.CONFIRM_INVENTORY,
                    SagaStatus.PAYMENT_COMPLETED, SagaStep.PROCESS_PAYMENT);
        } else if (paymentDefinitivelyFailed) {
            // Definitively failed -> safe to execute compensation
            log.info("Saga [{}] payment definitively FAILED. Executing inventory compensation...", sagaId);
            SagaStatus compensating = transition(sagaId, currentStatus, SagaStep.COMPENSATE_INVENTORY,
                    SagaStatus.COMPENSATING, null);
            try {
                inventoryClient.releaseStock(stockReq);
            } catch (Exception ex) {
                log.warn("Saga [{}] inventory release call failed: {}", sagaId, ex.getMessage());
            }
            SagaStatus compensated = transition(sagaId, compensating, SagaStep.COMPENSATE_INVENTORY,
                    SagaStatus.COMPENSATED, SagaStep.COMPENSATE_INVENTORY);
            transition(sagaId, compensated, SagaStep.PROCESS_PAYMENT, SagaStatus.FAILED, null);

            sagaStateService.updateOrderStatus(order.getId(), OrderStatus.FAILED, PaymentStatus.FAILED);
            order.setStatus(OrderStatus.FAILED);
            order.setPaymentStatus(PaymentStatus.FAILED);

            publishOrderEvent(order, "ORDER_FAILED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
            throw new PaymentException("Payment failed. Insufficient wallet balance or payment error.");
        } else {
            // Payment status is UNKNOWN/PENDING -> Never blindly release inventory or fail order!
            log.warn("Saga [{}] payment status is UNKNOWN/PENDING. Moving to PAYMENT_RECONCILIATION_REQUIRED.", sagaId);
            transition(sagaId, currentStatus, SagaStep.PROCESS_PAYMENT,
                    SagaStatus.PAYMENT_RECONCILIATION_REQUIRED, null);
            sagaStateService.updateOrderStatus(order.getId(), OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING);
            throw new PaymentException("Payment status is pending verification. Your order is placed and will be finalized shortly.");
        }
    }

    private SagaStatus executeCodPaymentStep(String sagaId, SagaStatus currentStatus, Order order, Long customerId) {
        try {
            ProcessPaymentRequest payReq = new ProcessPaymentRequest(
                    order.getId(),
                    customerId,
                    order.getTotalAmount(),
                    PaymentMethod.COD
            );
            paymentClient.processPayment(payReq);
        } catch (Exception e) {
            log.warn("Saga [{}] Step 2 (COD_PAYMENT) initialization warning: {}", sagaId, e.getMessage());
        }

        return transition(sagaId, currentStatus, SagaStep.CONFIRM_INVENTORY,
                SagaStatus.COD_PAYMENT_PENDING, SagaStep.PROCESS_PAYMENT);
    }

    private SagaStatus transition(String sagaId, SagaStatus fromStatus, SagaStep nextStep,
                                  SagaStatus toStatus, SagaStep completedStep) {
        validateTransition(fromStatus, toStatus);
        sagaStateService.updateState(sagaId, nextStep, toStatus, completedStep);
        return toStatus;
    }

    public void validateTransition(SagaStatus fromStatus, SagaStatus toStatus) {
        Set<SagaStatus> allowed = VALID_TRANSITIONS.getOrDefault(fromStatus, Set.of());
        if (!allowed.contains(toStatus)) {
            throw new InvalidOrderStateException("Illegal Saga state transition from " + fromStatus + " to " + toStatus);
        }
    }

    private void publishOrderEvent(Order order, String eventType, String routingKey) {
        try {
            OrderEvent event = new OrderEvent(
                    eventType,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    order.getTotalAmount(),
                    order.getStatus() != null ? order.getStatus().name() : "UNKNOWN",
                    order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "UNKNOWN",
                    order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "UNKNOWN"
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event);
        } catch (Exception e) {
            log.warn("Failed to publish order event {}: {}", eventType, e.getMessage());
        }
    }
}
