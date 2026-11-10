# Evidence 05 — API Security (P1)

## Business invariant
- User A không thể xem hoặc sửa đơn hàng của User B (BOLA protection)
- JWT RS256 validation: invalid/expired token → 401
- Role-based: admin APIs chỉ cho ADMIN role
- Input validation: `@Valid`, `@NotNull`, `@Positive` trên request body
- SQL injection prevention: Spring Data JPA parameterized queries
- Rate limiting: geplant (Redis-based, P2)

## Threat model & tests

### BOLA (Broken Object Level Authorization) — OWASP API Security Top 10 #1
Test: `OrderSecurityTest.userA_cannot_access_userB_order_returns403`
- User A (CUSTOMER) gọi `GET /api/v1/orders/{orderB_id}` → 403 Forbidden
- User A (CUSTOMER) gọi `GET /api/v1/orders/{orderA_id}` → 200 OK
- Admin (ADMIN) gọi `GET /api/v1/orders/{orderB_id}` → 200 OK

### JWT validation
- Invalid signature → 401
- Expired token → 401
- Missing token → 401
- Wrong issuer → 401

### Role-based authorization
- `GET /api/v1/orders/admin/**` → chỉ ADMIN role
- `POST /api/v1/orders` → CUSTOMER hoặc ADMIN

### Input validation
- `quantity <= 0` → 400 (via `@Positive`)
- Missing `Idempotency-Key` → 400
- Invalid UUID → 400

### SQL injection prevention
- Spring Data JPA uses parameterized queries (PreparedStatement)
- No string concatenation in SQL queries

## Automated negative tests
`OrderSecurityTest` covers:
- BOLA: user A cannot access user B's order (403)
- BOLA: user A can access own order (200)
- RBAC: admin can access any order (200)
- Auth: createOrder creates order for authenticated user

## Runtime verification
```sql
-- Verify no cross-user access in orders table
SELECT o1.id, o1.user_id, o2.user_id
FROM orders o1, orders o2
WHERE o1.id = o2.id AND o1.user_id <> o2.user_id;
-- Kết quả phải rỗng
```

## Traffic ×10
Nếu traffic tăng 10 lần, cần:
- Thêm Redis-based rate limiting (P2)
- Tăng connection pool cho DB
- Thêm API Gateway với rate limiting per user
