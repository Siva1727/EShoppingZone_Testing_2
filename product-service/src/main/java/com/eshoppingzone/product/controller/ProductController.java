package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ApiResponse;
import com.eshoppingzone.product.dto.CategoryDto;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Browsing", description = "Public / Customer APIs for browsing products and categories")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
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
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id) {
        ProductDto product = productService.getProductById(id);
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
