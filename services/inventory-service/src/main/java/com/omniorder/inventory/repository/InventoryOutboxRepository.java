package com.omniorder.inventory.repository;

import com.omniorder.inventory.domain.InventoryOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public interface InventoryOutboxRepository extends JpaRepository<InventoryOutbox, UUID> {

    @Query("SELECT o FROM InventoryOutbox o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<InventoryOutbox> findPending();

    @Modifying
    @Query("UPDATE InventoryOutbox o SET o.status = 'PUBLISHED', o.publishedAt = :publishedAt WHERE o.id = :id")
    @Transactional
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("UPDATE InventoryOutbox o SET o.retryCount = o.retryCount + 1 WHERE o.id = :id")
    @Transactional
    void incrementRetry(@Param("id") UUID id);
}
