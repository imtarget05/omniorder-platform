package com.omniorder.order.kafka;

import com.omniorder.contracts.event.StockRejectedEvent;
import com.omniorder.contracts.event.StockReservedEvent;
import com.omniorder.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventConsumer {

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "stock.reserved", groupId = "order-service")
    @Transactional
    public void onStockReserved(@Payload StockReservedEvent event) {
        orderService.confirmOrder(event.getOrderId());
    }

    @KafkaListener(topics = "stock.rejected", groupId = "order-service")
    @Transactional
    public void onStockRejected(@Payload StockRejectedEvent event) {
        orderService.rejectOrder(event.getOrderId(), event.getReason());
    }
}
