# ADR-004: Idempotency key scope and TTL/cleanup policy

## Status
Accepted

## Context
Cần ngăn client retry tạo thêm đơn. Cần định nghĩa phạm vi unique của idempotency key và chính sách cleanup.

## Options considered
1. **Global unique key**: `UNIQUE (idempotency_key)`.
   - Nhược điểm: Hai user khác nhau dùng cùng key (ví dụ "retry-1") sẽ xung đột, không nên.
2. **Per-user unique key (chosen)**: `UNIQUE (user_id, idempotency_key)`.
   - Ưu điểm: Mỗi user có không gian key riêng, không xung đột giữa các user.
3. **In-memory Map**: Không bền vững, mất khi restart, không scaling.

## Decision
Pham vi unique: `(user_id, idempotency_key)`. Lưu `idempotency_hash = SHA-256(payload fingerprint)` để phát hiện key tái sử dụng với payload khác (409).

## TTL / Cleanup
- Idempotency records (orders) giữ 30 ngày.
- Purge job định kỳ xóa bản ghi cũ (planned, chưa triển khai trong P0).

## Consequences
- Retry cùng key/payload → trả về cùng order, không tạo trùng.
- Cùng key, khác payload → 409 Conflict.
- Lưu bền vững trong PostgreSQL, không mất khi restart.

## Related code
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java`
- `services/order-service/src/main/resources/db/migration/V1__init.sql`
- `docs/evidence/02-idempotency.md`
