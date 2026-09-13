package com.eshoppingzone.delivery.controller;

import com.eshoppingzone.delivery.dto.ApiResponse;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.dto.UpdateDeliveryStatusRequest;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.security.UserPrincipal;
import com.eshoppingzone.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/deliveries")
@Tag(name = "Delivery Agent Operations", description = "APIs for delivery agents to view assignments and update delivery stages")
@PreAuthorize("hasAnyRole('DELIVERY', 'DELIVERY_AGENT', 'ADMIN')")
public class AgentDeliveryController {

    private final DeliveryService deliveryService;

    public AgentDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    private Long getAgentId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Get Agent Deliveries", description = "Delivery agent retrieves their assigned deliveries")
    public ResponseEntity<ApiResponse<List<DeliveryDto>>> getMyDeliveries(Authentication authentication,
                                                                         @RequestParam(required = false) String status) {
        Long agentId = getAgentId(authentication);
        List<DeliveryDto> list;
        if (status != null && !status.isBlank()) {
            DeliveryStatus deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
            list = deliveryService.getAgentDeliveriesByStatus(agentId, deliveryStatus);
        } else {
            list = deliveryService.getAgentDeliveries(agentId);
        }
        return ResponseEntity.ok(ApiResponse.success("Assigned deliveries retrieved successfully", list));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update Delivery Status", description = "Agent transitions delivery through lifecycle (ACCEPTED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED)")
    public ResponseEntity<ApiResponse<DeliveryDto>> updateDeliveryStatus(Authentication authentication,
                                                                        @PathVariable Long id,
                                                                        @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        Long agentId = getAgentId(authentication);
        DeliveryDto updated = deliveryService.updateDeliveryStatusByAgent(agentId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery status updated successfully", updated));
    }
}
