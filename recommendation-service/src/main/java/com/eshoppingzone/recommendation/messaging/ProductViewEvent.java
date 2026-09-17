package com.eshoppingzone.recommendation.messaging;

import java.time.Instant;

public class ProductViewEvent {

    private String eventId;
    private String eventType;
    private Long customerId;
    private Long productId;
    private Instant viewedAt;

    public ProductViewEvent() {
    }

    public ProductViewEvent(String eventId, Long customerId, Long productId, Instant viewedAt) {
        this.eventId = eventId;
        this.customerId = customerId;
        this.productId = productId;
        this.viewedAt = viewedAt;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public Instant getViewedAt() { return viewedAt; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}
