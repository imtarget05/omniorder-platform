package com.omniorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderStatus;
import com.omniorder.order.repository.OrderRepository;
import java.math.BigDecimal;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderServiceIdempotencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.4")
        .withDatabaseName("omniorder")
        .withUsername("omniorder")
        .withPassword("omniorder");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
    }

    @Test
    void sameIdempotencyKey_concurrentRequests_shouldCreateOnlyOneOrder() throws Exception {
        String key = "key-idempotency-test-1";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, threadCount)
            .mapToObj(i -> (Callable<Void>) () -> {
                try {
                    orderService.createOrder(USER_ID, PRODUCT_ID, 1, BigDecimal.TEN, key);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    duplicateCount.incrementAndGet();
                }
                return null;
            })
            .collect(Collectors.toList());

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        futures.forEach(f -> {
            try { f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        });

        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(1);
        assertThat(successCount.get() + duplicateCount.get()).isEqualTo(threadCount);

        Order order = orderRepository.findAll().get(0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    void sameKeyDifferentPayload_shouldReject() {
        String key = "key-idempotency-test-2";
        orderService.createOrder(USER_ID, PRODUCT_ID, 1, BigDecimal.TEN, key);

        assertThatThrownBy(() ->
            orderService.createOrder(USER_ID, PRODUCT_ID, 5, BigDecimal.valueOf(50), key))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Idempotency-Key reused with different payload");

        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(1);
    }
}
