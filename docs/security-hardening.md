# Security Hardening Checklist

- Terminate TLS 1.2+ at the ingress or reverse proxy.
- Override `JWT_SECRET` with at least 64 random bytes in every deployed environment.
- Keep application users non-root inside containers.
- Keep audit logs append-only at the application layer and restrict direct database write access.
- Run dependency and container scans in CI before production deployment.
- Verify 50-concurrent-user performance before UAT sign-off.
