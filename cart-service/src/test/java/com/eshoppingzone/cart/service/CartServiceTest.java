package com.eshoppingzone.cart.service;

import com.eshoppingzone.cart.client.ProductClient;
import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.dto.ProductSnapshotDto;
import com.eshoppingzone.cart.entity.Cart;
import com.eshoppingzone.cart.entity.CartItem;
import com.eshoppingzone.cart.exception.ResourceNotFoundException;
import com.eshoppingzone.cart.repository.CartItemRepository;
import com.eshoppingzone.cart.repository.CartRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartServiceImpl cartService;

    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        sampleCart = new Cart(1L, 100L);
        sampleCart.setItems(new ArrayList<>());
    }

    @Test
    void testGetMyCartReturnsExistingOrCreates() {
        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));

        CartDto cartDto = cartService.getMyCart(100L);

        assertNotNull(cartDto);
        assertEquals(100L, cartDto.getCustomerId());
        assertEquals(0, cartDto.getTotalItems());
    }

    @Test
    void testAddToCartNewItem() {
        AddToCartRequest request = new AddToCartRequest(1L, 2);
        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Smartphone", new BigDecimal("500.00"), "ACTIVE");

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        when(cartItemRepository.findByCartAndProductId(sampleCart, 1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart);

        CartDto result = cartService.addToCart(100L, request);

        assertNotNull(result);
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
        verify(cartRepository, times(1)).save(sampleCart);
    }

    @Test
    void testAddToCartDuplicateItemIncreasesQuantity() {
        AddToCartRequest request = new AddToCartRequest(1L, 3);
        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(1L, 2L, "Smartphone", new BigDecimal("500.00"), "ACTIVE");
        CartItem existingItem = new CartItem(10L, sampleCart, 1L, "Smartphone", new BigDecimal("500.00"), 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(productSnapshot));
        when(cartItemRepository.findByCartAndProductId(sampleCart, 1L)).thenReturn(Optional.of(existingItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart);

        CartDto result = cartService.addToCart(100L, request);

        assertNotNull(result);
        assertEquals(5, existingItem.getQuantity());
        verify(cartItemRepository, times(1)).save(existingItem);
    }

    @Test
    void testAddToCartProductNotFoundThrowsResourceNotFoundException() {
        AddToCartRequest request = new AddToCartRequest(99999L, 1);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        Request feignRequest = Request.create(Request.HttpMethod.GET, "/api/v1/products/99999/internal", Collections.emptyMap(), null, null, new RequestTemplate());
        when(productClient.getProductById(99999L)).thenThrow(new FeignException.NotFound("Product not found", feignRequest, null, null));

        assertThrows(ResourceNotFoundException.class, () -> cartService.addToCart(100L, request));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testAddToCartInactiveProductThrowsResourceNotFoundException() {
        AddToCartRequest request = new AddToCartRequest(1L, 2);
        ProductSnapshotDto inactiveProduct = new ProductSnapshotDto(1L, 2L, "Smartphone", new BigDecimal("500.00"), "PENDING_APPROVAL");

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(productClient.getProductById(1L)).thenReturn(ApiResponse.success(inactiveProduct));

        assertThrows(ResourceNotFoundException.class, () -> cartService.addToCart(100L, request));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testAddToCartProductClientFailureThrowsRuntimeException() {
        AddToCartRequest request = new AddToCartRequest(1L, 2);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(productClient.getProductById(1L)).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> cartService.addToCart(100L, request));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testUpdateCartItemQuantity() {
        CartItem existingItem = new CartItem(10L, sampleCart, 1L, "Smartphone", new BigDecimal("500.00"), 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByIdAndCart(10L, sampleCart)).thenReturn(Optional.of(existingItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart);

        CartDto result = cartService.updateCartItemQuantity(100L, 10L, 5);

        assertNotNull(result);
        assertEquals(5, existingItem.getQuantity());
        verify(cartItemRepository, times(1)).save(existingItem);
    }

    @Test
    void testUpdateCartItemNotFoundThrowsResourceNotFoundException() {
        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByIdAndCart(99L, sampleCart)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.updateCartItemQuantity(100L, 99L, 5));
    }

    @Test
    void testRemoveCartItem() {
        CartItem existingItem = new CartItem(10L, sampleCart, 1L, "Smartphone", new BigDecimal("500.00"), 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByIdAndCart(10L, sampleCart)).thenReturn(Optional.of(existingItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(sampleCart);

        CartDto result = cartService.removeCartItem(100L, 10L);

        assertNotNull(result);
        verify(cartItemRepository, times(1)).delete(existingItem);
        assertTrue(sampleCart.getItems().isEmpty());
    }

    @Test
    void testClearCart() {
        CartItem item = new CartItem(1L, sampleCart, 1L, "Item", new BigDecimal("10.00"), 1);
        sampleCart.getItems().add(item);

        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));

        cartService.clearCart(100L);

        assertTrue(sampleCart.getItems().isEmpty());
        verify(cartRepository, times(1)).save(sampleCart);
    }

    @Test
    void testGetCartByCustomerId() {
        when(cartRepository.findByCustomerId(100L)).thenReturn(Optional.of(sampleCart));

        CartDto result = cartService.getCartByCustomerId(100L);

        assertNotNull(result);
        assertEquals(100L, result.getCustomerId());
    }

    @Test
    void testGetCartByCustomerIdNotFoundThrowsException() {
        when(cartRepository.findByCustomerId(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.getCartByCustomerId(999L));
    }
}
