package com.eshoppingzone.order.client;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.CartDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cart-service")
public interface CartClient {

    @GetMapping("/api/v1/cart/customer/{customerId}")
    ApiResponse<CartDto> getCartByCustomerId(@PathVariable("customerId") Long customerId);

    @DeleteMapping("/api/v1/cart/customer/{customerId}/clear")
    ApiResponse<Void> clearCustomerCart(@PathVariable("customerId") Long customerId);
}
