package com.eshoppingzone.recommendation.service;

import com.eshoppingzone.recommendation.entity.CustomerProductView;

import java.time.LocalDateTime;
import java.util.List;

public interface RecommendationService {

    void recordProductView(Long customerId, Long productId, LocalDateTime viewedAt);

    List<CustomerProductView> getRecommendations(Long customerId);
}
