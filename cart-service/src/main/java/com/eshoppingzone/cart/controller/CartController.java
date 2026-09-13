package com.eshoppingzone.cart.controller;

import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.dto.UpdateCartItemRequest;
import com.eshoppingzone.cart.security.UserPrincipal;
import com.eshoppingzone.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart Management", description = "APIs for managing customer shopping carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private Long getCustomerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Get My Cart", description = "Retrieve the current customer's shopping cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CartDto>> getMyCart(Authentication authentication) {
        Long customerId = getCustomerId(authentication);
        CartDto cart = cartService.getMyCart(customerId);
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    @PostMapping("/items")
    @Operation(summary = "Add Item to Cart", description = "Add a product with quantity to the customer cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CartDto>> addToCart(Authentication authentication,
                                                          @Valid @RequestBody AddToCartRequest request) {
        Long customerId = getCustomerId(authentication);
        CartDto cart = cartService.addToCart(customerId, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update Cart Item Quantity", description = "Modify the quantity of an item in the cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CartDto>> updateCartItemQuantity(Authentication authentication,
                                                                       @PathVariable Long itemId,
                                                                       @Valid @RequestBody UpdateCartItemRequest request) {
        Long customerId = getCustomerId(authentication);
        CartDto cart = cartService.updateCartItemQuantity(customerId, itemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Cart item quantity updated", cart));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove Item from Cart", description = "Delete an item from the cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CartDto>> removeCartItem(Authentication authentication,
                                                               @PathVariable Long itemId) {
        Long customerId = getCustomerId(authentication);
        CartDto cart = cartService.removeCartItem(customerId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    @DeleteMapping
    @Operation(summary = "Clear Cart", description = "Empty all items from customer cart")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> clearCart(Authentication authentication) {
        Long customerId = getCustomerId(authentication);
        cartService.clearCart(customerId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", null));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get Customer Cart (Internal)", description = "Internal endpoint for Order Service checkout")
    public ResponseEntity<ApiResponse<CartDto>> getCartByCustomerId(@PathVariable Long customerId) {
        CartDto cart = cartService.getCartByCustomerId(customerId);
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    @DeleteMapping("/customer/{customerId}/clear")
    @Operation(summary = "Clear Customer Cart (Internal)", description = "Internal endpoint to empty cart after order checkout")
    public ResponseEntity<ApiResponse<Void>> clearCustomerCart(@PathVariable Long customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", null));
    }
}
