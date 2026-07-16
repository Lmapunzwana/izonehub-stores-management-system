-- V23: Update demo users to real email addresses for live testing

-- Update System Admin
UPDATE app_users 
SET email = 'lmapunzwana@gmail.com', 
    full_name = 'Leroy Mapunzwana'
WHERE email = 'admin@demo.com';

-- Update Central Manager
UPDATE app_users 
SET email = 'ronaldt.tsatsi@gmail.com', 
    full_name = 'Ronald Tsatsi'
WHERE email = 'central@demo.com';

-- Update Site Manager
UPDATE app_users 
SET email = 'takudzwachitsungo2@gmail.com', 
    full_name = 'Takudzwa Chitsungo'
WHERE email = 'site@demo.com';

-- Update Audit Logs for consistency with the new names
UPDATE audit_log SET performed_by = 'System Admin (Lmapunzwana)' WHERE performed_by = 'System Admin';
UPDATE audit_log SET performed_by = 'Central Manager (Ronald)' WHERE performed_by = 'Central Manager';
UPDATE audit_log SET performed_by = 'Site Manager (Takudzwa)' WHERE performed_by = 'Site Manager';
