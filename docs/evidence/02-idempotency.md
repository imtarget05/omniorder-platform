# Idempotency Test Evidence: Duplicate Request Prevention

## Test Scenario
10 concurrent requests with the same `Idempotency-Key` header attempt to create the same order.

## Test Method
- **Service**: Order Service (`createOrder` with idempotency key check)
- **Database**: PostgreSQL 15.4 (Testcontainers)
- **Concurrency**: 10 threads, same idempotency key, same payload
- **Database Constraint**: Unique index on `(user_id, idempotency_key)`

## Idempotency Logic
1. Compute SHA-256 hash of `(idempotency_key + user_id + product_id + quantity + total_price)`
2. Check if an order with this `(user_id, idempotency_key)` already exists
3. If exists, verify hash matches → return existing order (no duplicate)
4. If hash differs → reject (different payload with same key)
5. If not exists, create order + outbox row in single transaction
6. Catch `DataIntegrityViolationException` on unique constraint → return existing order

## Results
| Metric | Value |
|--------|-------|
| Concurrent requests | 10 |
| Orders created | 1 (exactly one) |
| Successful returns | 1 |
| Duplicate rejections | 9 |
| Total accounted | 10 |

## Additional Test: Same Key, Different Payload
- First request: quantity=1, total=10.00 → order created
- Second request: quantity=5, total=50.00, same key → rejected with "Idempotency-Key reused with different payload"
- Order count remains 1 ✓

## Test File
`services/order-service/src/test/java/com/omniorder/order/service/OrderServiceIdempotencyTest.java`
