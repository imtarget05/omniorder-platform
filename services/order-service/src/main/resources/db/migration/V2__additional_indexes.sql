-- V2__additional_indexes.sql
-- Additional indexes for performance optimization based on query workload analysis.

-- Order history queries: user fetches their orders with pagination, sorted by created_at DESC
-- Composite index supports: WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?
CREATE INDEX IF NOT EXISTS idx_orders_user_created_desc ON orders(user_id, created_at DESC);

-- Admin queries: fetch orders by status for dashboard/reporting
-- Already covered by idx_orders_status_created(status, created_at)

-- Outbox polling: relay scans pending events ordered by created_at ASC
-- Already covered by idx_outbox_status_created(status, created_at)

-- Reservation expiration: scan HELD reservations past expiry
-- Already covered by idx_reservation_expires(expires_at) WHERE status = 'HELD'

-- Product stock lookup: decrement/update by product ID (PK already covers this)
-- Available stock check for hot products
CREATE INDEX IF NOT EXISTS idx_products_sku_available ON products(sku, available_stock);

-- Reservation lookup by order for compensation/release
-- Already covered by idx_reservation_order(order_id)
