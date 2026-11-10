# OmniOrder QA and Architecture Audit

**Audit date:** 2026-11-09  
**Scope:** Repository implementation, Maven modules, tests, Dockerfiles, GitLab pipeline, and Kubernetes staging templates. No deployment, remote operation, commit, or push was performed.

## Executive assessment

OmniOrder has implemented meaningful order, inventory, idempotency, outbox, Kafka, JWT, and Argon2 behavior. PostgreSQL/Testcontainers tests exercise real databases and Kafka. Several build and test defects were corrected during this audit, including missing outbox repository queries and a cross-service test dependency. The repository is **not production-ready**: the accepted architecture requires Oracle XE/Oracle schemas, while the implementation and migrations remain PostgreSQL-only; important recovery, performance, and security requirements lack current runnable evidence; legacy combined Kubernetes manifests and local Compose defaults need to be distinguished from the parameterized staging templates; the prior example key material has been removed. No environment deployment was performed, so production-deployed status is **no**.

## Acceptance and claim → evidence

| Claim / acceptance | Evidence inspected or run | Result |
|---|---|---|
| Build runs with the committed Maven wrapper | `./mvnw -v`; wrapper uses `MavenWrapperMain` and checksum-pinned Maven 3.9.9 | Pass after wrapper repair |
| No H2 for business logic tests | Module POMs and test resources; H2 dependencies/config removed | Pass by repository scan |
| PostgreSQL and Kafka integration use real containers | `InventoryServiceConcurrencyTest`, `InventoryServiceReservationTest`, Testcontainers PostgreSQL 15.4 + Kafka 7.5.0 | Pass: 3 test methods when run with Colima socket and Ryuk disabled |
| Identity behavior tests | `./mvnw -B -pl services/identity-service test` | Pass: 7 tests, 0 failures/errors |
| Targeted 1,000-request flash-sale acceptance | `DOCKER_HOST=unix:///Users/mainguyenbinhtan/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -B -pl services/order-service -am -Dtest=FlashSaleE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`; `services/order-service/target/surefire-reports/TEST-com.omniorder.order.service.FlashSaleE2ETest.xml` | PASS — covered in the full unfiltered Maven `verify` below; the focused run also passed (1 test, 0 failures/errors, 180.5 s). Checks all orders settle and confirmed count does not exceed stock. |
| Oracle production profile, schema migrations, Oracle XE verification | `services/*/src/main/resources/db/migration`, service POMs, application configs, Docker Compose | **Fail / not implemented.** Only PostgreSQL driver, PostgreSQL SQL migrations, and PostgreSQL configs exist. No Oracle driver/profile/migrations or Oracle Testcontainers test. Oracle XE image availability alone is not verification. |
| SonarQube quality gate | `.gitlab-ci.yml` `quality:sonarqube` requires `SONAR_HOST_URL` and `SONAR_TOKEN`, waits for server quality gate | Pipeline configured, but not executed; CI variables/server not available in this audit. |
| Vulnerability and secret scanning | `.gitlab-ci.yml` has fail-closed Dependency Check, Gitleaks, Trivy FS and image scan jobs | YAML parsed locally; scanners/registry jobs not executed. |
| Staging deployment and smoke | Staging job requires isolated Kubernetes and environment variables and is manual; no target was configured | Not deployed; no staging result claimed. |

## Architecture and data flow

The intended path is REST order creation → durable order plus outbox in one database transaction → Kafka `order.requested` → inventory atomic conditional stock update plus reservation/outbox → Kafka stock result → order state transition. The inventory gRPC call supplies reference price/product information; it is not the reservation authority. Identity issues RS256 JWTs and hashes passwords with Argon2id. Durable idempotency and event deduplication are database-backed.

This design uses local transactions and an outbox instead of distributed 2PC. It accepts eventual consistency and duplicate delivery, with retries and deduplication expected to handle publication/acknowledgement failures. The outbox repository lacked explicit JPQL update declarations for pending/published/retry operations; these have been added. The cross-service flash-sale test initially lacked its inventory test dependency and Spring context configuration. The real Kafka run exposed outbox JSON being published as a string, the test profile omitting the JSON serializer, a command/result topic loop, and stale `@Version` entity updates. Producers now publish typed contract events; command/result topics are distinct; duplicate stock consumption is safe; order state transitions use conditional SQL updates before loading the entity. The targeted 1,000-request run passes; full verify remains outstanding.

## Important implementation decisions and trade-offs

- **PostgreSQL is currently the only database runtime.** PostgreSQL Testcontainers meets the minimum C1 integration-test requirement. It does not prove the Oracle production decision in ADR-0001 and SPEC §3. Oracle-specific execution plans/indexes and Oracle-compatible Flyway migrations remain unverified. No broad SQL/schema conversion was attempted because it needs a deliberate migration plan and Oracle runtime verification.
- **No H2 fallback.** H2 dependencies and H2 test datasource declarations were removed so database tests use PostgreSQL containers or pure unit/slice tests.
- **Credentials are supplied externally.** Identity key material is now required through `JWT_PRIVATE_KEY_PEM` and `JWT_PUBLIC_KEY_PEM`; these are Spring Resource locations. The test private key file was removed and the JWT service test now creates an ephemeral keypair. Production database profiles require credentials through environment variables; disposable Testcontainers retain fixed container-only credentials.
- **CI deploys only isolated staging templates.** The manual job requires protected target/config variables and checks required pre-created database/JWT secrets. Legacy combined Kubernetes templates remain separate from the parameterized staging manifests and require reconciliation before use. Inline secret examples were removed.

