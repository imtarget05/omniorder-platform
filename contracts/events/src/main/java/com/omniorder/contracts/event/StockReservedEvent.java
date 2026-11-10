package com.omniorder.contracts.event;

import java.time.Instant;
import java.util.UUID;

public class StockReservedEvent {

    private String eventId;
    private UUID orderId;
    private UUID productId;
    private Integer quantity;
    private Instant occurredAt;

    public StockReservedEvent() {}

    public StockReservedEvent(String eventId, UUID orderId, UUID productId, Integer quantity,
                              Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
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
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
