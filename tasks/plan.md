# tasks/plan.md — Triển khai theo thứ tự P0 → P1 → P2

Nguồn: SPEC.md, CONSTRAINTS.md

## Giai đoạn 1: P0 — Concurrency & Inventory Correctness (Overselling)

Mục tiêu: Chứng minh 1.000 request tranh mua 5 sản phẩm → tối đa 5 đơn CONFIRMED, tồn kho không âm.

Tasks:
1. [ ] Hoàn thiện order-service: entity `Order`, enum `OrderStatus`, repository `OrderRepository`, bảng `order_outbox`.
2. [ ] Hoàn thiện inventory-service: entity `Product`, entity `StockReservation`, repository, atomic conditional update SQL (`UPDATE products SET available_stock = available_stock - :qty WHERE id = :id AND available_stock >= :qty`).
3. [ ] Tạo REST controller order-service: `POST /orders` với `Idempotency-Key`, tạo đơn PENDING + outbox trong 1 transaction.
4. [ ] Tạo gRPC client trong order-service gọi inventory-service tra thông tin sản phẩm (chính tham khảo).
5. [ ] Tạo Kafka producer trong order-service (outbox relay) publish `order.requested`.
6. [ ] Tạo Kafka consumer trong inventory-service nhận `order.requested`, atomic reserve stock, publish `stock.reserved` / `stock.rejected`.
7. [ ] Viết Testcontainers test: Oracle + Kafka, 1.000 concurrent request, assert tối đa 5 CONFIRMED, stock không âm.
8. [ ] Tạo docs/evidence/01-concurrency.md (phương pháp test, logs, SQL verification, kết quả thực tế).
9. [ ] Tạo docs/interview-defense/01-concurrency.md.
10. [ ] ADR: Tại sao dùng atomic conditional update trong Oracle thay vì Redis distributed lock.

## Giai đoạn 2: P0 — Request Idempotency

Mục tiêu: 10 request cùng key chỉ tạo tối đa 1 order; retry không tạo trùng.

Tasks:
1. [ ] Thêm cột `idempotency_key`, `idempotency_hash` vào bảng `orders` + unique constraint `(user_id, idempotency_key)`.
2. [ ] Middleware/Interceptor kiểm tra `Idempotency-Key` header, từ chối key + payload khác (HTTP 409).
3. [ ] Concurrent request cùng key: chỉ 1 transaction thực thi, các request khác chờ/trả về trạng thái đang xử lý.
4. [ ] Viết test: 10 request đồng thời cùng key → 1 order; retry sau timeout không trùng; restart service rồi retry vẫn đúng; cùng key payload khác → 409.
5. [ ] Tạo docs/evidence/02-idempotency.md.
6. [ ] Tạo docs/interview-defense/02-idempotency.md.
7. [ ] ADR: Idempotency key scope và TTL/cleanup policy.

## Giai đoạn 3: P0 — Reliable Event Processing (Transactional Outbox)

Mục tiêu: DB commit nhưng Kafka tắt → event vẫn trong outbox; bật Kafka lại → publish và xử lý; duplicate delivery không side effect trùng.

Tasks:
1. [ ] Triển khai Outbox Relay (scheduled @Every 5s) quét outbox PENDING, publish Kafka, đánh dấu PUBLISHED.
2. [ ] Xử lý publish thành công nhưng ack mất → outbox vẫn PENDING → retry → consumer deduplicate qua inbox table.
3. [ ] Consumer Inbox / processed_events table để deduplicate side effects.
4. [ ] Retry giới hạn (max 5), exponential backoff, sau đó DLQ.
5. [ ] Quy trình inspect và replay DLQ an toàn.
6. [ ] Viết test: DB commit + Kafka tắt → outbox còn PENDING; bật Kafka → publish; consumer crash trước ack → không side effect lần 2; duplicate + out-of-order không hỏng trạng thái; poison message không retry vô hạn; replay từ DLQ.
7. [ ] Tạo docs/evidence/03-kafka-recovery.md.
8. [ ] Tạo docs/interview-defense/03-kafka-recovery.md.
9. [ ] ADR: Tại sao không phụ thuộc vào Kafka Exactly-Once alone; thiết kế idempotent consumer.

## Giai đoạn 4: P0 — Distributed Workflow & Compensation

Mục tiêu: Inventory giữ hàng nhưng checkout lỗi → hàng trả lại; reconciliation job phát hiện đơn treo.

