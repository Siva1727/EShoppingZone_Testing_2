package com.eshoppingzone.order.saga.scheduler;

import com.eshoppingzone.order.client.CartClient;
import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.OrderEvent;
import com.eshoppingzone.order.dto.PaymentResponseDto;
import com.eshoppingzone.order.dto.StockReservationItem;
import com.eshoppingzone.order.dto.StockReservationRequest;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderItem;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaOrchestrator;
import com.eshoppingzone.order.saga.repository.OrderSagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SagaRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryScheduler.class);

    private final OrderSagaStateRepository sagaStateRepository;
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final CartClient cartClient;
    private final CheckoutSagaOrchestrator orchestrator;
    private final RabbitTemplate rabbitTemplate;

    public SagaRecoveryScheduler(OrderSagaStateRepository sagaStateRepository,
                                 OrderRepository orderRepository,
                                 PaymentClient paymentClient,
                                 InventoryClient inventoryClient,
                                 CartClient cartClient,
                                 CheckoutSagaOrchestrator orchestrator,
                                 RabbitTemplate rabbitTemplate) {
        this.sagaStateRepository = sagaStateRepository;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.cartClient = cartClient;
        this.orchestrator = orchestrator;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<SagaStatus> recoverableStatuses = List.of(
                SagaStatus.PAYMENT_RECONCILIATION_REQUIRED,
                SagaStatus.INVENTORY_CONFIRMATION_FAILED,
                SagaStatus.CART_CLEAR_FAILED,
                SagaStatus.COMPENSATING,
                SagaStatus.INVENTORY_RESERVED
        );

        List<OrderSagaState> stuckSagas = sagaStateRepository.findByStatusInAndUpdatedAtBefore(recoverableStatuses, threshold);
        if (stuckSagas.isEmpty()) {
            return;
        }

        log.info("Found {} stuck/incomplete Saga(s) requiring recovery/reconciliation", stuckSagas.size());
        for (OrderSagaState saga : stuckSagas) {
            try {
                reconcileSaga(saga);
            } catch (Exception e) {
                log.warn("Failed to reconcile Saga [{}]: {}", saga.getSagaId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void reconcileSaga(OrderSagaState saga) {
        log.info("Reconciling Saga [{}] in state [{}] for Order ID [{}]", saga.getSagaId(), saga.getStatus(), saga.getOrderId());

        Optional<Order> orderOpt = orderRepository.findById(saga.getOrderId());
        if (orderOpt.isEmpty()) {
            log.error("Order [{}] not found for Saga [{}]. Marking FAILED.", saga.getOrderId(), saga.getSagaId());
            saga.setStatus(SagaStatus.FAILED);
            saga.setFailureReason("Order entity not found");
            sagaStateRepository.save(saga);
            return;
        }
        Order order = orderOpt.get();

        switch (saga.getStatus()) {
            case INVENTORY_RESERVED:
            case PAYMENT_RECONCILIATION_REQUIRED:
                reconcilePaymentTruth(saga, order);
                break;

            case INVENTORY_CONFIRMATION_FAILED:
                retryInventoryConfirmation(saga, order);
                break;

            case CART_CLEAR_FAILED:
                retryCartClear(saga, order);
                break;

            case COMPENSATING:
                retryCompensation(saga, order);
                break;

            default:
                break;
        }
    }

    private void reconcilePaymentTruth(OrderSagaState saga, Order order) {
        try {
            ApiResponse<PaymentResponseDto> paymentRes = paymentClient.getPaymentByOrderId(order.getId());
            if (paymentRes != null && paymentRes.getData() != null) {
                String paymentStatus = paymentRes.getData().getStatus();

                if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
                    log.info("Reconciliation found payment SUCCESS for Saga [{}]. Resuming forward flow...", saga.getSagaId());
                    orchestrator.validateTransition(saga.getStatus(), SagaStatus.PAYMENT_COMPLETED);
                    saga.setStatus(SagaStatus.PAYMENT_COMPLETED);
                    saga.addCompletedStep(SagaStep.PROCESS_PAYMENT);

                    // Forward step 3: confirm inventory
                    StockReservationRequest stockReq = createReservationRequest(order);
                    try {
                        inventoryClient.confirmStock(stockReq);
                        saga.setStatus(SagaStatus.INVENTORY_CONFIRMED);
                        saga.addCompletedStep(SagaStep.CONFIRM_INVENTORY);
                    } catch (Exception ex) {
                        log.warn("Inventory confirm error in recovery for Saga [{}]: {}", saga.getSagaId(), ex.getMessage());
                        saga.setStatus(SagaStatus.INVENTORY_CONFIRMATION_FAILED);
                    }

                    // Forward step 4: clear cart
                    try {
                        cartClient.clearCustomerCart(order.getCustomerId());
                        saga.setStatus(SagaStatus.CART_CLEARED);
                        saga.addCompletedStep(SagaStep.CLEAR_CART);
                    } catch (Exception ex) {
                        log.warn("Cart clear error in recovery for Saga [{}]: {}", saga.getSagaId(), ex.getMessage());
                        saga.setStatus(SagaStatus.CART_CLEAR_FAILED);
                    }

                    // Finalize
                    order.setStatus(OrderStatus.CONFIRMED);
                    order.setPaymentStatus(PaymentStatus.SUCCESS);
                    orderRepository.save(order);

                    saga.setStatus(SagaStatus.COMPLETED);
                    saga.setCurrentStep(SagaStep.FINALIZE_ORDER);
                    saga.addCompletedStep(SagaStep.FINALIZE_ORDER);
                    sagaStateRepository.save(saga);

                    publishOrderEvent(order, "ORDER_CONFIRMED", RabbitMQConfig.ORDER_CONFIRMED_ROUTING_KEY);
                    log.info("Saga [{}] successfully recovered and COMPLETED", saga.getSagaId());
                    return;

                } else if ("FAILED".equalsIgnoreCase(paymentStatus)) {
                    log.info("Reconciliation confirmed payment definitively FAILED for Saga [{}]. Executing compensation...", saga.getSagaId());
                    saga.setStatus(SagaStatus.COMPENSATING);
                    StockReservationRequest stockReq = createReservationRequest(order);
                    try {
                        inventoryClient.releaseStock(stockReq);
                    } catch (Exception ex) {
                        log.warn("Inventory release error in recovery for Saga [{}]: {}", saga.getSagaId(), ex.getMessage());
                    }
                    saga.setStatus(SagaStatus.COMPENSATED);
                    saga.addCompletedStep(SagaStep.COMPENSATE_INVENTORY);
                    saga.setStatus(SagaStatus.FAILED);
                    saga.setFailureStep(SagaStep.PROCESS_PAYMENT.name());
                    saga.setFailureReason("Payment failed");
                    sagaStateRepository.save(saga);

                    order.setStatus(OrderStatus.FAILED);
                    order.setPaymentStatus(PaymentStatus.FAILED);
                    orderRepository.save(order);

                    publishOrderEvent(order, "ORDER_FAILED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
                    log.info("Saga [{}] compensated and marked FAILED", saga.getSagaId());
                    return;
                }
            }
            log.warn("Payment truth for Saga [{}] remains UNKNOWN/PENDING. Preserving stock; awaiting next reconciliation cycle.", saga.getSagaId());
        } catch (Exception e) {
            log.warn("Could not reach PaymentClient during recovery for Saga [{}]: {}", saga.getSagaId(), e.getMessage());
        }
    }

    private void retryInventoryConfirmation(OrderSagaState saga, Order order) {
        StockReservationRequest stockReq = createReservationRequest(order);
        try {
            inventoryClient.confirmStock(stockReq);
            saga.addCompletedStep(SagaStep.CONFIRM_INVENTORY);
            saga.setStatus(SagaStatus.COMPLETED);
            sagaStateRepository.save(saga);
            log.info("Saga [{}] successfully confirmed inventory on retry", saga.getSagaId());
        } catch (Exception e) {
            log.warn("Saga [{}] inventory confirmation retry failed: {}", saga.getSagaId(), e.getMessage());
        }
    }

    private void retryCartClear(OrderSagaState saga, Order order) {
        try {
            cartClient.clearCustomerCart(order.getCustomerId());
            saga.addCompletedStep(SagaStep.CLEAR_CART);
            saga.setStatus(SagaStatus.COMPLETED);
            sagaStateRepository.save(saga);
            log.info("Saga [{}] successfully cleared cart on retry", saga.getSagaId());
        } catch (Exception e) {
            log.warn("Saga [{}] cart clear retry failed: {}", saga.getSagaId(), e.getMessage());
        }
    }

    private void retryCompensation(OrderSagaState saga, Order order) {
        StockReservationRequest stockReq = createReservationRequest(order);
        try {
            inventoryClient.releaseStock(stockReq);
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(saga);
            log.info("Saga [{}] compensation successfully finished on retry", saga.getSagaId());
        } catch (Exception e) {
            log.warn("Saga [{}] compensation retry failed: {}", saga.getSagaId(), e.getMessage());
        }
    }

    private StockReservationRequest createReservationRequest(Order order) {
        List<StockReservationItem> items = order.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        return new StockReservationRequest(order.getOrderNumber(), items);
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
