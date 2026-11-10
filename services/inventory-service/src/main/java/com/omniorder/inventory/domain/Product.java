package com.omniorder.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(nullable = false, length = 100, unique = true)
    private String sku;

    @NotNull
    @Column(nullable = false, length = 255)
    private String name;

    @NotNull
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @NotNull
    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(nullable = false)
    @Version
    private Long version = 1L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @LastModifiedDate
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, insertable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Instant updatedAt;

    protected Product() {}

    public Product(String sku, String name, BigDecimal price, Integer availableStock) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.availableStock = availableStock;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Integer getAvailableStock() { return availableStock; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
    public void setVersion(Long version) { this.version = version; }
}
