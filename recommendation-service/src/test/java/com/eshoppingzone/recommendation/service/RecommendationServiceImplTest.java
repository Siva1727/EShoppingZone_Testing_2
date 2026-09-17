package com.eshoppingzone.recommendation.service;

import com.eshoppingzone.recommendation.repository.CustomerProductViewRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class RecommendationServiceImplTest {

    @Test
    void recordsEveryViewForTheSameCustomerAndProductAtomically() {
        CustomerProductViewRepository repository = mock(CustomerProductViewRepository.class);
        RecommendationServiceImpl service = new RecommendationServiceImpl(repository);

        for (int i = 0; i < 4; i++) {
            service.recordProductView(101L, 501L, LocalDateTime.now());
        }

        verify(repository, times(4)).recordView(eq(101L), eq(501L), any(LocalDateTime.class));
        verify(repository, never()).save(any());
    }
}
