package com.omniorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.omniorder.order.domain.OrderOutbox;
import com.omniorder.order.repository.OrderOutboxRepository;
import com.omniorder.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
class OrderServiceOutboxTest {

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
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @org.junit.jupiter.api.BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_persistsOutboxPending_whenKafkaUnavailable() {
        orderService.createOrder(USER_ID, PRODUCT_ID, 1, BigDecimal.TEN, "key-outbox-test-1");

        List<OrderOutbox> pending = outboxRepository.findPending();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(pending.get(0).getEventType()).isEqualTo("ORDER_REQUESTED");
        assertThat(pending.get(0).getPublishedAt()).isNull();
    }

    @Test
    void createOrder_createsOrderAndOutboxInSameTransaction() {
        orderService.createOrder(USER_ID, PRODUCT_ID, 2, BigDecimal.valueOf(20), "key-outbox-test-2");

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
