package com.eshoppingzone.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class StockReservationRequest {

    @NotNull(message = "Order reference is required")
    private String orderReference;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<StockReservationItem> items;

    public StockReservationRequest() {
    }

    public StockReservationRequest(String orderReference, List<StockReservationItem> items) {
        this.orderReference = orderReference;
        this.items = items;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public List<StockReservationItem> getItems() {
        return items;
    }

    public void setItems(List<StockReservationItem> items) {
        this.items = items;
    }
}
