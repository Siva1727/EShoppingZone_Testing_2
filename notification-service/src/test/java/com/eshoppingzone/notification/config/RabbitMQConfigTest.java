package com.eshoppingzone.notification.config;

import com.eshoppingzone.notification.dto.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private boolean matchesTopic(String pattern, String routingKey) {
        String regex = "^" + pattern
                .replace(".", "\\.")
                .replace("*", "[^.]+")
                .replace("#", ".*") + "$";
        return Pattern.matches(regex, routingKey);
    }

    @Test
    void declaresDurableDeadLetterQueueAndBinding() {
        Queue queue = config.deadLetterQueue();
        TopicExchange exchange = config.deadLetterExchange();
        Binding binding = config.deadLetterBinding(queue, exchange);

        assertEquals(RabbitMQConfig.DLQ, queue.getName());
        assertEquals(RabbitMQConfig.DLX, exchange.getName());
        assertEquals(RabbitMQConfig.DLX, binding.getExchange());
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, binding.getRoutingKey());
        assertTrue(queue.isDurable());
    }

    @Test
    void createsRabbitListenerFactoryWithRetryDependencies() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                connectionFactory, rabbitTemplate);

        assertNotNull(factory);
    }

    @Test
    void testOrderConfirmedReachesNotificationOrderQueue() {
        Queue queue = config.orderQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.orderBinding(queue, exchange);

        assertEquals("notification.order.queue", binding.getDestination());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.order.#", binding.getRoutingKey());

        String publishedRoutingKey = "eshoppingzone.order.confirmed";
        assertTrue(matchesTopic(binding.getRoutingKey(), publishedRoutingKey),
                "eshoppingzone.order.confirmed should match the order binding pattern");
    }

    @Test
    void testOrderCreatedMatchesOrderBinding() {
        Queue queue = config.orderQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.orderBinding(queue, exchange);

        assertEquals("eshoppingzone.order.#", binding.getRoutingKey());

        String publishedRoutingKey = "eshoppingzone.order.created";
        assertTrue(matchesTopic(binding.getRoutingKey(), publishedRoutingKey),
                "eshoppingzone.order.created should match the order binding pattern");
    }

    @Test
    void testUserRegisteredMatchesUserBinding() {
        Queue queue = config.userQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.userBinding(queue, exchange);

        assertEquals("notification.user.queue", binding.getDestination());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.user.#", binding.getRoutingKey());

        String publishedRoutingKey = "eshoppingzone.user.registered";
        assertTrue(matchesTopic(binding.getRoutingKey(), publishedRoutingKey),
                "eshoppingzone.user.registered should match the user binding pattern");
    }

    @Test
    void testPaymentSuccessMatchesPaymentBinding() {
        Queue queue = config.paymentQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.paymentBinding(queue, exchange);

        assertEquals("notification.payment.queue", binding.getDestination());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.payment.#", binding.getRoutingKey());

        String publishedRoutingKey = "eshoppingzone.payment.success";
        assertTrue(matchesTopic(binding.getRoutingKey(), publishedRoutingKey),
                "eshoppingzone.payment.success should match the payment binding pattern");
    }

    @Test
    void testRefundCompletedMatchesRefundBinding() {
        Queue queue = config.paymentQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.refundBinding(queue, exchange);

        assertEquals("notification.payment.queue", binding.getDestination());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.refund.#", binding.getRoutingKey());

        String publishedRoutingKey = "eshoppingzone.refund.completed";
        assertTrue(matchesTopic(binding.getRoutingKey(), publishedRoutingKey),
                "eshoppingzone.refund.completed should match the refund binding pattern");
    }

    @Test
    void testOrderEventAmountCorrectlyDeserializedAsTotalAmount() throws Exception {
        String jsonPayloadWithAmount = "{"
                + "\"eventType\":\"ORDER_CONFIRMED\","
                + "\"orderId\":24,"
                + "\"customerId\":51,"
                + "\"amount\":899.99,"
                + "\"paymentMethod\":\"WALLET\","
                + "\"status\":\"CONFIRMED\""
                + "}";

        OrderEvent event = objectMapper.readValue(jsonPayloadWithAmount, OrderEvent.class);

        assertNotNull(event);
        assertEquals("ORDER_CONFIRMED", event.getEventType());
        assertEquals(24L, event.getOrderId());
        assertEquals(51L, event.getCustomerId());
        assertEquals(new BigDecimal("899.99"), event.getTotalAmount());
        assertEquals("WALLET", event.getPaymentMethod());
        assertEquals("CONFIRMED", event.getStatus());
    }

    @Test
    void testOrderEventTotalAmountDeserializationCompatibility() throws Exception {
        String jsonPayloadWithTotalAmount = "{"
                + "\"eventType\":\"ORDER_CONFIRMED\","
                + "\"orderId\":24,"
                + "\"customerId\":51,"
                + "\"totalAmount\":899.99,"
                + "\"paymentMethod\":\"WALLET\","
                + "\"status\":\"CONFIRMED\""
                + "}";

        OrderEvent event = objectMapper.readValue(jsonPayloadWithTotalAmount, OrderEvent.class);

        assertNotNull(event);
        assertEquals(new BigDecimal("899.99"), event.getTotalAmount());
    }
}
