package com.eshoppingzone.recommendation.messaging;

import com.eshoppingzone.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductViewEventListenerTest {

    @Test
    void forwardsCustomerAndProductFromEvent() {
        RecommendationService service = mock(RecommendationService.class);
        ProductViewEventListener listener = new ProductViewEventListener(service);
        Instant viewedAt = Instant.parse("2026-09-16T18:00:00Z");

        listener.onProductViewed(new ProductViewEvent("event-1", 101L, 501L, viewedAt));

        verify(service).recordProductView(eq(101L), eq(501L),
                eq(LocalDateTime.ofInstant(viewedAt, java.time.ZoneOffset.UTC)));
    }
}
