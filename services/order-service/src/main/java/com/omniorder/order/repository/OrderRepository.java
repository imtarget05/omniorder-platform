package com.omniorder.order.repository;

import com.omniorder.order.domain.Order;
import com.omniorder.order.domain.OrderOutbox;
import com.omniorder.order.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.idempotencyKey = :idempotencyKey")
    Optional<Order> findByUserIdAndIdempotencyKey(@Param("userId") UUID userId,
                                                   @Param("idempotencyKey") String idempotencyKey);

    List<Order> findByUserId(UUID userId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, java.time.Instant before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :nextStatus, o.version = o.version + 1 "
        + "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int transitionStatus(@Param("orderId") UUID orderId,
                         @Param("expectedStatus") OrderStatus expectedStatus,
                         @Param("nextStatus") OrderStatus nextStatus);
}
