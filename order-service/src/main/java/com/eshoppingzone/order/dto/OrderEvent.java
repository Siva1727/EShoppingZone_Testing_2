package com.eshoppingzone.order.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderEvent implements Serializable {
    private String eventType; // ORDER_CREATED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_STATUS_CHANGED
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private BigDecimal amount;
    private String status;
    private String paymentMethod;
    private String paymentStatus;

    public OrderEvent() {
    }

    public OrderEvent(String eventType, Long orderId, String orderNumber, Long customerId, BigDecimal amount, String status, String paymentMethod, String paymentStatus) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
