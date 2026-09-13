package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransactionDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.TransactionType;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.entity.WalletTransaction;
import com.eshoppingzone.wallet.exception.InsufficientBalanceException;
import com.eshoppingzone.wallet.exception.ResourceNotFoundException;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public WalletServiceImpl(WalletRepository walletRepository, WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    private Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wallet w = new Wallet();
                    w.setUserId(userId);
                    w.setBalance(BigDecimal.ZERO);
                    return walletRepository.save(w);
                });
    }

    @Override
    @Transactional
    public WalletDto getWalletByUserId(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return WalletDto.fromEntity(wallet);
    }

    @Override
    @Transactional
    public BigDecimal getBalance(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return wallet.getBalance();
    }

    @Override
    @Transactional
    public synchronized WalletDto topUp(Long userId, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet saved = walletRepository.save(wallet);

        recordTransaction(saved.getId(), TransactionType.TOP_UP, amount, "TOPUP-" + System.currentTimeMillis(), "Simulated wallet top-up");
        log.info("Top-up successful for userId: {}, amount: {}, new balance: {}", userId, amount, saved.getBalance());
        return WalletDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public synchronized WalletDto debit(WalletTransferRequest request) {
        Wallet wallet = getOrCreateWallet(request.getUserId());

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient wallet balance. Current balance: " + wallet.getBalance() + ", Requested: " + request.getAmount());
        }

        wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));
        Wallet saved = walletRepository.save(wallet);

        recordTransaction(saved.getId(), TransactionType.DEBIT, request.getAmount(), request.getReference(), request.getDescription());
        log.info("Wallet debited for userId: {}, amount: {}, new balance: {}", request.getUserId(), request.getAmount(), saved.getBalance());
        return WalletDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public synchronized WalletDto credit(WalletTransferRequest request) {
        Wallet wallet = getOrCreateWallet(request.getUserId());
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        Wallet saved = walletRepository.save(wallet);

        TransactionType type = (request.getDescription() != null && request.getDescription().toLowerCase().contains("refund")) ?
                TransactionType.REFUND : TransactionType.CREDIT;

        recordTransaction(saved.getId(), type, request.getAmount(), request.getReference(), request.getDescription());
        log.info("Wallet credited for userId: {}, amount: {}, new balance: {}", request.getUserId(), request.getAmount(), saved.getBalance());
        return WalletDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletTransactionDto> getTransactions(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for userId: " + userId));

        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(WalletTransactionDto::fromEntity)
                .collect(Collectors.toList());
    }

    private void recordTransaction(Long walletId, TransactionType type, BigDecimal amount, String reference, String description) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWalletId(walletId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setReference(reference);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }
}
