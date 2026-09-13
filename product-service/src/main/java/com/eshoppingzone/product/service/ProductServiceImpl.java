package com.eshoppingzone.product.service;

import com.eshoppingzone.product.dto.*;
import com.eshoppingzone.product.entity.Category;
import com.eshoppingzone.product.entity.Product;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.exception.DuplicateResourceException;
import com.eshoppingzone.product.exception.ResourceNotFoundException;
import com.eshoppingzone.product.exception.UnauthorizedException;
import com.eshoppingzone.product.repository.CategoryRepository;
import com.eshoppingzone.product.repository.ProductRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getActiveProducts(String category, String keyword, BigDecimal minPrice, BigDecimal maxPrice) {
        Specification<Product> spec = (root, query, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE);

        if (category != null && !category.trim().isEmpty()) {
            Specification<Product> catSpec = (root, query, cb) ->
                    cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
            spec = spec.and(catSpec);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            Specification<Product> kwSpec = (root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("description")), "%" + keyword.trim().toLowerCase() + "%")
                    );
            spec = spec.and(kwSpec);
        }

        if (minPrice != null) {
            Specification<Product> minSpec = (root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            spec = spec.and(minSpec);
        }

        if (maxPrice != null) {
            Specification<Product> maxSpec = (root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("price"), maxPrice);
            spec = spec.and(maxSpec);
        }

        return productRepository.findAll(spec).stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product is not currently available");
        }
        return ProductDto.fromEntity(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProductInternal(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductDto.fromEntity(product);
    }

    @Override
    @Transactional
    public ProductDto createProduct(Long merchantId, CreateProductRequest request) {
        Product product = new Product();
        product.setMerchantId(merchantId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setStatus(ProductStatus.PENDING_APPROVAL); // Requires Admin Approval

        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductDto updateProduct(Long id, Long merchantId, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getMerchantId().equals(merchantId)) {
            throw new UnauthorizedException("You can only update your own products");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setStatus(ProductStatus.PENDING_APPROVAL); // Re-submits for approval on public details modification

        Product updated = productRepository.save(product);
        return ProductDto.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getMerchantProducts(Long merchantId) {
        return productRepository.findByMerchantId(merchantId).stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getMerchantProductById(Long id, Long merchantId) {
        Product product = productRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id + " for merchant: " + merchantId));
        return ProductDto.fromEntity(product);
    }

    @Override
    @Transactional
    public void deactivateMerchantProduct(Long id, Long merchantId) {
        Product product = productRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id + " for merchant: " + merchantId));
        product.setStatus(ProductStatus.DEACTIVATED);
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getPendingProducts() {
        return productRepository.findByStatus(ProductStatus.PENDING_APPROVAL).stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductDto approveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setStatus(ProductStatus.ACTIVE);
        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductDto rejectProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setStatus(ProductStatus.REJECTED);
        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductDto deactivateProductByAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setStatus(ProductStatus.DEACTIVATED);
        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDto> getAllProductsAdmin() {
        return productRepository.findAll().stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CategoryDto createCategory(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Category already exists with name: " + request.getName());
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setActive(request.getActive() != null ? request.getActive() : true);

        Category saved = categoryRepository.save(category);
        return CategoryDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        Category saved = categoryRepository.save(category);
        return CategoryDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getActiveCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategoriesAdmin() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
    }
}
