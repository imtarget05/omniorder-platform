# Database Optimization Evidence: Indexes & Execution Plans

## Database Schema (Orders Table)
```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_price NUMERIC(18,2) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    idempotency_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_order_idempotency ON orders(user_id, idempotency_key);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status_created ON orders(status, created_at);
CREATE INDEX idx_orders_product_id ON orders(product_id);
```

## Key Query Patterns & Indexes

### 1. Idempotency Check (per request)
```sql
SELECT * FROM orders
WHERE user_id = ? AND idempotency_key = ?
```
**Index**: `uk_order_idempotency` (unique composite)
**Expected Plan**: Index Scan using `uk_order_idempotency`

### 2. Order Lookup by ID
```sql
SELECT * FROM orders WHERE id = ?
```
**Index**: Primary key (UUID)
**Expected Plan**: Index Scan using `orders_pkey`

### 3. User's Order History (pagination)
```sql
SELECT * FROM orders
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 0
```
**Index**: `idx_orders_user_id` + `idx_orders_status_created`
**Expected Plan**: Index Scan → Sort → Limit

### 4. Outbox Polling (scheduled)
```sql
SELECT * FROM order_outbox
WHERE status = 'PENDING'
ORDER BY created_at ASC
```
**Index**: `idx_outbox_status_created`
**Expected Plan**: Index Scan using `idx_outbox_status_created`

## Execution Plan Verification
Run against Oracle XE (local Docker):
```sql
EXPLAIN PLAN FOR
SELECT * FROM orders WHERE user_id = :user_id AND idempotency_key = :key;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
```

Expected: `INDEX UNIQUE SCAN` on `UK_ORDER_IDEMPOTENCY` — O(1) lookup, no full table scan.

## Stock Reservation Atomicity
```sql
UPDATE products
SET available_stock = available_stock - :qty, version = version + 1
WHERE id = :id AND available_stock >= :qty
```
**Index**: `idx_products_available_stock` + Primary Key
**Expected Plan**: Index Scan with filter predicate — row-level lock ensures atomicity under concurrency.
