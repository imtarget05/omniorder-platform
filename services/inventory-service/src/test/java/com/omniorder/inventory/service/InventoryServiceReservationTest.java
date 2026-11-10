package com.omniorder.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.omniorder.inventory.domain.Product;
import com.omniorder.inventory.domain.ReservationStatus;
import com.omniorder.inventory.domain.StockReservation;
import com.omniorder.inventory.repository.ProductRepository;
import com.omniorder.inventory.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Testcontainers
class InventoryServiceReservationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.4")
        .withDatabaseName("omniorder")
        .withUsername("omniorder")
        .withPassword("omniorder");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private InventoryService inventoryService;

    private UUID productId;
    private UUID orderId = UUID.randomUUID();

    @BeforeEach
    void seedProduct() {
        Product product = new Product("SKU-RESERVE-" + java.util.UUID.randomUUID(), "Reserve Product", BigDecimal.TEN, 10);
        product = productRepository.save(product);
        productId = product.getId();
    }

    @Test
    void reserveStock_thenRelease_returnsStockIdempotently() {
        // Reserve
        InventoryService.StockReservationResult result = inventoryService.reserveStock(
            productId, orderId, 3, "event-reserve-1");
        assertThat(result.reserved()).isTrue();

        // Verify stock decreased
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getAvailableStock()).isEqualTo(7);

        // Release
        String releaseEventId = "event-release-" + orderId;
        inventoryService.releaseReservation(orderId, releaseEventId);
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getAvailableStock()).isEqualTo(10);

        // Release again (idempotent)
        inventoryService.releaseReservation(orderId, releaseEventId);
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getAvailableStock()).isEqualTo(10); // No double increment

        StockReservation reservation = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void reserveStock_duplicateEvent_returnsDuplicate() {
        String eventId = "event-dup-1";
        InventoryService.StockReservationResult first = inventoryService.reserveStock(
            productId, orderId, 3, eventId);
        assertThat(first.reserved()).isTrue();

        InventoryService.StockReservationResult second = inventoryService.reserveStock(
            productId, orderId, 3, eventId);
        assertThat(second.duplicate()).isTrue();
    }
}
