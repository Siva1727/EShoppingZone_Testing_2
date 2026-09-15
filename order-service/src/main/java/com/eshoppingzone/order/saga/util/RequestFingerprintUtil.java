package com.eshoppingzone.order.saga.util;

import com.eshoppingzone.order.dto.CartItemDto;
import com.eshoppingzone.order.dto.CheckoutRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public final class RequestFingerprintUtil {

    private RequestFingerprintUtil() {
    }

    public static String computeFingerprint(Long customerId, List<CartItemDto> cartItems, CheckoutRequest request) {
        String itemsSummary = "";
        if (cartItems != null) {
            itemsSummary = cartItems.stream()
                    .sorted(Comparator.comparing(CartItemDto::getProductId))
                    .map(item -> item.getProductId() + ":" + item.getQuantity())
                    .collect(Collectors.joining(","));
        }

        String raw = "customerId=" + (customerId != null ? customerId : "")
                + "|items=" + itemsSummary
                + "|paymentMethod=" + (request != null && request.getPaymentMethod() != null ? request.getPaymentMethod().name() : "")
                + "|addressId=" + (request != null && request.getAddressId() != null ? request.getAddressId() : "")
                + "|street=" + (request != null && request.getShippingStreet() != null ? request.getShippingStreet().trim() : "")
                + "|city=" + (request != null && request.getShippingCity() != null ? request.getShippingCity().trim() : "")
                + "|state=" + (request != null && request.getShippingState() != null ? request.getShippingState().trim() : "")
                + "|postalCode=" + (request != null && request.getShippingPostalCode() != null ? request.getShippingPostalCode().trim() : "")
                + "|country=" + (request != null && request.getShippingCountry() != null ? request.getShippingCountry().trim() : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
