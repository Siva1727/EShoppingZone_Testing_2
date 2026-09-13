package com.eshoppingzone.auth.config;

import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser("admin", "admin@eshoppingzone.com", "Admin@123", "System Administrator", "9999999991", Role.ADMIN);
        seedUser("merchant1", "merchant1@eshoppingzone.com", "Merchant@123", "Primary Merchant", "9999999992", Role.MERCHANT);
        seedUser("delivery1", "delivery1@eshoppingzone.com", "Delivery@123", "Primary Delivery Agent", "9999999993", Role.DELIVERY_AGENT);
        seedUser("customer1", "customer1@eshoppingzone.com", "Customer@123", "John Customer", "9999999994", Role.CUSTOMER);
    }

    private void seedUser(String username, String email, String rawPassword, String fullName, String phone, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setFullName(fullName);
            user.setPhoneNumber(phone);
            user.setRole(role);
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            log.info("Seeded initial user: {} with role: {}", username, role);
        }
    }
}
