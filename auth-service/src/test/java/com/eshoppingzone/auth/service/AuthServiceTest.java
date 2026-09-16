package com.eshoppingzone.auth.service;

import com.eshoppingzone.auth.dto.*;
import com.eshoppingzone.auth.entity.PasswordResetToken;
import com.eshoppingzone.auth.entity.RefreshToken;
import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.exception.DuplicateResourceException;
import com.eshoppingzone.auth.exception.InvalidCredentialsException;
import com.eshoppingzone.auth.exception.InvalidTokenException;
import com.eshoppingzone.auth.repository.PasswordResetTokenRepository;
import com.eshoppingzone.auth.repository.RefreshTokenRepository;
import com.eshoppingzone.auth.repository.UserRepository;
import com.eshoppingzone.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("testuser");
        sampleUser.setEmail("test@example.com");
        sampleUser.setPassword("encodedPassword");
        sampleUser.setFullName("Test User");
        sampleUser.setPhoneNumber("1234567890");
        sampleUser.setRole(Role.CUSTOMER);
        sampleUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "Password123", "New User", "9876543210");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserDto response = authService.register(request);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals(Role.CUSTOMER, response.getRole());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        verify(userRepository, times(1)).save(any(User.class));
        verify(jwtTokenProvider, never()).generateAccessToken(any(User.class));
    }

    @Test
    void testRegisterDuplicateUsernameThrowsException() {
        RegisterRequest request = new RegisterRequest("existinguser", "new@example.com", "Password123", "New User", "9876543210");
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Login generates both Access Token and Refresh Token and persists refresh JTI")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("testuser", "Password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(sampleUser)).thenReturn("mock-access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(sampleUser), anyString())).thenReturn("mock-refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertEquals("mock-access-token", response.getToken()); // backward compatibility getter
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(Role.CUSTOMER, response.getRole());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void testLoginInvalidPasswordThrowsException() {
        LoginRequest request = new LoginRequest("testuser", "WrongPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "encodedPassword")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testLoginBlockedUserThrowsException() {
        sampleUser.setStatus(UserStatus.BLOCKED);
        LoginRequest request = new LoginRequest("testuser", "Password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testLoginInactiveUserThrowsException() {
        sampleUser.setStatus(UserStatus.INACTIVE);
        LoginRequest request = new LoginRequest("testuser", "Password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Refresh Token succeeds: rotates refresh token, revokes old JTI, issues new token pair")
    void testRefreshTokenSuccess() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        RefreshToken existingRecord = new RefreshToken("old-jti-123", sampleUser, LocalDateTime.now().plusDays(7));

        when(jwtTokenProvider.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("valid-refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("valid-refresh-token")).thenReturn("old-jti-123");
        when(refreshTokenRepository.findByJti("old-jti-123")).thenReturn(Optional.of(existingRecord));
        when(jwtTokenProvider.generateAccessToken(sampleUser)).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(sampleUser), anyString())).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        TokenRefreshResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        assertTrue(existingRecord.isRevoked());
        assertNotNull(existingRecord.getReplacedByJti());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh Token fails if an Access Token is provided to /refresh")
    void testRefreshTokenFailsWithAccessToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("access-token-accidentally-sent");

        when(jwtTokenProvider.validateToken("access-token-accidentally-sent")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("access-token-accidentally-sent")).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        assertTrue(ex.getMessage().contains("Access token cannot be used to refresh tokens"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refresh Token fails when attempting to reuse a revoked token (Reuse Detection)")
    void testRefreshTokenFailsWhenRevoked_ReuseDetection() {
        RefreshTokenRequest request = new RefreshTokenRequest("revoked-refresh-token");
        RefreshToken revokedRecord = new RefreshToken("revoked-jti", sampleUser, LocalDateTime.now().plusDays(7));
        revokedRecord.setRevoked(true);

        when(jwtTokenProvider.validateToken("revoked-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("revoked-refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("revoked-refresh-token")).thenReturn("revoked-jti");
        when(refreshTokenRepository.findByJti("revoked-jti")).thenReturn(Optional.of(revokedRecord));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        assertTrue(ex.getMessage().contains("Revoked refresh token reuse detected"));
    }

    @Test
    @DisplayName("Refresh Token fails when token is expired")
    void testRefreshTokenFailsWhenExpired() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh-token");
        RefreshToken expiredRecord = new RefreshToken("expired-jti", sampleUser, LocalDateTime.now().minusDays(1));

        when(jwtTokenProvider.validateToken("expired-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("expired-refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("expired-refresh-token")).thenReturn("expired-jti");
        when(refreshTokenRepository.findByJti("expired-jti")).thenReturn(Optional.of(expiredRecord));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Refresh Token fails when user status is BLOCKED or INACTIVE")
    void testRefreshTokenFailsWhenUserBlocked() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        sampleUser.setStatus(UserStatus.BLOCKED);
        RefreshToken activeRecord = new RefreshToken("valid-jti", sampleUser, LocalDateTime.now().plusDays(7));

        when(jwtTokenProvider.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("valid-refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("valid-refresh-token")).thenReturn("valid-jti");
        when(refreshTokenRepository.findByJti("valid-jti")).thenReturn(Optional.of(activeRecord));

        assertThrows(InvalidCredentialsException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("Logout revokes the specific Refresh Token session by JTI")
    void testLogoutSuccess() {
        LogoutRequest request = new LogoutRequest("valid-refresh-token");
        RefreshToken activeRecord = new RefreshToken("target-jti", sampleUser, LocalDateTime.now().plusDays(7));

        when(jwtTokenProvider.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("valid-refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("valid-refresh-token")).thenReturn("target-jti");
        when(refreshTokenRepository.findByJti("target-jti")).thenReturn(Optional.of(activeRecord));

        authService.logout(request);

        assertTrue(activeRecord.isRevoked());
        assertNotNull(activeRecord.getRevokedAt());
        verify(refreshTokenRepository, times(1)).save(activeRecord);
    }

    @Test
    @DisplayName("Logout rejects access token supplied as logout credential")
    void testLogoutFailsWithAccessToken() {
        LogoutRequest request = new LogoutRequest("access-token-sent-to-logout");

        when(jwtTokenProvider.validateToken("access-token-sent-to-logout")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("access-token-sent-to-logout")).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);

        assertThrows(InvalidTokenException.class, () -> authService.logout(request));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Logout of already revoked token is idempotent and safe")
    void testLogoutIdempotentWhenAlreadyRevoked() {
        LogoutRequest request = new LogoutRequest("already-revoked-token");
        RefreshToken revokedRecord = new RefreshToken("target-jti", sampleUser, LocalDateTime.now().plusDays(7));
        revokedRecord.setRevoked(true);

        when(jwtTokenProvider.validateToken("already-revoked-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("already-revoked-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        when(jwtTokenProvider.getJti("already-revoked-token")).thenReturn("target-jti");
        when(refreshTokenRepository.findByJti("target-jti")).thenReturn(Optional.of(revokedRecord));

        authService.logout(request);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void testResetPasswordSuccess() {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "NewPassword123");
        PasswordResetToken resetToken = new PasswordResetToken("valid-token", sampleUser, LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("newEncodedPassword");

        authService.resetPassword(request);

        assertTrue(resetToken.isUsed());
        verify(userRepository, times(1)).save(sampleUser);
        verify(tokenRepository, times(1)).save(resetToken);
    }

    @Test
    void testResetPasswordExpiredTokenThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "NewPassword123");
        PasswordResetToken resetToken = new PasswordResetToken("expired-token", sampleUser, LocalDateTime.now().minusHours(1));

        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(resetToken));

        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetAllUsersNoFiltersReturnsAllUsers() {
        User merchant = new User(2L, "merchant1", "m1@example.com", "pass", "Merchant One", "1111111111", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(sampleUser, merchant));

        List<UserDto> result = authService.getAllUsers(null, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository, times(1)).findAll();
        verify(userRepository, never()).findByRole(any());
        verify(userRepository, never()).findByStatus(any());
        verify(userRepository, never()).findByRoleAndStatus(any(), any());
    }

    @Test
    void testGetAllUsersRoleFilterOnly() {
        User merchant = new User(2L, "merchant1", "m1@example.com", "pass", "Merchant One", "1111111111", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findByRole(Role.MERCHANT)).thenReturn(List.of(merchant));

        List<UserDto> result = authService.getAllUsers(Role.MERCHANT, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("merchant1", result.get(0).getUsername());
        assertEquals(Role.MERCHANT, result.get(0).getRole());
        verify(userRepository, times(1)).findByRole(Role.MERCHANT);
        verify(userRepository, never()).findAll();
    }

    @Test
    void testGetAllUsersStatusFilterOnly() {
        User inactiveUser = new User(3L, "inactiveUser", "in@example.com", "pass", "Inactive User", "2222222222", Role.CUSTOMER, UserStatus.INACTIVE);
        when(userRepository.findByStatus(UserStatus.INACTIVE)).thenReturn(List.of(inactiveUser));

        List<UserDto> result = authService.getAllUsers(null, UserStatus.INACTIVE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("inactiveUser", result.get(0).getUsername());
        assertEquals(UserStatus.INACTIVE, result.get(0).getStatus());
        verify(userRepository, times(1)).findByStatus(UserStatus.INACTIVE);
        verify(userRepository, never()).findAll();
    }

    @Test
    void testGetAllUsersRoleAndStatusFilter() {
        User activeMerchant = new User(2L, "merchant1", "m1@example.com", "pass", "Merchant One", "1111111111", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findByRoleAndStatus(Role.MERCHANT, UserStatus.ACTIVE)).thenReturn(List.of(activeMerchant));

        List<UserDto> result = authService.getAllUsers(Role.MERCHANT, UserStatus.ACTIVE);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("merchant1", result.get(0).getUsername());
        assertEquals(Role.MERCHANT, result.get(0).getRole());
        assertEquals(UserStatus.ACTIVE, result.get(0).getStatus());
        verify(userRepository, times(1)).findByRoleAndStatus(Role.MERCHANT, UserStatus.ACTIVE);
        verify(userRepository, never()).findAll();
        verify(userRepository, never()).findByRole(any());
        verify(userRepository, never()).findByStatus(any());
    }

    @Test
    void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserDto result = authService.getUserById(1L);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void testUpdateUserStatusSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserDto result = authService.updateUserStatus(1L, UserStatus.BLOCKED);

        assertNotNull(result);
        assertEquals(UserStatus.BLOCKED, sampleUser.getStatus());
        verify(userRepository, times(1)).save(sampleUser);
    }
}
