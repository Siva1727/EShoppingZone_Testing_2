package com.eshoppingzone.recommendation.config;

import com.eshoppingzone.recommendation.messaging.ProductViewEventListener;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "eshoppingzone.exchange";
    public static final String ROUTING_KEY = "eshoppingzone.product.viewed";
    public static final String QUEUE = ProductViewEventListener.QUEUE;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue productViewedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding productViewedBinding(Queue productViewedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(productViewedQueue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
