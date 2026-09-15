package com.eshoppingzone.profile.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class RabbitMqDeadLetterIT {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Test
    void publishesFailedMessageToDeclaredDeadLetterQueue() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        RabbitMQConfig config = new RabbitMQConfig();
        TopicExchange exchange = config.deadLetterExchange();
        Queue queue = config.deadLetterQueue();
        Binding binding = config.deadLetterBinding(queue, exchange);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.convertAndSend(RabbitMQConfig.DLX, RabbitMQConfig.DLQ_ROUTING_KEY, "failed-event");

        assertEquals("failed-event", template.receiveAndConvert(RabbitMQConfig.DLQ, 5000));
        connectionFactory.destroy();
    }
}
