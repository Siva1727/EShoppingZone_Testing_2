package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.client.OrderClient;
import com.eshoppingzone.payment.client.WalletClient;
import com.eshoppingzone.payment.dto.*;
import com.eshoppingzone.payment.entity.*;
import com.eshoppingzone.payment.exception.InvalidRefundException;
import com.eshoppingzone.payment.exception.PaymentException;
import com.eshoppingzone.payment.repository.PaymentRepository;
import com.eshoppingzone.payment.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Payment mockPayment;
    private Refund mockRefund;

    @BeforeEach
    void setUp() {
        mockPayment = new Payment(1L, 100L, 4L, new BigDecimal("250.00"), PaymentMethod.WALLET, PaymentStatus.SUCCESS, "TXN-12345");
        mockRefund = new Refund(10L, 1L, 100L, 4L, new BigDecimal("250.00"), "Defective product", RefundStatus.PENDING, "REF-12345");
    }

    @Test
    void testProcessPaymentWalletSuccess() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(100L, 4L, new BigDecimal("250.00"), PaymentMethod.WALLET);

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(walletClient.debit(any(WalletTransferRequest.class)))
                .thenReturn(ApiResponse.success("Debited", new WalletDto(1L, 4L, new BigDecimal("750.00"))));
        when(walletClient.credit(any(WalletTransferRequest.class)))
                .thenReturn(ApiResponse.success("Credited", new WalletDto(2L, 1L, new BigDecimal("1250.00"))));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentDto result = paymentService.processPayment(req);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(PaymentMethod.WALLET, result.getPaymentMethod());
        verify(walletClient).debit(any(WalletTransferRequest.class));
        verify(walletClient).credit(any(WalletTransferRequest.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PaymentEvent.class));
    }

    @Test
    void testProcessPaymentWalletFailure() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(100L, 4L, new BigDecimal("250.00"), PaymentMethod.WALLET);

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(walletClient.debit(any(WalletTransferRequest.class)))
                .thenThrow(new RuntimeException("Insufficient wallet balance"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        assertThrows(PaymentException.class, () -> paymentService.processPayment(req));
        verify(paymentRepository, atLeastOnce()).save(any(Payment.class));
    }

    @Test
    void testProcessPaymentCodSuccess() {
        ProcessPaymentRequest req = new ProcessPaymentRequest(101L, 4L, new BigDecimal("150.00"), PaymentMethod.COD);

        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentDto result = paymentService.processPayment(req);

        assertNotNull(result);
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertEquals(PaymentMethod.COD, result.getPaymentMethod());
        verifyNoInteractions(walletClient);
    }

    @Test
    void testGetPaymentByOrderId() {
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(mockPayment));

        PaymentDto result = paymentService.getPaymentByOrderId(100L);

        assertNotNull(result);
        assertEquals(100L, result.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
    }

    @Test
    void testCompleteCodPayment() {
        Payment codPayment = new Payment(2L, 102L, 4L, new BigDecimal("100.00"), PaymentMethod.COD, PaymentStatus.PENDING, "COD-102");
        when(paymentRepository.findByOrderId(102L)).thenReturn(Optional.of(codPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentDto result = paymentService.completeCodPayment(102L);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PaymentEvent.class));
    }

    @Test
    void testRequestRefundSuccess() {
        RefundRequest request = new RefundRequest(100L, new BigDecimal("250.00"), "Product broken");

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(mockPayment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            r.setId(10L);
            return r;
        });

        RefundDto result = paymentService.requestRefund(4L, request);

        assertNotNull(result);
        assertEquals(RefundStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("250.00"), result.getAmount());
    }

    @Test
    void testApproveRefundSuccess() {
        when(refundRepository.findById(10L)).thenReturn(Optional.of(mockRefund));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(mockPayment));
        when(walletClient.debit(any(WalletTransferRequest.class)))
                .thenReturn(ApiResponse.success("Platform debited", new WalletDto(1L, 1L, new BigDecimal("5000.00"))));
        when(walletClient.credit(any(WalletTransferRequest.class)))
                .thenReturn(ApiResponse.success("Customer credited", new WalletDto(2L, 4L, new BigDecimal("1000.00"))));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));

        RefundDto result = paymentService.approveRefund(10L);

        assertNotNull(result);
        assertEquals(RefundStatus.APPROVED, result.getStatus());
        verify(walletClient).debit(any(WalletTransferRequest.class));
        verify(walletClient).credit(any(WalletTransferRequest.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PaymentEvent.class));
    }

    @Test
    void testRejectRefundSuccess() {
        when(refundRepository.findById(10L)).thenReturn(Optional.of(mockRefund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));

        RefundDto result = paymentService.rejectRefund(10L, "Out of return window");

        assertNotNull(result);
        assertEquals(RefundStatus.REJECTED, result.getStatus());
    }
}
