package com.eshoppingzone.inventory.service;

import com.eshoppingzone.inventory.dto.*;
import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.entity.MovementType;
import com.eshoppingzone.inventory.entity.StockMovement;
import com.eshoppingzone.inventory.exception.InsufficientStockException;
import com.eshoppingzone.inventory.exception.ResourceNotFoundException;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import com.eshoppingzone.inventory.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository, StockMovementRepository stockMovementRepository) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Override
    @Transactional
    public InventoryDto createInventory(CreateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(request.getProductId());
                    inv.setAvailableStock(0);
                    inv.setReservedStock(0);
                    return inv;
                });

        int additional = request.getInitialStock() != null ? request.getInitialStock() : 0;
        inventory.setAvailableStock(inventory.getAvailableStock() + additional);
        Inventory saved = inventoryRepository.save(inventory);

        if (additional > 0) {
            recordMovement(saved.getId(), saved.getProductId(), MovementType.INITIAL, additional, "INITIAL", "Initial stock setup");
        }

        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));
        return InventoryDto.fromEntity(inventory);
    }

    @Override
    @Transactional
    public synchronized InventoryDto addStock(Long productId, Integer quantity, String reason) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(productId);
                    inv.setAvailableStock(0);
                    inv.setReservedStock(0);
                    return inventoryRepository.save(inv);
                });

        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
        Inventory saved = inventoryRepository.save(inventory);

        recordMovement(saved.getId(), productId, MovementType.ADD_STOCK, quantity, "MANUAL_ADD", reason != null ? reason : "Stock added by merchant");
        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public synchronized InventoryDto reduceStock(Long productId, Integer quantity, String reason) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));

        if (inventory.getAvailableStock() < quantity) {
            throw new InsufficientStockException("Cannot reduce stock. Available: " + inventory.getAvailableStock() + ", requested: " + quantity);
        }

        inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
        Inventory saved = inventoryRepository.save(inventory);

        recordMovement(saved.getId(), productId, MovementType.REDUCE_STOCK, quantity, "MANUAL_REDUCE", reason != null ? reason : "Stock reduced by merchant");
        return InventoryDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public synchronized void reserveStock(StockReservationRequest request) {
        log.info("Attempting to reserve stock for order: {}", request.getOrderReference());

        // 1. Verify availability for all items first
        for (StockReservationItem item : request.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + item.getProductId()));

            if (inventory.getAvailableStock() < item.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product ID: " + item.getProductId()
                        + ". Available: " + inventory.getAvailableStock() + ", Requested: " + item.getQuantity());
            }
        }

        // 2. Perform reservations atomically
        for (StockReservationItem item : request.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).get();
            inventory.setAvailableStock(inventory.getAvailableStock() - item.getQuantity());
            inventory.setReservedStock(inventory.getReservedStock() + item.getQuantity());
            inventoryRepository.save(inventory);

            recordMovement(inventory.getId(), item.getProductId(), MovementType.RESERVE, item.getQuantity(),
                    request.getOrderReference(), "Stock reserved for order");
        }
        log.info("Stock reserved successfully for order: {}", request.getOrderReference());
    }

    @Override
    @Transactional
    public synchronized void releaseStock(StockReservationRequest request) {
        log.info("Releasing reserved stock for order: {}", request.getOrderReference());
        for (StockReservationItem item : request.getItems()) {
            inventoryRepository.findByProductId(item.getProductId()).ifPresent(inventory -> {
                int releaseQty = Math.min(inventory.getReservedStock(), item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - releaseQty);
                inventory.setAvailableStock(inventory.getAvailableStock() + releaseQty);
                inventoryRepository.save(inventory);

                recordMovement(inventory.getId(), item.getProductId(), MovementType.RELEASE, releaseQty,
                        request.getOrderReference(), "Reserved stock released back to available");
            });
        }
    }

    @Override
    @Transactional
    public synchronized void confirmStock(StockReservationRequest request) {
        log.info("Confirming stock consumption for order: {}", request.getOrderReference());
        for (StockReservationItem item : request.getItems()) {
            inventoryRepository.findByProductId(item.getProductId()).ifPresent(inventory -> {
                int confirmQty = Math.min(inventory.getReservedStock(), item.getQuantity());
                inventory.setReservedStock(inventory.getReservedStock() - confirmQty);
                inventoryRepository.save(inventory);

                recordMovement(inventory.getId(), item.getProductId(), MovementType.CONFIRM_CONSUMPTION, confirmQty,
                        request.getOrderReference(), "Stock consumption confirmed after successful payment");
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementDto> getMovements(Long productId) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(StockMovementDto::fromEntity)
                .collect(Collectors.toList());
    }

    private void recordMovement(Long inventoryId, Long productId, MovementType type, Integer quantity, String reference, String description) {
        StockMovement movement = new StockMovement();
        movement.setInventoryId(inventoryId);
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReference(reference);
        movement.setDescription(description);
        stockMovementRepository.save(movement);
    }
}