Tasks:
1. [ ] Hoàn thiện Order State Machine (PENDING, CONFIRMED, REJECTED, CANCELLED, COMPENSATING).
2. [ ] Tạo simulated payment/checkout adapter có thể chủ động trả lỗi (không tích hợp cổng thanh toán thật).
3. [ ] Xử lý timeout, partial failure, duplicate callback, stale event.
4. [ ] Triển khai compensating transaction: trả lại hàng khi checkout thất bại (idempotent).
5. [ ] Tạo reconciliation job: phát hiện PENDING quá hạn / reservation HELD quá hạn → xử lý hoặc release.
6. [ ] Viết test: reservation thành công + checkout thất bại → hàng trả lại đúng 1 lần; order-service restart giữa workflow → resume/reconcile; timeout sau downstream thành công → retry không side effect trùng; reconciliation chạy nhiều lần không phá dữ liệu.
7. [ ] Tạo docs/evidence/04-distributed-recovery.md.
8. [ ] Tạo docs/interview-defense/04-distributed-recovery.md.
9. [ ] ADR: Khi dùng gRPC sync và khi dùng Kafka async.

## Giai đoạn 5: P1 — API Security

Mục tiêu: BOLA protection, JWT validation, role-based, negative tests.

Tasks:
1. [ ] JWT RS256 validation (issuer, audience, expiry) trên order-service/inventory-service.
2. [ ] Object-level authorization: user A không thể xem/sửa order của B.
3. [ ] Role-based: ADMIN vs CUSTOMER.
4. [ ] Mass assignment protection (DTO whitelist).
5. [ ] Input validation, request size limits.
6. [ ] SQL injection prevention (JPA parameter binding).
7. [ ] Rate limiting (Redis) trên `/auth/login` và `/orders`.
8. [ ] Secure error handling (không rò rơi internals).
9. [ ] Viết automated negative tests (không chỉ happy path).
10. [ ] Tạo docs/evidence/05-api-security.md.
11. [ ] Tạo docs/interview-defense/05-api-security.md.

## Giai đoạn 6: P1 — Database & Performance Engineering

Mục tiêu: Đo được vấn đề performance thực, tối ưu index, ghi trước/sau.

Tasks:
1. [ ] Seed dataset ≥ 100.000 orders.
2. [ ] Chọn query thực tế: order history filter/sort/pagination.
3. [ ] Đo execution plan và thời gian trước tối ưu.
4. [ ] Phân tích full table scan, index selectivity, N+1, connection pool.
5. [ ] Tạo composite index dựa trên workload thực.
6. [ ] Load test k6: throughput, p50/p95/p99, error rate.
7. [ ] Redis: đo cache hit/miss, TTL, chứng minh stale cache không sai nghiệp vụ.
8. [ ] Tạo docs/evidence/06-database-performance.md.
9. [ ] Tạo docs/interview-defense/06-database-performance.md.

## Giai đoạn 7: P1 — CI/CD, Deployment & Recovery

Mục tiêu: GitLab CI pipeline thật, bad deployment + rollback kiểm chứng.

Tasks:
1. [ ] Tạo `.gitlab-ci.yml`: build, static analysis, unit/integration tests, dependency/SAST/secret scan, Docker build (immutable tag), K8s deploy staging, readiness/health, smoke tests, rollback.
2. [ ] Tạo Dockerfile cho mỗi service.
3. [ ] Tạo Kubernetes manifests (Deployment, Service, ConfigMap, Secret, HPA).
4. [ ] Tạo docker-compose.local để chạy toàn bộ stack (Oracle, Kafka, Redis, services).
5. [ ] Thực hiện bad deployment kiểm soát: deploy version tốt → tạo version lỗi → quality gate phát hiện → rollback → chứng minh API hoạt trở lại.
6. [ ] Ghi pipeline logs, image tags, deployment history. Nếu không có runner/K8s/credentials → ghi BLOCKED, không giả mạo.
7. [ ] Tạo docs/evidence/07-cicd-recovery.md.

## Giai đoạn 8: P1 — Observability & Incident Simulation

Tasks:
1. [ ] Structured logs JSON với correlation ID.
2. [ ] Micrometer metrics: HTTP, Kafka lag, outbox pending, reservation expired.
3. [ ] Actuator health endpoints.
4. [ ] Thử nghiệm 3 sự cố: Kafka unavailable, Inventory unavailable, DB connection exhaustion.
5. [ ] Tạo incident report (timeline, symptoms, root cause, recovery, prevention).
6. [ ] Tạo docs/interview-defense/observability.md.

## Giai đoạn 9: P2 — Notification Worker, MongoDB Audit, Gateway, K8s

Tasks (làm sau khi P0/P1 ổn định):
1. [ ] Notification worker: Kafka consumer → gửi thông báo (log/in-memory).
2. [ ] MongoDB: audit history (giai đoạn 3 trong đề xuất ban đầu).
3. [ ] Gateway: JWT auth, routing, rate limiting.
4. [ ] Kubernetes production manifests, monitoring (Prometheus/Grafana).

## Kết quả cuối mỗi milestone

- Vấn đề thực tế đã giải quyết.
- Danh sách file/thành phần đã thay đổi.
- Tests đã chạy, lệnh thực thi, kết quả.
- Kịch bản lỗi đã tái hiện.
- Link evidence.
- Vấn đề còn tồn tại.
- Câu hỏi phỏng vấn có thể đặt ra.
- Milestone tiếp theo có giá trị nhất.


