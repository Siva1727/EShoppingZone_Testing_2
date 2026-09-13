package com.eshoppingzone.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "eshoppingzone.exchange";

    public static final String QUEUE_USER_NOTIFICATIONS = "notification.user.queue";
    public static final String QUEUE_ORDER_NOTIFICATIONS = "notification.order.queue";
    public static final String QUEUE_PAYMENT_NOTIFICATIONS = "notification.payment.queue";
    public static final String QUEUE_DELIVERY_NOTIFICATIONS = "notification.delivery.queue";

    public static final String ROUTING_KEY_USER = "user.#";
    public static final String ROUTING_KEY_ORDER = "order.#";
    public static final String ROUTING_KEY_PAYMENT = "payment.#";
    public static final String ROUTING_KEY_REFUND = "refund.#";
    public static final String ROUTING_KEY_DELIVERY = "delivery.#";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue userQueue() {
        return new Queue(QUEUE_USER_NOTIFICATIONS, true);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(QUEUE_ORDER_NOTIFICATIONS, true);
    }

    @Bean
    public Queue paymentQueue() {
        return new Queue(QUEUE_PAYMENT_NOTIFICATIONS, true);
    }

    @Bean
    public Queue deliveryQueue() {
        return new Queue(QUEUE_DELIVERY_NOTIFICATIONS, true);
    }

    @Bean
    public Binding userBinding(Queue userQueue, TopicExchange exchange) {
        return BindingBuilder.bind(userQueue).to(exchange).with(ROUTING_KEY_USER);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange exchange) {
        return BindingBuilder.bind(orderQueue).to(exchange).with(ROUTING_KEY_ORDER);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange exchange) {
        return BindingBuilder.bind(paymentQueue).to(exchange).with(ROUTING_KEY_PAYMENT);
    }

    @Bean
    public Binding refundBinding(Queue paymentQueue, TopicExchange exchange) {
        return BindingBuilder.bind(paymentQueue).to(exchange).with(ROUTING_KEY_REFUND);
    }

    @Bean
    public Binding deliveryBinding(Queue deliveryQueue, TopicExchange exchange) {
        return BindingBuilder.bind(deliveryQueue).to(exchange).with(ROUTING_KEY_DELIVERY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
