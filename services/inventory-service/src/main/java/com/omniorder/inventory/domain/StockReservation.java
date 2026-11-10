package com.omniorder.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "stock_reservations")
@EntityListeners(AuditingEntityListener.class)
public class StockReservation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "product_id", columnDefinition = "UUID", nullable = false)
    private UUID productId;

    @NotNull
    @Column(name = "order_id", columnDefinition = "UUID", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.HELD;

    @Column(name = "reserved_at", nullable = false, insertable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant reservedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(nullable = false)
    @Version
    private Long version = 1L;

    protected StockReservation() {}

    public StockReservation(UUID productId, UUID orderId, Integer quantity) {
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getOrderId() { return orderId; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getReservedAt() { return reservedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Long getVersion() { return version; }

    public void setStatus(ReservationStatus status) { this.status = status; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public void setVersion(Long version) { this.version = version; }
}
