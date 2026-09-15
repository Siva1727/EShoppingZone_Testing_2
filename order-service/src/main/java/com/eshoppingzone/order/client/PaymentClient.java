package com.eshoppingzone.order.client;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.PaymentResponseDto;
import com.eshoppingzone.order.dto.ProcessPaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/process")
    ApiResponse<PaymentResponseDto> processPayment(@RequestBody ProcessPaymentRequest request);

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/payments/order/{orderId}")
    ApiResponse<PaymentResponseDto> getPaymentByOrderId(@org.springframework.web.bind.annotation.PathVariable("orderId") Long orderId);
}
