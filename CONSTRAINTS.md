# OmniOrder Platform Constraints

Hard constraints that must be satisfied for the repository to be considered complete. These map directly to JD requirements and interview evidence.

## Database Constraints

### C1: No H2 replacing Oracle
- Integration tests must run against PostgreSQL (via Testcontainers) at minimum
- Oracle-specific features (execution plans, indexes) must be verified locally against Oracle XE
- H2 is not acceptable for business logic tests due to dialect mismatch

### C2: No mock Kafka
- All Kafka interaction tests must use Testcontainers Kafka (real broker)
- Consumer deduplication must be tested with real message redelivery
- Outbox pattern must be tested with real Kafka producer/consumer

### C3: No in-memory idempotency/stock
- Idempotency keys must be stored durably in database with unique constraint
- Stock reservation must use atomic conditional UPDATE in database
- No `ConcurrentHashMap`, `AtomicInteger`, or similar in-memory substitutes

## Code Quality Constraints

### C4: Every business rule must have unit + integration tests
- Unit tests (JUnit 5 + Mockito) for pure logic without DB
- Integration tests (Testcontainers) for DB + Kafka interaction

### C5: No committed secrets
- RSA private keys must NOT be committed to git
- Database passwords must NOT be in plain text in repo
- Use environment variables or Docker secrets for production

## Performance Constraints

### C6: No fabricated benchmark numbers
- All performance claims must be backed by `docs/evidence/` with real test results
- p95, throughput, error rate must be measured, not estimated
- Concurrency test must demonstrate actual 1000-request behavior

## Security Constraints

### C7: Password hashing must be Argon2id, not SHA-256
- SHA-256 is only acceptable for data integrity hashing (idempotency keys)
- Passwords must use Argon2id via `Argon2PasswordEncoder`

### C8: JWT must use RS256 (RSA), not HS256
- Public key verification without exposing private key
- Key rotation support

## Definition of Done

A task is DONE only when:
- [ ] Code compiles with `./mvnw compile`
- [ ] Unit tests pass (`./mvnw test` with JUnit 5 + Mockito)
- [ ] Integration tests pass against real infrastructure (Testcontainers)
- [ ] Evidence documented in `docs/evidence/`
- [ ] ADR updated if architectural decision changed
- [ ] No `TODO` markers for incomplete implementation
- [ ] No compilation warnings for the module
