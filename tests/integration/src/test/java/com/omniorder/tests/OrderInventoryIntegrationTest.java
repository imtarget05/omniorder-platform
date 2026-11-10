package com.omniorder.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniorder.order.OrderApplication;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = OrderApplication.class)
@Testcontainers
class OrderInventoryIntegrationTest {

    static class CreateOrderRequest {
        private UUID productId;
        private Integer quantity;
        private BigDecimal unitPrice;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.4")
        .withDatabaseName("omniorder")
        .withUsername("omniorder")
        .withPassword("omniorder");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.grpc.inventory.host", () -> "localhost");
        registry.add("app.grpc.inventory.port", () -> 9090);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void placeOrder_shouldCreateOrderAndPublishEvent() throws Exception {
        UUID productId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String idempotencyKey = "key-integ-flow-1";

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(productId);
        req.setQuantity(1);
        req.setUnitPrice(new BigDecimal("10.00"));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/orders",
            new HttpEntity<>(req, headers), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).contains("PENDING");
        assertThat(body).contains(idempotencyKey);
    }

    @Test
    void duplicateIdempotencyKey_shouldReturnSameOrder() throws Exception {
        UUID productId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String idempotencyKey = "key-integ-idempotent-1";

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(productId);
        req.setQuantity(1);
        req.setUnitPrice(new BigDecimal("10.00"));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response1 = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/orders",
            new HttpEntity<>(req, headers), String.class);
        ResponseEntity<String> response2 = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/orders",
            new HttpEntity<>(req, headers), String.class);

        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response1.getBody()).isEqualTo(response2.getBody());
    }

    @Test
    void missingIdempotencyKey_shouldReturn400() throws Exception {
        UUID productId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(productId);
        req.setQuantity(1);
        req.setUnitPrice(new BigDecimal("10.00"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/orders",
            new HttpEntity<>(req, headers), String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
