package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.dto.*;

import java.util.List;

public interface PaymentService {
    PaymentDto processPayment(ProcessPaymentRequest request);
    PaymentDto getPaymentByOrderId(Long orderId);
    List<PaymentDto> getMyPayments(Long customerId);
    PaymentDto completeCodPayment(Long orderId);

    RefundDto requestRefund(Long customerId, RefundRequest request);
    List<RefundDto> getPendingRefunds();
    RefundDto approveRefund(Long refundId);
    RefundDto rejectRefund(Long refundId, String reason);
    List<RefundDto> getMyRefunds(Long customerId);
}
