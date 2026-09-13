package com.eshoppingzone.payment.client;

import com.eshoppingzone.payment.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "order-service")
public interface OrderClient {

    @PutMapping("/api/v1/orders/{id}/status/internal")
    ApiResponse<Object> updateOrderStatusInternal(@PathVariable("id") Long id, @RequestBody Map<String, String> request);
}
