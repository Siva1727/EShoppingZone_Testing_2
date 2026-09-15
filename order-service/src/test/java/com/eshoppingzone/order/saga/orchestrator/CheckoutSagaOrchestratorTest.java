package com.eshoppingzone.order.saga.orchestrator;

import com.eshoppingzone.order.client.CartClient;
import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.exception.InsufficientStockException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.exception.PaymentException;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutSagaOrchestratorTest {

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private CartClient cartClient;

    @Mock
    private SagaStateService sagaStateService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CheckoutSagaOrchestrator orchestrator;

    private Order sampleOrder;
    private List<StockReservationItem> reservationItems;
    private CheckoutRequest walletRequest;
    private CheckoutRequest codRequest;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(1L, "ORD-12345678", 100L, 2L, new BigDecimal("100.00"),
                OrderStatus.PENDING_PAYMENT, PaymentMethod.WALLET, PaymentStatus.PENDING);
        sampleOrder.setItems(new ArrayList<>());

        reservationItems = List.of(new StockReservationItem(10L, 2));
        walletRequest = new CheckoutRequest(1L, PaymentMethod.WALLET);
        codRequest = new CheckoutRequest(1L, PaymentMethod.COD);
    }

    @Test
    void testWalletCheckout_Success() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-1", "IDEMP-1", "FP-1", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentRes));
        when(inventoryClient.confirmStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(PaymentStatus.SUCCESS, result.getPaymentStatus());

        verify(sagaStateService).initSagaState("SAGA-1", "IDEMP-1", "FP-1", sampleOrder, SagaStep.RESERVE_INVENTORY);
        verify(sagaStateService).updateState("SAGA-1", SagaStep.PROCESS_PAYMENT, SagaStatus.INVENTORY_RESERVED, SagaStep.RESERVE_INVENTORY);
        verify(sagaStateService).updateState("SAGA-1", SagaStep.CONFIRM_INVENTORY, SagaStatus.PAYMENT_COMPLETED, SagaStep.PROCESS_PAYMENT);
        verify(sagaStateService).updateState("SAGA-1", SagaStep.CLEAR_CART, SagaStatus.INVENTORY_CONFIRMED, SagaStep.CONFIRM_INVENTORY);
        verify(sagaStateService).updateState("SAGA-1", SagaStep.FINALIZE_ORDER, SagaStatus.CART_CLEARED, SagaStep.CLEAR_CART);
        verify(sagaStateService).updateState("SAGA-1", SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED, SagaStep.FINALIZE_ORDER);
        verify(inventoryClient, times(1)).confirmStock(any());
        verify(cartClient, times(1)).clearCustomerCart(100L);
    }

    @Test
    void testWalletCheckout_InventoryOutOfStock() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-2", "IDEMP-2", "FP-2", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class)))
                .thenThrow(new RuntimeException("Insufficient stock"));

        assertThrows(InsufficientStockException.class, () -> orchestrator.executeSaga(context));

        verify(sagaStateService).updateState("SAGA-2", SagaStep.RESERVE_INVENTORY, SagaStatus.FAILED, null);
        verify(sagaStateService).updateOrderStatus(1L, OrderStatus.FAILED, PaymentStatus.FAILED);
        verify(paymentClient, never()).processPayment(any());
    }

    @Test
    void testWalletCheckout_PaymentFailed_CompensatesInventory() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-3", "IDEMP-3", "FP-3", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "FAILED", "TXN-1", null);
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentRes));

        assertThrows(PaymentException.class, () -> orchestrator.executeSaga(context));

        verify(inventoryClient, times(1)).releaseStock(any());
        verify(cartClient, never()).clearCustomerCart(any());
        verify(sagaStateService).updateState("SAGA-3", SagaStep.COMPENSATE_INVENTORY, SagaStatus.COMPENSATING, null);
        verify(sagaStateService).updateState("SAGA-3", SagaStep.COMPENSATE_INVENTORY, SagaStatus.COMPENSATED, SagaStep.COMPENSATE_INVENTORY);
        verify(sagaStateService).updateState("SAGA-3", SagaStep.PROCESS_PAYMENT, SagaStatus.FAILED, null);
        verify(sagaStateService).updateOrderStatus(1L, OrderStatus.FAILED, PaymentStatus.FAILED);
    }

    @Test
    void testWalletCheckout_PaymentTimeout_VerifiedSuccess() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-4", "IDEMP-4", "FP-4", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenThrow(new RuntimeException("SocketTimeoutException"));
        PaymentResponseDto lookupRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);
        when(paymentClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.success(lookupRes));
        when(inventoryClient.confirmStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(inventoryClient, never()).releaseStock(any());
        verify(inventoryClient, times(1)).confirmStock(any());
    }

    @Test
    void testWalletCheckout_PaymentTimeout_VerifiedFailed() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-5", "IDEMP-5", "FP-5", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenThrow(new RuntimeException("500 Internal Server Error"));
        PaymentResponseDto lookupRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "FAILED", "TXN-1", null);
        when(paymentClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.success(lookupRes));

        assertThrows(PaymentException.class, () -> orchestrator.executeSaga(context));

        verify(inventoryClient, times(1)).releaseStock(any());
        verify(sagaStateService).updateOrderStatus(1L, OrderStatus.FAILED, PaymentStatus.FAILED);
    }

    @Test
    void testWalletCheckout_PaymentTimeout_Unreachable_ReconciliationRequired() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-6", "IDEMP-6", "FP-6", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenThrow(new RuntimeException("Read timeout"));
        when(paymentClient.getPaymentByOrderId(1L)).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(PaymentException.class, () -> orchestrator.executeSaga(context));

        // Must NOT release stock when payment truth is unknown!
        verify(inventoryClient, never()).releaseStock(any());
        verify(sagaStateService).updateState("SAGA-6", SagaStep.PROCESS_PAYMENT, SagaStatus.PAYMENT_RECONCILIATION_REQUIRED, null);
        verify(sagaStateService).updateOrderStatus(1L, OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING);
    }

    @Test
    void testWalletCheckout_ConfirmStockFailure_PreservesOrder() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-7", "IDEMP-7", "FP-7", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentRes));
        when(inventoryClient.confirmStock(any())).thenThrow(new RuntimeException("Inventory service temporarily down"));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(sagaStateService).updateState("SAGA-7", SagaStep.CLEAR_CART, SagaStatus.INVENTORY_CONFIRMATION_FAILED, null);
    }

    @Test
    void testWalletCheckout_ClearCartFailure_PreservesOrder() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-8", "IDEMP-8", "FP-8", 100L, sampleOrder, reservationItems, walletRequest);

        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentRes));
        when(inventoryClient.confirmStock(any())).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenThrow(new RuntimeException("Cart clear timeout"));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(sagaStateService).updateState("SAGA-8", SagaStep.FINALIZE_ORDER, SagaStatus.CART_CLEAR_FAILED, null);
    }

    @Test
    void testCodCheckout_Success() {
        Order codOrder = new Order(2L, "ORD-COD-1", 100L, 2L, new BigDecimal("100.00"),
                OrderStatus.PENDING_PAYMENT, PaymentMethod.COD, PaymentStatus.PENDING);
        codOrder.setItems(new ArrayList<>());
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-COD-1", "IDEMP-COD-1", "FP-COD", 100L, codOrder, reservationItems, codRequest);

        when(inventoryClient.reserveStock(any())).thenReturn(ApiResponse.success(null));
        when(paymentClient.processPayment(any())).thenReturn(ApiResponse.success(null));
        when(inventoryClient.confirmStock(any())).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());

        verify(sagaStateService).updateState("SAGA-COD-1", SagaStep.CONFIRM_INVENTORY, SagaStatus.COD_PAYMENT_PENDING, SagaStep.PROCESS_PAYMENT);
        verify(sagaStateService).updateState("SAGA-COD-1", SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED, SagaStep.FINALIZE_ORDER);
    }

    @Test
    void testConcurrentIdempotencyCollision_ReusesWinningOrder() {
        CheckoutSagaContext context = new CheckoutSagaContext(
                "SAGA-CONC-2", "IDEMP-SAME", "FP-SAME", 100L, sampleOrder, reservationItems, walletRequest);

        // Simulate unique constraint collision on second thread
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry 'IDEMP-SAME' for key 'uk_saga_idempotency'"))
                .when(sagaStateService).initSagaState(eq("SAGA-CONC-2"), eq("IDEMP-SAME"), eq("FP-SAME"), any(), any());

        OrderSagaState winningSaga = new OrderSagaState("SAGA-CONC-1", "IDEMP-SAME", "FP-SAME", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.FINALIZE_ORDER, SagaStatus.COMPLETED);

        when(sagaStateService.findByIdempotencyKey("IDEMP-SAME")).thenReturn(java.util.Optional.of(winningSaga));
        when(sagaStateService.findOrderById(1L)).thenReturn(java.util.Optional.of(sampleOrder));

        OrderDto result = orchestrator.executeSaga(context);

        assertNotNull(result);
        assertEquals("ORD-12345678", result.getOrderNumber());
        // Verify duplicate draft order cleanup
        verify(sagaStateService, times(1)).deleteOrderById(1L);
        // Verify no second payment or inventory reservation call occurred
        verify(inventoryClient, never()).reserveStock(any());
        verify(paymentClient, never()).processPayment(any());
    }

    @Test
    void testIllegalStateTransition_ThrowsException() {
        assertThrows(InvalidOrderStateException.class, () ->
                orchestrator.validateTransition(SagaStatus.STARTED, SagaStatus.COMPLETED));

        assertThrows(InvalidOrderStateException.class, () ->
                orchestrator.validateTransition(SagaStatus.COMPLETED, SagaStatus.FAILED));
    }
}
