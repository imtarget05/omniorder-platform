# ADR-001: Atomic conditional update in PostgreSQL instead of Redis distributed lock

## Status
Accepted

## Context
Cần ngăn overselling khi nhiều request cùng mua sản phẩm có tồn kho giới hạn. Cần đảm bảo `available_stock` không bao giờ âm.

## Options considered
1. **Redis distributed lock**: Lock key `product:{id}`, chỉ một thread xử lý mỗi lần.
   - Ưu điểm: Nhanh, không阻塞 database.
   - Nhược điểm: Redis không phải source of truth. Nếu Redis mất dữ liệu/crash, lock lost → overselling. Thêm độ phức tạp vận hành (cần cluster, persistence, monitoring).
2. **Optimistic locking (version column)**: `UPDATE ... WHERE version = :expectedVersion`.
   - Ưu điểm: Không khóa row dài.
   - Nhược điểm: Cần retry ở application layer, có livelock dưới tải cao, phức tạp code.
3. **Atomic conditional update (chosen)**: `UPDATE products SET available_stock = available_stock - :qty WHERE id = :id AND available_stock >= :qty`.
   - Ưu điểm: Database đảm bảo atomicity qua row-level lock. `CHECK (available_stock >= 0)` là lớp bảo vệ cuốiสุด. Đơn giản, đúng, không phụ thuộc dịch vụ bên thứ hai.
   - Nhược điểm: Row-level lock có thể gây contention nếu tất cả request tập trung vào một product.

## Decision
Dùng atomic conditional update trong PostgreSQL, kết hợp `CHECK (available_stock >= 0)` constraint. Không dùng Redis làm nguồn sự thật tồn kho (Redis chỉ dùng cache + rate limiting).

## Consequences
- Tồn kho luôn đúng, không overselling.
- Contention có thể xảy ra trên product hot; giảm bằng batch reservation hoặc sharding nếu cần.
- Không cần vận hành Redis cho tồn kho.

## Related code
- `services/inventory-service/src/main/java/com/omniorder/inventory/repository/ProductRepository.java`
- `services/inventory-service/src/main/resources/db/migration/V1__init.sql`
- `docs/evidence/01-concurrency.md`
