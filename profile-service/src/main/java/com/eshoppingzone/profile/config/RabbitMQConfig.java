package com.eshoppingzone.profile.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "eshoppingzone.exchange";
    public static final String PROFILE_USER_REGISTERED_QUEUE = "eshoppingzone.profile.user.registered.queue";
    public static final String USER_REGISTERED_ROUTING_KEY = "eshoppingzone.user.registered";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue profileUserRegisteredQueue() {
        return new Queue(PROFILE_USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Binding profileUserRegisteredBinding(Queue profileUserRegisteredQueue, TopicExchange exchange) {
        return BindingBuilder.bind(profileUserRegisteredQueue).to(exchange).with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
