package com.eshoppingzone.notification.consumer;

import com.eshoppingzone.notification.config.RabbitMQConfig;
import com.eshoppingzone.notification.dto.DeliveryEvent;
import com.eshoppingzone.notification.dto.OrderEvent;
import com.eshoppingzone.notification.dto.PaymentEvent;
import com.eshoppingzone.notification.dto.UserEvent;
import com.eshoppingzone.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_NOTIFICATIONS)
    public void handleUserRegistration(UserEvent event) {
        log.info("\n======================================================\n" +
                "[NOTIFICATION-SERVICE] User Registered Event\n" +
                "User ID: {}\n" +
                "Username: {}\n" +
                "Email: {}\n" +
                "Role: {}\n" +
                "======================================================",
                event.getUserId(), event.getUsername(), event.getEmail(), event.getRole());

        String title = "Welcome to EShoppingZone!";
        String message = "Hello " + (event.getFullName() != null ? event.getFullName() : event.getUsername()) +
                ", your account has been successfully created with role " + event.getRole() + ".";

        notificationService.createNotification(event.getUserId(), title, message, "USER_REGISTRATION");
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_NOTIFICATIONS)
    public void handleOrderEvent(OrderEvent event) {
        log.info("\n======================================================\n" +
                "[NOTIFICATION-SERVICE] Order Event: {}\n" +
                "Order ID: {}\n" +
                "Customer ID: {}\n" +
                "Total Amount: ${}\n" +
                "Status: {}\n" +
                "Payment Method: {}\n" +
                "======================================================",
                event.getEventType(), event.getOrderId(), event.getCustomerId(),
                event.getTotalAmount(), event.getStatus(), event.getPaymentMethod());

        String title;
        String message;
        String type;

        if ("ORDER_CREATED".equalsIgnoreCase(event.getEventType()) || "ORDER_CONFIRMED".equalsIgnoreCase(event.getEventType())) {
            title = "Order Confirmed #" + event.getOrderId();
            message = "Your order #" + event.getOrderId() + " for $" + event.getTotalAmount() + " has been placed successfully via " + event.getPaymentMethod() + ".";
            type = "ORDER_CONFIRMED";
        } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.getEventType())) {
            title = "Order Cancelled #" + event.getOrderId();
            message = "Your order #" + event.getOrderId() + " has been cancelled. Any reserved amount will be refunded.";
            type = "ORDER_CANCELLED";
        } else {
            title = "Order Update #" + event.getOrderId();
            message = "Your order #" + event.getOrderId() + " status is now " + event.getStatus() + ".";
            type = "ORDER_STATUS";
        }

        notificationService.createNotification(event.getCustomerId(), title, message, type);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_NOTIFICATIONS)
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("\n======================================================\n" +
                "[NOTIFICATION-SERVICE] Payment Event: {}\n" +
                "Payment ID: {}\n" +
                "Order ID: {}\n" +
                "Customer ID: {}\n" +
                "Amount: ${}\n" +
                "Status: {}\n" +
                "Txn Ref: {}\n" +
                "======================================================",
                event.getEventType(), event.getPaymentId(), event.getOrderId(),
                event.getCustomerId(), event.getAmount(), event.getStatus(), event.getTransactionRef());

        String title;
        String message;
        String type;

        if ("REFUND_COMPLETED".equalsIgnoreCase(event.getEventType())) {
            title = "Refund Processed for Order #" + event.getOrderId();
            message = "A refund of $" + event.getAmount() + " has been credited to your wallet (Ref: " + event.getTransactionRef() + ").";
            type = "REFUND_SUCCESS";
        } else if ("PAYMENT_SUCCESS".equalsIgnoreCase(event.getEventType())) {
            title = "Payment Successful for Order #" + event.getOrderId();
            message = "Payment of $" + event.getAmount() + " via " + event.getPaymentMethod() + " was successful.";
            type = "PAYMENT_SUCCESS";
        } else {
            title = "Payment Update for Order #" + event.getOrderId();
            message = "Payment status: " + event.getStatus() + ". " + (event.getMessage() != null ? event.getMessage() : "");
            type = "PAYMENT_STATUS";
        }

        notificationService.createNotification(event.getCustomerId(), title, message, type);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELIVERY_NOTIFICATIONS)
    public void handleDeliveryEvent(DeliveryEvent event) {
        log.info("\n======================================================\n" +
                "[NOTIFICATION-SERVICE] Delivery Event: {}\n" +
                "Delivery ID: {}\n" +
                "Order ID: {}\n" +
                "Customer ID: {}\n" +
                "Agent ID: {}\n" +
                "Status: {}\n" +
                "======================================================",
                event.getEventType(), event.getDeliveryId(), event.getOrderId(),
                event.getCustomerId(), event.getDeliveryAgentId(), event.getStatus());

        String title;
        String message;
        String type;

        if ("DELIVERY_ASSIGNED".equalsIgnoreCase(event.getEventType())) {
            title = "Delivery Agent Assigned for Order #" + event.getOrderId();
            message = "A delivery agent has been assigned to your order #" + event.getOrderId() + " and will dispatch shortly.";
            type = "DELIVERY_ASSIGNED";
        } else if ("DELIVERED".equalsIgnoreCase(event.getStatus())) {
            title = "Order Delivered! #" + event.getOrderId();
            message = "Your package for order #" + event.getOrderId() + " has been delivered successfully. Thank you for shopping with EShoppingZone!";
            type = "DELIVERY_DELIVERED";
        } else if ("FAILED".equalsIgnoreCase(event.getStatus())) {
            title = "Delivery Attempt Failed #" + event.getOrderId();
            message = "Delivery attempt failed for order #" + event.getOrderId() + ". Reason: " + (event.getFailureReason() != null ? event.getFailureReason() : "Recipient unavailable");
            type = "DELIVERY_FAILED";
        } else {
            title = "Delivery Update for Order #" + event.getOrderId();
            message = "Your order #" + event.getOrderId() + " delivery status is now " + event.getStatus() + ".";
            type = "DELIVERY_STATUS";
        }

        notificationService.createNotification(event.getCustomerId(), title, message, type);
    }
}
