package com.eshoppingzone.delivery.service;

import com.eshoppingzone.delivery.client.OrderClient;
import com.eshoppingzone.delivery.client.PaymentClient;
import com.eshoppingzone.delivery.dto.*;
import com.eshoppingzone.delivery.entity.Delivery;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.exception.DeliveryException;
import com.eshoppingzone.delivery.exception.ResourceNotFoundException;
import com.eshoppingzone.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private Delivery mockDelivery;

    @BeforeEach
    void setUp() {
        mockDelivery = new Delivery(100L, 4L, "123 Main St, New York, NY", "555-1234");
        mockDelivery.setId(1L);
        mockDelivery.setDeliveryAgentId(3L);
        mockDelivery.setStatus(DeliveryStatus.ASSIGNED);
        mockDelivery.setAssignedAt(LocalDateTime.now());
    }

    @Test
    void testAssignDeliveryNew() {
        AssignDeliveryRequest request = new AssignDeliveryRequest(101L, 4L, 3L, "Merchant Warehouse A", "456 Oak St", "555-9876");

        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(i -> {
            Delivery d = i.getArgument(0);
            d.setId(2L);
            return d;
        });

        DeliveryDto result = deliveryService.assignDelivery(request);

        assertNotNull(result);
        assertEquals(101L, result.getOrderId());
        assertEquals(3L, result.getDeliveryAgentId());
        assertEquals("ASSIGNED", result.getStatus());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(DeliveryEvent.class));
    }

    @Test
    void testGetAllDeliveries() {
        when(deliveryRepository.findAll()).thenReturn(Collections.singletonList(mockDelivery));

        List<DeliveryDto> list = deliveryService.getAllDeliveries();

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(100L, list.get(0).getOrderId());
    }

    @Test
    void testUpdateDeliveryStatusPickedUp() {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest("PICKED_UP", null);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery));
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

        DeliveryDto result = deliveryService.updateDeliveryStatusByAgent(3L, 1L, request);

        assertNotNull(result);
        assertEquals("PICKED_UP", result.getStatus());
        assertNotNull(result.getPickedUpAt());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(DeliveryEvent.class));
    }

    @Test
    void testUpdateDeliveryStatusOutForDelivery() {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest("OUT_FOR_DELIVERY", null);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery));
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

        DeliveryDto result = deliveryService.updateDeliveryStatusByAgent(3L, 1L, request);

        assertNotNull(result);
        assertEquals("OUT_FOR_DELIVERY", result.getStatus());
        verify(orderClient).updateOrderStatus(eq(100L), anyMap());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(DeliveryEvent.class));
    }

    @Test
    void testUpdateDeliveryStatusDelivered() {
        mockDelivery.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest("DELIVERED", null);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery));
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

        DeliveryDto result = deliveryService.updateDeliveryStatusByAgent(3L, 1L, request);

        assertNotNull(result);
        assertEquals("DELIVERED", result.getStatus());
        assertNotNull(result.getDeliveredAt());
        verify(orderClient).updateOrderStatus(eq(100L), anyMap());
        verify(paymentClient).completeCodPayment(100L);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(DeliveryEvent.class));
    }

    @Test
    void testUpdateDeliveryStatusUnauthorizedAgent() {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest("DELIVERED", null);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(mockDelivery));

        assertThrows(DeliveryException.class, () -> deliveryService.updateDeliveryStatusByAgent(99L, 1L, request));
    }

    @Test
    void testGetDeliveryByOrderId() {
        when(deliveryRepository.findByOrderId(100L)).thenReturn(Optional.of(mockDelivery));

        DeliveryDto result = deliveryService.getDeliveryByOrderId(100L);

        assertNotNull(result);
        assertEquals(100L, result.getOrderId());
        assertEquals("ASSIGNED", result.getStatus());
    }
}
