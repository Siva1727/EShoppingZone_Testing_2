package com.eshoppingzone.delivery.dto;

import java.time.LocalDateTime;

public class DeliveryEvent {

    private String eventType;
    private Long deliveryId;
    private Long orderId;
    private Long customerId;
    private Long deliveryAgentId;
    private String status;
    private String failureReason;
    private LocalDateTime timestamp;

    public DeliveryEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public DeliveryEvent(String eventType, Long deliveryId, Long orderId, Long customerId, Long deliveryAgentId, String status, String failureReason) {
        this.eventType = eventType;
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.deliveryAgentId = deliveryAgentId;
        this.status = status;
        this.failureReason = failureReason;
        this.timestamp = LocalDateTime.now();
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
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

    public Long getDeliveryAgentId() {
        return deliveryAgentId;
    }

    public void setDeliveryAgentId(Long deliveryAgentId) {
        this.deliveryAgentId = deliveryAgentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
