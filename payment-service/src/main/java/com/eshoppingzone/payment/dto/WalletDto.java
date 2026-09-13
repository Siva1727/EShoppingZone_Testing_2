package com.eshoppingzone.payment.dto;

import java.math.BigDecimal;

public class WalletDto {
    private Long id;
    private Long userId;
    private BigDecimal balance;

    public WalletDto() {
    }

    public WalletDto(Long id, Long userId, BigDecimal balance) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
