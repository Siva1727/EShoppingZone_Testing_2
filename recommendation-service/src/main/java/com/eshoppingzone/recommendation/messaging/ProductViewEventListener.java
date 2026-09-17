package com.eshoppingzone.recommendation.messaging;

import com.eshoppingzone.recommendation.service.RecommendationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class ProductViewEventListener {

    public static final String QUEUE = "eshoppingzone.recommendation.product.viewed.queue";
    private final RecommendationService recommendationService;

    public ProductViewEventListener(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @RabbitListener(queues = QUEUE)
    public void onProductViewed(ProductViewEvent event) {
        if (event == null || event.getCustomerId() == null || event.getProductId() == null) {
            throw new IllegalArgumentException("A product view event must identify a customer and product");
        }
        LocalDateTime viewedAt = event.getViewedAt() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(event.getViewedAt(), ZoneOffset.UTC);
        recommendationService.recordProductView(event.getCustomerId(), event.getProductId(), viewedAt);
    }
}
