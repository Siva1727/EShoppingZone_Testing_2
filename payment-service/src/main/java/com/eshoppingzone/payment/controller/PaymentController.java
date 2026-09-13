package com.eshoppingzone.payment.controller;

import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.PaymentDto;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
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

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Management", description = "APIs for processing payments and querying payment status")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @PostMapping("/process")
    @Operation(summary = "Process Payment", description = "Internal or checkout endpoint to process WALLET or COD payment")
    public ResponseEntity<ApiResponse<PaymentDto>> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        PaymentDto payment = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", payment));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get Payment by Order ID", description = "Retrieve payment transaction details for a specific order")
    public ResponseEntity<ApiResponse<PaymentDto>> getPaymentByOrderId(@PathVariable Long orderId) {
        PaymentDto payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", payment));
    }

    @GetMapping("/my")
    @Operation(summary = "Get My Payments", description = "Retrieve payment history for current authenticated customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getMyPayments(Authentication authentication) {
        Long customerId = getUserId(authentication);
        List<PaymentDto> payments = paymentService.getMyPayments(customerId);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @PostMapping("/cod/{orderId}/complete")
    @Operation(summary = "Complete COD Payment", description = "Internal endpoint called by Delivery Service when Cash on Delivery is collected")
    public ResponseEntity<ApiResponse<PaymentDto>> completeCodPayment(@PathVariable Long orderId) {
        PaymentDto payment = paymentService.completeCodPayment(orderId);
        return ResponseEntity.ok(ApiResponse.success("COD payment completed successfully", payment));
    }
}
