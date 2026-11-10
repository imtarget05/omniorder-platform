package com.omniorder.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.omniorder.inventory.domain.Product;
import com.omniorder.inventory.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class InventoryServiceConcurrencyTest {

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
    private InventoryService inventoryService;

    private UUID productId;

    @BeforeEach
    void seedProduct() {
        Product product = new Product("SKU-TEST-" + java.util.UUID.randomUUID(), "Test Product", BigDecimal.TEN, 5);
        product = productRepository.save(product);
        productId = product.getId();
    }

    @Test
    void concurrentStockDecrement_shouldNotOversell_when1000RequestsFor5Stock() throws Exception {
        int threadCount = 1000;
        int quantity = 1;
        ExecutorService executor = Executors.newFixedThreadPool(128);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, threadCount)
            .mapToObj(i -> (Callable<Integer>) () -> {
                int updated = productRepository.decrementStock(productId, quantity);
                if (updated > 0) {
                    successCount.incrementAndGet();
                    return 1;
                } else {
                    failureCount.incrementAndGet();
                    return 0;
                }
            })
            .collect(Collectors.toList());

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int totalSuccess = futures.stream()
            .mapToInt(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .sum();

        // Invariant: at most 5 successful decrements (stock=5), stock never negative
        assertThat(totalSuccess).isEqualTo(successCount.get());
        assertThat(totalSuccess).isLessThanOrEqualTo(5);
        assertThat(totalSuccess + failureCount.get()).isEqualTo(threadCount);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getAvailableStock()).isEqualTo(5 - totalSuccess);
        assertThat(product.getAvailableStock()).isGreaterThanOrEqualTo(0);
    }
}
