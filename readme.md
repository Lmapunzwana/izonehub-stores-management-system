# New Sahara Ventures — Stores Management System

Phase 0 foundation for the stores management system.

## Modules

- `backend/` — Spring Boot 3.x, Java 21, PostgreSQL, Flyway, Spring Security, JWT, CQRS-oriented command services.
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
