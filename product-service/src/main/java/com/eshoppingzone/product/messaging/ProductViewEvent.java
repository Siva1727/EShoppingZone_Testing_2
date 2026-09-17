package com.eshoppingzone.product.messaging;

import java.time.Instant;
import java.util.UUID;

public record ProductViewEvent(String eventType, String eventId, Long customerId, Long productId, Instant viewedAt) {

    public ProductViewEvent(Long customerId, Long productId) {
        this("PRODUCT_VIEWED", UUID.randomUUID().toString(), customerId, productId, Instant.now());
    }
}
