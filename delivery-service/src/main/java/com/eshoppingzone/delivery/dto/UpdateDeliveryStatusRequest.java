package com.eshoppingzone.delivery.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateDeliveryStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String failureReason;

    public UpdateDeliveryStatusRequest() {
    }

    public UpdateDeliveryStatusRequest(String status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