## Documentation consistency findings

Several existing `docs/evidence/*.md` files describe scenarios or expected output without raw test reports or command logs. `docs/evidence/04-kafka-outbox-recovery.md` describes an Oracle transaction although the implementation currently runs PostgreSQL. `docs/evidence/06-database-performance.md` describes a 100,000-row data set as a plan, not measured evidence. `docs/evidence/05-security-test.md` includes a BOLA result marked TBD. Treat these as design notes or planned acceptance, not completed evidence. Existing narrative performance, security, and recovery claims need actual test reports before being presented as achieved.

## Failure modes and recovery evidence

| Failure scenario | Current mechanism | Audit status |
|---|---|---|
| Duplicate order request | Database uniqueness/idempotency hash, see `OrderService` and order migrations | Code exists; complete concurrent replay/mismatch matrix not established in this run |
| Oversell under concurrency | Conditional inventory stock update and reservation transaction | Inventory Testcontainers suite and focused 1,000-request end-to-end test pass. This is a bounded correctness scenario, not a throughput benchmark. |
| Kafka publish/ack loss | Durable outbox retry and consumer deduplication | Code paths exist; complete outage/replay/DLQ acceptance is not established by the passing tests listed here |
| Database unavailable during scheduled relay | Scheduler attempts persistence work; logs show connection failures during Testcontainers teardown | Tests pass but teardown noise exposes scheduler lifecycle/test isolation weakness; operational alerting/recovery needs a dedicated failure test |
| Oracle dialect/schema incompatibility | No Oracle profile/migration/runtime path currently exists | Unhandled; release blocker for the documented Oracle target |
| Missing CI secrets or cluster | Required-variable checks and manual staging gate | Fail-closed configuration exists; actual GitLab execution unavailable |

## Changes made during this audit

- Repaired Maven wrapper invocation and pinned distribution checksum/source.
- Removed H2 test dependencies/configuration; removed committed RSA private-key test fixture and changed JWT tests to generate key material in memory.
- Fixed inventory outbox repository query declarations and made the reservation release regression test use the required durable event identifier.
- Fixed the order security test’s missing inventory gRPC mock.
- Declared inventory-service as a test-scope dependency for the cross-service flash-sale test.
- Corrected Kafka outbox JSON-to-contract serialization, separated `stock.consume`/`stock.release` commands from `stock.consumed`/`stock.released` outcomes, and made order state transitions conditional and atomic.
- Fixed Docker build context and `.dockerignore` so Maven reactor modules and source files are available in image builds.
- Added JaCoCo reporting and Sonar Maven scanner metadata; configured CI SonarQube, dependency, secret, Trivy filesystem/image gates to fail closed.
- Reworked GitLab package/staging validation flow. No Git remote, push, deployment, or credential use occurred.

## Commands and results

- `./mvnw -v` — pass (Maven 3.9.9, Java 21).
- `./mvnw -B -pl services/identity-service test` — pass (7 tests; 0 failures, 0 errors).
- `DOCKER_HOST=unix:///Users/mainguyenbinhtan/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -B -pl services/inventory-service,services/order-service -am verify` — first attempt failed at order test compilation because `FlashSaleE2ETest` imported inventory classes without a test dependency; defect fixed afterward.
- `DOCKER_HOST=unix:///Users/mainguyenbinhtan/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -B -ntp verify` — PASS; Maven reactor `BUILD SUCCESS`; 19 current Surefire tests, 0 failures/errors/skips, including FlashSaleE2ETest 1/1. Scheduled tasks log PostgreSQL disconnects during Testcontainers shutdown; test reports remain green.
- Earlier focused FlashSaleE2ETest — PASS, 1/1, 0 failures/errors.
- `python3` YAML parse of `.gitlab-ci.yml` — pass; expected stages and jobs parsed.
- `docker compose config --quiet` — pass for root and infrastructure Compose files when supplied dummy environment values for required DB/JWT variables; this checks rendering only, not container startup.
- SonarQube, Gitleaks, Dependency Check, Trivy, Docker image push, staging rollout, smoke test, and Oracle XE verification — not run.

## Production readiness conclusion

**Production-ready: no. Production-deployed: no.** The implementation demonstrates important backend and cloud-native engineering patterns, but claims must remain bounded to passing tests and configured pipeline source. The central Oracle production requirement is not implemented, and there is no executed SonarQube gate, scanner report, registry image scan, staging smoke result, Oracle XE verification, or complete recovery/load evidence. Do not describe this repository as production-ready or Oracle-supported until those items are implemented and verified.

## Required follow-up before claiming completion

1. Decide and implement the Oracle production profile, service schemas, Oracle-compatible Flyway migrations and Oracle XE integration/plan checks while retaining PostgreSQL Testcontainers as the minimum CI database.
2. Configure a real SonarQube Server and protected CI variables; run and retain the quality-gate result. Execute the Trivy, Gitleaks, and dependency scans and resolve findings.
3. Add explicit Kafka outage, retry/DLQ, redelivery, compensation and reconciliation scenarios with retained Testcontainers evidence.
4. Retain current Surefire reports and add explicit recovery/load scenarios beyond the verified 19-test suite; the existing queue outage, redelivery, DLQ and reconciliation evidence remains incomplete.
5. Reconcile the legacy combined Kubernetes templates with the parameterized staging manifests; ensure required Kubernetes secrets are provisioned from a secret manager and confirm every deployable manifest matches current services.
6. Add Oracle and PostgreSQL migration compatibility tests, then validate staging only after an isolated Kubernetes namespace, registry, database, Kafka, and rotated credentials are configured.
