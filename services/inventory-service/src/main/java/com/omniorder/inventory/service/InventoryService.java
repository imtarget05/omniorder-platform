package com.omniorder.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniorder.contracts.event.StockConsumedEvent;
import com.omniorder.contracts.event.StockReleasedEvent;
import com.omniorder.contracts.event.StockRejectedEvent;
import com.omniorder.contracts.event.StockReservedEvent;
import com.omniorder.inventory.domain.InventoryOutbox;
import com.omniorder.inventory.domain.Product;
import com.omniorder.inventory.domain.ReservationStatus;
import com.omniorder.inventory.domain.StockReservation;
import com.omniorder.inventory.repository.InventoryOutboxRepository;
import com.omniorder.inventory.repository.ProductRepository;
import com.omniorder.inventory.repository.ProcessedEventRepository;
import com.omniorder.inventory.repository.StockReservationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final String TOPIC_STOCK_RESERVED = "stock.reserved";
    private static final String TOPIC_STOCK_REJECTED = "stock.rejected";
    private static final String TOPIC_STOCK_CONSUME = "stock.consume";
    private static final String TOPIC_STOCK_RELEASE = "stock.release";
    private static final String TOPIC_STOCK_CONSUMED = "stock.consumed";
    private static final String TOPIC_STOCK_RELEASED = "stock.released";
    private static final long RESERVATION_TTL_SECONDS = 300;
    private static final int MAX_RETRY = 5;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryService(ProductRepository productRepository,
                            StockReservationRepository reservationRepository,
                            ProcessedEventRepository processedEventRepository,
                            InventoryOutboxRepository outboxRepository,
                            KafkaTemplate<String, Object> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StockReservationResult reserveStock(UUID productId, UUID orderId, Integer quantity,
                                               String eventId) {
        int recorded = processedEventRepository.tryRecordProcessed(
            eventId, "Order", orderId.toString(), "ORDER_REQUESTED", Instant.now());
        if (recorded == 0) {
            return StockReservationResult.ofDuplicate();
        }

        int updated = productRepository.decrementStock(productId, quantity);
        if (updated == 0) {
            // Publish stock.rejected via outbox (no stock change)
            try {
                StockRejectedEvent event = new StockRejectedEvent(
                    UUID.randomUUID().toString(), orderId, productId, quantity,
                    "Insufficient stock", Instant.now());
                InventoryOutbox outbox = new InventoryOutbox(
                    orderId, "Order", "STOCK_REJECTED", toJson(event));
                outboxRepository.save(outbox);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to publish stock.rejected event", e);
            }
            return StockReservationResult.ofRejected("Insufficient stock");
        }

        StockReservation reservation = new StockReservation(productId, orderId, quantity);
        reservation.setExpiresAt(Instant.now().plusSeconds(RESERVATION_TTL_SECONDS));
        reservation = reservationRepository.save(reservation);

        // Publish stock.reserved via outbox
        try {
            StockReservedEvent event = new StockReservedEvent(
                UUID.randomUUID().toString(), orderId, productId, quantity, Instant.now());
            InventoryOutbox outbox = new InventoryOutbox(
                orderId, "Order", "STOCK_RESERVED", toJson(event));
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // If outbox save fails, rollback the stock decrement
            throw new IllegalStateException("Failed to publish stock.reserved event", e);
        }

        return StockReservationResult.ofReserved(reservation);
    }

    @Transactional
    public void consumeReservation(UUID orderId, String eventId) {
        int recorded = processedEventRepository.tryRecordProcessed(
            eventId, "Order", orderId.toString(), "STOCK_CONSUME", Instant.now());
        if (recorded == 0) {
            return; // Duplicate
        }

        StockReservation reservation = reservationRepository.findByOrderId(orderId)
            .orElseThrow(() -> new IllegalStateException("Reservation not found for order: " + orderId));
        if (reservation.getStatus() == ReservationStatus.CONSUMED) {
            return; // A repeated consume command is idempotent even with a new event id.
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new IllegalStateException("Reservation is not in HELD state: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.CONSUMED);
        reservation.setConsumedAt(Instant.now());
        reservationRepository.save(reservation);

        // Publish stock.consumed via outbox
        try {
            StockConsumedEvent event = new StockConsumedEvent(
                UUID.randomUUID().toString(), orderId, reservation.getProductId(),
                reservation.getQuantity(), Instant.now());
            InventoryOutbox outbox = new InventoryOutbox(
                orderId, "Order", "STOCK_CONSUMED", toJson(event));
            outboxRepository.save(outbox);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish stock.consumed event", e);
        }
    }

    @Transactional
    public void releaseReservation(UUID orderId, String eventId) {
        int recorded = processedEventRepository.tryRecordProcessed(
            eventId, "Order", orderId.toString(), "STOCK_RELEASE", Instant.now());
        if (recorded == 0) {
            return; // Duplicate
        }

        StockReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            // Nothing was reserved (e.g. order was rejected before reservation). Nothing to release.
            return;
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.CONSUMED) {
            return; // Already handled idempotently
        }
        productRepository.incrementStock(reservation.getProductId(), reservation.getQuantity());
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservationRepository.save(reservation);

        // Publish stock.released via outbox
        try {
            StockReleasedEvent event = new StockReleasedEvent(
                UUID.randomUUID().toString(), orderId, reservation.getProductId(),
                reservation.getQuantity(), Instant.now());
            InventoryOutbox outbox = new InventoryOutbox(
                orderId, "Order", "STOCK_RELEASED", toJson(event));
            outboxRepository.save(outbox);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish stock.released event", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.inventory.reservation-expiry-poll-ms:60000}")
    @Transactional
    public void expireReservations() {
        Instant now = Instant.now();
        List<StockReservation> expired = reservationRepository.findByStatusAndReservedAtBefore(
            ReservationStatus.HELD, now.minusSeconds(RESERVATION_TTL_SECONDS));
        for (StockReservation reservation : expired) {
            releaseReservation(reservation.getOrderId(), "expiry-" + reservation.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.inventory.outbox.poll-delay-ms:1000}")
    @Transactional
    public void publishOutbox() {
        List<InventoryOutbox> pending = outboxRepository.findPending();
        for (InventoryOutbox outbox : pending) {
            try {
                String topic = switch (outbox.getEventType()) {
                    case "STOCK_RESERVED" -> TOPIC_STOCK_RESERVED;
                    case "STOCK_REJECTED" -> TOPIC_STOCK_REJECTED;
            case "STOCK_CONSUMED" -> TOPIC_STOCK_CONSUMED;
            case "STOCK_RELEASED" -> TOPIC_STOCK_RELEASED;
                    default -> throw new IllegalStateException("Unknown event type: " + outbox.getEventType());
                };
                kafkaTemplate.send(topic, outbox.getAggregateId().toString(), deserializeEvent(outbox))
                    .get(SEND_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                outboxRepository.markPublished(outbox.getId(), Instant.now());
            } catch (Exception e) {
                outboxRepository.incrementRetry(outbox.getId());
                int retryCount = outbox.getRetryCount() + 1;
                if (retryCount >= MAX_RETRY) {
                    outboxRepository.save(outbox); // Will be handled by DLQ logic
                }
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    private Object deserializeEvent(InventoryOutbox outbox) {
        Class<?> eventType = switch (outbox.getEventType()) {
            case "STOCK_RESERVED" -> StockReservedEvent.class;
            case "STOCK_REJECTED" -> StockRejectedEvent.class;
            case "STOCK_CONSUMED" -> StockConsumedEvent.class;
            case "STOCK_RELEASED" -> StockReleasedEvent.class;
            default -> throw new IllegalStateException("Unknown event type: " + outbox.getEventType());
        };
        try {
            return objectMapper.readValue(outbox.getPayload(), eventType);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid outbox payload for " + outbox.getEventType(), e);
        }
    }

    public record StockReservationResult(
        boolean reserved,
        boolean rejected,
        boolean duplicate,
        String reason,
        StockReservation reservation
    ) {
        public static StockReservationResult ofReserved(StockReservation reservation) {
            return new StockReservationResult(true, false, false, null, reservation);
        }
        public static StockReservationResult ofRejected(String reason) {
            return new StockReservationResult(false, true, false, reason, null);
        }
        public static StockReservationResult ofDuplicate() {
            return new StockReservationResult(false, false, true, "Duplicate event", null);
        }
    }
}
