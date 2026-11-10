# Interview Defense — Idempotency

## Business problem
Mobile client bấm đặt hàng 3 lần do mạng chậm (timeout), server nhận 3 request, nếu không có idempotency sẽ tạo 3 đơn cho cùng một yêu cầu.

## Initial implementation (failure mode)
Không có idempotency key. Mỗi `POST /orders` tạo đơn mới, bất kể client có đang retry hay không.

## How to reproduce
Chạy `OrderServiceIdempotencyTest`: 10 request cùng `Idempotency-Key` concurrent. Trước fix: 10 đơn được tạo.

## Root cause
Không có unique constraint và không lưu fingerprint payload → không thể nhận diện retry.

## Technical options considered
1. **In-memory Map (ConcurrentHashMap)**: nhanh, nhưng mất dữ liệu khi restart, không scaling qua nhiều instance. Không đáp ứng yêu cầu "lưu bền vững".
2. **Database unique constraint (chosen)**: `UNIQUE (user_id, idempotency_key)`. Lưu bền vững, scaling qua nhiều instance, không mất dữ liệu khi restart.
3. **Redis SETNX với TTL**: nhanh, nhưng không phải source of truth; nếu Redis mất dữ liệu, idempotency mất. Chỉ dùng làm cache, không dùng làm source of truth.

## Why chosen
Database unique constraint đảm bảo idempotency bền vững và đúng trên nhiều instance. `idempotency_hash` giúp phát hiện key tái sử dụng với payload khác (409).

## Trade-offs / limitations
- Unique constraint cần index, có overhead ghi nhẹ.
- Cần chính sách cleanup/purge cho idempotency records cũ (TTL 30 ngày).

## Related code
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java:createOrder`
- `services/order-service/src/main/resources/db/migration/V1__init.sql` (unique constraint)
- `services/order-service/src/test/java/com/omniorder/order/service/OrderServiceIdempotencyTest.java`

## Test results
10 request cùng key → 1 order. Cùng key payload khác → IllegalStateException (409).

## Traffic ×10
Unique constraint trên `(user_id, idempotency_key)` hoạt động nhưng có thể gây lock contention. Cần tăng buffer pool, partition theo user_id nếu cần.

## Follow-up questions & answers
1. **Tại sao không dùng in-memory Map?** → Mất dữ liệu khi restart, không scaling qua nhiều instance.
2. **SHA-256 hash dùng làm gì?** → Phát hiện idempotency key tái sử dụng với payload khác → từ chối rõ ràng (409).
3. **TTL/cleanup thế nào?** → Giữ 30 ngày, purge job định kỳ xóa bản ghi cũ.
4. **Cùng key, khác payload → thế nào?** → Trả 409 Conflict, không ghi đè đơn cũ.
5. **Restart service rồi retry vẫn đúng không?** → Có, vì idempotency lưu trong PostgreSQL, không mất khi restart.
