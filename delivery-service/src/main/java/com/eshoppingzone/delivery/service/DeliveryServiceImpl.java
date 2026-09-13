package com.eshoppingzone.delivery.service;

import com.eshoppingzone.delivery.client.OrderClient;
import com.eshoppingzone.delivery.client.PaymentClient;
import com.eshoppingzone.delivery.config.RabbitMQConfig;
import com.eshoppingzone.delivery.dto.ApiResponse;
import com.eshoppingzone.delivery.dto.AssignDeliveryRequest;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.dto.DeliveryEvent;
import com.eshoppingzone.delivery.dto.OrderDto;
import com.eshoppingzone.delivery.dto.UpdateDeliveryStatusRequest;
import com.eshoppingzone.delivery.entity.Delivery;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.exception.DeliveryException;
import com.eshoppingzone.delivery.exception.ResourceNotFoundException;
import com.eshoppingzone.delivery.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private final DeliveryRepository deliveryRepository;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final RabbitTemplate rabbitTemplate;

    public DeliveryServiceImpl(DeliveryRepository deliveryRepository,
                               OrderClient orderClient,
                               PaymentClient paymentClient,
                               RabbitTemplate rabbitTemplate) {
        this.deliveryRepository = deliveryRepository;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public DeliveryDto assignDelivery(AssignDeliveryRequest request) {
        log.info("Assigning delivery for orderId: {} to agentId: {}", request.getOrderId(), request.getDeliveryAgentId());

        Optional<Delivery> existingOpt = deliveryRepository.findByOrderId(request.getOrderId());
        Delivery delivery;
        if (existingOpt.isPresent()) {
            delivery = existingOpt.get();
            if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
                throw new DeliveryException("Order has already been delivered");
            }
            delivery.setDeliveryAgentId(request.getDeliveryAgentId());
            delivery.setPickupAddress(request.getPickupAddress());
            delivery.setDeliveryAddress(request.getDeliveryAddress());
            delivery.setCustomerPhone(request.getCustomerPhone());
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            delivery.setAssignedAt(LocalDateTime.now());
        } else {
            delivery = new Delivery(request.getOrderId(), request.getCustomerId(), request.getDeliveryAddress(), request.getCustomerPhone());
            delivery.setDeliveryAgentId(request.getDeliveryAgentId());
            delivery.setPickupAddress(request.getPickupAddress());
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            delivery.setAssignedAt(LocalDateTime.now());
        }

        Delivery saved = deliveryRepository.save(delivery);

        // Publish DELIVERY_ASSIGNED event
        try {
            DeliveryEvent event = new DeliveryEvent(
                    "DELIVERY_ASSIGNED",
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getCustomerId(),
                    saved.getDeliveryAgentId(),
                    saved.getStatus().name(),
                    null
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_DELIVERY_ASSIGNED, event);
        } catch (Exception ex) {
            log.error("Failed to publish DELIVERY_ASSIGNED event: {}", ex.getMessage());
        }

        return DeliveryDto.fromEntity(saved);
    }

    @Override
    public List<DeliveryDto> getAllDeliveries() {
        return deliveryRepository.findAll().stream()
                .map(DeliveryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeliveryDto> getDeliveriesByStatus(DeliveryStatus status) {
        return deliveryRepository.findByStatus(status).stream()
                .map(DeliveryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeliveryDto> getAgentDeliveries(Long deliveryAgentId) {
        return deliveryRepository.findByDeliveryAgentId(deliveryAgentId).stream()
                .map(DeliveryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeliveryDto> getAgentDeliveriesByStatus(Long deliveryAgentId, DeliveryStatus status) {
        return deliveryRepository.findByDeliveryAgentIdAndStatus(deliveryAgentId, status).stream()
                .map(DeliveryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DeliveryDto updateDeliveryStatusByAgent(Long deliveryAgentId, Long deliveryId, UpdateDeliveryStatusRequest request) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (!delivery.getDeliveryAgentId().equals(deliveryAgentId)) {
            throw new DeliveryException("You are not authorized to update this delivery task");
        }

        DeliveryStatus newStatus;
        try {
            newStatus = DeliveryStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DeliveryException("Invalid delivery status: " + request.getStatus());
        }

        validateStatusTransition(delivery.getStatus(), newStatus);
        delivery.setStatus(newStatus);

        if (newStatus == DeliveryStatus.PICKED_UP) {
            delivery.setPickedUpAt(LocalDateTime.now());
        } else if (newStatus == DeliveryStatus.OUT_FOR_DELIVERY) {
            // Update order status to OUT_FOR_DELIVERY
            try {
                orderClient.updateOrderStatus(delivery.getOrderId(), Collections.singletonMap("status", "OUT_FOR_DELIVERY"));
            } catch (Exception ex) {
                log.warn("Failed to notify order-service of OUT_FOR_DELIVERY status: {}", ex.getMessage());
            }
        } else if (newStatus == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(LocalDateTime.now());
            // Update order status to DELIVERED
            try {
                orderClient.updateOrderStatus(delivery.getOrderId(), Collections.singletonMap("status", "DELIVERED"));
            } catch (Exception ex) {
                log.warn("Failed to notify order-service of DELIVERED status: {}", ex.getMessage());
            }

            // Complete COD payment if applicable
            try {
                paymentClient.completeCodPayment(delivery.getOrderId());
            } catch (Exception ex) {
                log.info("COD payment completion check finished for orderId: {}", delivery.getOrderId());
            }
        } else if (newStatus == DeliveryStatus.FAILED) {
            delivery.setFailureReason(request.getFailureReason() != null ? request.getFailureReason() : "Delivery failed");
        }

        Delivery saved = deliveryRepository.save(delivery);

        // Publish event
        try {
            DeliveryEvent event = new DeliveryEvent(
                    "DELIVERY_STATUS_CHANGED",
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getCustomerId(),
                    saved.getDeliveryAgentId(),
                    saved.getStatus().name(),
                    saved.getFailureReason()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_DELIVERY_STATUS, event);
        } catch (Exception ex) {
            log.error("Failed to publish DELIVERY_STATUS_CHANGED event: {}", ex.getMessage());
        }

        return DeliveryDto.fromEntity(saved);
    }

    private void validateStatusTransition(DeliveryStatus current, DeliveryStatus next) {
        if (current == DeliveryStatus.DELIVERED) {
            throw new DeliveryException("Cannot change status of an already delivered package");
        }
        if (current == DeliveryStatus.CANCELLED) {
            throw new DeliveryException("Cannot change status of a cancelled delivery");
        }
    }

    @Override
    public DeliveryDto getDeliveryByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found for orderId: " + orderId));
        return DeliveryDto.fromEntity(delivery);
    }

    @Override
    public List<DeliveryDto> getMyDeliveries(Long customerId) {
        return deliveryRepository.findByCustomerId(customerId).stream()
                .map(DeliveryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public DeliveryDto getDeliveryById(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        return DeliveryDto.fromEntity(delivery);
    }
}
