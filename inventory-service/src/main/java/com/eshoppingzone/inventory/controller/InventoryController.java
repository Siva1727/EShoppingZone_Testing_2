package com.eshoppingzone.inventory.controller;

import com.eshoppingzone.inventory.dto.*;
import com.eshoppingzone.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory Management", description = "APIs for stock management, reservation, and audit trails")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Initialize Inventory", description = "Create initial inventory for a product")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<InventoryDto>> createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        InventoryDto inventory = inventoryService.createInventory(request);
        return new ResponseEntity<>(ApiResponse.success("Inventory initialized successfully", inventory), HttpStatus.CREATED);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get Inventory by Product ID", description = "Check available stock for a product")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventoryByProductId(@PathVariable Long productId) {
        InventoryDto inventory = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved successfully", inventory));
    }

    @PostMapping("/product/{productId}/add")
    @Operation(summary = "Add Stock", description = "Increase available stock for a product")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<InventoryDto>> addStock(@PathVariable Long productId,
                                                              @Valid @RequestBody StockUpdateRequest request) {
        InventoryDto inventory = inventoryService.addStock(productId, request.getQuantity(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Stock increased successfully", inventory));
    }

    @PostMapping("/product/{productId}/reduce")
    @Operation(summary = "Reduce Stock", description = "Decrease available stock for a product")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<InventoryDto>> reduceStock(@PathVariable Long productId,
                                                                 @Valid @RequestBody StockUpdateRequest request) {
        InventoryDto inventory = inventoryService.reduceStock(productId, request.getQuantity(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Stock reduced successfully", inventory));
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve Stock", description = "Internal endpoint for order checkout to reserve stock")
    public ResponseEntity<ApiResponse<Void>> reserveStock(@Valid @RequestBody StockReservationRequest request) {
        inventoryService.reserveStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock reserved successfully", null));
    }

    @PostMapping("/release")
    @Operation(summary = "Release Stock", description = "Internal endpoint to release reserved stock if payment fails or order is cancelled")
    public ResponseEntity<ApiResponse<Void>> releaseStock(@Valid @RequestBody StockReservationRequest request) {
        inventoryService.releaseStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock released successfully", null));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm Stock Consumption", description = "Internal endpoint to finalize stock deduction after successful payment")
    public ResponseEntity<ApiResponse<Void>> confirmStock(@Valid @RequestBody StockReservationRequest request) {
        inventoryService.confirmStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock consumption confirmed", null));
    }

    @GetMapping("/product/{productId}/movements")
    @Operation(summary = "Get Stock Movements", description = "Audit trail of stock adjustments, reservations, and releases")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getMovements(@PathVariable Long productId) {
        List<StockMovementDto> movements = inventoryService.getMovements(productId);
        return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved successfully", movements));
    }
}
