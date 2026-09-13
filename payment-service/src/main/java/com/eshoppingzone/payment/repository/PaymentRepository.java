package com.eshoppingzone.payment.repository;

import com.eshoppingzone.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Payment> findByTransactionReference(String transactionReference);
}
