# SPEC.md — OmniOrder Platform Technical Specification

Mục tiêu: Chuyển OmniOrder từ skeleton code thành hệ thống chứng minh khả năng giải quyết production problem thực tế (overselling, idempotency, distributed transaction, service failure, security, performance, CI/CD).

Không tạo mock demonstration. Mọi tính năng phải có test chạy trên infrastructure thật (Oracle/Testcontainers, Kafka/Testcontainers, Redis/Testcontainers).

---

## 1. Hệ thống & ranh giới nghiệp vụ

### 1.1 Services
| Service | Nhiệm vụ | DB sở hữu |
|---------|----------|-----------|
| identity-service | Users, Auth, JWT RS256, Argon2id | Oracle schema `IDENTITY` |
| order-service | Đơn hàng, idempotency, outbox, state machine, compensation | Oracle schema `ORDER` |
| inventory-service | Sản phẩm, tồn kho, atomic reservation, gRPC server, Kafka consumer | Oracle schema `INVENTORY` |
| notification-worker | Kafka consumer → gửi thông báo (giai đoạn 2) | — |
| gateway | JWT auth, routing, rate limiting (giai đoạn 3) | — |

Ranh giới: Không chia service theo bảng DB. Mỗi service sở hữu schema của mình. Giao tiếp nội bộ qua gRPC (order → inventory tra sản phẩm) và Kafka (events bất đồng bộ).

### 1.2 Business invariants (phải bảo vệ bất chấp tải)
1. `available_stock` không bao giờ âm.
2. Số đơn `CONFIRMED` không vượt quá tồn kho thực tế lúc tạo.
3. Một `idempotency_key` = một yêu cầu_logic trong phạm vi user + operation.
4. Một sự kiện Kafka được giao lại không gây side effect trùng (idempotent consumer).
5. Mọi inventory reservation phải có kết quả cuối cùng: `consumed` hoặc `released`, trừ khi đang chờ có cơ chế recovery.
6. Không để đơn `PENDING` bị bỏ quên vĩnh viễn — reconciliation job phát hiện và xử lý.

---

## 2. API Contracts

### 2.1 Identity (REST)
- `POST /auth/register` → RegisterRequest → 201
- `POST /auth/login` → AuthRequest → AuthResponse (JWT)
- `GET /auth/me` → hiện user đang đăng nhập

### 2.2 Order (REST public)
- `POST /api/v1/orders` (header `Idempotency-Key`, `Authorization: Bearer <jwt>`) → tạo đơn PENDING + outbox trong 1 transaction
- `GET /api/v1/orders/{id}` → lấy đơn (chỉ owner hoặc ADMIN)
- `GET /api/v1/orders` → danh sách đơn của user (pagination, composite index)
- `POST /api/v1/orders/{id}/cancel` → hủy đơn (chỉ owner, trạng thái cho phép)

### 2.3 Inventory (gRPC internal, dùng cho order-service tra cứu)
- `GetProduct(GetProductRequest) → GetProductResponse` (tên, giá, tồn kho tham khảo)
- Ghi nhận qua gRPC chỉ mang tính tham khảo; việc giữ hàng thực hiện bằng atomic transaction trong inventory-service.

### 2.4 Kafka Events (outbox → inventory → order)
| Topic | Producer | Consumer | Mục đích |
|-------|----------|----------|----------|
| `order.requested` | order-service | inventory-service | Yêu cầu giữ hàng |
| `stock.reserved` | inventory-service | order-service, notification-worker | Đã giữ hàng thành công |
| `stock.rejected` | inventory-service | order-service, notification-worker | Hết hàng / lỗi |
| `order.confirmed` | order-service | notification-worker | Đơn xác nhận |
| `order.rejected` | order-service | notification-worker | Đơn từ chối |
| `dlq.order.events` | — | — | Dead letter queue |

Mỗi event: `event_id`, `aggregate_id`, `aggregate_type`, `schema_version`, `occurred_at`, `payload`.

---

## 3. Database Schema (Oracle / Testcontainers Oracle)

Mỗi service có schema riêng. Dùng Flyway migration.

### 3.1 order-service schema
```sql
CREATE TABLE orders (
    id RAW(16) PRIMARY KEY,
    user_id RAW(16) NOT NULL,
    product_id RAW(16) NOT NULL,
    quantity NUMBER NOT NULL CHECK (quantity > 0),
    status VARCHAR2(32) NOT NULL, -- PENDING, CONFIRMED, REJECTED, CANCELLED, COMPENSATING
    total_price NUMBER(18,2) NOT NULL,
    idempotency_key VARCHAR2(255) NOT NULL,
    idempotency_hash VARCHAR2(64) NOT NULL, -- SHA-256 of payload fingerprint
    version NUMBER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_order_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_outbox (
    id RAW(16) PRIMARY KEY,
    aggregate_id RAW(16) NOT NULL,
    aggregate_type VARCHAR2(64) NOT NULL,
    event_type VARCHAR2(128) NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR2(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count NUMBER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status_created ON order_outbox(status, created_at);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status_created ON orders(status, created_at);
CREATE INDEX idx_orders_idempotency ON orders(user_id, idempotency_key);
```

