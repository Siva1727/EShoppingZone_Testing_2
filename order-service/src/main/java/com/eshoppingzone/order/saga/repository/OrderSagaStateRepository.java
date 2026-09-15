package com.eshoppingzone.order.saga.repository;

import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderSagaStateRepository extends JpaRepository<OrderSagaState, Long> {

    Optional<OrderSagaState> findBySagaId(String sagaId);

    Optional<OrderSagaState> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderSagaState> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<OrderSagaState> findByOrderId(Long orderId);

    List<OrderSagaState> findByStatusInAndUpdatedAtBefore(List<SagaStatus> statuses, LocalDateTime threshold);
}
