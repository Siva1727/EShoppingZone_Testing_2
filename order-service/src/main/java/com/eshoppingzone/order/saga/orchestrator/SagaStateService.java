package com.eshoppingzone.order.saga.orchestrator;

import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.entity.SagaStep;
import com.eshoppingzone.order.saga.repository.OrderSagaStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SagaStateService {

    private final OrderSagaStateRepository sagaStateRepository;
    private final OrderRepository orderRepository;

    public SagaStateService(OrderSagaStateRepository sagaStateRepository, OrderRepository orderRepository) {
        this.sagaStateRepository = sagaStateRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderSagaState initSagaState(String sagaId, String idempotencyKey, String requestFingerprint, Order order, SagaStep initialStep) {
        OrderSagaState state = new OrderSagaState(
                sagaId,
                idempotencyKey,
                requestFingerprint,
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getPaymentMethod().name(),
                order.getTotalAmount(),
                initialStep,
                SagaStatus.STARTED
        );
        return sagaStateRepository.saveAndFlush(state);
    }

    @Transactional
    public OrderSagaState updateState(String sagaId, SagaStep step, SagaStatus status, SagaStep completedStep) {
        OrderSagaState state = sagaStateRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga state not found for sagaId: " + sagaId));
        state.setCurrentStep(step);
        state.setStatus(status);
        if (completedStep != null) {
            state.addCompletedStep(completedStep);
        }
        return sagaStateRepository.save(state);
    }

    @Transactional
    public OrderSagaState recordFailure(String sagaId, SagaStep step, SagaStatus status, String reason) {
        OrderSagaState state = sagaStateRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga state not found for sagaId: " + sagaId));
        state.setCurrentStep(step);
        state.setStatus(status);
        state.setFailureStep(step != null ? step.name() : null);
        state.setFailureReason(reason != null && reason.length() > 950 ? reason.substring(0, 950) : reason);
        return sagaStateRepository.save(state);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus orderStatus, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found with id: " + orderId));
        if (orderStatus != null) {
            order.setStatus(orderStatus);
        }
        if (paymentStatus != null) {
            order.setPaymentStatus(paymentStatus);
        }
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<OrderSagaState> findByIdempotencyKey(String idempotencyKey) {
        return sagaStateRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<OrderSagaState> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey) {
        return sagaStateRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<OrderSagaState> findBySagaId(String sagaId) {
        return sagaStateRepository.findBySagaId(sagaId);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public void deleteOrderById(Long orderId) {
        orderRepository.deleteById(orderId);
    }
}
