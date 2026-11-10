package com.omniorder.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
})
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "user_id", columnDefinition = "UUID", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "product_id", columnDefinition = "UUID", nullable = false)
    private UUID productId;

    @NotNull
    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PENDING;

    @NotNull
    @Column(name = "total_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "idempotency_hash", nullable = false, length = 64)
    private String idempotencyHash;

    @Column(name = "version", nullable = false)
    @Version
    private Long version = 1L;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @LastModifiedDate
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, insertable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant updatedAt;

    protected Order() {}

    public Order(UUID userId, UUID productId, Integer quantity, BigDecimal totalPrice,
                 String idempotencyKey, String idempotencyHash) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyHash = idempotencyHash;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getIdempotencyHash() { return idempotencyHash; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(OrderStatus status) { this.status = status; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setIdempotencyHash(String idempotencyHash) { this.idempotencyHash = idempotencyHash; }
    public void setVersion(Long version) { this.version = version; }
}
