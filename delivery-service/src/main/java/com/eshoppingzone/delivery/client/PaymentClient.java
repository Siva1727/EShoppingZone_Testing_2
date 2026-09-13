package com.eshoppingzone.delivery.client;

import com.eshoppingzone.delivery.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/cod/{orderId}/complete")
    ApiResponse<Object> completeCodPayment(@PathVariable("orderId") Long orderId);
}
