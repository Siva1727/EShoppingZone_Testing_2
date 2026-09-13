package com.eshoppingzone.product.repository;

import com.eshoppingzone.product.entity.Product;
import com.eshoppingzone.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByMerchantId(Long merchantId);
    Optional<Product> findByIdAndMerchantId(Long id, Long merchantId);
    List<Product> findByCategoryAndStatus(String category, ProductStatus status);
    List<Product> findByNameContainingIgnoreCaseAndStatus(String keyword, ProductStatus status);
}
