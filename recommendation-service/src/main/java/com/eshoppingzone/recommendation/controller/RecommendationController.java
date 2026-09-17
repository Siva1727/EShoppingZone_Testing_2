package com.eshoppingzone.recommendation.controller;

import com.eshoppingzone.recommendation.dto.ApiResponse;
import com.eshoppingzone.recommendation.dto.RecommendationDto;
import com.eshoppingzone.recommendation.entity.CustomerProductView;
import com.eshoppingzone.recommendation.security.UserPrincipal;
import com.eshoppingzone.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationDto>>> getRecommendations(
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<RecommendationDto> recommendations = recommendationService
                .getRecommendations(principal.getUserId())
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Recommendations retrieved successfully", recommendations));
    }

    private RecommendationDto toDto(CustomerProductView view) {
        return new RecommendationDto(view.getProductId(), "VIEW_COUNT_THRESHOLD");
    }
}
