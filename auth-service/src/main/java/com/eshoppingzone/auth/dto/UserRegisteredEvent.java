package com.eshoppingzone.auth.dto;

import com.eshoppingzone.auth.entity.Role;
import java.io.Serializable;

public class UserRegisteredEvent implements Serializable {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private Role role;

    public UserRegisteredEvent() {
    }

    public UserRegisteredEvent(Long userId, String username, String email, String fullName, Role role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
