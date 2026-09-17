package com.eshoppingzone.recommendation.service;

import com.eshoppingzone.recommendation.entity.CustomerProductView;
import com.eshoppingzone.recommendation.repository.CustomerProductViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final int RECOMMENDATION_THRESHOLD = 3;
    private final CustomerProductViewRepository repository;

    public RecommendationServiceImpl(CustomerProductViewRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void recordProductView(Long customerId, Long productId, LocalDateTime viewedAt) {
        if (customerId == null || productId == null) {
            throw new IllegalArgumentException("customerId and productId are required");
        }
        repository.recordView(customerId, productId,
                viewedAt == null ? LocalDateTime.now() : viewedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerProductView> getRecommendations(Long customerId) {
        return repository.findByCustomerIdAndRecommendedTrueOrderByUpdatedAtDesc(customerId);
    }

    public static int threshold() {
        return RECOMMENDATION_THRESHOLD;
    }
}
