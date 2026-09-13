package com.eshoppingzone.order.client;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.StockReservationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/reserve")
    ApiResponse<Void> reserveStock(@RequestBody StockReservationRequest request);

    @PostMapping("/api/v1/inventory/release")
    ApiResponse<Void> releaseStock(@RequestBody StockReservationRequest request);

    @PostMapping("/api/v1/inventory/confirm")
    ApiResponse<Void> confirmStock(@RequestBody StockReservationRequest request);
}
