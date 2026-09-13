package com.eshoppingzone.delivery.client;

import com.eshoppingzone.delivery.dto.ApiResponse;
import com.eshoppingzone.delivery.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/v1/orders/{id}")
    ApiResponse<OrderDto> getOrderById(@PathVariable("id") Long id);

    @PutMapping("/api/v1/orders/{id}/status/internal")
    ApiResponse<OrderDto> updateOrderStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> statusBody);
}
