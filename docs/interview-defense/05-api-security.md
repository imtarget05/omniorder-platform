# Interview Defense — API Security

## Business problem
User A không thể xem hoặc sửa đơn hàng của User B (BOLA — OWASP API Security Top 10 #1). JWT phải được validate (issuer, expiry, signature).

## Initial implementation (failure mode)
Không có authorization check trong controller. Mọi user đã authenticate có thể truy cập bất kỳ đơn hàng nào bằng cách guess order ID.

## How to reproduce
Gọi `GET /api/v1/orders/{orderB_id}` với JWT của user A. Nếu không có BOLA check, sẽ trả về 200 với dữ liệu đơn hàng của user B.

## Root cause
Không có kiểm tra ownership của order trước khi trả về dữ liệu. Authentication (đã đăng nhập) ≠ Authorization (có quyền truy cập đối tượng này).

## Technical options considered
1. **Controller-level check (chosen)**: Trong `getOrder()`, so sánh `order.getUserId()` với `userId` từ JWT claim. Nếu khác và không phải admin → `AccessDeniedException` (403).
2. **Spring Security `@PreAuthorize`**: Có thể dùng `@PreAuthorize("#orderId == authentication.principal.attributes['userId']")`. Nhưng cần load order trước khi check, phức tạp hơn.
3. **Repository-level filter**: Thêm `user_id = ?` vào mọi query. Không linh hoạt cho admin access.

## Why chosen
Controller-level check đơn giản, rõ ràng, dễ test. Admin có thể bypass qua `isAdmin` check.

## Trade-offs / limitations
- Mỗi query getOrder phải load order trước rồi mới check ownership (một query bổ sung).
- Không có rate limiting trên auth endpoints (P2).

## Related code
- `services/order-service/src/main/java/com/omniorder/order/controller/OrderController.java:getOrder()`
- `services/order-service/src/main/java/com/omniorder/order/exception/GlobalExceptionHandler.java:handleAccessDenied()`
- `services/order-service/src/main/java/com/omniorder/order/security/SecurityConfig.java`
- `services/order-service/src/test/java/com/omniorder/order/controller/OrderSecurityTest.java`

## Test results
4/4 OrderSecurityTest pass:
- User A cannot access User B's order → 403
- User A can access own order → 200
- Admin can access any order → 200
- Create order for authenticated user → 200

## Traffic ×10
- Thêm Redis-based rate limiting per user ID
- Thêm API Gateway với rate limiting
- Cache JWT validation result (short TTL)

## Follow-up questions & answers
1. **Authentication vs Authorization là gì khác?** → Authentication = ai bạn (JWT valid). Authorization = bạn được làm gì (BOLa check, role).
2. **Tại sao không dùng `@PreAuthorize`?** → Controller-level check đơn giản hơn và dễ test với MockMvc.
3. **SQL injection có thể xảy ra ở đâu?** → Không có, vì Spring Data JPA dùng parameterized queries.
4. **JWT RS256 verify như thế nào?** → Resource server dùng public key từ identity-service để verify signature.
5. **Token hết hạn thì sao?** → Spring Security OAuth2 Resource Server tự động reject expired JWT (401).
