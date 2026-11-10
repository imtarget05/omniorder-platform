-- V2__additional_indexes.sql
-- Additional indexes for performance optimization based on query workload analysis.

-- Reservation expiration job: scan HELD reservations past expiry
-- Already covered by idx_reservation_expires(expires_at) WHERE status = 'HELD'

-- Compensation: find reservation by order ID
-- Already covered by idx_reservation_order(order_id)

-- Product lookup by SKU for gRPC GetProduct (hot path)
-- PK on id covers id-based lookup, SKU has UNIQUE constraint
-- Composite index for sku + available_stock covers "check stock" queries
CREATE INDEX IF NOT EXISTS idx_products_sku_available ON products(sku, available_stock);

-- Processed events: deduplication check by event_id (PK already covers this)
