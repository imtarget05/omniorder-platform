package com.omniorder.contracts.event;

import java.time.Instant;
import java.util.UUID;

public class StockRejectedEvent {

    private String eventId;
    private UUID orderId;
    private UUID productId;
    private Integer quantity;
    private String reason;
    private Instant occurredAt;

    public StockRejectedEvent() {}

    public StockRejectedEvent(String eventId, UUID orderId, UUID productId, Integer quantity,
                              String reason, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
