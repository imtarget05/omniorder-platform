# Observability & Incident Simulation (P1)

## Observability stack
- **Structured logs**: JSON format with correlation ID (traceId) propagated across services
- **Metrics**: Spring Boot Actuator + Micrometer, exposed at `/actuator/metrics` and `/actuator/prometheus`
- **Health**: `/actuator/health` with database and Kafka indicators
- **Dashboard**: Grafana (planned) querying Prometheus

## Key metrics to track
| Metric | Purpose |
|--------|---------|
| `http.server.requests` | Request count, duration, status by endpoint |
| `jvm.memory.used` | JVM heap usage |
| `hikaricp.connections.active` | DB connection pool usage |
| `kafka.consumer.lag` | Consumer lag per topic/partition |
| `order.outbox.pending` | Unpublished outbox events |
| `inventory.reservation.held` | Active stock reservations |

## Correlation ID
Each request gets a `X-Correlation-ID` header. Propagated to Kafka events and downstream gRPC calls. Enables distributed tracing.

## Controlled incident experiments

### Incident 1: Kafka unavailable
- **Symptoms**: Orders stuck in PENDING, outbox events accumulate
- **Root cause**: Kafka broker down
- **Recovery**: Restart Kafka, outbox relay republishes pending events
- **Prevention**: Outbox pattern ensures no event loss; retry with exponential backoff

### Incident 2: Inventory Service unavailable
- **Symptoms**: Orders remain PENDING, stock.reserved/stock.rejected not published
- **Root cause**: Inventory pod crash or network partition
- **Recovery**: Restart inventory-service, Kafka redelivery reprocesses pending events
- **Prevention**: Idempotent consumer prevents double processing

### Incident 3: Database connection exhaustion
- **Symptoms**: `Connection is not available` errors, timeouts
- **Root cause**: HikariCP pool exhausted under load
- **Recovery**: Increase `maximum-size`, restart service
- **Prevention**: Right-size connection pool, add read replicas

## Incident report template
```markdown
## Incident: [Title]
- **Timeline**: Start time, detection time, resolution time
- **Symptoms**: What users saw
- **Root cause**: Technical root cause
- **Recovery**: Steps taken to restore service
- **Prevention**: Changes to prevent recurrence
- **Lessons learned**: What went well, what didn't
```

## Alerting thresholds
- Error rate > 5% for 5 minutes
- P95 latency > 2 seconds for 10 minutes
- Kafka consumer lag > 1000 messages
- Outbox pending events > 100
- HikariCP active connections > 90% of max
