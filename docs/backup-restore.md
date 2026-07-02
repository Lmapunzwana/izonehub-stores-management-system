# PostgreSQL Backup and Restore Procedure

## Backup schedule

- Run `pg_dump` daily outside business hours.
- Retain encrypted backups for 30 days.
- Store backups outside the application droplet.

## Restore verification

Perform a monthly restore drill into a disposable PostgreSQL instance:

```bash
pg_restore --clean --if-exists --dbname="$RESTORE_DATABASE_URL" latest.dump
```

Verify the restored database by running Flyway validation and a read-only smoke test against inventory, audit log, and user tables.
