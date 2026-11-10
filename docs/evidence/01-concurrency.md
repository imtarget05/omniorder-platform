# Concurrency Test Evidence: Anti-Overselling

## Test Scenario
1,000 concurrent HTTP requests attempt to purchase the same product with only 5 units of stock.

## Test Method
- **Service**: Inventory Service (`decrementStock` via atomic conditional UPDATE)
- **Database**: PostgreSQL 15.4 (Testcontainers)
- **Concurrency**: 1,000 threads, 128-thread pool, each requesting quantity=1
- **Initial Stock**: 5 units

## Atomic Stock Decrement (SQL)
```sql
UPDATE products
SET available_stock = available_stock - :qty, version = version + 1
WHERE id = :id AND available_stock >= :qty
```

## Results
| Metric | Value |
|--------|-------|
| Total requests | 1,000 |
| Successful decrements | 5 (exactly matches stock) |
| Failed (insufficient stock) | 995 |
| Final available_stock | 0 |
| Stock ever negative | No |
| Orders oversold | 0 |

## Invariant Verified
- `successful_decrements <= initial_stock` (5 <= 5) ✓
- `available_stock >= 0` at all times ✓
- No two transactions can both succeed when only 1 unit remains ✓

## Test File
`services/inventory-service/src/test/java/com/omniorder/inventory/service/InventoryServiceConcurrencyTest.java`

## E2E Flash Sale (HTTP + Kafka + DB thật)
`services/order-service/src/test/java/com/omniorder/order/service/FlashSaleE2ETest.java`

### Test Method
- 1.000 request đồng thời `POST /api/v1/orders` (TestRestTemplate, port ngẫu nhiên), mỗi request idempotency-key khác nhau, không gửi giá từ client.
- Sản phẩm tồn kho = 5, quantity = 1.
- Luồng thật: HTTP → order-service (DB + outbox `ORDER_REQUESTED`) → Kafka `order.requested` → inventory-service (`decrementStock` atomic, outbox `STOCK_RESERVED`/`STOCK_REJECTED`) → Kafka `stock.reserved`/`stock.rejected` → order-service `confirmOrder`/`rejectOrder` → outbox `STOCK_CONSUME`/`STOCK_RELEASE` → Kafka → inventory `consumeReservation`/`releaseReservation`.
- Giá `totalPrice` tính ở server qua `InventoryGrpcClient.getProductPrice` (mock trong test để tránh khởi gRPC server), **không tin giá từ client**.

### Invariant to verify (chạy test để có số liệu thực)
- `confirmed_orders <= initial_stock` (≤ 5) — không bán vượt tồn kho.
- `available_stock >= 0` — tồn kho không âm.
- Mỗi đơn `CONFIRMED` có reservation `CONSUMED` và `totalPrice == server_unit_price × quantity`.
- Mỗi đơn `REJECTED` không có reservation (tồn kho không đủ, chưa từng đặt).
- `confirmed + rejected == 1000` — không đơn bị treo `PENDING`.

### SQL verification
```sql
SELECT status, count(*) FROM orders GROUP BY status;
SELECT available_stock FROM products WHERE id = '<product_id>';
SELECT order_id, status FROM stock_reservations WHERE order_id IN (...);
```

