# Interview Defense — Distributed Recovery / Compensation

## Business problem
Inventory đã giữ hàng (reserve) nhưng checkout thất bại hoặc order-service crash giữa workflow → hàng bị treo ở HELD, không được trả lại, đơn không bao giờ kết thúc.

## Initial implementation (failure mode)
Không có trạng thái terminal cho reservation, không có reconciliation job. Reservation HELD vĩnh viễn nếu không có bước tiếp theo xử lý.

## How to reproduce
Reserve stock thành công, sau đó gọi `releaseReservation(orderId)` mô phỏng checkout thất bại → stock phải về lại. Nếu gọi release hai lần → stock không được tăng đôi (idempotent).

## Root cause
Không có cơ chế phát hiện và xử lý reservation/order bị treo.

## Technical options considered
1. **Distributed transaction (2PC) giữa Oracle và Kafka**: phức tạp, không hiệu quả, không cần thiết.
2. **Compensating transaction + reconciliation job (chosen)**: Khi checkout thất bại → gọi releaseReservation (trả stock). Reconciliation job định kỳ phát hiện PENDING/HELD quá hạn → xử lý.

## Why chosen
Compensating transaction đơn giản, không cần 2PC. Reconciliation job đảm bảo eventually consistency. Release idempotent đảm bảo an toàn khi chạy nhiều lần.

## Trade-offs / limitations
- Eventual consistency: hàng có thể bị khóa trong khoảng TTL (5 phút) trước khi reconciliation chạy.
- Cần chạy job định kỳ, có overhead đọc DB.

## Related code
- `services/inventory-service/src/main/java/com/omniorder/inventory/service/InventoryService.java:releaseReservation, expireReservations`
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java:confirmOrder, rejectOrder, cancelOrder`
- `services/order-service/src/main/java/com/omniorder/order/service/ReconciliationJob.java`
- `services/inventory-service/src/test/java/com/omniorder/inventory/service/InventoryServiceReservationTest.java`

## Test results
Reserve → release: stock về lại. Release lại lần hai: stock không tăng đôi (idempotent). Duplicate event: trả về duplicate.

## Traffic ×10
Reconciliation job quét toàn bộ bảng. Với traffic ×10, cần index composite `stock_reservations(status, reserved_at)` và `orders(status, created_at)` (đã có).

## Follow-up questions & answers
1. **Tại sao không dùng 2PC?** → Phức tạp, hiệu quả thấp, không cần thiết cho eventual consistency.
2. **Release idempotent sao?** → Kiểm tra status trước khi release; nếu đã RELEASED/CONSUMED thì bỏ qua.
3. **Reconciliation chạy nhiều lần có sao không?** → Không, vì release idempotent và chỉ xử lý status HELD quá hạn.
4. **TTL reservation là bao lâu?** → 5 phút (cấu hình `RESERVATION_TTL_SECONDS`).
5. **Làm sao biết đơn nào bị treo?** → Query `orders` status='PENDING' và `created_at` quá hạn; hoặc `stock_reservations` status='HELD' và `reserved_at` quá hạn.
