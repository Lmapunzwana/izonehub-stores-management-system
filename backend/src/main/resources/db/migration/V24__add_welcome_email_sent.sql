-- V24: Add welcome_email_sent flag to app_users (default true for existing users)
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS welcome_email_sent BOOLEAN NOT NULL DEFAULT true;
