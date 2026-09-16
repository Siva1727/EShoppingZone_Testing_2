package com.eshoppingzone.order.service;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.exception.IdempotencyConflictException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.exception.PaymentException;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaContext;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaOrchestrator;
import com.eshoppingzone.order.saga.orchestrator.SagaStateService;
import com.eshoppingzone.order.saga.util.RequestFingerprintUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartClient cartClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private ProfileClient profileClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private CheckoutSagaOrchestrator checkoutSagaOrchestrator;

    @Mock
    private SagaStateService sagaStateService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(1L, "ORD-12345678", 100L, 2L, new BigDecimal("100.00"),
                OrderStatus.CONFIRMED, PaymentMethod.WALLET, PaymentStatus.SUCCESS);
        sampleOrder.setItems(new ArrayList<>());
    }

    @Test
    void testCheckoutDelegatesToSagaOrchestrator() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        CartItemDto cartItem = new CartItemDto(1L, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartDto cartDto = new CartDto(1L, 100L, List.of(cartItem), 2, new BigDecimal("100.00"));
        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Item", new BigDecimal("50.00"), "ACTIVE");

        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(cartDto));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
        when(checkoutSagaOrchestrator.executeSaga(any(CheckoutSagaContext.class)))
                .thenReturn(OrderDto.fromEntity(sampleOrder));

        OrderDto result = orderService.checkout(100L, request);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(checkoutSagaOrchestrator, times(1)).executeSaga(any(CheckoutSagaContext.class));
    }

    @Test
    void test1_SameKey_SameCustomer_SameRequest_ReturnsExistingOrder() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        String idempotencyKey = "IDEMP-ABC-123";
        OrderItem orderItem = new OrderItem(1L, sampleOrder, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        sampleOrder.setItems(List.of(orderItem));

        CartItemDto cartItem = new CartItemDto(null, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        String expectedFingerprint = RequestFingerprintUtil.computeFingerprint(100L, List.of(cartItem), request);

        OrderSagaState completedSaga = new OrderSagaState("SAGA-1", idempotencyKey, expectedFingerprint, 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        when(sagaStateService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(completedSaga));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        OrderDto result = orderService.checkout(100L, request, idempotencyKey);

        assertNotNull(result);
        assertEquals("ORD-12345678", result.getOrderNumber());
        // Verify cartClient is never called
        verify(cartClient, never()).getCartByCustomerId(any());
        verify(productClient, never()).getProductById(any());
        verify(orderRepository, never()).save(any());
        verify(checkoutSagaOrchestrator, never()).executeSaga(any());
    }

    @Test
    void test2_SameKey_DifferentCustomer_ThrowsException() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        String idempotencyKey = "IDEMP-ABC-123";

        // Saga was started by customer 200L, but customer 100L is calling with same key
        OrderSagaState existingSaga = new OrderSagaState("SAGA-1", idempotencyKey, "FP-200", 1L, "ORD-12345678",
                200L, "WALLET", new BigDecimal("100.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        when(sagaStateService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingSaga));

        assertThrows(InvalidOrderStateException.class, () ->
                orderService.checkout(100L, request, idempotencyKey));
        verify(cartClient, never()).getCartByCustomerId(any());
        verify(checkoutSagaOrchestrator, never()).executeSaga(any());
    }

    @Test
    void test3_SameKey_SameCustomer_DifferentCart_ThrowsConflictException() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        String idempotencyKey = "IDEMP-ABC-123";
        CartItemDto originalItem = new CartItemDto(null, 1L, "Item 1", new BigDecimal("50.00"), 1, new BigDecimal("50.00"));
        String originalFingerprint = RequestFingerprintUtil.computeFingerprint(100L, List.of(originalItem), request);

        OrderSagaState existingSaga = new OrderSagaState("SAGA-1", idempotencyKey, originalFingerprint, 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("50.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        // Order has changed items compared to original fingerprint
        OrderItem changedOrderItem = new OrderItem(1L, sampleOrder, 2L, "Item 2", new BigDecimal("70.00"), 1, new BigDecimal("70.00"));
        sampleOrder.setItems(List.of(changedOrderItem));

        when(sagaStateService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingSaga));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        assertThrows(IdempotencyConflictException.class, () ->
                orderService.checkout(100L, request, idempotencyKey));
        verify(cartClient, never()).getCartByCustomerId(any());
        verify(checkoutSagaOrchestrator, never()).executeSaga(any());
    }

    @Test
    void test4_SameKey_SameCustomer_DifferentPaymentMethod_ThrowsConflictException() {
        CheckoutRequest originalRequest = new CheckoutRequest(null, PaymentMethod.WALLET);
        String idempotencyKey = "IDEMP-ABC-123";
        CartItemDto item = new CartItemDto(null, 1L, "Item 1", new BigDecimal("50.00"), 1, new BigDecimal("50.00"));
        String originalFingerprint = RequestFingerprintUtil.computeFingerprint(100L, List.of(item), originalRequest);

        OrderSagaState existingSaga = new OrderSagaState("SAGA-1", idempotencyKey, originalFingerprint, 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("50.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        OrderItem orderItem = new OrderItem(1L, sampleOrder, 1L, "Item 1", new BigDecimal("50.00"), 1, new BigDecimal("50.00"));
        sampleOrder.setItems(List.of(orderItem));

        CheckoutRequest newRequest = new CheckoutRequest(null, PaymentMethod.COD);

        when(sagaStateService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingSaga));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        assertThrows(IdempotencyConflictException.class, () ->
                orderService.checkout(100L, newRequest, idempotencyKey));
        verify(cartClient, never()).getCartByCustomerId(any());
        verify(checkoutSagaOrchestrator, never()).executeSaga(any());
    }

    @Test
    void test5_SameKey_SameCustomer_DifferentShippingAddress_ThrowsConflictException() {
        CheckoutRequest originalRequest = new CheckoutRequest(1L, PaymentMethod.WALLET);
        String idempotencyKey = "IDEMP-ABC-123";
        CartItemDto item = new CartItemDto(null, 1L, "Item 1", new BigDecimal("50.00"), 1, new BigDecimal("50.00"));
        String originalFingerprint = RequestFingerprintUtil.computeFingerprint(100L, List.of(item), originalRequest);

        OrderSagaState existingSaga = new OrderSagaState("SAGA-1", idempotencyKey, originalFingerprint, 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("50.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        OrderItem orderItem = new OrderItem(1L, sampleOrder, 1L, "Item 1", new BigDecimal("50.00"), 1, new BigDecimal("50.00"));
        sampleOrder.setItems(List.of(orderItem));

        CheckoutRequest newRequest = new CheckoutRequest(2L, PaymentMethod.WALLET);

        when(sagaStateService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingSaga));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        assertThrows(IdempotencyConflictException.class, () ->
                orderService.checkout(100L, newRequest, idempotencyKey));
        verify(cartClient, never()).getCartByCustomerId(any());
        verify(checkoutSagaOrchestrator, never()).executeSaga(any());
    }

    @Test
    void test6_NoIdempotencyKey_GeneratesFreshUUID_ExecutesIndependentSaga() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        CartItemDto cartItem = new CartItemDto(1L, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartDto cartDto = new CartDto(1L, 100L, List.of(cartItem), 2, new BigDecimal("100.00"));
        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Item", new BigDecimal("50.00"), "ACTIVE");

        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(cartDto));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
        when(checkoutSagaOrchestrator.executeSaga(any(CheckoutSagaContext.class)))
                .thenReturn(OrderDto.fromEntity(sampleOrder));

        OrderDto result = orderService.checkout(100L, request, null);

        assertNotNull(result);
        verify(checkoutSagaOrchestrator, times(1)).executeSaga(any(CheckoutSagaContext.class));
    }

    @Test
    void testCheckoutEmptyCartThrowsException() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(new CartDto(1L, 100L, List.of(), null, null)));

        assertThrows(InvalidOrderStateException.class, () -> orderService.checkout(100L, request));
    }

    @Test
    void testCancelOrderSuccess() {
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndCustomerId(1L, 100L)).thenReturn(Optional.of(sampleOrder));
        when(inventoryClient.releaseStock(any())).thenReturn(ApiResponse.success(null));

        OrderDto result = orderService.cancelOrder(1L, 100L, "Changed mind");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryClient, times(1)).releaseStock(any());
    }

    @Test
    void testProcessMerchantOrderSuccess() {
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndMerchantId(1L, 2L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderDto result = orderService.processMerchantOrder(1L, 2L);

        assertEquals(OrderStatus.PROCESSING, result.getStatus());
    }

    @Test
    void testReadyForDeliverySuccess() {
        sampleOrder.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdAndMerchantId(1L, 2L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderDto result = orderService.readyForDelivery(1L, 2L);

        assertEquals(OrderStatus.READY_FOR_DELIVERY, result.getStatus());
    }

    @Test
    void testRejectMerchantOrderSuccess() {
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndMerchantId(1L, 2L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
        when(inventoryClient.releaseStock(any())).thenReturn(ApiResponse.success(null));

        OrderDto result = orderService.rejectMerchantOrder(1L, 2L, "Out of stock");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryClient, times(1)).releaseStock(any());
    }
}
