package com.eshoppingzone.auth.controller;

import com.eshoppingzone.auth.dto.ApiResponse;
import com.eshoppingzone.auth.dto.CreateUserRequest;
import com.eshoppingzone.auth.dto.UpdateStatusRequest;
import com.eshoppingzone.auth.dto.UserDto;
import com.eshoppingzone.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/admin/users")
@Tag(name = "Admin User Management", description = "Admin endpoints to create and manage users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @Operation(summary = "Create User (Admin Only)", description = "Create a MERCHANT, DELIVERY_AGENT, or ADMIN user")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto userDto = authService.createUserByAdmin(request);
        return new ResponseEntity<>(ApiResponse.success("User created successfully", userDto), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get All Users (Admin Only)", description = "List all registered users in the platform")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        List<UserDto> users = authService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get User by ID (Admin Only)", description = "Retrieve a user by their user ID")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        UserDto userDto = authService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", userDto));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update User Status (Admin Only)", description = "Set user status to ACTIVE, INACTIVE, or BLOCKED")
    public ResponseEntity<ApiResponse<UserDto>> updateUserStatus(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateStatusRequest request) {
        UserDto userDto = authService.updateUserStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", userDto));
    }
}
