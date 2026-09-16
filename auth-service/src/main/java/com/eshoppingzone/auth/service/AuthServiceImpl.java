package com.eshoppingzone.auth.service;

import com.eshoppingzone.auth.config.RabbitMQConfig;
import com.eshoppingzone.auth.dto.*;
import com.eshoppingzone.auth.entity.PasswordResetToken;
import com.eshoppingzone.auth.entity.RefreshToken;
import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.exception.DuplicateResourceException;
import com.eshoppingzone.auth.exception.InvalidCredentialsException;
import com.eshoppingzone.auth.exception.InvalidTokenException;
import com.eshoppingzone.auth.exception.ResourceNotFoundException;
import com.eshoppingzone.auth.repository.PasswordResetTokenRepository;
import com.eshoppingzone.auth.repository.RefreshTokenRepository;
import com.eshoppingzone.auth.repository.UserRepository;
import com.eshoppingzone.auth.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RabbitTemplate rabbitTemplate;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordResetTokenRepository tokenRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        // Publish event to RabbitMQ
        try {
            UserRegisteredEvent event = new UserRegisteredEvent(
                    savedUser.getId(),
                    savedUser.getUsername(),
                    savedUser.getEmail(),
                    savedUser.getFullName(),
                    savedUser.getRole()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_REGISTERED_ROUTING_KEY, event);
        } catch (Exception e) {
            log.warn("Failed to publish user registered event to RabbitMQ: {}", e.getMessage());
        }

        return UserDto.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new InvalidCredentialsException("Account has been blocked. Please contact support.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InvalidCredentialsException("Account is inactive. Please contact support.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, refreshJti);

        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs() / 1000);
        RefreshToken refreshTokenEntity = new RefreshToken(refreshJti, user, expiryDate);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Refresh token cannot be blank");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(token);
        if (JwtTokenProvider.TOKEN_TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException("Access token cannot be used to refresh tokens. Please provide a valid refresh token.");
        }
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException("Invalid token type. Expected a refresh token.");
        }

        String jti = jwtTokenProvider.getJti(token);
        if (jti == null || jti.isBlank()) {
            throw new InvalidTokenException("Invalid refresh token: missing JTI");
        }

        RefreshToken tokenRecord = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or invalid"));

        if (tokenRecord.isRevoked()) {
            throw new InvalidTokenException("Revoked refresh token reuse detected. Please log in again.");
        }

        if (tokenRecord.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        User user = tokenRecord.getUser();
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new InvalidCredentialsException("Account has been blocked. Please contact support.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InvalidCredentialsException("Account is inactive. Please contact support.");
        }

        // Token rotation: Revoke existing refresh token and link to new JTI
        String newRefreshJti = UUID.randomUUID().toString();
        tokenRecord.setRevoked(true);
        tokenRecord.setRevokedAt(LocalDateTime.now());
        tokenRecord.setReplacedByJti(newRefreshJti);
        refreshTokenRepository.save(tokenRecord);

        // Generate new token pair
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user, newRefreshJti);

        LocalDateTime newExpiryDate = LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs() / 1000);
        RefreshToken newRecord = new RefreshToken(newRefreshJti, user, newExpiryDate);
        refreshTokenRepository.save(newRecord);

        return new TokenRefreshResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        String token = request.getRefreshToken();
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Refresh token is required for logout");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(token);
        if (JwtTokenProvider.TOKEN_TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException("Invalid token type for logout. Expected a refresh token.");
        }
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException("Invalid token type for logout.");
        }

        String jti = jwtTokenProvider.getJti(token);
        if (jti != null && !jti.isBlank()) {
            refreshTokenRepository.findByJti(jti).ifPresent(record -> {
                if (!record.isRevoked()) {
                    record.setRevoked(true);
                    record.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(record);
                }
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        // Invalidate any existing active tokens
        tokenRepository.findByUserAndUsedFalse(user).ifPresent(t -> {
            t.setUsed(true);
            tokenRepository.save(t);
        });

        String resetToken = UUID.randomUUID().toString();
        PasswordResetToken tokenEntity = new PasswordResetToken(
                resetToken,
                user,
                LocalDateTime.now().plusHours(1)
        );
        tokenRepository.save(tokenEntity);

        // Print to console for backend testing simulation
        System.out.println("==================================================");
        System.out.println("[AUTH-SERVICE] PASSWORD RESET REQUEST");
        System.out.println("User: " + user.getUsername() + " (" + user.getEmail() + ")");
        System.out.println("Reset Token: " + resetToken);
        System.out.println("Expires At: " + tokenEntity.getExpiryDate());
        System.out.println("==================================================");
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new InvalidTokenException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        System.out.println("[AUTH-SERVICE] Password successfully reset for user: " + user.getUsername());
    }

    @Override
    @Transactional
    public UserDto createUserByAdmin(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        return UserDto.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(Role role, UserStatus status) {
        List<User> users;
        if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status);
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else if (status != null) {
            users = userRepository.findByStatus(status);
        } else {
            users = userRepository.findAll();
        }

        return users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public UserDto updateUserStatus(Long id, UserStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setStatus(status);
        User saved = userRepository.save(user);
        return UserDto.fromEntity(saved);
    }
}
