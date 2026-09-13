package com.eshoppingzone.payment.controller;

import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.RefundDto;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.security.UserPrincipal;
import com.eshoppingzone.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/refunds")
@Tag(name = "Refund Management", description = "APIs for requesting and processing refunds")
public class RefundController {

    private final PaymentService paymentService;

    public RefundController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @PostMapping
    @Operation(summary = "Request Refund", description = "Customer requests refund for a cancelled or returned order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<RefundDto>> requestRefund(Authentication authentication,
                                                                @Valid @RequestBody RefundRequest request) {
        Long customerId = getUserId(authentication);
        RefundDto refund = paymentService.requestRefund(customerId, request);
        return ResponseEntity.ok(ApiResponse.success("Refund requested successfully", refund));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get Pending Refunds", description = "Admin lists all pending refund requests requiring review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<RefundDto>>> getPendingRefunds() {
        List<RefundDto> pending = paymentService.getPendingRefunds();
        return ResponseEntity.ok(ApiResponse.success("Pending refunds retrieved successfully", pending));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve Refund", description = "Admin approves refund; wallet is credited to customer from platform wallet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundDto>> approveRefund(@PathVariable Long id) {
        RefundDto refund = paymentService.approveRefund(id);
        return ResponseEntity.ok(ApiResponse.success("Refund approved and processed", refund));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject Refund", description = "Admin rejects refund request with reason")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundDto>> rejectRefund(@PathVariable Long id,
                                                               @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Refund request rejected by admin";
        RefundDto refund = paymentService.rejectRefund(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Refund rejected", refund));
    }

    @GetMapping("/my")
    @Operation(summary = "Get My Refunds", description = "Customer retrieves their own refund requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<RefundDto>>> getMyRefunds(Authentication authentication) {
        Long customerId = getUserId(authentication);
        List<RefundDto> refunds = paymentService.getMyRefunds(customerId);
        return ResponseEntity.ok(ApiResponse.success("Refunds retrieved successfully", refunds));
    }
}
