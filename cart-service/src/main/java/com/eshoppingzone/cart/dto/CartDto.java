package com.eshoppingzone.cart.dto;

import com.eshoppingzone.cart.entity.Cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CartDto {
    private Long id;
    private Long customerId;
    private List<CartItemDto> items = new ArrayList<>();
    private Integer totalItems = 0;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CartDto() {
    }

    public CartDto(Long id, Long customerId, List<CartItemDto> items, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = items != null ? items : new ArrayList<>();
        this.totalItems = this.items.stream().mapToInt(CartItemDto::getQuantity).sum();
        this.totalAmount = this.items.stream()
                .map(CartItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CartDto fromEntity(Cart cart) {
        if (cart == null) return null;
        List<CartItemDto> itemDtos = cart.getItems() != null ?
                cart.getItems().stream().map(CartItemDto::fromEntity).collect(Collectors.toList()) :
                new ArrayList<>();
        return new CartDto(cart.getId(), cart.getCustomerId(), itemDtos, cart.getCreatedAt(), cart.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<CartItemDto> getItems() {
        return items;
    }

    public void setItems(List<CartItemDto> items) {
        this.items = items;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
