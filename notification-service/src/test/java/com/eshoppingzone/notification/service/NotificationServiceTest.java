package com.eshoppingzone.notification.service;

import com.eshoppingzone.notification.dto.NotificationDto;
import com.eshoppingzone.notification.entity.Notification;
import com.eshoppingzone.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification mockNotification;

    @BeforeEach
    void setUp() {
        mockNotification = new Notification(4L, "Order Confirmed", "Your order #100 is placed", "ORDER_CONFIRMED");
        mockNotification.setId(1L);
    }

    @Test
    void testCreateNotification() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(1L);
            return n;
        });

        NotificationDto result = notificationService.createNotification(4L, "Order Confirmed", "Your order #100 is placed", "ORDER_CONFIRMED");

        assertNotNull(result);
        assertEquals(4L, result.getUserId());
        assertEquals("Order Confirmed", result.getTitle());
        assertFalse(result.isRead());
    }

    @Test
    void testGetMyNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(4L))
                .thenReturn(Collections.singletonList(mockNotification));

        List<NotificationDto> result = notificationService.getMyNotifications(4L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Order Confirmed", result.get(0).getTitle());
    }

    @Test
    void testGetMyUnreadNotifications() {
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(4L, false))
                .thenReturn(Collections.singletonList(mockNotification));

        List<NotificationDto> result = notificationService.getMyUnreadNotifications(4L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertFalse(result.get(0).isRead());
    }

    @Test
    void testMarkAsRead() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(mockNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto result = notificationService.markAsRead(1L, 4L);

        assertNotNull(result);
        assertTrue(result.isRead());
    }

    @Test
    void testMarkAllAsRead() {
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(4L, false))
                .thenReturn(Collections.singletonList(mockNotification));

        notificationService.markAllAsRead(4L);

        verify(notificationRepository).saveAll(anyList());
        assertTrue(mockNotification.isRead());
    }
}
