package com.omniorder.order.controller;

import com.omniorder.order.domain.Order;
import com.omniorder.order.service.OrderService;
import com.omniorder.order.grpc.InventoryGrpcClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final InventoryGrpcClient inventoryGrpcClient;

    public OrderController(OrderService orderService, InventoryGrpcClient inventoryGrpcClient) {
        this.orderService = orderService;
        this.inventoryGrpcClient = inventoryGrpcClient;
    }

    public static class CreateOrderRequest {
        @NotNull private UUID productId;
        @NotNull @Positive private Integer quantity;
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody CreateOrderRequest request) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));

        // Lấy giá sản phẩm từ Inventory Service qua gRPC (không tin cậy giá từ client)
        BigDecimal unitPrice = inventoryGrpcClient.getProductPrice(request.getProductId());
        if (unitPrice == null) {
            throw new IllegalStateException("Product not found: " + request.getProductId());
        }
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = orderService.createOrder(
            userId, request.getProductId(), request.getQuantity(), totalPrice, idempotencyKey);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID orderId,
                                          @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        boolean isAdmin = jwt.getClaimAsStringList("roles").contains("ADMIN");
        Order order = orderService.getOrder(orderId);
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: order belongs to another user");
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Order> confirmOrder(@PathVariable UUID orderId,
                                              @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        boolean isAdmin = jwt.getClaimAsStringList("roles").contains("ADMIN");
        Order order = orderService.getOrder(orderId);
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: order belongs to another user");
        }
        Order confirmed = orderService.confirmOrder(orderId);
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable UUID orderId,
                                             @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        boolean isAdmin = jwt.getClaimAsStringList("roles").contains("ADMIN");
        Order order = orderService.getOrder(orderId);
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: order belongs to another user");
        }
        Order cancelled = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping
    public ResponseEntity<List<Order>> listOrders(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        boolean isAdmin = jwt.getClaimAsStringList("roles").contains("ADMIN");
        List<Order> orders;
        if (isAdmin) {
            orders = orderService.listAllOrders();
        } else {
            orders = orderService.listOrdersByUser(userId);
        }
        return ResponseEntity.ok(orders);
    }
}
