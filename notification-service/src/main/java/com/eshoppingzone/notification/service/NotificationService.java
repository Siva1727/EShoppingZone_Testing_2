package com.eshoppingzone.notification.service;

import com.eshoppingzone.notification.dto.NotificationDto;

import java.util.List;

public interface NotificationService {
    NotificationDto createNotification(Long userId, String title, String message, String type);
    List<NotificationDto> getMyNotifications(Long userId);
    List<NotificationDto> getMyUnreadNotifications(Long userId);
    NotificationDto markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
    List<NotificationDto> getUserNotifications(Long userId);
}
