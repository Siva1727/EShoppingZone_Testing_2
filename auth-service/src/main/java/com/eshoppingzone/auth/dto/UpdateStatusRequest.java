package com.eshoppingzone.auth.dto;

import com.eshoppingzone.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private UserStatus status;

    public UpdateStatusRequest() {
    }

    public UpdateStatusRequest(UserStatus status) {
        this.status = status;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
