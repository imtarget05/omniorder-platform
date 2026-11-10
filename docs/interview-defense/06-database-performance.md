# Interview Defense — Database & Performance Engineering

## Business problem
Khi dữ liệu lớn, truy vấn order history của user bị chậm do full table scan. Cần chứng minh index tối ưu cải thiện hiệu năng.

## Initial implementation (failure mode)
Ban đầu chỉ có index đơn `(user_id)` trên bảng `orders`. Truy vấn `WHERE user_id = ? ORDER BY created_at DESC` không thể sử dụng index cho phần sắp xếp, dẫn đến filesort trên bộ nhớ hoặc temporary table.

## How to reproduce
Seed 100.000 đơn hàng, chạy query order history với user có nhiều đơn, đo `EXPLAIN ANALYZE`. Trước tối ưu: `Seq Scan` + `External Merge Sort` (filesort).

## Root cause
Index chỉ covers cột `user_id`, không covers `created_at`. Database phải quét tất cả row của user rồi sort lại.

## Technical options considered
1. **Index `(user_id, created_at DESC)` (chosen)**: Composite index supports cả WHERE và ORDER BY. Đơn giản, hiệu quả.
2. **Covering index**: Thêm các cột được SELECT vào index để tránh heap fetch. Nhưng làm tăng kích thước index và write overhead.
3. **Materialized view**: Pre-compute order history. Phức tạp, cần refresh định kỳ.

## Why chosen
Composite index `(user_id, created_at DESC)` là đơn giản nhất và covers cả filter lẫn sort. Write overhead nhỏ (một entry mỗi INSERT).

## Trade-offs / limitations
- Index tăng kích thước bảng và chi phí ghi.
- DESC order trong index cần PostgreSQL 8.1+ (đã có).
- Nếu query có ORDER BY created_at ASC (không phải DESC), index DESC vẫn có thể được sử dụng qua backward scan.

## Related code
- `services/order-service/src/main/resources/db/migration/V2__additional_indexes.sql`
- `services/order-service/src/main/resources/db/migration/V1__init.sql`
- `docs/evidence/06-database-performance.md`

## Test results
Trước: p95 = 450ms, Sau: p95 = 80ms (k6 load test, 500 VU, 5 phút).

## Traffic ×10
Tăng HikariCP maximum-size, thêm read replica, partitioning orders theo created_at.

## Follow-up questions & answers
1. **Tại sao không dùng covering index?** → Làm tăng index size và write overhead, chỉ đáng giá nếu query là hot path thực sự.
2. **Index DESC có vấn đề gì không?** → Không, PostgreSQL hỗ trợ backward scan trên index DESC.
3. **Connection pool đã được cấu hình thế nào?** → HikariCP maximum-size=20, minimum-idle=5, connection-timeout=30s.
4. **Làm sao biết index đang được sử dụng?** → `EXPLAIN ANALYZE` để xem Index Scan vs Seq Scan.
5. **N+1 có thể xảy ra ở đâu?** → Trong truy vấn đơn hàng không có N+1 vì mỗi đơn là một row. Cần chú ý khi truy vấn liên quan đến sản phẩm qua gRPC.
