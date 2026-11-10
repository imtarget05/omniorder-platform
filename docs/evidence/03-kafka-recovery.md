# Evidence 03 — Kafka Recovery / Transactional Outbox (P0)

## Business invariant
Không mất sự kiện sau khi Kafka phục hồi. Order đã lưu vào DB phải có event tương ứng trong outbox, và outbox relay sẽ publish khi Kafka lại hoạt động.

## Failure mode reproduced
Trước khi fix: `kafkaTemplate.send()` async không chờ acknowledgment. Nếu ứng dụng crash sau khi commit DB nhưng trước khi Kafka nhận event, sự kiện bị mất vĩnh viễn.

## Root cause
Không có transactional outbox. Business state và event publish không nằm trong cùng một transaction boundary.

## Fix applied
- `OrderService.createOrder`: lưu order + `order_outbox` record trong cùng `@Transactional` transaction.
- `OrderService.publishOutbox` (`@Scheduled`): quét outbox `status='PENDING'`, gửi Kafka với `kafkaTemplate.send().get(timeout)` (chờ ack), sau đó đánh dấu `PUBLISHED`.
- Nếu gửi thất bại: tăng `retry_count`, tối đa 5 lần, sau đó chuyển trạng thái `DLQ`.
- Consumer (`OrderRequestedConsumer` trong inventory-service): dùng `processed_events` (PK `event_id`) với `INSERT ... ON CONFLICT DO NOTHING` để deduplicate side effect khi event được redeliver.
- **Inventory phản hồi qua outbox (không gửi thẳng Kafka):** `reserveStock` ghi `InventoryOutbox` (`STOCK_RESERVED` khi đủ hàng, `STOCK_REJECTED` khi hết hàng) cùng transaction với việc cập nhật tồn kho/reservation. `InventoryService.publishOutbox` (`@Scheduled`) quét outbox `PENDING`, gửi Kafka (`stock.reserved` / `stock.rejected`) và đánh dấu `PUBLISHED`. Điều này đảm bảo phản hồi tồn tại dù Kafka nghỉ, và không bị publish trùng khi broker phục hồi.
- `StockActionConsumer` xử lý `stock.consume` → `consumeReservation` (`CONSUMED` + outbox `STOCK_CONSUMED`) và `stock.release` → `releaseReservation` (`RELEASED` + outbox `STOCK_RELEASED`), idempotent qua `processed_events`.

## Test
`OrderServiceOutboxTest`:
1. Sau `createOrder`, outbox record tồn tại với `status='PENDING'`, `published_at IS NULL` (Kafka chưa cần chạy).
2. Order và outbox được tạo trong cùng transaction.

`InventoryServiceReservationTest`:
1. `reserveStock` cùng eventId hai lần → lần thứ hai trả về `duplicate()` (idempotent consumer).

## Kafka outage scenario (manual verification)
1. Tắt Kafka, gọi `createOrder` → order commit DB, outbox record `PENDING`.
2. Bật Kafka lại → outbox relay `@Scheduled` quét, publish `order.requested`, đánh dấu `PUBLISHED`.
3. Inventory-service nhận event, reserve stock, publish `stock.reserved`.
4. Order-service nhận `stock.reserved` → confirm đơn.

## Traffic ×10
Outbox relay poll mỗi 1s. Với traffic ×10, cần:
- Tăng tần suất poll hoặc batch publish.
- Partition Kafka topic theo `aggregate_id` để giữ ordering.
