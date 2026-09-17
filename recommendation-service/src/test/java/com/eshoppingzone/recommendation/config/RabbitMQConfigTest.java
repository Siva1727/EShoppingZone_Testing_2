package com.eshoppingzone.recommendation.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {

    @Test
    void declaresProductViewExchangeQueueAndBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        TopicExchange exchange = config.exchange();
        Queue queue = config.productViewedQueue();
        Binding binding = config.productViewedBinding(queue, exchange);

        assertEquals("eshoppingzone.exchange", exchange.getName());
        assertEquals("eshoppingzone.recommendation.product.viewed.queue", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.recommendation.product.viewed.queue", binding.getDestination());
        assertEquals("eshoppingzone.product.viewed", binding.getRoutingKey());
    }
}
