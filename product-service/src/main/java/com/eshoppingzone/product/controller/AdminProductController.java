package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ApiResponse;
import com.eshoppingzone.product.dto.CategoryDto;
import com.eshoppingzone.product.dto.CategoryRequest;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin Product & Category Management", description = "Admin APIs for product approval and category management")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/pending")
    @Operation(summary = "Get Pending Products", description = "List all products waiting for Admin approval")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getPendingProducts() {
        List<ProductDto> products = productService.getPendingProducts();
        return ResponseEntity.ok(ApiResponse.success("Pending products retrieved successfully", products));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve Product", description = "Approve a pending product making it publicly active")
    public ResponseEntity<ApiResponse<ProductDto>> approveProduct(@PathVariable Long id) {
        ProductDto product = productService.approveProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product approved successfully", product));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject Product", description = "Reject a product submitted by a merchant")
    public ResponseEntity<ApiResponse<ProductDto>> rejectProduct(@PathVariable Long id) {
        ProductDto product = productService.rejectProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product rejected", product));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate Product", description = "Deactivate any active product")
    public ResponseEntity<ApiResponse<ProductDto>> deactivateProduct(@PathVariable Long id) {
        ProductDto product = productService.deactivateProductByAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated", product));
    }

    @GetMapping
    @Operation(summary = "Get All Products", description = "List all products across all statuses")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts() {
        List<ProductDto> products = productService.getAllProductsAdmin();
        return ResponseEntity.ok(ApiResponse.success("All products retrieved successfully", products));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create Category", description = "Create a new product category")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryDto category = productService.createCategory(request);
        return new ResponseEntity<>(ApiResponse.success("Category created successfully", category), HttpStatus.CREATED);
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update Category", description = "Update category name, description, or active status")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(@PathVariable Long id,
                                                                   @Valid @RequestBody CategoryRequest request) {
        CategoryDto category = productService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get All Categories", description = "List all categories including inactive ones")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getAllCategories() {
        List<CategoryDto> categories = productService.getAllCategoriesAdmin();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }
}
