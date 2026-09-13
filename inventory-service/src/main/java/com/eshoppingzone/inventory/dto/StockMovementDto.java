package com.eshoppingzone.inventory.dto;

import com.eshoppingzone.inventory.entity.MovementType;
import com.eshoppingzone.inventory.entity.StockMovement;

import java.time.LocalDateTime;

public class StockMovementDto {
    private Long id;
    private Long inventoryId;
    private Long productId;
    private MovementType movementType;
    private Integer quantity;
    private String reference;
    private String description;
    private LocalDateTime createdAt;

    public StockMovementDto() {
    }

    public StockMovementDto(Long id, Long inventoryId, Long productId, MovementType movementType, Integer quantity, String reference, String description, LocalDateTime createdAt) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.reference = reference;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static StockMovementDto fromEntity(StockMovement m) {
        if (m == null) return null;
        return new StockMovementDto(
                m.getId(),
                m.getInventoryId(),
                m.getProductId(),
                m.getMovementType(),
                m.getQuantity(),
                m.getReference(),
                m.getDescription(),
                m.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
