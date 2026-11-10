package com.omniorder.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.omniorder.order.controller.OrderController;
import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderStatus;
import com.omniorder.order.repository.OrderOutboxRepository;
import com.omniorder.order.repository.OrderRepository;
import com.omniorder.inventory.domain.Product;
import com.omniorder.inventory.domain.ReservationStatus;
import com.omniorder.inventory.domain.StockReservation;
import com.omniorder.inventory.kafka.OrderRequestedConsumer;
import com.omniorder.inventory.kafka.StockActionConsumer;
import com.omniorder.inventory.repository.ProductRepository;
import com.omniorder.inventory.repository.StockReservationRepository;
import com.omniorder.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = com.omniorder.order.OrderApplication.class)
@ActiveProfiles("testcontainers")
@Testcontainers
@Import(FlashSaleE2ETest.InventoryTestConfig.class)
class FlashSaleE2ETest {

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
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "order-service-test");
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "com.omniorder.*");
        registry.add("app.outbox.poll-delay-ms", () -> "100");
        registry.add("app.inventory.outbox.poll-delay-ms", () -> "100");
        registry.add("app.reconciliation.poll-delay-ms", () -> "600000");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @MockBean
    private com.omniorder.order.grpc.InventoryGrpcClient inventoryGrpcClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final int STOCK = 5;
    private static final BigDecimal UNIT_PRICE = BigDecimal.TEN;

    private UUID productId;

    @BeforeEach
    void setUp() {
        when(inventoryGrpcClient.getProductPrice(any())).thenReturn(UNIT_PRICE);

        Jwt testJwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("userId", USER_ID.toString())
            .claim("roles", List.of("CUSTOMER"))
            .build();
        when(jwtDecoder.decode(anyString())).thenReturn(testJwt);

        Product product = new Product("SKU-FLASH-SALE", "Flash Sale Product", UNIT_PRICE, STOCK);
        product = productRepository.save(product);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void flashSale_1000Requests_5Stock_Max5Confirmed_NoOversell() throws Exception {
        int threadCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(128);
        HttpHeaders baseHeaders = new HttpHeaders();
        baseHeaders.setBearerAuth("test-token");
        baseHeaders.setContentType(MediaType.APPLICATION_JSON);

        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, threadCount)
            .mapToObj(i -> (Callable<Void>) () -> {
                OrderController.CreateOrderRequest req = new OrderController.CreateOrderRequest();
                req.setProductId(productId);
                req.setQuantity(1);
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(baseHeaders);
                headers.set("Idempotency-Key", "flash-sale-key-" + i);
                restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/v1/orders",
                    new HttpEntity<>(req, headers), String.class);
                return null;
            })
            .collect(Collectors.toList());

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(orderRepository.count()).isEqualTo(threadCount);

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        boolean settled = false;
        while (System.currentTimeMillis() < deadline) {
            long pending = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            if (pending == 0) {
                settled = true;
                break;
            }
            Thread.sleep(500);
        }
        assertThat(settled).as("All orders should leave PENDING after inventory flow").isTrue();

        List<Order> orders = orderRepository.findAll();
        long confirmed = orders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        long rejected = orders.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED).count();

        assertThat(confirmed).isLessThanOrEqualTo((long) STOCK);
        assertThat(confirmed + rejected).isEqualTo((long) threadCount);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getAvailableStock()).isGreaterThanOrEqualTo(0);

        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                assertThat(order.getTotalPrice()).isEqualByComparingTo(UNIT_PRICE);
                StockReservation res = reservationRepository.findByOrderId(order.getId()).orElseThrow(
                    () -> new AssertionError("Confirmed order " + order.getId() + " has no reservation"));
                assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONSUMED);
            } else {
                assertThat(reservationRepository.findByOrderId(order.getId())).isEmpty();
            }
        }
    }

    @Configuration
    @Import({
        InventoryService.class,
        OrderRequestedConsumer.class,
        StockActionConsumer.class
    })
    @EnableJpaRepositories(basePackages = {
        "com.omniorder.order.repository",
        "com.omniorder.inventory.repository"
    })
    @EntityScan(basePackages = {
        "com.omniorder.order.domain",
        "com.omniorder.inventory.domain"
    })
    static class InventoryTestConfig {
    }
}

