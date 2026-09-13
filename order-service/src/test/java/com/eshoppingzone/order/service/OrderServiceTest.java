package com.eshoppingzone.order.service;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.exception.InsufficientStockException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(1L, "ORD-12345678", 100L, 2L, new BigDecimal("100.00"), OrderStatus.CONFIRMED, PaymentMethod.WALLET, PaymentStatus.SUCCESS);
        sampleOrder.setItems(new ArrayList<>());
    }

    @Test
    void testCheckoutSuccess() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);
        CartItemDto cartItem = new CartItemDto(1L, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartDto cartDto = new CartDto(1L, 100L, List.of(cartItem), 2, new BigDecimal("100.00"));

        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Item", new BigDecimal("50.00"), "ACTIVE");
        PaymentResponseDto paymentResponse = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);

        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(cartDto));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentResponse));

        OrderDto result = orderService.checkout(100L, request);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(inventoryClient, times(1)).confirmStock(any());
        verify(cartClient, times(1)).clearCustomerCart(100L);
    }

    @Test
    void testCheckoutCodSuccess() {
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.COD);
        CartItemDto cartItem = new CartItemDto(1L, 1L, "Item", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartDto cartDto = new CartDto(1L, 100L, List.of(cartItem), 2, new BigDecimal("100.00"));

        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Item", new BigDecimal("50.00"), "ACTIVE");
        PaymentResponseDto paymentResponse = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "COD", "PENDING", "TXN-COD-1", null);

        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(cartDto));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        Order codOrder = new Order(1L, "ORD-12345678", 100L, 2L, new BigDecimal("100.00"), OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PENDING);
        codOrder.setItems(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenReturn(codOrder);
        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentResponse));

        OrderDto result = orderService.checkout(100L, request);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
        assertEquals(PaymentMethod.COD, result.getPaymentMethod());
        verify(paymentClient, times(1)).processPayment(any(ProcessPaymentRequest.class));
        verify(inventoryClient, times(1)).confirmStock(any());
        verify(cartClient, times(1)).clearCustomerCart(100L);
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
}
