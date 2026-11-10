package com.omniorder.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderOutbox;
import com.omniorder.order.domain.OrderStatus;
import com.omniorder.contracts.event.OrderRequestedEvent;
import com.omniorder.contracts.event.StockConsumedEvent;
import com.omniorder.contracts.event.StockReleasedEvent;
import com.omniorder.contracts.event.StockRejectedEvent;
import com.omniorder.contracts.event.StockReservedEvent;
import com.omniorder.order.repository.OrderRepository;
import com.omniorder.order.repository.OrderOutboxRepository;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.ExponentialBackOff;

@Service
public class OrderService {

    private static final String TOPIC_ORDER_REQUESTED = "order.requested";
    private static final String TOPIC_STOCK_CONSUME = "stock.consume";
    private static final String TOPIC_STOCK_RELEASE = "stock.release";
    private static final int MAX_RETRY = 5;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2000L);

    public OrderService(OrderRepository orderRepository,
                        OrderOutboxRepository outboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createOrder(UUID userId, UUID productId, Integer quantity,
                             BigDecimal totalPrice, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        String idempotencyHash = computeIdempotencyHash(idempotencyKey, userId, productId, quantity, totalPrice);

        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            Order existingOrder = existing.get();
            if (existingOrder.getIdempotencyHash() == null || !idempotencyHash.equals(existingOrder.getIdempotencyHash())) {
                throw new IllegalStateException(
                    "Idempotency-Key reused with different payload. Existing order: " + existingOrder.getId());
            }
            return existingOrder;
        }

        Order order = new Order(userId, productId, quantity, totalPrice, idempotencyKey, idempotencyHash);
        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            Optional<Order> existingOrder = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existingOrder.isPresent()) {
                return existingOrder.get();
            }
            throw e;
        }

        OrderRequestedEvent event = new OrderRequestedEvent(
            UUID.randomUUID().toString(), order.getId(), userId, productId,
            quantity, idempotencyKey, Instant.now());

        OrderOutbox outbox = new OrderOutbox(
            order.getId(), "Order", "ORDER_REQUESTED", toJson(event));
        outboxRepository.save(outbox);

        return order;
    }

    @Transactional
    public Order confirmOrder(UUID orderId) {
        int transitioned = orderRepository.transitionStatus(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        if (transitioned == 0) {
            Order current = orderRepository.findById(orderId).orElseThrow();
            if (current.getStatus() == OrderStatus.CONFIRMED) {
                return current;
            }
            throw new IllegalStateException("Order is not in PENDING state: " + current.getStatus());
        }
        Order order = orderRepository.findById(orderId).orElseThrow();

        // Publish stock.consume to permanently deduct reserved stock
        StockConsumedEvent event = new StockConsumedEvent(
            UUID.randomUUID().toString(), orderId, order.getProductId(),
            order.getQuantity(), Instant.now());
        OrderOutbox outbox = new OrderOutbox(
            orderId, "Order", "STOCK_CONSUME", toJson(event));
        outboxRepository.save(outbox);

        return order;
    }

    @Transactional
    public Order rejectOrder(UUID orderId, String reason) {
        int transitioned = orderRepository.transitionStatus(orderId, OrderStatus.PENDING, OrderStatus.REJECTED);
        if (transitioned == 0) {
            Order current = orderRepository.findById(orderId).orElseThrow();
            if (current.getStatus() == OrderStatus.REJECTED) {
                return current;
            }
            throw new IllegalStateException("Order is not in PENDING state: " + current.getStatus());
        }
        Order order = orderRepository.findById(orderId).orElseThrow();

        // No stock.release here: rejection means stock was never reserved (insufficient stock),
        // so there is nothing to compensate.
        return order;
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order; // Idempotent: already cancelled
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Order cannot be cancelled from state: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        // Publish stock.release to return reserved stock (compensation)
        StockReleasedEvent event = new StockReleasedEvent(
            UUID.randomUUID().toString(), orderId, order.getProductId(),
            order.getQuantity(), Instant.now());
        OrderOutbox outbox = new OrderOutbox(
            orderId, "Order", "STOCK_RELEASE", toJson(event));
        outboxRepository.save(outbox);

        return order;
    }

    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
    }

    public java.util.List<Order> listOrdersByUser(UUID userId) {
        return orderRepository.findByUserId(userId);
    }

    public java.util.List<Order> listAllOrders() {
        return orderRepository.findAll();
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishOutbox() {
        List<OrderOutbox> pending = outboxRepository.findPending();
        for (OrderOutbox outbox : pending) {
            try {
                String topic = switch (outbox.getEventType()) {
                    case "ORDER_REQUESTED" -> TOPIC_ORDER_REQUESTED;
                    case "STOCK_CONSUME" -> TOPIC_STOCK_CONSUME;
                    case "STOCK_RELEASE" -> TOPIC_STOCK_RELEASE;
                    default -> throw new IllegalStateException("Unknown event type: " + outbox.getEventType());
                };
                SendResult<String, Object> result = kafkaTemplate.send(
                    topic, outbox.getAggregateId().toString(), deserializeEvent(outbox))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outboxRepository.markPublished(outbox.getId(), Instant.now());
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                outboxRepository.incrementRetry(outbox.getId());
                int retryCount = outbox.getRetryCount() + 1;
                if (retryCount >= MAX_RETRY) {
                    moveToDeadLetterQueue(outbox, e.getMessage());
                }
            }
        }
    }

    private void moveToDeadLetterQueue(OrderOutbox outbox, String error) {
        outbox.setStatus("DLQ");
        outboxRepository.save(outbox);
        System.err.println("Outbox event " + outbox.getId() + " moved to DLQ after " + MAX_RETRY + " retries. Last error: " + error);
    }

    private String computeIdempotencyHash(String idempotencyKey, UUID userId, UUID productId,
                                           Integer quantity, BigDecimal totalPrice) {
        String payload = idempotencyKey + ":" + userId + ":" + productId + ":" + quantity + ":" + totalPrice;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Object deserializeEvent(OrderOutbox outbox) {
        Class<?> eventType = switch (outbox.getEventType()) {
            case "ORDER_REQUESTED" -> OrderRequestedEvent.class;
            case "STOCK_CONSUME" -> StockConsumedEvent.class;
            case "STOCK_RELEASE" -> StockReleasedEvent.class;
            default -> throw new IllegalStateException("Unknown event type: " + outbox.getEventType());
        };
        try {
            return objectMapper.readValue(outbox.getPayload(), eventType);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid outbox payload for " + outbox.getEventType(), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
