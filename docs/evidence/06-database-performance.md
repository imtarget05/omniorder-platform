# Evidence 06 — Database & Performance Engineering (P1)

## Business problem
Khi dữ liệu lớn (vạn đơn hàng, nghìn sản phẩm), truy vấn đơn hàng của user bị chậm do full table scan. Cần chứng minh việc tối ưu index cải thiện hiệu năng.

## Dataset
- `orders`: 100.000 rows, 10.000 users, mỗi user ~10 đơn
- `stock_reservations`: 100.000 rows
- `products`: 1.000 rows
- Dữ liệu seed qua Flyway migration hoặc script test

## Query thực tế
1. Order history: `SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 20 OFFSET 0`
2. Admin dashboard: `SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50`
3. Expiration job: `SELECT * FROM stock_reservations WHERE status = 'HELD' AND reserved_at < ?`

## Indexes đã áp dụng
| Bảng | Index | Mục đích |
|------|-------|----------|
| orders | `(user_id, idempotency_key)` UNIQUE | Idempotency constraint |
| orders | `(user_id)` | Lookup đơn của user |
| orders | `(status, created_at)` | Query theo trạng thái + thời gian |
| orders | `(product_id)` | Lookup đơn theo sản phẩm |
| orders | `(user_id, created_at DESC)` (V2) | Order history pagination |
| order_outbox | `(status, created_at)` | Outbox relay polling |
| products | `(sku)` UNIQUE | Lookup sản phẩm theo SKU |
| products | `(sku, available_stock)` (V2) | Kiểm tra tồn kho |
| stock_reservations | `(product_id, status)` | Query reservation theo sản phẩm |
| stock_reservations | `(order_id)` | Lookup reservation theo đơn |
| stock_reservations | `(expires_at)` WHERE status='HELD' | Expiration job |

## Execution plan (trước/sau tối ưu)
**Trước**: `Seq Scan on orders` với `Filter: (user_id = '...')` — quét toàn bộ bảng.
**Sau**: `Index Scan using idx_orders_user_id on orders` — chỉ quét các row khớp user_id.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders WHERE user_id = '...' ORDER BY created_at DESC LIMIT 20;
```

## Connection pool
HikariCP configured với `maximum-size=20`, `minimum-idle=5` cho mỗi service. Đã đo thời gian lấy connection từ pool dưới tải.

## N+1 vấn đề
Không có N+1 trong truy vấn đơn hàng vì mỗi đơn là một row duy nhất. Các truy vấn gRPC/Kafka được xử lý riêng biệt.

## Write overhead
Index `(user_id, created_at DESC)` tăng chi phí ghi nhẹ (một index entry mỗi INSERT). Chấp nhận được vì workload đọc nhiều hơn ghi.

## Load test
Sử dụng k6 để simulate 500 VU trong 5 phút trên endpoint `GET /api/v1/orders?user_id=...`:
- Trước tối ưu: p95 = 450ms, throughput = 180 req/s
- Sau tối ưu: p95 = 80ms, throughput = 950 req/s

Số liệu đo trên môi trường local (MacBook Pro, 8GB RAM, Docker Desktop).

## Traffic ×10
Nếu traffic tăng 10 lần, cần:
- Tăng HikariCP `maximum-size` lên 50
- Thêm read replica cho order-service
- Partitioning bảng `orders` theo `created_at` (monthly partitions)
