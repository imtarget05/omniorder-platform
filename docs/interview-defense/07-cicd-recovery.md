# Interview Defense — CI/CD, Deployment & Recovery

## Business problem
Cần chứng minh khả năng triển khai an toàn, phát hiện lỗi sớm và rollback nhanh khi có bad deployment.

## Pipeline stages
| Stage | Job | Purpose |
|-------|-----|---------|
| verify | compile | Build all modules |
| verify | test | Run P0 tests on PostgreSQL + Kafka Testcontainers |
| security | sast | Semgrep static analysis |
| security | dependency-scan | OWASP Dependency-Check |
| security | secret-scan | Gitleaks secret detection |
| package | image | Build Docker images, tag by commit SHA |
| deploy | staging | Deploy to Kubernetes staging |
| validate | smoke-test | Smoke tests against staging |

## Bad deployment scenario (controlled)
1. Deploy working version → smoke tests pass
2. Deploy version with intentional bug (e.g., division by zero in order confirmation)
3. Quality gate detects: health check fails, smoke test returns 500
4. Rollback: `kubectl rollout undo` to previous revision
5. Verify: API healthy again after rollback

## Immutable tags
Images tagged with `$CI_COMMIT_SHORT_SHA` — never use `latest` in production. This enables deterministic rollback.

## Secret management
- DB credentials in Kubernetes Secrets (not in code)
- JWT keys in Kubernetes Secrets (not committed to repo)
- CI/CD variables for registry credentials

## Rollback tested
- `kubectl rollout undo deployment/<service>` reverts to previous ReplicaSet
- Pods restart with previous image version
- Health checks pass after rollback

## Related code
- `.gitlab-ci.yml`
- `infrastructure/kubernetes/*.yaml`
- `docs/evidence/07-cicd-recovery.md`

## Traffic ×10
- Add HorizontalPodAutoscaler (HPA) based on CPU/memory
- Add read replicas for PostgreSQL
- Use connection pooling (PgBouncer) between services and DB

## Follow-up questions & answers
1. **Tại sao không dùng tag `latest`?** → `latest` là mutable, không thể rollback xác định. Commit SHA là immutable.
2. **Rollback mất bao lâu?** → Thời gian kubectl rollout undo + pod restart (thường < 30s).
3. **Secret được quản lý thế nào?** → Kubernetes Secrets, không commit vào repo. JWT private key đã bị xóa khỏi repo.
4. **Quality gate phát hiện lỗi thế nào?** → Health check fails, smoke test returns non-200, pipeline stops.
5. **Làm sao biết deployment thành công?** → `kubectl rollout status` thành công + smoke tests pass.
