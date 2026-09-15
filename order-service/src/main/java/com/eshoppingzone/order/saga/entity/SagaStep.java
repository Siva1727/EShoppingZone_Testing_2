package com.eshoppingzone.order.saga.entity;

public enum SagaStep {
    RESERVE_INVENTORY,
    PROCESS_PAYMENT,
    CONFIRM_INVENTORY,
    CLEAR_CART,
    FINALIZE_ORDER,
    COMPENSATE_INVENTORY
}
