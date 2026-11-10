# Evidence 04 — Distributed Recovery / Compensation (P0)

## Business invariant
Mọi inventory reservation phải có kết quả cuối cùng: `CONSUMED` hoặc `RELEASED`. Không để đơn `PENDING` bị bỏ quên vĩnh viễn. Hàng phải được trả lại idempotent khi checkout thất bại.

## Failure mode (trước khi fix)
Inventory reserve stock nhưng order-service crash trước khi confirm/reject → reservation treo ở `HELD`, hàng không được trả lại, đơn bị lãng quên. Ngoài ra, `stock.rejected` kích hoạt `stock.release` mặc dù chưa từng có reservation → `releaseReservation` ném exception vì không tìm thấy reservation.

## Fix applied
- `OrderService.confirmOrder(orderId)`: PENDING → CONFIRMED + xuất bản `STOCK_CONSUME` (outbox) → Kafka `stock.consume` → inventory đánh dấu reservation `CONSUMED`. Idempotent (nếu đã CONFIRMED thì bỏ qua).
- `OrderService.rejectOrder(orderId, reason)`: PENDING → REJECTED. **Không xuất bản `STOCK_RELEASE`** vì rejection xảy ra khi tồn kho không đủ, chưa từng tạo reservation → không có gì cần bù.
- `OrderService.cancelOrder(orderId)`: PENDING/CONFIRMED → CANCELLED + xuất bản `STOCK_RELEASE` (outbox) → Kafka `stock.release` → inventory hoàn tồn kho idempotent (compensation).
- `OrderEventConsumer.onStockReserved` → `confirmOrder`; `onStockRejected` → `rejectOrder` (cả hai idempotent, an toàn khi Kafka redeliver).
- `InventoryService.releaseReservation`: nếu không tìm thấy reservation cho order (không bao giờ được đặt) thì trả về ngay (không ném exception, không tăng tồn kho). Nếu đã `RELEASED`/`CONSUMED` thì bỏ qua.
- `InventoryService.expireReservations` (`@Scheduled`): quét reservation `HELD` quá TTL (5 phút) → `releaseReservation`.
- `ReconciliationJob` (`@Scheduled`, order-service): quét đơn `PENDING` quá TTL → `rejectOrder` (idempotent). Không tạo side effect trùng khi chạy nhiều lần.
- `OrderRepository.findByStatusAndCreatedAtBefore(PENDING, cutoff)`: dùng cho reconciliation job.

## Test
`InventoryServiceReservationTest`:
1. Reserve stock (10 → 7), release → stock về 10.
2. Release lại lần nữa (idempotent) → stock vẫn là 10 (không tăng đôi).
3. `reserveStock` cùng eventId hai lần → lần hai trả về `duplicate()`.

`FlashSaleE2ETest` (order-service, HTTP/Kafka/DB thật):
1. 1.000 request đồng thời `POST /api/v1/orders` với idempotency-key khác nhau, sản phẩm tồn kho 5.
2. Luồng: HTTP → order-service (DB + outbox `ORDER_REQUESTED`) → Kafka `order.requested` → inventory-service (atomic `decrementStock`, outbox `STOCK_RESERVED`/`STOCK_REJECTED`) → Kafka `stock.reserved`/`stock.rejected` → order-service `confirmOrder`/`rejectOrder` → outbox `STOCK_CONSUME` → Kafka `stock.consume` → inventory `consumeReservation` (`CONSUMED`).
3. Assert: `confirmed <= 5`, `available_stock >= 0`, mỗi đơn CONFIRMED có reservation `CONSUMED` và `totalPrice == serverPrice * qty` (giá tính ở server), mỗi đơn REJECTED không có reservation.

## Traffic ×10
Reconciliation job quét `orders(status, created_at)` và `stock_reservations(status, reserved_at)`. Với traffic ×10, cần:
- Index composite `orders(status, created_at)` (đã có).
- Index composite `stock_reservations(status, reserved_at)` (đã có).
- Tăng tần suất reconciliation hoặc partition theo thời gian.

## File liên quan
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java` (`confirmOrder`, `rejectOrder`, `cancelOrder`)
- `services/order-service/src/main/java/com/omniorder/order/kafka/OrderEventConsumer.java`
- `services/order-service/src/main/java/com/omniorder/order/service/ReconciliationJob.java`
- `services/inventory-service/src/main/java/com/omniorder/inventory/service/InventoryService.java` (`releaseReservation`, `expireReservations`)
- `services/inventory-service/src/main/java/com/omniorder/inventory/kafka/StockActionConsumer.java`
