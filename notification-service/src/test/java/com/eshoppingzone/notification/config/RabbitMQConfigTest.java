package com.eshoppingzone.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void declaresDurableDeadLetterQueueAndBinding() {
        Queue queue = config.deadLetterQueue();
        DirectExchange exchange = config.deadLetterExchange();
        Binding binding = config.deadLetterBinding(queue, exchange);

        assertEquals(RabbitMQConfig.DLQ, queue.getName());
        assertEquals(RabbitMQConfig.DLX, exchange.getName());
        assertEquals(RabbitMQConfig.DLX, binding.getExchange());
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, binding.getRoutingKey());
        assertEquals(true, queue.isDurable());
    }

    @Test
    void createsRabbitListenerFactoryWithRetryDependencies() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                connectionFactory, rabbitTemplate);

        assertNotNull(factory);
    }
}
