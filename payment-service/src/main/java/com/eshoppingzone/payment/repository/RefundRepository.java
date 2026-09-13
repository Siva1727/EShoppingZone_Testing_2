package com.eshoppingzone.payment.repository;

import com.eshoppingzone.payment.entity.Refund;
import com.eshoppingzone.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByStatusOrderByCreatedAtDesc(RefundStatus status);
    List<Refund> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Refund> findByOrderId(Long orderId);
}
