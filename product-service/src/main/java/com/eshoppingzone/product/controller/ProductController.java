package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ApiResponse;
import com.eshoppingzone.product.dto.CategoryDto;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.service.ProductService;
import com.eshoppingzone.product.security.UserPrincipal;
import com.eshoppingzone.product.messaging.ProductViewEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Browsing", description = "Public / Customer APIs for browsing products and categories")
public class ProductController {

    private final ProductService productService;
    private final ProductViewEventPublisher productViewEventPublisher;

    public ProductController(ProductService productService) {
        this(productService, null);
    }

    @Autowired
    public ProductController(ProductService productService, ProductViewEventPublisher productViewEventPublisher) {
        this.productService = productService;
        this.productViewEventPublisher = productViewEventPublisher;
    }

    @GetMapping
    @Operation(summary = "Browse Active Products", description = "Search and filter active approved products")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getActiveProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        List<ProductDto> products = productService.getActiveProducts(category, keyword, minPrice, maxPrice);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Product Details", description = "Retrieve product details by ID (Active products only)")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id,
                                                                  Authentication authentication) {
        ProductDto product = productService.getProductById(id);
        if (productViewEventPublisher != null
                && authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.getUserId() != null) {
            productViewEventPublisher.publish(principal.getUserId(), id);
        }
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get Active Categories", description = "List all active product categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getActiveCategories() {
        List<CategoryDto> categories = productService.getActiveCategories();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @GetMapping("/{id}/internal")
    @Operation(summary = "Get Product Internal", description = "Internal endpoint for other microservices (Order/Cart)")
    public ResponseEntity<ApiResponse<ProductDto>> getProductInternal(@PathVariable Long id) {
        ProductDto product = productService.getProductInternal(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }
}
