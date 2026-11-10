package com.omniorder.inventory.repository;

import com.omniorder.inventory.domain.ReservationStatus;
import com.omniorder.inventory.domain.StockReservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    Optional<StockReservation> findByOrderId(UUID orderId);

    List<StockReservation> findByStatusAndReservedAtBefore(ReservationStatus status, Instant reservedAtBefore);
}
