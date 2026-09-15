package com.eshoppingzone.order.service;

import com.eshoppingzone.order.dto.CheckoutRequest;
import com.eshoppingzone.order.dto.OrderDto;
import com.eshoppingzone.order.entity.OrderStatus;

import java.util.List;

public interface OrderService {
    OrderDto checkout(Long customerId, CheckoutRequest request);
    OrderDto checkout(Long customerId, CheckoutRequest request, String idempotencyKey);
    List<OrderDto> getCustomerOrders(Long customerId);
    OrderDto getOrderById(Long orderId, Long customerId);
    OrderDto cancelOrder(Long orderId, Long customerId, String reason);
    OrderDto requestReturn(Long orderId, Long customerId, String reason);

    List<OrderDto> getMerchantOrders(Long merchantId);
    OrderDto getMerchantOrderById(Long orderId, Long merchantId);
    OrderDto processMerchantOrder(Long orderId, Long merchantId);
    OrderDto readyForDelivery(Long orderId, Long merchantId);
    OrderDto rejectMerchantOrder(Long orderId, Long merchantId, String reason);

    List<OrderDto> getAllOrdersAdmin();
    OrderDto updateOrderStatusAdmin(Long orderId, OrderStatus status);
    OrderDto updateOrderStatusInternal(Long orderId, OrderStatus status);
    OrderDto getOrderInternal(Long orderId);
}
