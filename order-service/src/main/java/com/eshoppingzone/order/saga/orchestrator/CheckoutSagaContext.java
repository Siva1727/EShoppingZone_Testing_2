package com.eshoppingzone.order.saga.orchestrator;

import com.eshoppingzone.order.dto.CheckoutRequest;
import com.eshoppingzone.order.dto.StockReservationItem;
import com.eshoppingzone.order.entity.Order;

import java.util.List;

public class CheckoutSagaContext {

    private final String sagaId;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final Long customerId;
    private final Order order;
    private final List<StockReservationItem> reservationItems;
    private final CheckoutRequest checkoutRequest;

    public CheckoutSagaContext(String sagaId, String idempotencyKey, String requestFingerprint, Long customerId, Order order,
                               List<StockReservationItem> reservationItems, CheckoutRequest checkoutRequest) {
        this.sagaId = sagaId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.customerId = customerId;
        this.order = order;
        this.reservationItems = reservationItems;
        this.checkoutRequest = checkoutRequest;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Order getOrder() {
        return order;
    }

    public List<StockReservationItem> getReservationItems() {
        return reservationItems;
    }

    public CheckoutRequest getCheckoutRequest() {
        return checkoutRequest;
    }
}
