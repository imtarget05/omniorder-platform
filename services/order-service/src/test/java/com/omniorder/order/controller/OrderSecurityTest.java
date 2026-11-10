package com.omniorder.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniorder.order.grpc.InventoryGrpcClient;
import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderStatus;
import com.omniorder.order.service.OrderService;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class OrderSecurityTest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private InventoryGrpcClient inventoryGrpcClient;

    private static final UUID USER_A_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ORDER_A_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private JwtAuthenticationToken authAsUserA() {
        Jwt jwt = Jwt.withTokenValue("token-user-a")
            .header("alg", "RS256")
            .claim("userId", USER_A_ID.toString())
            .claim("roles", List.of("CUSTOMER"))
            .build();
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        token.setAuthenticated(true);
        return token;
    }

    private JwtAuthenticationToken authAsAdmin() {
        Jwt jwt = Jwt.withTokenValue("token-admin")
            .header("alg", "RS256")
            .claim("userId", UUID.randomUUID().toString())
            .claim("roles", List.of("ADMIN"))
            .build();
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt,
            List.of((GrantedAuthority) () -> "ROLE_ADMIN"));
        token.setAuthenticated(true);
        return token;
    }

    @Test
    void userA_cannot_access_userB_order_returns403() throws Exception {
        Order orderB = mock(Order.class);
        when(orderB.getId()).thenReturn(ORDER_A_ID);
        when(orderB.getUserId()).thenReturn(USER_B_ID);
        when(orderB.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(orderService.getOrder(ORDER_A_ID)).thenReturn(orderB);

        mockMvc.perform(get("/api/v1/orders/" + ORDER_A_ID)
                .with(authentication(authAsUserA())))
            .andExpect(status().isForbidden());
    }

    @Test
    void userA_can_access_own_order_returns200() throws Exception {
        Order orderA = mock(Order.class);
        when(orderA.getId()).thenReturn(ORDER_A_ID);
        when(orderA.getUserId()).thenReturn(USER_A_ID);
        when(orderA.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(orderService.getOrder(ORDER_A_ID)).thenReturn(orderA);

        mockMvc.perform(get("/api/v1/orders/" + ORDER_A_ID)
                .with(authentication(authAsUserA())))
            .andExpect(status().isOk());
    }

    @Test
    void admin_can_access_any_order_returns200() throws Exception {
        Order orderB = mock(Order.class);
        when(orderB.getId()).thenReturn(ORDER_A_ID);
        when(orderB.getUserId()).thenReturn(USER_B_ID);
        when(orderB.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(orderService.getOrder(ORDER_A_ID)).thenReturn(orderB);

        mockMvc.perform(get("/api/v1/orders/" + ORDER_A_ID)
                .with(authentication(authAsAdmin())))
            .andExpect(status().isOk());
    }

    @Test
    void createOrder_creates_order_for_authenticated_user() throws Exception {
        Order createdOrder = mock(Order.class);
        when(createdOrder.getId()).thenReturn(ORDER_A_ID);
        when(createdOrder.getUserId()).thenReturn(USER_A_ID);
        when(createdOrder.getStatus()).thenReturn(OrderStatus.PENDING);
        when(orderService.createOrder(any(), any(), any(), any(), any())).thenReturn(createdOrder);
        when(inventoryGrpcClient.getProductPrice(any())).thenReturn(BigDecimal.TEN);

        OrderController.CreateOrderRequest req = new OrderController.CreateOrderRequest();
        req.setProductId(UUID.randomUUID());
        req.setQuantity(1);

        mockMvc.perform(post("/api/v1/orders")
                .with(authentication(authAsUserA()))
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        verify(orderService).createOrder(eq(USER_A_ID), any(), any(), any(), any());
    }
}
