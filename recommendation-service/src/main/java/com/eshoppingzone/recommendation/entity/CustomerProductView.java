package com.eshoppingzone.recommendation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_product_view",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_product_view",
                columnNames = {"customer_id", "product_id"}))
public class CustomerProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "first_viewed_at", nullable = false)
    private LocalDateTime firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerProductView() {
    }

    public CustomerProductView(Long customerId, Long productId, LocalDateTime viewedAt) {
        this.customerId = customerId;
        this.productId = productId;
        this.viewCount = 1;
        this.recommended = false;
        this.firstViewedAt = viewedAt;
        this.lastViewedAt = viewedAt;
        this.createdAt = viewedAt;
        this.updatedAt = viewedAt;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public long getViewCount() { return viewCount; }
    public boolean isRecommended() { return recommended; }
    public LocalDateTime getFirstViewedAt() { return firstViewedAt; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
