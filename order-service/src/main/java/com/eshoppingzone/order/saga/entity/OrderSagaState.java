package com.eshoppingzone.order.saga.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_saga_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_saga_idempotency", columnNames = {"idempotencyKey"}),
                @UniqueConstraint(name = "uk_saga_id", columnNames = {"sagaId"})
        },
        indexes = {
                @Index(name = "idx_saga_customer_idempotency", columnList = "customerId, idempotencyKey"),
                @Index(name = "idx_saga_order_id", columnList = "orderId"),
                @Index(name = "idx_saga_status_updated", columnList = "status, updatedAt")
        }
)
public class OrderSagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sagaId;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 64)
    private String orderNumber;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 32)
    private String paymentMethod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private SagaStatus status;

    @Column(length = 255)
    private String completedSteps;

    @Column(length = 64)
    private String failureStep;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private int retryCount = 0;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public OrderSagaState() {
    }

    public OrderSagaState(String sagaId, String idempotencyKey, String requestFingerprint, Long orderId, String orderNumber,
                          Long customerId, String paymentMethod, BigDecimal totalAmount,
                          SagaStep currentStep, SagaStatus status) {
        this.sagaId = sagaId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.currentStep = currentStep;
        this.status = status;
        this.completedSteps = "";
        this.retryCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addCompletedStep(SagaStep step) {
        if (this.completedSteps == null || this.completedSteps.isBlank()) {
            this.completedSteps = step.name();
        } else if (!this.completedSteps.contains(step.name())) {
            this.completedSteps = this.completedSteps + "," + step.name();
        }
    }

    public boolean hasCompletedStep(SagaStep step) {
        return this.completedSteps != null && this.completedSteps.contains(step.name());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(SagaStep currentStep) {
        this.currentStep = currentStep;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public String getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(String completedSteps) {
        this.completedSteps = completedSteps;
    }

    public String getFailureStep() {
        return failureStep;
    }

    public void setFailureStep(String failureStep) {
        this.failureStep = failureStep;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
