package com.omniorder.audit.repository;

import com.omniorder.audit.domain.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
    List<AuditEvent> findByAggregateId(String aggregateId);
    List<AuditEvent> findByEventType(String eventType);
}
