package com.omniorder.inventory.repository;

import com.omniorder.inventory.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);

    @Modifying
    @Transactional
    @Query(nativeQuery = true,
        value = "UPDATE products SET available_stock = available_stock - :qty, version = version + 1 " +
                "WHERE id = :id AND available_stock >= :qty")
    int decrementStock(@Param("id") UUID id, @Param("qty") Integer qty);

    @Modifying
    @Transactional
    @Query(nativeQuery = true,
        value = "UPDATE products SET available_stock = available_stock + :qty, version = version + 1 " +
                "WHERE id = :id")
    void incrementStock(@Param("id") UUID id, @Param("qty") Integer qty);
}
