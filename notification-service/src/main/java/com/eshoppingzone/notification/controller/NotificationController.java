package com.eshoppingzone.notification.controller;

import com.eshoppingzone.notification.dto.ApiResponse;
import com.eshoppingzone.notification.dto.NotificationDto;
import com.eshoppingzone.notification.security.UserPrincipal;
import com.eshoppingzone.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification Management", description = "APIs for viewing user notifications and updating read status")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping("/my")
    @Operation(summary = "Get My Notifications", description = "Retrieve list of all notifications for the current authenticated user")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getMyNotifications(Authentication authentication,
                                                                                @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        Long userId = getUserId(authentication);
        List<NotificationDto> notifications;
        if (unreadOnly) {
            notifications = notificationService.getMyUnreadNotifications(userId);
        } else {
            notifications = notificationService.getMyNotifications(userId);
        }
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", notifications));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark Notification as Read", description = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(Authentication authentication,
                                                                   @PathVariable Long id) {
        Long userId = getUserId(authentication);
        NotificationDto notification = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notification));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark All as Read", description = "Mark all unread notifications as read for current user")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
        Long userId = getUserId(authentication);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get User Notifications (Admin)", description = "Admin views notification log for any specific user")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getUserNotifications(@PathVariable Long userId) {
        List<NotificationDto> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success("User notifications retrieved successfully", notifications));
    }
}
