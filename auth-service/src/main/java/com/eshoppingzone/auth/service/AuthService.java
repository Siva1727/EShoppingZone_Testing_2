package com.eshoppingzone.auth.service;

import com.eshoppingzone.auth.dto.*;
import com.eshoppingzone.auth.entity.UserStatus;

import java.util.List;

public interface AuthService {
    UserDto register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserDto getCurrentUser(String username);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    UserDto createUserByAdmin(CreateUserRequest request);
    List<UserDto> getAllUsers();
    UserDto getUserById(Long id);
    UserDto updateUserStatus(Long id, UserStatus status);
}

