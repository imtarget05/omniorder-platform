# Security Test Evidence: Authorization & OWASP Compliance

## Authentication
- **Password Hashing**: Argon2id (16 KiB memory, 32 hash length, 1 parallelism, 65536 memory, 3 iterations)
- **JWT**: RS256 (RSA 2048-bit key pair), 24-hour expiration
- **Token Storage**: Client-side (Bearer header), never server-side sessions

## Authorization (BOLA Protection)
| Scenario | Expected | Actual |
|----------|----------|--------|
| User A reads own order | 200 OK | 200 OK ✓ |
| User A reads User B's order | 403 Forbidden | TBD (gateway phase) |
| User A modifies User B's order | 403 Forbidden | TBD (gateway phase) |
| Unauthenticated request | 401 Unauthorized | 401 ✓ |
| Tampered JWT signature | 401 Unauthorized | TBD (gateway phase) |
| Expired JWT | 401 Unauthorized | 401 ✓ |

## OWASP Compliance
| OWASP Top 10 | Implementation |
|--------------|----------------|
| A01: Broken Access Control | Idempotency key per user+key; ownership checks in gateway (Phase 3) |
| A02: Cryptographic Failures | Argon2id for passwords, RSA-2048 for JWT, SHA-256 only for integrity hashing |
| A03: Injection | Parameterized queries (JPA/Hibernate), no string concatenation in SQL |
| A04: Insecure Design | Transactional outbox, state machine, bounded retries, DLQ |
| A05: Security Misconfiguration | CSRF disabled (stateless JWT), secure headers, no debug endpoints exposed |
| A06: Vulnerable Components | Dependency scanning in CI (Phase 4) |
| A07: Authentication Failures | Argon2id, JWT expiration, rate limiting on auth endpoints |
| A08: Software/Data Integrity Failures | Outbox pattern, idempotency keys, consumer deduplication |
| A09: Security Logging | Actuator endpoints, structured error responses |
| A10: SSRF | No outbound URL fetching from user input |

## Password Storage Verification
- `SHA-256` is used **only** for idempotency hash (integrity), **never** for passwords
- Passwords use `Argon2id` via Spring Security's `Argon2PasswordEncoder(16, 32, 1, 65536, 3)`

## Test Files
- `services/identity-service/src/test/java/com/omniorder/identity/service/AuthServiceTest.java`
- `services/identity-service/src/test/java/com/omniorder/identity/security/JwtServiceTest.java`
- `services/identity-service/src/test/java/com/omniorder/identity/controller/AuthControllerTest.java`
