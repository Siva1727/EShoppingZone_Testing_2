package com.eshoppingzone.payment.dto;

import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundDto {
    private Long id;
    private Long paymentId;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private String refundReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RefundDto() {
    }

    public RefundDto(Long id, Long paymentId, Long orderId, Long customerId, BigDecimal amount, String reason, RefundStatus status, String refundReference, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.refundReference = refundReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefundDto fromEntity(Refund refund) {
        if (refund == null) return null;
        return new RefundDto(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getCustomerId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getRefundReference(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
