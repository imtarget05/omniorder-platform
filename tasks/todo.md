# tasks/todo.md — Todo list theo P0 (chỉ làm P0 trước, không mở P1 cho đến khi P0 xong)

## P0.1 — Order Service core (order-service)
- [ ] Tạo entity `Order` (id, user_id, product_id, quantity, status, total_price, idempotency_key, idempotency_hash, version, timestamps)
- [ ] Tạo enum `OrderStatus` (PENDING, CONFIRMED, REJECTED, CANCELLED, COMPENSATING)
- [ ] Tạo repository `OrderRepository` (JpaRepository + custom queries)
- [ ] Tạo bảng `order_outbox` + entity `OrderOutbox`
- [ ] Flyway migration V1__init.sql cho order-service (Oracle dialect)
- [ ] REST `POST /api/v1/orders` với header Idempotency-Key, Authorization JWT
- [ ] Transactional service: tạo đơn PENDING + ghi outbox trong 1 transaction
- [ ] Unique constraint `(user_id, idempotency_key)` + kiểm tra hash payload
- [ ] gRPC client gọi inventory-service `GetProduct` (tham khảo)
- [ ] Outbox relay scheduled publish Kafka `order.requested`

## P0.2 — Inventory Service core (inventory-service)
- [ ] Tạo entity `Product` (id, sku, name, price, available_stock, version)
- [ ] Tạo entity `StockReservation` (id, product_id, order_id, quantity, status HELD/CONSUMED/RELEASED/EXPIRED, timestamps)
- [ ] Flyway migration V1__init.sql cho inventory-service (Oracle)
- [ ] Atomic conditional update: `UPDATE products SET available_stock = available_stock - :qty WHERE id = :id AND available_stock >= :qty`
- [ ] gRPC server `GetProduct(GetProductRequest) → GetProductResponse`
- [ ] Kafka consumer: nhận `order.requested`, reserve stock, publish `stock.reserved` / `stock.rejected`
- [ ] Inbox/dedup table cho consumer (`processed_events`)
- [ ] Reservation expiration + release idempotent

## P0.3 — Tests & Evidence (P0)
- [ ] Testcontainers setup: Oracle + Kafka cho order-service + inventory-service
- [ ] Test overselling: 1.000 concurrent request mua 5 sản phẩm → tối đa 5 CONFIRMED, stock không âm
- [ ] Test idempotency: 10 request cùng key → 1 order; retry không trùng; restart rồi retry vẫn đúng; cùng key payload khác → 409
- [ ] Test Kafka outage: DB commit + Kafka tắt → outbox PENDING; bật Kafka → publish và xử lý
- [ ] Test duplicate delivery: consumer crash trước ack → không side effect lần 2
- [ ] Test compensation: reservation thành công + checkout thất bại → hàng trả lại 1 lần
- [ ] Test reconciliation: PENDING quá hạn → xử lý/release; chạy nhiều lần không phá dữ liệu
- [ ] Tạo docs/evidence/01-concurrency.md, 02-idempotency.md, 03-kafka-recovery.md, 04-distributed-recovery.md
- [ ] Tạo docs/interview-defense/ cho từng bài toán trên
- [ ] Tạo docs/architecture/adr/ cho các quyết định quan trọng

## P1 (chỉ mở sau khi P0全部 xong)
- [ ] API Security (JWT validation, BOLA, role-based, negative tests)
- [ ] Database & Performance (index, execution plan, k6 load test)
- [ ] CI/CD (GitLab CI, Docker, K8s, bad deployment + rollback)
- [ ] Observability (structured logs, correlation ID, Micrometer, incident simulation)

## Khoá (Không làm trước khi P0 xong)
- [ ] Notification worker
- [ ] MongoDB audit
- [ ] Gateway rate limiting
- [ ] Kubernetes production manifests
