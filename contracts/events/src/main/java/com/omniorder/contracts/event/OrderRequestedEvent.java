package com.omniorder.contracts.event;

import java.time.Instant;
import java.util.UUID;

public class OrderRequestedEvent {

    private String eventId;
    private UUID orderId;
    private UUID userId;
    private UUID productId;
    private Integer quantity;
    private String idempotencyKey;
    private Instant occurredAt;

    public OrderRequestedEvent() {}

    public OrderRequestedEvent(String eventId, UUID orderId, UUID userId, UUID productId,
                               Integer quantity, String idempotencyKey, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
