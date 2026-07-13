-- V8: Audit log improvements + logo classpath update
-- Adds: description column to audit_log, proper indexes

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS description TEXT;

-- Rename columns to snake_case if they were created in camelCase (safety migration)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'audit_log' AND column_name = 'entitytype'
    ) THEN
        ALTER TABLE audit_log
            RENAME COLUMN entitytype  TO entity_type;
        ALTER TABLE audit_log
            RENAME COLUMN entityid    TO entity_id;
        ALTER TABLE audit_log
            RENAME COLUMN performedby TO performed_by;
        ALTER TABLE audit_log
            RENAME COLUMN performedat TO performed_at;
        ALTER TABLE audit_log
            RENAME COLUMN oldstate    TO old_state;
        ALTER TABLE audit_log
            RENAME COLUMN newstate    TO new_state;
    END IF;
END$$;

-- Indexes for common audit log query patterns
CREATE INDEX IF NOT EXISTS idx_audit_entity    ON audit_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_performed ON audit_log (performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor     ON audit_log (performed_by);
