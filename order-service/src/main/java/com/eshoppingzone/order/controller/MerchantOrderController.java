package com.eshoppingzone.order.controller;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.OrderDto;
import com.eshoppingzone.order.security.UserPrincipal;
import com.eshoppingzone.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/orders")
@Tag(name = "Merchant Order Management", description = "APIs for merchants to view and process their orders")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantOrderController {

    private final OrderService orderService;

    public MerchantOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private Long getMerchantId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Get Merchant Orders", description = "List all orders received by the merchant")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getMerchantOrders(Authentication authentication) {
        Long merchantId = getMerchantId(authentication);
        List<OrderDto> orders = orderService.getMerchantOrders(merchantId);
        return ResponseEntity.ok(ApiResponse.success("Merchant orders retrieved successfully", orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Merchant Order by ID", description = "Get details of an order owned by the merchant")
    public ResponseEntity<ApiResponse<OrderDto>> getMerchantOrderById(Authentication authentication,
                                                                      @PathVariable Long id) {
        Long merchantId = getMerchantId(authentication);
        OrderDto order = orderService.getMerchantOrderById(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    @PutMapping("/{id}/process")
    @Operation(summary = "Process Order", description = "Transition order from CONFIRMED to PROCESSING")
    public ResponseEntity<ApiResponse<OrderDto>> processOrder(Authentication authentication,
                                                              @PathVariable Long id) {
        Long merchantId = getMerchantId(authentication);
        OrderDto order = orderService.processMerchantOrder(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as PROCESSING", order));
    }

    @PutMapping("/{id}/ready")
    @Operation(summary = "Mark Ready for Delivery", description = "Transition order from PROCESSING to READY_FOR_DELIVERY")
    public ResponseEntity<ApiResponse<OrderDto>> readyForDelivery(Authentication authentication,
                                                                  @PathVariable Long id) {
        Long merchantId = getMerchantId(authentication);
        OrderDto order = orderService.readyForDelivery(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as READY_FOR_DELIVERY", order));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject Order", description = "Merchant reject order and release inventory")
    public ResponseEntity<ApiResponse<OrderDto>> rejectOrder(Authentication authentication,
                                                             @PathVariable Long id,
                                                             @RequestParam(required = false) String reason) {
        Long merchantId = getMerchantId(authentication);
        OrderDto order = orderService.rejectMerchantOrder(id, merchantId, reason);
        return ResponseEntity.ok(ApiResponse.success("Order rejected successfully", order));
    }
}
