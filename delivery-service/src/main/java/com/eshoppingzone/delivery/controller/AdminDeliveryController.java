package com.eshoppingzone.delivery.controller;

import com.eshoppingzone.delivery.dto.ApiResponse;
import com.eshoppingzone.delivery.dto.AssignDeliveryRequest;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/deliveries")
@Tag(name = "Admin Delivery Management", description = "APIs for Admin to assign and oversee delivery operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DeliveryService deliveryService;

    public AdminDeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign Delivery Agent", description = "Admin assigns an order delivery to a specific delivery agent")
    public ResponseEntity<ApiResponse<DeliveryDto>> assignDelivery(@Valid @RequestBody AssignDeliveryRequest request) {
        DeliveryDto delivery = deliveryService.assignDelivery(request);
        return ResponseEntity.ok(ApiResponse.success("Delivery assigned successfully", delivery));
    }

    @GetMapping
    @Operation(summary = "Get All Deliveries", description = "Admin lists all deliveries across the system")
    public ResponseEntity<ApiResponse<List<DeliveryDto>>> getAllDeliveries(@RequestParam(required = false) String status) {
        List<DeliveryDto> list;
        if (status != null && !status.isBlank()) {
            DeliveryStatus deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
            list = deliveryService.getDeliveriesByStatus(deliveryStatus);
        } else {
            list = deliveryService.getAllDeliveries();
        }
        return ResponseEntity.ok(ApiResponse.success("Deliveries retrieved successfully", list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Delivery by ID", description = "Admin retrieves specific delivery record by ID")
    public ResponseEntity<ApiResponse<DeliveryDto>> getDeliveryById(@PathVariable Long id) {
        DeliveryDto delivery = deliveryService.getDeliveryById(id);
        return ResponseEntity.ok(ApiResponse.success("Delivery retrieved successfully", delivery));
    }
}
