# ADR-002: Transactional Outbox + idempotent consumer instead of Kafka Exactly-Once alone

## Status
Accepted

## Context
Order service cần publish sự kiện `order.requested` đến inventory service sau khi đơn được lưu vào DB. Cần đảm bảo sự kiện không bị mất và không được xử lý hai lần.

## Options considered
1. **Dual write (chained)**: Lưu DB, rồi gọi Kafka producer.
   - Nhược điểm: Nếu crash giữa hai bước, sự kiện bị mất. Không nhất quán.
2. **Kafka Exactly-Once (EOS) alone**:
   - Nhược điểm: Kafka EOS chỉ đảm bảo trong ranh giới Kafka. Side effect ở database bên ngoài (inventory stock, order status) không được bảo đảm. Vẫn cần idempotent consumer.
3. **Transactional Outbox + idempotent consumer (chosen)**:
   - Lưu business state + outbox record trong cùng DB transaction.
   - Outbox relay publish Kafka với retry, đánh dấu PUBLISHED.
   - Consumer dùng `processed_events` (PK event_id) với INSERT ON CONFLICT DO NOTHING để deduplicate.

## Decision
Dùng Transactional Outbox pattern. Không tuyên bố end-to-end exactly-once mà chưa chứng minh bằng test.

## Consequences
- Sự kiện không bao giờ bị mất (luôn trong outbox DB).
- Duplicate delivery không gây side effect trùng (idempotent consumer).
- Eventual consistency: order có thể ở PENDING trong khoảng thời gian outbox chưa publish.
- Cần outbox relay chạy định kỳ.

## Related code
- `services/order-service/src/main/java/com/omniorder/order/service/OrderService.java`
- `services/inventory-service/src/main/java/com/omniorder/inventory/repository/ProcessedEventRepository.java`
- `docs/evidence/03-kafka-recovery.md`
