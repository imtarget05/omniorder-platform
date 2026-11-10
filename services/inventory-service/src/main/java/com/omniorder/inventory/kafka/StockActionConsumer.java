package com.omniorder.inventory.kafka;

import com.omniorder.contracts.event.StockConsumedEvent;
import com.omniorder.contracts.event.StockReleasedEvent;
import com.omniorder.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class StockActionConsumer {

    private static final String TOPIC_STOCK_CONSUME = "stock.consume";
    private static final String TOPIC_STOCK_RELEASE = "stock.release";

    private final InventoryService inventoryService;

    public StockActionConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = TOPIC_STOCK_CONSUME, groupId = "inventory-service")
    @Transactional
    public void onStockConsume(@Payload StockConsumedEvent event) {
        inventoryService.consumeReservation(event.getOrderId(), event.getEventId());
    }

    @KafkaListener(topics = TOPIC_STOCK_RELEASE, groupId = "inventory-service")
    @Transactional
    public void onStockRelease(@Payload StockReleasedEvent event) {
        inventoryService.releaseReservation(event.getOrderId(), event.getEventId());
    }
}
