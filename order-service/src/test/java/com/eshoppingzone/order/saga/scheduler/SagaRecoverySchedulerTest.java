package com.eshoppingzone.order.saga.scheduler;

import com.eshoppingzone.order.client.CartClient;
import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.PaymentResponseDto;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaOrchestrator;
import com.eshoppingzone.order.saga.repository.OrderSagaStateRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaRecoverySchedulerTest {

    @Mock
    private OrderSagaStateRepository sagaStateRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private CartClient cartClient;

    @Mock
    private CheckoutSagaOrchestrator orchestrator;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SagaRecoveryScheduler recoveryScheduler;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(1L, "ORD-12345678", 100L, 2L, new BigDecimal("100.00"),
                OrderStatus.PENDING_PAYMENT, PaymentMethod.WALLET, PaymentStatus.PENDING);
        sampleOrder.setItems(new ArrayList<>());
    }

    @Test
    void testRecovery_PaymentReconciliation_ResolvesSuccess() {
        OrderSagaState saga = new OrderSagaState("SAGA-REC-1", "IDEMP-REC-1", "FP-1", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.PROCESS_PAYMENT, SagaStatus.PAYMENT_RECONCILIATION_REQUIRED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "SUCCESS", "TXN-1", null);
        when(paymentClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.success(paymentRes));
        when(inventoryClient.confirmStock(any())).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        recoveryScheduler.reconcileSaga(saga);

        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        assertEquals(OrderStatus.CONFIRMED, sampleOrder.getStatus());
        assertEquals(PaymentStatus.SUCCESS, sampleOrder.getPaymentStatus());
        verify(orderRepository).save(sampleOrder);
        verify(sagaStateRepository).save(saga);
    }

    @Test
    void testRecovery_PaymentReconciliation_ResolvesFailed() {
        OrderSagaState saga = new OrderSagaState("SAGA-REC-2", "IDEMP-REC-2", "FP-2", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.PROCESS_PAYMENT, SagaStatus.PAYMENT_RECONCILIATION_REQUIRED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "FAILED", "TXN-1", null);
        when(paymentClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.success(paymentRes));

        recoveryScheduler.reconcileSaga(saga);

        assertEquals(SagaStatus.FAILED, saga.getStatus());
        assertEquals(OrderStatus.FAILED, sampleOrder.getStatus());
        verify(inventoryClient, times(1)).releaseStock(any());
        verify(orderRepository).save(sampleOrder);
        verify(sagaStateRepository).save(saga);
    }

    @Test
    void testRecovery_InventoryReserved_NeverBlindlyReleases_UnlessPaymentFailed() {
        OrderSagaState saga = new OrderSagaState("SAGA-REC-3", "IDEMP-REC-3", "FP-3", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.PROCESS_PAYMENT, SagaStatus.INVENTORY_RESERVED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        // Payment is still PENDING/UNKNOWN
        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("100.00"), "WALLET", "PENDING", "TXN-1", null);
        when(paymentClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.success(paymentRes));

        recoveryScheduler.reconcileSaga(saga);

        // Must NOT release inventory when payment truth is still pending!
        verify(inventoryClient, never()).releaseStock(any());
        assertEquals(SagaStatus.INVENTORY_RESERVED, saga.getStatus());
    }

    @Test
    void testRecovery_InventoryConfirmation_RetriesConfirm() {
        OrderSagaState saga = new OrderSagaState("SAGA-REC-4", "IDEMP-REC-4", "FP-4", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.CONFIRM_INVENTORY, SagaStatus.INVENTORY_CONFIRMATION_FAILED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(inventoryClient.confirmStock(any())).thenReturn(ApiResponse.success(null));

        recoveryScheduler.reconcileSaga(saga);

        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        verify(inventoryClient, times(1)).confirmStock(any());
        verify(sagaStateRepository).save(saga);
    }

    @Test
    void testRecovery_CartClear_RetriesClear() {
        OrderSagaState saga = new OrderSagaState("SAGA-REC-5", "IDEMP-REC-5", "FP-5", 1L, "ORD-12345678",
                100L, "WALLET", new BigDecimal("100.00"), SagaStep.CLEAR_CART, SagaStatus.CART_CLEAR_FAILED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));

        recoveryScheduler.reconcileSaga(saga);

        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        verify(cartClient, times(1)).clearCustomerCart(100L);
        verify(sagaStateRepository).save(saga);
    }
}
