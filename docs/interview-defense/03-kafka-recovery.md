# Interview Defense — Kafka Recovery / Transactional Outbox

## Business problem
Order đã lưu vào DB nhưng ứng dụng crash trước khi publish Kafka event → sự kiện bị mất, inventory không bao giờ biết cần giữ hàng, đơn bị treo vĩnh viễn ở PENDING.

## Initial implementation (failure mode)
`kafkaTemplate.send()` async, không chờ acknowledgment. Nếu crash sau commit DB nhưng trước khi Kafka nhận, sự kiện bị mất.

## How to reproduce
Tắt Kafka, gọi `createOrder` → order commit DB, outbox record `PENDING`. Nếu không có outbox, sự kiện bị mất vĩnh viễn.

## Root cause
Không có transactional outbox. Business state và event publish không nằm trong cùng transaction boundary.

## Technical options considered
1. **Dual write (chained)**: Lưu DB, rồi gọi Kafka. Nếu crash giữa hai bước → không nhất quán. Không an toàn.
2. **Kafka Exactly-Once alone**: Kafka EOS chỉ đảm bảo trong ranh giới Kafka, không đảm bảo side effect ở database bên ngoài (inventory, orders). Vẫn cần idempotent consumer.
3. **Transactional Outbox (chosen)**: Lưu business state + outbox record trong cùng transaction. Outbox relay publish Kafka với retry. Consumer deduplicate qua processed_events.

## Why chosen
Outbox pattern đảm bảo event không bao giờ bị mất (luôn trong DB). Idempotent consumer đảm bảo duplicate delivery không gây side effect trùng. Không phụ thuộc vào Kafka Exactly-Once.

## Trade-offs / limitations
- Eventual consistency: order có thể ở PENDING trong khoảng thời gian outbox chưa publish.
- Cần outbox relay chạy định kỳ, có overhead đọc DB.

## Related code
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java:createOrder, publishOutbox`
- `services/order-service/src/main/resources/db/migration/V1__init.sql` (order_outbox table)
- `services/inventory-service/src/main/java/com/omniorder/inventory/repository/ProcessedEventRepository.java`
- `services/order-service/src/test/java/com/omniorder/order/service/OrderServiceOutboxTest.java`

## Test results
Sau createOrder, outbox record tồn tại với status='PENDING'. Chạy nhiều lần, outbox luôn được tạo.

## Traffic ×10
Outbox relay poll mỗi 1s. Với traffic ×10, cần tăng tần suất poll hoặc batch publish. Partition Kafka topic theo aggregate_id để giữ ordering.

## Follow-up questions & answers
1. **Tại sao không dùng Kafka Exactly-Once?** → Kafka EOS chỉ đảm bảo trong Kafka, không đảm bảo database bên ngoài. Vẫn cần idempotent consumer và outbox.
2. **Duplicate delivery xử lý thế nào?** → Consumer kiểm tra `processed_events` (PK event_id) với INSERT ON CONFLICT DO NOTHING. Nếu event đã xử lý → bỏ qua.
3. **Publish thành công nhưng ack mất sao?** → Outbox record vẫn PENDING → retry publish → consumer deduplicate.
4. **Poison message xử lý thế nào?** → Retry tối đa 5 lần, sau đó chuyển DLQ. Không retry vô hạn.
5. **Làm sao replay từ DLQ?** → Query outbox status='DLQ', sửa lỗi, chuyển về PENDING, outbox relay sẽ publish lại.
