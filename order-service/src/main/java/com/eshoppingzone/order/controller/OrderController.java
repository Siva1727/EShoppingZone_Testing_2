package com.eshoppingzone.order.controller;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.CheckoutRequest;
import com.eshoppingzone.order.dto.OrderDto;
import com.eshoppingzone.order.dto.UpdateOrderStatusRequest;
import com.eshoppingzone.order.security.UserPrincipal;
import com.eshoppingzone.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Customer Order Management", description = "APIs for placing, viewing, and managing customer orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private Long getCustomerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @PostMapping("/checkout")
    @Operation(summary = "Checkout and Place Order", description = "Places order from customer cart, reserves inventory, and processes payment (WALLET / COD)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderDto>> checkout(Authentication authentication,
                                                          @Valid @RequestBody CheckoutRequest request) {
        Long customerId = getCustomerId(authentication);
        OrderDto order = orderService.checkout(customerId, request);
        return new ResponseEntity<>(ApiResponse.success("Order placed successfully", order), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get My Orders", description = "List all orders placed by the authenticated customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getMyOrders(Authentication authentication) {
        Long customerId = getCustomerId(authentication);
        List<OrderDto> orders = orderService.getCustomerOrders(customerId);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Order Details", description = "Retrieve details of a specific order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderDto>> getOrderById(Authentication authentication,
                                                              @PathVariable Long id) {
        Long customerId = getCustomerId(authentication);
        OrderDto order = orderService.getOrderById(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Order details retrieved successfully", order));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel Order", description = "Cancel an eligible order and release stock")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(Authentication authentication,
                                                             @PathVariable Long id,
                                                             @RequestParam(required = false) String reason) {
        Long customerId = getCustomerId(authentication);
        OrderDto order = orderService.cancelOrder(id, customerId, reason);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }

    @PutMapping("/{id}/return")
    @Operation(summary = "Request Return", description = "Request a return/refund for a delivered order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderDto>> requestReturn(Authentication authentication,
                                                               @PathVariable Long id,
                                                               @RequestParam(required = false) String reason) {
        Long customerId = getCustomerId(authentication);
        OrderDto order = orderService.requestReturn(id, customerId, reason);
        return ResponseEntity.ok(ApiResponse.success("Return requested successfully", order));
    }

    @PutMapping("/{id}/status/internal")
    @Operation(summary = "Update Order Status (Internal)", description = "Internal endpoint for Delivery Service status updates")
    public ResponseEntity<ApiResponse<OrderDto>> updateOrderStatusInternal(@PathVariable Long id,
                                                                           @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderDto order = orderService.updateOrderStatusInternal(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", order));
    }

    @GetMapping("/{id}/internal")
    @Operation(summary = "Get Order Internal", description = "Internal endpoint for other services to get order info")
    public ResponseEntity<ApiResponse<OrderDto>> getOrderInternal(@PathVariable Long id) {
        OrderDto order = orderService.getOrderInternal(id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }
}
