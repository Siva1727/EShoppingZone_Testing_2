package com.eshoppingzone.inventory.config;

import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final InventoryRepository inventoryRepository;

    public DataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        seedStock(1L, 50);
        seedStock(2L, 75);
        seedStock(3L, 100);
        seedStock(4L, 40);
    }

    private void seedStock(Long productId, int quantity) {
        if (!inventoryRepository.existsByProductId(productId)) {
            Inventory inv = new Inventory();
            inv.setProductId(productId);
            inv.setAvailableStock(quantity);
            inv.setReservedStock(0);
            inventoryRepository.save(inv);
            log.info("Seeded initial stock for product ID {}: {} units", productId, quantity);
        }
    }
}
