package com.omniorder.inventory.repository;

import com.omniorder.inventory.domain.ProcessedEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    Optional<ProcessedEvent> findById(String eventId);
    boolean existsById(String eventId);

    /**
     * Atomically record a processed event. Returns true if this is the first time
     * the eventId is recorded (i.e. the insert succeeded), false if it already existed.
     * Uses INSERT ... ON CONFLICT DO NOT NULL to avoid race conditions.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true,
        value = "INSERT INTO processed_events (event_id, aggregate_type, aggregate_id, event_type, processed_at) " +
                "VALUES (:eventId, :aggregateType, :aggregateId, :eventType, :processedAt) " +
                "ON CONFLICT (event_id) DO NOTHING")
    int tryRecordProcessed(
        @Param("eventId") String eventId,
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") String aggregateId,
        @Param("eventType") String eventType,
        @Param("processedAt") java.time.Instant processedAt);
}
