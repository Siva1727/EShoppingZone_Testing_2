package com.eshoppingzone.recommendation.repository;

import com.eshoppingzone.recommendation.entity.CustomerProductView;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerProductViewRepository extends JpaRepository<CustomerProductView, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO customer_product_view
                (customer_id, product_id, view_count, recommended,
                 first_viewed_at, last_viewed_at, created_at, updated_at)
            VALUES (:customerId, :productId, 1, false,
                    :viewedAt, :viewedAt, :viewedAt, :viewedAt)
            ON DUPLICATE KEY UPDATE
                recommended = (view_count + 1 >= 3),
                view_count = view_count + 1,
                last_viewed_at = :viewedAt,
                updated_at = :viewedAt
            """, nativeQuery = true)
    int recordView(@Param("customerId") Long customerId,
                   @Param("productId") Long productId,
                   @Param("viewedAt") LocalDateTime viewedAt);

    Optional<CustomerProductView> findByCustomerIdAndProductId(Long customerId, Long productId);

    List<CustomerProductView> findByCustomerIdAndRecommendedTrueOrderByUpdatedAtDesc(Long customerId);
}
