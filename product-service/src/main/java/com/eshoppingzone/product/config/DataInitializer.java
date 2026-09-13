package com.eshoppingzone.product.config;

import com.eshoppingzone.product.entity.Category;
import com.eshoppingzone.product.entity.Product;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.repository.CategoryRepository;
import com.eshoppingzone.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        seedCategories();
        seedProducts();
    }

    private void seedCategories() {
        seedCategory("Electronics", "Smartphones, laptops, headphones, accessories");
        seedCategory("Fashion", "Clothing, shoes, watches, accessories");
        seedCategory("Books", "Technical books, fiction, literature");
        seedCategory("Home & Kitchen", "Appliances, cookware, home decor");
    }

    private void seedCategory(String name, String description) {
        if (!categoryRepository.existsByName(name)) {
            Category category = new Category();
            category.setName(name);
            category.setDescription(description);
            category.setActive(true);
            categoryRepository.save(category);
            log.info("Seeded category: {}", name);
        }
    }

    private void seedProducts() {
        if (productRepository.count() == 0) {
            Product p1 = new Product();
            p1.setMerchantId(2L);
            p1.setName("Ultra Smartphone 5G");
            p1.setDescription("Latest flagship 5G smartphone with 120Hz AMOLED display and triple lens camera");
            p1.setCategory("Electronics");
            p1.setPrice(new BigDecimal("899.99"));
            p1.setImageUrl("https://example.com/images/phone5g.jpg");
            p1.setStatus(ProductStatus.ACTIVE);
            productRepository.save(p1);

            Product p2 = new Product();
            p2.setMerchantId(2L);
            p2.setName("Wireless Noise-Cancelling Headphones");
            p2.setDescription("Premium over-ear headphones with active noise cancellation and 40h battery");
            p2.setCategory("Electronics");
            p2.setPrice(new BigDecimal("199.99"));
            p2.setImageUrl("https://example.com/images/headphones.jpg");
            p2.setStatus(ProductStatus.ACTIVE);
            productRepository.save(p2);

            Product p3 = new Product();
            p3.setMerchantId(2L);
            p3.setName("Classic Cotton T-Shirt");
            p3.setDescription("100% breathable organic cotton comfortable crew-neck t-shirt");
            p3.setCategory("Fashion");
            p3.setPrice(new BigDecimal("24.99"));
            p3.setImageUrl("https://example.com/images/tshirt.jpg");
            p3.setStatus(ProductStatus.ACTIVE);
            productRepository.save(p3);

            Product p4 = new Product();
            p4.setMerchantId(2L);
            p4.setName("Spring Microservices in Action");
            p4.setDescription("Comprehensive guide to building production-ready microservices with Spring Boot");
            p4.setCategory("Books");
            p4.setPrice(new BigDecimal("44.99"));
            p4.setImageUrl("https://example.com/images/springbook.jpg");
            p4.setStatus(ProductStatus.ACTIVE);
            productRepository.save(p4);

            log.info("Seeded initial active products for merchant 2");
        }
    }
}
