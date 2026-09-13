package com.eshoppingzone.profile.listener;

import com.eshoppingzone.profile.config.RabbitMQConfig;
import com.eshoppingzone.profile.dto.UserRegisteredEvent;
import com.eshoppingzone.profile.entity.UserProfile;
import com.eshoppingzone.profile.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventListener.class);

    private final UserProfileRepository userProfileRepository;

    public UserRegisteredEventListener(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.PROFILE_USER_REGISTERED_QUEUE)
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Received USER_REGISTERED event for userId: {}", event.getUserId());
        if (!userProfileRepository.existsByUserId(event.getUserId())) {
            UserProfile profile = new UserProfile();
            profile.setUserId(event.getUserId());
            profile.setUsername(event.getUsername());
            profile.setEmail(event.getEmail());
            profile.setFullName(event.getFullName());
            userProfileRepository.save(profile);
            log.info("Initialized UserProfile for userId: {}", event.getUserId());
        }
    }
}
