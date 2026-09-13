package com.eshoppingzone.inventory.service;

import com.eshoppingzone.inventory.dto.*;

import java.util.List;

public interface InventoryService {
    InventoryDto createInventory(CreateInventoryRequest request);
    InventoryDto getInventoryByProductId(Long productId);
    InventoryDto addStock(Long productId, Integer quantity, String reason);
    InventoryDto reduceStock(Long productId, Integer quantity, String reason);

    void reserveStock(StockReservationRequest request);
    void releaseStock(StockReservationRequest request);
    void confirmStock(StockReservationRequest request);

    List<StockMovementDto> getMovements(Long productId);
}
