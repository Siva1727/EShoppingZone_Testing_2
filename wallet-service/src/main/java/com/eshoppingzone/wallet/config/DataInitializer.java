package com.eshoppingzone.wallet.config;

import com.eshoppingzone.wallet.entity.TransactionType;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.entity.WalletTransaction;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public DataInitializer(WalletRepository walletRepository, WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) {
        seedWallet(1L, new BigDecimal("100000.00"), "Platform Reserve Wallet");
        seedWallet(2L, new BigDecimal("5000.00"), "Merchant Wallet");
        seedWallet(3L, new BigDecimal("500.00"), "Delivery Agent Wallet");
        seedWallet(4L, new BigDecimal("1000.00"), "Customer Starter Wallet");
    }

    private void seedWallet(Long userId, BigDecimal balance, String description) {
        if (!walletRepository.existsByUserId(userId)) {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(balance);
            Wallet saved = walletRepository.save(wallet);

            WalletTransaction txn = new WalletTransaction();
            txn.setWalletId(saved.getId());
            txn.setType(TransactionType.TOP_UP);
            txn.setAmount(balance);
            txn.setReference("INITIAL_SEED");
            txn.setDescription(description);
            transactionRepository.save(txn);

            log.info("Seeded initial wallet for userId: {} with balance: {}", userId, balance);
        }
    }
}
