# Deployment Runbook & Rollback (P1)

## Overview
OmniOrder platform deployed on Kubernetes with 3 services: identity-service, order-service, inventory-service.

## Prerequisites
- `kubectl` configured to target staging cluster
- Docker registry credentials in CI/CD variables
- Namespace `omniorder` created

## Deploy to Staging
```bash
# Apply namespace and secrets
kubectl apply -f infrastructure/kubernetes/namespace.yaml

# Deploy services
kubectl apply -f infrastructure/kubernetes/identity-service.yaml
kubectl apply -f infrastructure/kubernetes/order-service.yaml
kubectl apply -f infrastructure/kubernetes/inventory-service.yaml

# Verify rollout
kubectl rollout status deployment/order-service --namespace=omniorder --timeout=300s
kubectl rollout status deployment/inventory-service --namespace=omniorder --timeout=300s
kubectl rollout status deployment/identity-service --namespace=omniorder --timeout=300s
```

## Health Checks
```bash
# Check all pods
kubectl get pods -n omniorder

# Check service endpoints
kubectl get svc -n omniorder

# Check readiness/liveness
kubectl describe pod <pod-name> -n omniorder

# Port-forward for local testing
kubectl port-forward svc/order-service 8082:8082 -n omniorder
curl http://localhost:8082/actuator/health
```

## Rollback Procedure
```bash
# Rollback all deployments to previous revision
kubectl rollout undo deployment/order-service -n omniorder
kubectl rollout undo deployment/inventory-service -n omniorder
kubectl rollout undo deployment/identity-service -n omniorder

# Verify rollback
kubectl rollout history deployment/order-service -n omniorder
kubectl rollout status deployment/order-service -n omniorder --timeout=300s
```

## When to Rollback
- Error rate > 5% after deployment
- Health checks failing
- Smoke tests failing
- Kafka consumer lag increasing unbounded
- Database connection pool exhaustion

## Post-Deployment Verification
1. `GET /actuator/health` returns UP for all services
2. `POST /api/v1/orders` with valid JWT returns 201
3. Idempotency: same `Idempotency-Key` returns same order
4. Overselling: stock never goes negative under concurrent load
5. Kafka consumer lag is stable

## Smoke Test Script
```bash
TOKEN=$(curl -s -X POST http://identity-service:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}' | jq -r .token)

ORDER_ID=$(curl -s -X POST http://order-service:8082/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: smoke-test-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{"productId":"...","quantity":1,"unitPrice":10.00}' | jq -r .id)

curl -s http://order-service:8082/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $TOKEN"
```