### 3.2 inventory-service schema
```sql
CREATE TABLE products (
    id RAW(16) PRIMARY KEY,
    sku VARCHAR2(100) NOT NULL UNIQUE,
    name VARCHAR2(255) NOT NULL,
    price NUMBER(18,2) NOT NULL,
    available_stock NUMBER NOT NULL CHECK (available_stock >= 0),
    version NUMBER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE stock_reservations (
    id RAW(16) PRIMARY KEY,
    product_id RAW(16) NOT NULL,
    order_id RAW(16) NOT NULL,
    quantity NUMBER NOT NULL CHECK (quantity > 0),
    status VARCHAR2(32) NOT NULL, -- HELD, CONSUMED, RELEASED, EXPIRED
    reserved_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    consumed_at TIMESTAMP,
    released_at TIMESTAMP,
    version NUMBER NOT NULL DEFAULT 1,
    CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_reservation_product_status ON stock_reservations(product_id, status);
CREATE INDEX idx_reservation_order ON stock_reservations(order_id);
CREATE INDEX idx_reservation_expires ON stock_reservations(expires_at) WHERE status = 'HELD';
CREATE INDEX idx_products_available_stock ON products(available_stock);
```

### 3.3 identity-service schema (đã có V1__init.sql)

---

## 4. Transaction Boundaries

| Hành động | Transaction boundary | Pattern |
|-----------|---------------------|---------|
| Tạo đơn PENDING + ghi outbox | 1 DB transaction (order-service) | Transactional Outbox |
| Giữ hàng (reserve) + ghi reservation | 1 DB transaction (inventory-service) | Atomic conditional update |
| Xác nhận đơn + publish outbox | 1 DB transaction (order-service) | Outbox relay |
| Hoàn hàng (compensation) + cập nhật đơn | 1 DB transaction (inventory-service + order-service qua event) | Compensating transaction |
| Xử lý Kafka event + inbox dedup | 1 DB transaction (consumer service) | Consumer Inbox / dedup table |

---

## 5. Idempotency Design

- Header `Idempotency-Key` bắt buộc cho `POST /orders`.
- Phạm vi unique: `(user_id, idempotency_key)`.
- Lưu `idempotency_hash = SHA-256(payload fingerprint)` để phát hiện key tái sử dụng với payload khác → từ chối rõ ràng (HTTP 409).
- Concurrent requests cùng key: chỉ 1 transaction thực thi nghiệp vụ, các request khác chờ hoặc trả về trạng thái đang xử lý.
- Không dùng in-memory Map. Lưu bền vững trong bảng `orders` (unique constraint).
- TTL/cleanup: idempotency records giữ 30 ngày, purge job định kỳ.

---

## 6. Transactional Outbox (Reliable Event Processing)

1. Business state + outbox record ghi trong cùng 1 transaction.
2. Outbox relay (scheduled @Every 5s) quét `status = 'PENDING'`, publish Kafka, cập nhật `status = 'PUBLISHED'` + `published_at`.
3. Nếu publish thành công nhưng ack bị mất → outbox record vẫn PENDING → retry publish → consumer deduplicate qua inbox table.
4. Retry có giới hạn (max 5), exponential backoff, sau đó chuyển DLQ.
5. Consumer có bảng `processed_events(event_id PRIMARY KEY)` để deduplicate.
6. Không tuyên bố end-to-end exactly-once nếu chưa chứng minh bằng test.

---

## 7. Order State Machine

```
PENDING ──(stock.reserved)──► CONFIRMED ──► COMPLETED
   │                              │
   │                              └─(cancel)──► CANCELLED
   │
   └─(stock.rejected / timeout)──► REJECTED
   │
   └─(reconciliation timeout)──► COMPENSATING ──► CANCELLED (+release stock)
```

Trạng thái chỉ chuyển theo quy tắc rõ ràng. Không cho phép từ CONFIRMED quay lại PENDING.

---

## 8. Security Requirements

- JWT RS256 (Nimbus), validate issuer/audience/expiry.
- Password hash: Argon2id (không dùng SHA-256 trực tiếp cho password).
- Object-level authorization: user A không thể xem/sửa order của user B.
- Role-based: ADMIN truy cập tất cả, CUSTOMER chỉ của mình.
- Mass assignment protection: DTO whitelist fields.
- Input validation + request size limits.
- Secure error handling (không rò rơi stack trace/internal info).
- Rate limiting trên `/auth/login` và `/orders` (Redis, giai đoạn 2).
- Không commit secret keys vào repo.

---

## 9. Performance Requirements

- Dataset đủ lớn (≥ 100.000 orders) để đo query thực tế.
- Composite index cho `orders(user_id, status, created_at DESC)` + pagination.
- Đo execution plan trước/sau tối ưu index.
- Load test k6: throughput, p50/p95/p99, error rate.
- Redis: đo cache hit/miss, TTL, chứng minh stale cache không làm sai nghiệp vụ.
- Không bịa số liệu benchmark.

---

## 10. Observability

- Structured logs (JSON) với correlation ID (X-Correlation-ID / trace ID).
- Micrometer metrics: `http.server.requests`, `kafka.consumer.lag`, `outbox.pending.count`, `reservation.expired.count`.
- Health endpoints: `/actuator/health`, `/actuator/metrics`.
- Incident simulation: Kafka down, Inventory down, DB connection exhaustion.

---

## 11. Definition of Done (mỗi bài toán P0/P1)

Một bài toán chỉ DONE khi có:
1. Business invariant rõ ràng.
2. Reproduction test / baseline failure.
3. Implementation hoàn thành.
4. Automated tests chạy thành công (JUnit + Testcontainers thật).
5. Build + regression tests xanh.
6. Runtime verification trên infrastructure thích hợp.
7. Evidence trong `docs/evidence/`.
8. ADR giải thích quyết định quan trọng.
9. Interview-defense document trong `docs/interview-defense/`.
10. Không có lỗi nghiêm trọng chưa công khai.




