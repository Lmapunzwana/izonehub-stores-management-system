# New Sahara Ventures — Stores Management System

Phase 2 inventory-core foundation for the stores management system. Phase 3 movement flows are intentionally not implemented until this inventory balance layer is reviewed and hardened.

## Modules

- `backend/` — Spring Boot 3.x, Java 21, PostgreSQL, Flyway, Spring Security, JWT, CQRS-oriented command services, incoming stock, GRN variance detection, overdue expected receipts, low-stock notifications.
- `frontend/` — React 18, TypeScript, Ant Design 5, TanStack Query, Zustand-ready shell.
- `k3s/` — starter Kubernetes manifests for k3s deployments.

## Local development

```bash
docker compose up --build
```

## Checks

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
```

## Image footprint

The local compose stack uses Alpine-based Postgres, Maven build, and JRE runtime images to reduce first-time Docker pulls for development.
