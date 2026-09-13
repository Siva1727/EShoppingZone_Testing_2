package com.eshoppingzone.order.dto;

public class StockReservationItem {
    private Long productId;
    private Integer quantity;

    public StockReservationItem() {
    }

    public StockReservationItem(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
