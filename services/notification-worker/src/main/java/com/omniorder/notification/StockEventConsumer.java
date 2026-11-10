package com.omniorder.notification;

import com.omniorder.contracts.event.StockReservedEvent;
import com.omniorder.contracts.event.StockRejectedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockEventConsumer {

    @KafkaListener(topics = "stock.reserved", groupId = "notification-worker")
    public void onStockReserved(StockReservedEvent event) {
        System.out.println("[NOTIFICATION] Stock reserved for order " + event.getOrderId()
            + " product " + event.getProductId() + " qty " + event.getQuantity()
            + " at " + event.getOccurredAt());
    }

    @KafkaListener(topics = "stock.rejected", groupId = "notification-worker")
    public void onStockRejected(StockRejectedEvent event) {
        System.out.println("[NOTIFICATION] Stock rejected for order " + event.getOrderId()
            + " product " + event.getProductId() + " qty " + event.getQuantity()
            + " reason: " + event.getReason() + " at " + event.getOccurredAt());
    }
}
