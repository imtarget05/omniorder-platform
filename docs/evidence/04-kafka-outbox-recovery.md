# Kafka Failure Recovery Evidence: Transactional Outbox

## Problem
Database commit succeeds but application crashes before sending Kafka event → order exists but inventory never knows → stock inconsistency.

## Solution: Transactional Outbox Pattern
Order and outbox row are written in the **same Oracle transaction**. A scheduled poller reads unprocessed outbox rows and publishes to Kafka.

## Data Flow
```
1. POST /orders → BEGIN TRANSACTION
2. INSERT INTO orders (PENDING) 
3. INSERT INTO order_outbox (PENDING, event_type='ORDER_REQUESTED')
4. COMMIT TRANSACTION (atomic: both or neither)
5. Scheduled poller (every 1s) reads PENDING outbox rows
6. KafkaProducer.send("order.requested", payload)
7. UPDATE order_outbox SET status='PUBLISHED', published_at=now()
```

## Failure Scenarios

### Scenario 1: Crash after DB commit, before Kafka send
- Order + outbox row committed ✓
- Outbox remains `PENDING` (published_at is NULL)
- Next poller cycle picks it up and sends to Kafka ✓
- **Result**: Event eventually delivered, no data loss

### Scenario 2: Kafka temporarily unavailable
- Poller reads PENDING rows, attempts `kafkaTemplate.send()`
- Send fails → `incrementRetry()` on outbox row
- After `MAX_RETRY` (5) failures → moved to DLQ (status='DLQ')
- **Result**: No infinite retry loop, events quarantined for manual inspection

### Scenario 3: Duplicate Kafka delivery (at-least-once)
- Inventory service consumer checks `processed_events` table by `event_id`
- If already processed → skip (no double stock decrement)
- **Result**: Idempotent consumer, no double-decrement

### Scenario 4: Consumer crash mid-processing
- `@Transactional` on consumer method → rollback on exception
- `processed_events` INSERT uses `ON CONFLICT DO NOTHING` → idempotent
- **Result**: Safe to retry, no partial state

## Test Evidence
- Outbox row created in same transaction as order (`createOrder_createsOrderAndOutboxInSameTransaction`)
- Outbox status is `PENDING` after order creation (`createOrder_persistsOutboxPending_whenKafkaUnavailable`)

## Test Files
- `services/order-service/src/test/java/com/omniorder/order/service/OrderServiceOutboxTest.java`
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java` (`publishOutbox()` method)
