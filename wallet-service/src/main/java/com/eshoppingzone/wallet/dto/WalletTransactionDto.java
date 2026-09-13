package com.eshoppingzone.wallet.dto;

import com.eshoppingzone.wallet.entity.TransactionType;
import com.eshoppingzone.wallet.entity.WalletTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletTransactionDto {
    private Long id;
    private Long walletId;
    private TransactionType type;
    private BigDecimal amount;
    private String reference;
    private String description;
    private LocalDateTime createdAt;

    public WalletTransactionDto() {
    }

    public WalletTransactionDto(Long id, Long walletId, TransactionType type, BigDecimal amount, String reference, String description, LocalDateTime createdAt) {
        this.id = id;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static WalletTransactionDto fromEntity(WalletTransaction t) {
        if (t == null) return null;
        return new WalletTransactionDto(
                t.getId(),
                t.getWalletId(),
                t.getType(),
                t.getAmount(),
                t.getReference(),
                t.getDescription(),
                t.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
