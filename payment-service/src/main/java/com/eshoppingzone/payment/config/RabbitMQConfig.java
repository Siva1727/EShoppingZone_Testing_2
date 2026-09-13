package com.eshoppingzone.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "eshoppingzone.exchange";
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "eshoppingzone.payment.success";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "eshoppingzone.payment.failed";
    public static final String REFUND_COMPLETED_ROUTING_KEY = "eshoppingzone.refund.completed";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
