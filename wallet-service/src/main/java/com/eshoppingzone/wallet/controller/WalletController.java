package com.eshoppingzone.wallet.controller;

import com.eshoppingzone.wallet.dto.*;
import com.eshoppingzone.wallet.security.UserPrincipal;
import com.eshoppingzone.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet Management", description = "APIs for internal customer and platform wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Get My Wallet", description = "Retrieve wallet details and current balance")
    public ResponseEntity<ApiResponse<WalletDto>> getMyWallet(Authentication authentication) {
        Long userId = getUserId(authentication);
        WalletDto wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }

    @GetMapping("/balance")
    @Operation(summary = "Get Balance", description = "Check current available balance in user wallet")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(Authentication authentication) {
        Long userId = getUserId(authentication);
        BigDecimal balance = walletService.getBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("Balance retrieved successfully", balance));
    }

    @PostMapping("/topup")
    @Operation(summary = "Top Up Wallet", description = "Simulate adding funds to user wallet")
    public ResponseEntity<ApiResponse<WalletDto>> topUp(Authentication authentication,
                                                        @Valid @RequestBody TopUpRequest request) {
        Long userId = getUserId(authentication);
        WalletDto wallet = walletService.topUp(userId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success("Wallet topped up successfully", wallet));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get Wallet Transactions", description = "View credit/debit transaction statement")
    public ResponseEntity<ApiResponse<List<WalletTransactionDto>>> getTransactions(Authentication authentication) {
        Long userId = getUserId(authentication);
        List<WalletTransactionDto> txns = walletService.getTransactions(userId);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", txns));
    }

    @PostMapping("/debit")
    @Operation(summary = "Debit Wallet (Internal)", description = "Internal endpoint for payment processing")
    public ResponseEntity<ApiResponse<WalletDto>> debit(@Valid @RequestBody WalletTransferRequest request) {
        WalletDto wallet = walletService.debit(request);
        return ResponseEntity.ok(ApiResponse.success("Wallet debited successfully", wallet));
    }

    @PostMapping("/credit")
    @Operation(summary = "Credit Wallet (Internal)", description = "Internal endpoint for refund or revenue credit")
    public ResponseEntity<ApiResponse<WalletDto>> credit(@Valid @RequestBody WalletTransferRequest request) {
        WalletDto wallet = walletService.credit(request);
        return ResponseEntity.ok(ApiResponse.success("Wallet credited successfully", wallet));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get Wallet by User ID (Internal)", description = "Internal endpoint to get user wallet")
    public ResponseEntity<ApiResponse<WalletDto>> getWalletByUserId(@PathVariable Long userId) {
        WalletDto wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }
}
