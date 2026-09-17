package com.eshoppingzone.product.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductViewEventPublisher {

    public static final String EXCHANGE = "eshoppingzone.exchange";
    public static final String ROUTING_KEY = "eshoppingzone.product.viewed";

    private static final Logger log = LoggerFactory.getLogger(ProductViewEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ProductViewEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long customerId, Long productId) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY,
                    new ProductViewEvent(customerId, productId));
        } catch (RuntimeException ex) {
            // Recommendations are asynchronous and must not make product browsing fail.
            log.warn("Unable to publish product view event for customer {} and product {}",
                    customerId, productId, ex);
        }
    }
}
