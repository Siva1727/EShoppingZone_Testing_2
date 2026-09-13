package com.eshoppingzone.payment.dto;

import java.math.BigDecimal;

public class WalletTransferRequest {
    private Long userId;
    private BigDecimal amount;
    private String reference;
    private String description;

    public WalletTransferRequest() {
    }

    public WalletTransferRequest(Long userId, BigDecimal amount, String reference, String description) {
        this.userId = userId;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
