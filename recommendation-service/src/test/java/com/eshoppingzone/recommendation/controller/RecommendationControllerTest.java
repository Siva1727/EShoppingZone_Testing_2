package com.eshoppingzone.recommendation.controller;

import com.eshoppingzone.recommendation.entity.CustomerProductView;
import com.eshoppingzone.recommendation.security.UserPrincipal;
import com.eshoppingzone.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RecommendationControllerTest {

    @Test
    void queriesRecommendationsUsingAuthenticatedCustomerOnly() {
        RecommendationService service = mock(RecommendationService.class);
        CustomerProductView view = new CustomerProductView(101L, 501L, LocalDateTime.now());
        when(service.getRecommendations(101L)).thenReturn(List.of(view));
        UserPrincipal principal = new UserPrincipal(101L, "c101", "c101@example.com", "CUSTOMER");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var response = new RecommendationController(service).getRecommendations(authentication);

        verify(service).getRecommendations(101L);
        verify(service, never()).getRecommendations(102L);
        assertEquals(501L, response.getBody().getData().get(0).productId());
        assertEquals("VIEW_COUNT_THRESHOLD", response.getBody().getData().get(0).reason());
    }
}
