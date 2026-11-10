package com.omniorder.audit.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private String id;

    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Map<String, Object> payload;
    private Instant occurredAt;
    private Instant receivedAt;

    public AuditEvent() {}

    public AuditEvent(String eventId, String aggregateType, String aggregateId,
                      String eventType, Map<String, Object> payload, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.receivedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
