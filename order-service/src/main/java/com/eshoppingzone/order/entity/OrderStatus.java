package com.eshoppingzone.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    READY_FOR_DELIVERY,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    FAILED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED
}
