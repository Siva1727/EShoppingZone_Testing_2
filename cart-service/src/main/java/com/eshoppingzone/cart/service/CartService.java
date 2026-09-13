package com.eshoppingzone.cart.service;

import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.CartDto;

public interface CartService {
    CartDto getMyCart(Long customerId);
    CartDto addToCart(Long customerId, AddToCartRequest request);
    CartDto updateCartItemQuantity(Long customerId, Long itemId, Integer quantity);
    CartDto removeCartItem(Long customerId, Long itemId);
    void clearCart(Long customerId);
    CartDto getCartByCustomerId(Long customerId);
}
