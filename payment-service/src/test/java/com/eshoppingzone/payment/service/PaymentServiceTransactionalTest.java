package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.client.OrderClient;
import com.eshoppingzone.payment.client.WalletClient;
import com.eshoppingzone.payment.dto.ApiResponse;
import com.eshoppingzone.payment.dto.PaymentDto;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.entity.Payment;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.exception.PaymentException;
import com.eshoppingzone.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentServiceTransactionalTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:paymentdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @MockBean
    private WalletClient walletClient;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void processPayment_WhenWalletDebitFails_PersistsFailedPaymentInDatabaseThroughTransactionalProxy() {
        Long orderId = 24L;
        Long customerId = 51L;
        BigDecimal amount = new BigDecimal("899.99");
        ProcessPaymentRequest request = new ProcessPaymentRequest(orderId, customerId, amount, PaymentMethod.WALLET);

        when(walletClient.debit(any())).thenThrow(new RuntimeException("Insufficient wallet balance"));

        PaymentException exception = assertThrows(PaymentException.class, () -> paymentService.processPayment(request));
        assertNotNull(exception);

        Optional<Payment> savedPaymentOpt = paymentRepository.findByOrderId(orderId);
        assertTrue(savedPaymentOpt.isPresent(), "Payment record MUST be committed to the database even though PaymentException was thrown");

        Payment savedPayment = savedPaymentOpt.get();
        assertEquals(orderId, savedPayment.getOrderId());
        assertEquals(customerId, savedPayment.getCustomerId());
        assertEquals(0, amount.compareTo(savedPayment.getAmount()));
        assertEquals(PaymentMethod.WALLET, savedPayment.getPaymentMethod());
        assertEquals(PaymentStatus.FAILED, savedPayment.getStatus());
        assertNotNull(savedPayment.getTransactionReference());

        PaymentDto retrievedPayment = paymentService.getPaymentByOrderId(orderId);
        assertNotNull(retrievedPayment);
        assertEquals(orderId, retrievedPayment.getOrderId());
        assertEquals(PaymentStatus.FAILED, retrievedPayment.getStatus());
    }

    @Test
    void processPayment_WhenWalletDebitSucceeds_PersistsSuccessPaymentInDatabase() {
        Long orderId = 25L;
        Long customerId = 51L;
        BigDecimal amount = new BigDecimal("100.00");
        ProcessPaymentRequest request = new ProcessPaymentRequest(orderId, customerId, amount, PaymentMethod.WALLET);

        when(walletClient.debit(any())).thenReturn(ApiResponse.success("Debited", null));
        when(walletClient.credit(any())).thenReturn(ApiResponse.success("Credited", null));

        PaymentDto result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());

        Optional<Payment> savedPaymentOpt = paymentRepository.findByOrderId(orderId);
        assertTrue(savedPaymentOpt.isPresent());
        assertEquals(PaymentStatus.SUCCESS, savedPaymentOpt.get().getStatus());
    }
}
