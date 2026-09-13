package com.eshoppingzone.order.service;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.exception.InsufficientStockException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.exception.PaymentException;
import com.eshoppingzone.order.exception.ResourceNotFoundException;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartClient cartClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ProfileClient profileClient;
    private final RabbitTemplate rabbitTemplate;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CartClient cartClient,
                            ProductClient productClient,
                            InventoryClient inventoryClient,
                            PaymentClient paymentClient,
                            ProfileClient profileClient,
                            RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartClient = cartClient;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.profileClient = profileClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public OrderDto checkout(Long customerId, CheckoutRequest request) {
        log.info("Starting checkout for customer ID: {}, PaymentMethod: {}", customerId, request.getPaymentMethod());

        // 1. Fetch items from Customer's Cart
        ApiResponse<CartDto> cartResponse = cartClient.getCartByCustomerId(customerId);
        if (cartResponse == null || cartResponse.getData() == null || cartResponse.getData().getItems().isEmpty()) {
            throw new InvalidOrderStateException("Shopping cart is empty. Cannot place order.");
        }

        CartDto cart = cartResponse.getData();
        List<CartItemDto> cartItems = cart.getItems();

        // 2. Fetch shipping address
        String street = request.getShippingStreet();
        String city = request.getShippingCity();
        String state = request.getShippingState();
        String postalCode = request.getShippingPostalCode();
        String country = request.getShippingCountry();

        if (request.getAddressId() != null) {
            try {
                ApiResponse<AddressDto> addressResponse = profileClient.getAddressById(request.getAddressId());
                if (addressResponse != null && addressResponse.getData() != null) {
                    AddressDto addr = addressResponse.getData();
                    street = addr.getStreet();
                    city = addr.getCity();
                    state = addr.getState();
                    postalCode = addr.getPostalCode();
                    country = addr.getCountry();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch address details from ProfileClient: {}", e.getMessage());
            }
        }

        if (street == null || street.trim().isEmpty()) {
            street = "123 Default Street";
            city = "City";
            state = "State";
            postalCode = "12345";
            country = "Country";
        }

        // 3. Create initial Order
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomerId(customerId);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShippingStreet(street);
        order.setShippingCity(city);
        order.setShippingState(state);
        order.setShippingPostalCode(postalCode);
        order.setShippingCountry(country);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;
        Long inferredMerchantId = null;

        List<OrderItem> orderItems = new ArrayList<>();
        List<StockReservationItem> reservationItems = new ArrayList<>();

        for (CartItemDto cartItem : cartItems) {
            // Fetch live product info for merchant and snapshot
            String productName = cartItem.getProductName();
            BigDecimal unitPrice = cartItem.getUnitPrice();
            try {
                ApiResponse<ProductSnapshotDto> productRes = productClient.getProductById(cartItem.getProductId());
                if (productRes != null && productRes.getData() != null) {
                    productName = productRes.getData().getName();
                    unitPrice = productRes.getData().getPrice();
                    if (inferredMerchantId == null) {
                        inferredMerchantId = productRes.getData().getMerchantId();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve latest product details: {}", e.getMessage());
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setProductName(productName);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);

            reservationItems.add(new StockReservationItem(cartItem.getProductId(), cartItem.getQuantity()));
        }

        order.setMerchantId(inferredMerchantId != null ? inferredMerchantId : 2L);
        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // 4. Reserve stock in Inventory Service
        StockReservationRequest stockReq = new StockReservationRequest(orderNumber, reservationItems);
        try {
            ApiResponse<Void> stockRes = inventoryClient.reserveStock(stockReq);
            if (stockRes != null && !stockRes.isSuccess()) {
                savedOrder.setStatus(OrderStatus.FAILED);
                orderRepository.save(savedOrder);
                throw new InsufficientStockException("Inventory reservation failed for order items.");
            }
        } catch (Exception e) {
            savedOrder.setStatus(OrderStatus.FAILED);
            orderRepository.save(savedOrder);
            throw new InsufficientStockException("Failed to reserve inventory: " + e.getMessage());
        }

        // 5. Process Payment
        if (request.getPaymentMethod() == PaymentMethod.WALLET) {
            ProcessPaymentRequest payReq = new ProcessPaymentRequest(
                    savedOrder.getId(),
                    customerId,
                    totalAmount,
                    PaymentMethod.WALLET
            );

            boolean paymentSuccess = false;
            try {
                ApiResponse<PaymentResponseDto> payRes = paymentClient.processPayment(payReq);
                if (payRes != null && payRes.isSuccess() && payRes.getData() != null && "SUCCESS".equalsIgnoreCase(payRes.getData().getStatus())) {
                    paymentSuccess = true;
                }
            } catch (Exception e) {
                log.error("Payment processing error: {}", e.getMessage());
            }

            if (paymentSuccess) {
                savedOrder.setStatus(OrderStatus.CONFIRMED);
                savedOrder.setPaymentStatus(PaymentStatus.SUCCESS);
                orderRepository.save(savedOrder);

                // Confirm stock consumption
                try {
                    inventoryClient.confirmStock(stockReq);
                } catch (Exception e) {
                    log.warn("Failed to confirm stock consumption: {}", e.getMessage());
                }

                // Clear customer cart
                try {
                    cartClient.clearCustomerCart(customerId);
                } catch (Exception e) {
                    log.warn("Failed to clear cart: {}", e.getMessage());
                }

                // Publish RabbitMQ events
                publishOrderEvent(savedOrder, "ORDER_CONFIRMED", RabbitMQConfig.ORDER_CONFIRMED_ROUTING_KEY);
            } else {
                // Payment failed -> release reserved stock and mark order FAILED
                savedOrder.setStatus(OrderStatus.FAILED);
                savedOrder.setPaymentStatus(PaymentStatus.FAILED);
                orderRepository.save(savedOrder);

                try {
                    inventoryClient.releaseStock(stockReq);
                } catch (Exception e) {
                    log.warn("Failed to release stock after payment failure: {}", e.getMessage());
                }

                publishOrderEvent(savedOrder, "ORDER_FAILED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
                throw new PaymentException("Payment failed. Insufficient wallet balance or payment error.");
            }
        } else if (request.getPaymentMethod() == PaymentMethod.COD) {
            // Cash on Delivery -> Order is confirmed, payment status remains PENDING until delivery
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            savedOrder.setPaymentStatus(PaymentStatus.PENDING);
            orderRepository.save(savedOrder);

            // Initialize COD Payment record in Payment Service
            try {
                ProcessPaymentRequest payReq = new ProcessPaymentRequest(
                        savedOrder.getId(),
                        customerId,
                        totalAmount,
                        PaymentMethod.COD
                );
                paymentClient.processPayment(payReq);
            } catch (Exception e) {
                log.warn("Failed to initialize COD payment in Payment Service: {}", e.getMessage());
            }

            // Confirm stock consumption for COD
            try {
                inventoryClient.confirmStock(stockReq);
            } catch (Exception e) {
                log.warn("Failed to confirm stock for COD order: {}", e.getMessage());
            }

            // Clear customer cart
            try {
                cartClient.clearCustomerCart(customerId);
            } catch (Exception e) {
                log.warn("Failed to clear cart: {}", e.getMessage());
            }

            publishOrderEvent(savedOrder, "ORDER_CONFIRMED", RabbitMQConfig.ORDER_CONFIRMED_ROUTING_KEY);
        }

        return OrderDto.fromEntity(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Order cannot be cancelled in current state: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Release stock
        List<StockReservationItem> items = order.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            inventoryClient.releaseStock(new StockReservationRequest(order.getOrderNumber(), items));
        } catch (Exception e) {
            log.warn("Failed to release stock on order cancellation: {}", e.getMessage());
        }

        publishOrderEvent(order, "ORDER_CANCELLED", RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY);
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto requestReturn(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException("Return can only be requested for delivered orders");
        }

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "RETURN_REQUESTED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getMerchantOrders(Long merchantId) {
        return orderRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getMerchantOrderById(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId + " for merchant: " + merchantId));
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto processMerchantOrder(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Only CONFIRMED orders can be processed. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.PROCESSING);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "ORDER_PROCESSING", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto readyForDelivery(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException("Only PROCESSING orders can be marked READY_FOR_DELIVERY");
        }

        order.setStatus(OrderStatus.READY_FOR_DELIVERY);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "READY_FOR_DELIVERY", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto rejectMerchantOrder(Long orderId, Long merchantId, String reason) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException("Order cannot be rejected in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        // Release stock
        List<StockReservationItem> items = order.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            inventoryClient.releaseStock(new StockReservationRequest(order.getOrderNumber(), items));
        } catch (Exception e) {
            log.warn("Failed to release stock on order rejection: {}", e.getMessage());
        }

        publishOrderEvent(saved, "ORDER_REJECTED", RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrdersAdmin() {
        return orderRepository.findAll().stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatusAdmin(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        order.setStatus(status);
        if (status == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.SUCCESS);
        }

        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "ORDER_STATUS_UPDATED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatusInternal(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        order.setStatus(status);
        if (status == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.SUCCESS);
        }

        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "ORDER_STATUS_UPDATED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrderInternal(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderDto.fromEntity(order);
    }

    private void publishOrderEvent(Order order, String eventType, String routingKey) {
        try {
            OrderEvent event = new OrderEvent(
                    eventType,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    order.getTotalAmount(),
                    order.getStatus().name(),
                    order.getPaymentMethod().name(),
                    order.getPaymentStatus().name()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event);
        } catch (Exception e) {
            log.warn("Failed to publish order event {}: {}", eventType, e.getMessage());
        }
    }
}
