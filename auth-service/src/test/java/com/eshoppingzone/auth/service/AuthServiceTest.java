package com.eshoppingzone.auth.service;

import com.eshoppingzone.auth.dto.*;
import com.eshoppingzone.auth.entity.PasswordResetToken;
import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.exception.DuplicateResourceException;
import com.eshoppingzone.auth.exception.InvalidCredentialsException;
import com.eshoppingzone.auth.exception.InvalidTokenException;
import com.eshoppingzone.auth.repository.PasswordResetTokenRepository;
import com.eshoppingzone.auth.repository.UserRepository;
import com.eshoppingzone.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

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
        verify(jwtTokenProvider, never()).generateToken(any(User.class));
    }

    @Test
    void testRegisterDuplicateUsernameThrowsException() {
        RegisterRequest request = new RegisterRequest("existinguser", "new@example.com", "Password123", "New User", "9876543210");
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("testuser", "Password123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateToken(sampleUser)).thenReturn("mock-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals(Role.CUSTOMER, response.getRole());
    }

    @Test
    void testLoginInvalidPasswordThrowsException() {
        LoginRequest request = new LoginRequest("testuser", "WrongPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "encodedPassword")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
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
