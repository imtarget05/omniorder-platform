# OmniOrder Platform — Agent Instructions

## Build Commands
```bash
# Set Java 21
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jdk-21.0.13+9/Contents/Home
export PATH=~/opt/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH

# For Colima Docker (macOS)
export DOCKER_HOST=unix:///$HOME/.colima/default/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true

# Compile all modules
./mvnw compile

# Run all tests
./mvnw test

# Run specific module tests
./mvnw -pl services/order-service test
./mvnw -pl services/inventory-service test
./mvnw -pl services/identity-service test

# Install to local repo
./mvnw install -DskipTests
```

## Module Structure
- `platform-bom` — Dependency versions (import in all service POMs)
- `contracts/events` — Shared Kafka event POJOs (used by order + inventory)
- `services/identity-service` — Auth, JWT RS256, Argon2id
- `services/order-service` — Orders, idempotency, outbox, state machine
- `services/inventory-service` — Products, atomic stock, Kafka consumer
- `services/gateway` — API Gateway (Phase 3)
- `services/notification-worker` — Kafka notifications (Phase 2)

## Key Design Patterns
- **Transactional Outbox**: Order + outbox row in same DB transaction
- **Idempotency Key**: Unique DB constraint prevents duplicate orders
- **Atomic Stock Update**: `UPDATE ... WHERE stock >= qty` prevents oversell
- **Consumer Deduplication**: `processed_events` table prevents double-processing

## Test Infrastructure
- PostgreSQL 15.4 via Testcontainers for integration tests
- Kafka via Testcontainers for event-driven tests
- Spring Boot 3.5.16, Java 21

## Documentation
- `docs/evidence/` — Test evidence for interviews (concurrency, idempotency, etc.)
- `docs/architecture/adr/` — Architecture Decision Records
- `CONSTRAINTS.md` — Hard requirements that must be met

## CI/CD
- `.gitlab-ci.yml` — 5-stage pipeline (verify, security, package, deploy, validate)
- `infrastructure/docker/compose.yaml` — Local Docker Compose stack
- `infrastructure/kubernetes/deployments.yaml` — K8s manifests
