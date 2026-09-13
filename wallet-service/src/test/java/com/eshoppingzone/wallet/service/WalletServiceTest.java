package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransactionDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.TransactionType;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.entity.WalletTransaction;
import com.eshoppingzone.wallet.exception.InsufficientBalanceException;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet sampleWallet;

    @BeforeEach
    void setUp() {
        sampleWallet = new Wallet(1L, 100L, new BigDecimal("500.00"));
    }

    @Test
    void testGetWalletByUserId() {
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(sampleWallet));

        WalletDto dto = walletService.getWalletByUserId(100L);

        assertNotNull(dto);
        assertEquals(new BigDecimal("500.00"), dto.getBalance());
    }

    @Test
    void testTopUpSuccess() {
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(sampleWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(sampleWallet);

        WalletDto dto = walletService.topUp(100L, new BigDecimal("100.00"));

        assertNotNull(dto);
        assertEquals(new BigDecimal("600.00"), sampleWallet.getBalance());
        verify(transactionRepository, times(1)).save(any(WalletTransaction.class));
    }

    @Test
    void testDebitSuccess() {
        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("200.00"), "TXN-1", "Purchase");
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(sampleWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(sampleWallet);

        WalletDto dto = walletService.debit(request);

        assertNotNull(dto);
        assertEquals(new BigDecimal("300.00"), sampleWallet.getBalance());
        verify(transactionRepository, times(1)).save(any(WalletTransaction.class));
    }

    @Test
    void testDebitInsufficientBalanceThrowsException() {
        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("1000.00"), "TXN-1", "Large Purchase");
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(sampleWallet));

        assertThrows(InsufficientBalanceException.class, () -> walletService.debit(request));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void testCreditSuccess() {
        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("50.00"), "REF-1", "Refund");
        when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(sampleWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(sampleWallet);

        WalletDto dto = walletService.credit(request);

        assertNotNull(dto);
        assertEquals(new BigDecimal("550.00"), sampleWallet.getBalance());
    }
}
