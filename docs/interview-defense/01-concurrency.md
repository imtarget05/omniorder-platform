# Interview Defense — Concurrency / Overselling

## Business problem
Flash sale: 5 sản phẩm, 1.000 khách mua đồng thời. Nếu không kiểm soát, hệ thống sẽ bán 6+ sản phẩm khi chỉ còn 5 (overselling), gây mất tiền và nỗi lòng khách hàng.

## Initial implementation (failure mode)
Ban đầu có thể dùng `SELECT available_stock` rồi `UPDATE available_stock = available_stock - 1`. Hai request cùng đọc `available_stock = 5`, cùng quy điều kiện đủ hàng, cùng trừ → tồn kho bị âm, overselling.

## How to reproduce
Chạy `InventoryServiceConcurrencyTest`: 1.000 thread gọi `decrementStock` cùng lúc trên product stock=5. Trước fix: total success > 5, available_stock < 0.

## Root cause
Check-then-act không atomic. Hai transaction có thể cùng pass điều kiện `available_stock >= qty` trước khi commit.

## Technical options considered
1. **Redis distributed lock**: lock key `product:{id}`, chỉ một thread xử lý mỗi lần. Nhưng Redis không phải source of truth; nếu Redis mất dữ liệu hoặc crash, lock bị lost → overselling. Cũng thêm độ phức tạp vận hành.
2. **Optimistic locking (version)**: `UPDATE ... WHERE version = :expectedVersion`. Cần retry ở application layer, phức tạp và có thể livelock dưới tải cao.
3. **Atomic conditional update (chosen)**: `UPDATE ... WHERE available_stock >= :qty`. Database đảm bảo atomicity qua row-level lock. `CHECK (available_stock >= 0)` là lớp bảo vệ cuối cùng.

## Why chosen
PostgreSQL là source of truth cho tồn kho. Atomic conditional update đơn giản, đúng, và không phụ thuộc vào dịch vụ bên thứ hai. `CHECK constraint` đảm bảo invariant ngay cả khi có bug application.

## Trade-offs / limitations
- Row-level lock có thể gây contention nếu tất cả request tập trung vào một product. Có thể giảm bằng batch reservation hoặc sharding stock.
- `decrementStock` trả về số row affected; nếu 0 → hết hàng, không cần đọc lại stock.

## Related code
- `services/inventory-service/src/main/java/com/omniorder/inventory/repository/ProductRepository.java:decrementStock`
- `services/inventory-service/src/main/resources/db/migration/V1__init.sql` (CHECK constraint)
- `services/inventory-service/src/test/java/com/omniorder/inventory/service/InventoryServiceConcurrencyTest.java`

## Test results
Test ch.Assert: tối đa 5 decrement thành công, available_stock >= 0, chạy ổn định nhiều lần.

## Traffic ×10
Nếu traffic tăng 10 lần: tăng HikariCP connection pool, thêm chỉ số `products(id)` (đã là PK), cân nhắc partitioning stock hoặc batch reservation để giảm row lock contention.

## Follow-up questions & answers
1. **Tại sao không dùng Redis lock?** → Redis không phải source of truth, mất dữ liệu là mất lock, dẫn đến overselling. Database là source of truth.
2. **CHECK constraint đủ chưa?** → Không đủ một mình, cần atomic UPDATE để ngăn race condition. CHECK là lớp cuối cùng.
3. **Transaction isolation level?** → Mặc định READ COMMITTED của PostgreSQL đủ vì UPDATE lấy row lock.
4. **Deadlock có thể xảy ra không?** → Có thể nếu cập nhật nhiều product cùng transaction. Tránh bằng cách chỉ cập nhật một product mỗi transaction.
5. **Làm sao kiểm tra tồn kho không âm?** → Query `SELECT available_stock FROM products WHERE available_stock < 0` phải trả về rỗng.
