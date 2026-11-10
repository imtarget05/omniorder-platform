# ADR-003: gRPC for sync product lookup, Kafka for async stock reservation

## Status
Accepted

## Context
Order service cần thông tin sản phẩm (tên, giá) từ inventory service để hiển thị và tính tổng tiền. Order service cũng cần yêu cầu inventory giữ hàng (reserve) một cách bất đồng bộ.

## Options considered
1. **gRPC for everything**: Order gọi gRPC inventory để kiểm tra và giữ hàng.
   - Nhược điểm: Đồng bộ, chặn request. Nếu inventory chậm/lỗi, order request cũng thất bại. Không phù hợp cho flash sale tải cao.
2. **Kafka for everything**: Order publish `order.requested`, inventory nhận và reserve. Không có cách tra thông tin sản phẩm ngay lập tức.
   - Nhược điểm: Không thể tra giá/tên sản phẩm ngay khi tạo đơn (phải đợi event).
3. **Hybrid (chosen)**: gRPC `GetProduct` để tra thông tin sản phẩm (chính tham khảo, không giữ hàng). Kafka `order.requested` → inventory reserve hàng atomic → `stock.reserved`/`stock.rejected` → order confirm/reject.

## Decision
Dùng gRPC cho sync product lookup (tham khảo), dùng Kafka cho async stock reservation (transactional, atomic trong inventory service).

## Consequences
- Thông tin sản phẩm luôn có sẵn qua gRPC.
- Việc giữ hàng được thực hiện bằng atomic transaction trong inventory service, không qua gRPC.
- Order status chuyển PENDING → CONFIRMED/REJECTED qua Kafka event.

## Related code
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java` (gRPC client - planned)
- `services/inventory-service/src/main/java/com/omniorder/inventory/kafka/OrderRequestedConsumer.java`
- `docs/architecture/adr/ADR-003-grpc-vs-kafka.md`
