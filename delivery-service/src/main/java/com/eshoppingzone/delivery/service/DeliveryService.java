package com.eshoppingzone.delivery.service;

import com.eshoppingzone.delivery.dto.AssignDeliveryRequest;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.dto.UpdateDeliveryStatusRequest;
import com.eshoppingzone.delivery.entity.DeliveryStatus;

import java.util.List;

public interface DeliveryService {
    DeliveryDto assignDelivery(AssignDeliveryRequest request);
    List<DeliveryDto> getAllDeliveries();
    List<DeliveryDto> getDeliveriesByStatus(DeliveryStatus status);
    List<DeliveryDto> getAgentDeliveries(Long deliveryAgentId);
    List<DeliveryDto> getAgentDeliveriesByStatus(Long deliveryAgentId, DeliveryStatus status);
    DeliveryDto updateDeliveryStatusByAgent(Long deliveryAgentId, Long deliveryId, UpdateDeliveryStatusRequest request);
    DeliveryDto getDeliveryByOrderId(Long orderId);
    List<DeliveryDto> getMyDeliveries(Long customerId);
    DeliveryDto getDeliveryById(Long id);
}
