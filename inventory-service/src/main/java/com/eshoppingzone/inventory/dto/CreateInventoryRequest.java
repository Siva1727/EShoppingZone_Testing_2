package com.eshoppingzone.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class CreateInventoryRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Initial stock is required")
    @PositiveOrZero(message = "Initial stock must be zero or positive")
    private Integer initialStock = 0;

    public CreateInventoryRequest() {
    }

    public CreateInventoryRequest(Long productId, Integer initialStock) {
        this.productId = productId;
        this.initialStock = initialStock;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getInitialStock() {
        return initialStock;
    }

    public void setInitialStock(Integer initialStock) {
        this.initialStock = initialStock;
    }
}
