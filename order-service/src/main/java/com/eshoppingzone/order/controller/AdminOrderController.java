package com.eshoppingzone.order.controller;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.OrderDto;
import com.eshoppingzone.order.dto.UpdateOrderStatusRequest;
import com.eshoppingzone.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin Order Management", description = "APIs for platform administrators to monitor and manage all orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get All Orders", description = "List all customer orders across all statuses")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getAllOrders() {
        List<OrderDto> orders = orderService.getAllOrdersAdmin();
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved successfully", orders));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update Order Status", description = "Admin update order status for exceptional handling")
    public ResponseEntity<ApiResponse<OrderDto>> updateOrderStatus(@PathVariable Long id,
                                                                   @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderDto order = orderService.updateOrderStatusAdmin(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", order));
    }
}
