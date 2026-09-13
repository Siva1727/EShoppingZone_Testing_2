package com.eshoppingzone.product.service;

import com.eshoppingzone.product.dto.CreateProductRequest;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.dto.UpdateProductRequest;
import com.eshoppingzone.product.entity.Product;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.exception.ResourceNotFoundException;
import com.eshoppingzone.product.exception.UnauthorizedException;
import com.eshoppingzone.product.repository.CategoryRepository;
import com.eshoppingzone.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product(1L, 2L, "Test Laptop", "Fast laptop", "Electronics", new BigDecimal("999.99"), "http://image.jpg", ProductStatus.ACTIVE);
    }

    @Test
    void testGetActiveProducts() {
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(sampleProduct));

        List<ProductDto> products = productService.getActiveProducts(null, null, null, null);

        assertEquals(1, products.size());
        assertEquals("Test Laptop", products.get(0).getName());
    }

    @Test
    void testGetProductByIdSuccess() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductDto dto = productService.getProductById(1L);

        assertNotNull(dto);
        assertEquals("Test Laptop", dto.getName());
    }

    @Test
    void testCreateProductSetsPendingApproval() {
        CreateProductRequest request = new CreateProductRequest("New Product", "Desc", "Electronics", new BigDecimal("100.00"), "url");
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductDto dto = productService.createProduct(2L, request);

        assertNotNull(dto);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void testUpdateProductUnauthorizedMerchantThrowsException() {
        UpdateProductRequest request = new UpdateProductRequest("Updated", "Desc", "Electronics", new BigDecimal("120.00"), "url");
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(UnauthorizedException.class, () -> productService.updateProduct(1L, 999L, request));
    }

    @Test
    void testApproveProductChangesStatusToActive() {
        Product pendingProduct = new Product(2L, 2L, "Pending Item", "Desc", "Books", new BigDecimal("15.00"), "url", ProductStatus.PENDING_APPROVAL);
        when(productRepository.findById(2L)).thenReturn(Optional.of(pendingProduct));
        when(productRepository.save(any(Product.class))).thenReturn(pendingProduct);

        ProductDto dto = productService.approveProduct(2L);

        assertEquals(ProductStatus.ACTIVE, pendingProduct.getStatus());
        verify(productRepository, times(1)).save(pendingProduct);
    }
}
