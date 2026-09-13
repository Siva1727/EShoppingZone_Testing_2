package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ApiResponse;
import com.eshoppingzone.product.dto.CreateProductRequest;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.dto.UpdateProductRequest;
import com.eshoppingzone.product.security.UserPrincipal;
import com.eshoppingzone.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/products")
@Tag(name = "Merchant Product Management", description = "Merchant APIs to manage own products")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantProductController {

    private final ProductService productService;

    public MerchantProductController(ProductService productService) {
        this.productService = productService;
    }

    private Long getMerchantId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @PostMapping
    @Operation(summary = "Create Product", description = "Create a product which will be submitted for Admin approval")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(Authentication authentication,
                                                                 @Valid @RequestBody CreateProductRequest request) {
        Long merchantId = getMerchantId(authentication);
        ProductDto product = productService.createProduct(merchantId, request);
        return new ResponseEntity<>(ApiResponse.success("Product created and submitted for approval", product), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Product", description = "Update own product details. Re-submits for admin approval.")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(Authentication authentication,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody UpdateProductRequest request) {
        Long merchantId = getMerchantId(authentication);
        ProductDto product = productService.updateProduct(id, merchantId, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated and submitted for approval", product));
    }

    @GetMapping
    @Operation(summary = "Get My Products", description = "List all products created by the authenticated merchant")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getMyProducts(Authentication authentication) {
        Long merchantId = getMerchantId(authentication);
        List<ProductDto> products = productService.getMerchantProducts(merchantId);
        return ResponseEntity.ok(ApiResponse.success("Merchant products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get My Product Details", description = "Get details of a specific product owned by the merchant")
    public ResponseEntity<ApiResponse<ProductDto>> getMyProductById(Authentication authentication,
                                                                    @PathVariable Long id) {
        Long merchantId = getMerchantId(authentication);
        ProductDto product = productService.getMerchantProductById(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate Product", description = "Deactivate a product owned by the merchant")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(Authentication authentication,
                                                               @PathVariable Long id) {
        Long merchantId = getMerchantId(authentication);
        productService.deactivateMerchantProduct(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated successfully", null));
    }
}
