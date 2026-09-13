package com.eshoppingzone.cart.client;

import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.ProductSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}/internal")
    ApiResponse<ProductSnapshotDto> getProductById(@PathVariable("id") Long id);
}
