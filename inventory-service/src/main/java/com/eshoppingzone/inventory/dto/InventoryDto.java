package com.eshoppingzone.inventory.dto;

import com.eshoppingzone.inventory.entity.Inventory;

import java.time.LocalDateTime;

public class InventoryDto {
    private Long id;
    private Long productId;
    private Integer availableStock;
    private Integer reservedStock;
    private Integer totalStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InventoryDto() {
    }

    public InventoryDto(Long id, Long productId, Integer availableStock, Integer reservedStock, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.totalStock = (availableStock != null ? availableStock : 0) + (reservedStock != null ? reservedStock : 0);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InventoryDto fromEntity(Inventory inventory) {
        if (inventory == null) return null;
        return new InventoryDto(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public Integer getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(Integer reservedStock) {
        this.reservedStock = reservedStock;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
