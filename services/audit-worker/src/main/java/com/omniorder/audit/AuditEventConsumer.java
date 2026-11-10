package com.omniorder.audit;

import com.omniorder.audit.domain.AuditEvent;
import com.omniorder.audit.repository.AuditEventRepository;
import com.omniorder.contracts.event.OrderRequestedEvent;
import com.omniorder.contracts.event.StockReservedEvent;
import com.omniorder.contracts.event.StockRejectedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuditEventConsumer {

    private final AuditEventRepository auditEventRepository;

    public AuditEventConsumer(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @KafkaListener(topics = "order.requested", groupId = "audit-worker")
    public void onOrderRequested(OrderRequestedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", event.getOrderId());
        payload.put("userId", event.getUserId());
        payload.put("productId", event.getProductId());
        payload.put("quantity", event.getQuantity());
        payload.put("idempotencyKey", event.getIdempotencyKey());

        AuditEvent auditEvent = new AuditEvent(
            event.getEventId(), "Order", event.getOrderId().toString(),
            "ORDER_REQUESTED", payload, event.getOccurredAt());
        auditEventRepository.save(auditEvent);
    }

    @KafkaListener(topics = "stock.reserved", groupId = "audit-worker")
    public void onStockReserved(StockReservedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", event.getOrderId());
        payload.put("productId", event.getProductId());
        payload.put("quantity", event.getQuantity());

        AuditEvent auditEvent = new AuditEvent(
            event.getEventId(), "Order", event.getOrderId().toString(),
            "STOCK_RESERVED", payload, event.getOccurredAt());
        auditEventRepository.save(auditEvent);
    }

    @KafkaListener(topics = "stock.rejected", groupId = "audit-worker")
    public void onStockRejected(StockRejectedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", event.getOrderId());
        payload.put("productId", event.getProductId());
        payload.put("quantity", event.getQuantity());
        payload.put("reason", event.getReason());

        AuditEvent auditEvent = new AuditEvent(
            event.getEventId(), "Order", event.getOrderId().toString(),
            "STOCK_REJECTED", payload, event.getOccurredAt());
        auditEventRepository.save(auditEvent);
    }
}
