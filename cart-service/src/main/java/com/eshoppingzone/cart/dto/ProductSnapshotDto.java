package com.eshoppingzone.cart.dto;

import java.math.BigDecimal;

public class ProductSnapshotDto {
    private Long id;
    private Long merchantId;
    private String name;
    private BigDecimal price;
    private String status;

    public ProductSnapshotDto() {
    }

    public ProductSnapshotDto(Long id, Long merchantId, String name, BigDecimal price, String status) {
        this.id = id;
        this.merchantId = merchantId;
        this.name = name;
        this.price = price;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
