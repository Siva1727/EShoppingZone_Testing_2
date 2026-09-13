package com.eshoppingzone.inventory.service;

import com.eshoppingzone.inventory.dto.*;
import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.entity.MovementType;
import com.eshoppingzone.inventory.entity.StockMovement;
import com.eshoppingzone.inventory.exception.InsufficientStockException;
import com.eshoppingzone.inventory.exception.ResourceNotFoundException;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import com.eshoppingzone.inventory.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Inventory sampleInventory;

    @BeforeEach
    void setUp() {
        sampleInventory = new Inventory(1L, 100L, 50, 0);
    }

    @Test
    void testGetInventoryByProductIdFound() {
        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.of(sampleInventory));

        InventoryDto dto = inventoryService.getInventoryByProductId(100L);

        assertNotNull(dto);
        assertEquals(50, dto.getAvailableStock());
        assertEquals(0, dto.getReservedStock());
    }

    @Test
    void testAddStock() {
        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(sampleInventory);

        InventoryDto dto = inventoryService.addStock(100L, 20, "Restock");

        assertNotNull(dto);
        assertEquals(70, sampleInventory.getAvailableStock());
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    void testReduceStockInsufficientThrowsException() {
        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.of(sampleInventory));

        assertThrows(InsufficientStockException.class, () -> inventoryService.reduceStock(100L, 100, "Large order"));
    }

    @Test
    void testReserveStockSuccess() {
        StockReservationRequest request = new StockReservationRequest(
                "ORD-101",
                List.of(new StockReservationItem(100L, 10))
        );

        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.of(sampleInventory));

        inventoryService.reserveStock(request);

        assertEquals(40, sampleInventory.getAvailableStock());
        assertEquals(10, sampleInventory.getReservedStock());
        verify(inventoryRepository, times(1)).save(sampleInventory);
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    void testReleaseStock() {
        sampleInventory.setAvailableStock(40);
        sampleInventory.setReservedStock(10);

        StockReservationRequest request = new StockReservationRequest(
                "ORD-101",
                List.of(new StockReservationItem(100L, 10))
        );

        when(inventoryRepository.findByProductId(100L)).thenReturn(Optional.of(sampleInventory));

        inventoryService.releaseStock(request);

        assertEquals(50, sampleInventory.getAvailableStock());
        assertEquals(0, sampleInventory.getReservedStock());
    }
}
