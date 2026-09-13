package com.eshoppingzone.order.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "eshoppingzone.exchange";
    public static final String ORDER_CREATED_ROUTING_KEY = "eshoppingzone.order.created";
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "eshoppingzone.order.confirmed";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "eshoppingzone.order.cancelled";
    public static final String ORDER_STATUS_ROUTING_KEY = "eshoppingzone.order.status";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
