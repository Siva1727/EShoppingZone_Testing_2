package com.eshoppingzone.delivery.repository;

import com.eshoppingzone.delivery.entity.Delivery;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findByDeliveryAgentId(Long deliveryAgentId);
    List<Delivery> findByDeliveryAgentIdAndStatus(Long deliveryAgentId, DeliveryStatus status);
    List<Delivery> findByCustomerId(Long customerId);
    List<Delivery> findByStatus(DeliveryStatus status);
}
