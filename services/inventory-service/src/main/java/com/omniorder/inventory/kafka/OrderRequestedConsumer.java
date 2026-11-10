package com.omniorder.inventory.kafka;

import com.omniorder.inventory.service.InventoryService;
import com.omniorder.contracts.event.OrderRequestedEvent;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderRequestedConsumer {

    private static final String TOPIC_ORDER_REQUESTED = "order.requested";

    private final InventoryService inventoryService;

    public OrderRequestedConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = TOPIC_ORDER_REQUESTED, groupId = "inventory-service")
    @Transactional
    public void onOrderRequested(OrderRequestedEvent event) {
        String eventId = event.getEventId();
        UUID orderId = event.getOrderId();

        // Dedup handled atomically inside reserveStock via INSERT ON CONFLICT
        InventoryService.StockReservationResult result = inventoryService.reserveStock(
            event.getProductId(), orderId, event.getQuantity(), eventId);

        if (result.duplicate()) {
            // Already processed, skip idempotently
            return;
        }

        // reserveStock already published stock.reserved or stock.rejected via InventoryOutbox
    }
}
