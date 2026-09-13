package com.eshoppingzone.order.dto;

import java.util.List;

public class StockReservationRequest {
    private String orderReference;
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
