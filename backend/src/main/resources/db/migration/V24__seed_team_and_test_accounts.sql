-- V24: Seed team members and test accounts and add welcome_email_sent flag

-- 1. Add welcome_email_sent column to app_users (default true for existing users)
ALTER TABLE app_users ADD COLUMN welcome_email_sent BOOLEAN DEFAULT true;

-- 2. Update Athlone store type to CENTRAL
UPDATE store SET type = 'CENTRAL' WHERE name = 'Athlone and Head Office';

-- 3. Seed users and update store manager associations
DO $$
DECLARE
  v_petronelah_id UUID := gen_random_uuid();
  v_artwell_id UUID := gen_random_uuid();
  v_benedict_id UUID := gen_random_uuid();
  v_ronald_id UUID := gen_random_uuid();
  v_leroy_site_id UUID := gen_random_uuid();
  v_leroy_central_id UUID := gen_random_uuid();
  
  v_athlone_store_id UUID := '1dc8d10f-cc5d-435a-b5f1-d57c27a0c5bf';
  v_sabi_store_id UUID := '1dc4f6ea-2071-4777-9f0b-7c722373ed66';
  v_sunports_store_id UUID := '5333d6da-8398-4759-be7c-d02ace6fa444';
  v_murombedzi_store_id UUID := 'e8031e79-9f38-4b4c-93ad-07e2864dcc41';
  
  -- BCrypt hash of 'password123'
  v_pwd_hash VARCHAR := '$2b$10$/ovhITo0GKrB80taBhejiO3tJqABy3w6fSJd4ap1vv/DcqIf0CVyi';
BEGIN
  -- Petronelah Majaja
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'petronelah@newsaharaventures.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_petronelah_id, NOW(), 'Petronelah Majaja', 'petronelah@newsaharaventures.com', v_pwd_hash, true, true, 0, false, v_athlone_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_petronelah_id, 'CENTRAL_STORE_MANAGER');
    UPDATE store SET manager_id = v_petronelah_id WHERE id = v_athlone_store_id;
  END IF;

  -- Artwell Kanjanda
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'artwell@newsaharaventures.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_artwell_id, NOW(), 'Artwell Kanjanda', 'artwell@newsaharaventures.com', v_pwd_hash, true, true, 0, false, v_sabi_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_artwell_id, 'SITE_STORE_MANAGER');
    UPDATE store SET manager_id = v_artwell_id WHERE id = v_sabi_store_id;
  END IF;

  -- Benedict Kwashira
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'benedict@newsaharaventures.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_benedict_id, NOW(), 'Benedict Kwashira', 'benedict@newsaharaventures.com', v_pwd_hash, true, true, 0, false, v_sunports_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_benedict_id, 'SITE_STORE_MANAGER');
    UPDATE store SET manager_id = v_benedict_id WHERE id = v_sunports_store_id;
  END IF;

  -- Ronald Tsatsi
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'ronald@newsaharaventures.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_ronald_id, NOW(), 'Ronald Tsatsi', 'ronald@newsaharaventures.com', v_pwd_hash, true, true, 0, false, v_murombedzi_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_ronald_id, 'SITE_STORE_MANAGER');
    UPDATE store SET manager_id = v_ronald_id WHERE id = v_murombedzi_store_id;
  END IF;

  -- Leroy Mapunzwana (Site Manager Test)
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'lmapunzwana@talksal.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_leroy_site_id, NOW(), 'Leroy Mapunzwana', 'lmapunzwana@talksal.com', v_pwd_hash, true, true, 0, false, v_sabi_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_leroy_site_id, 'SITE_STORE_MANAGER');
  END IF;

  -- Leroy Mapunzwana (Central Manager Test)
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'leroymapunzwana@gmail.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id, welcome_email_sent)
    VALUES (v_leroy_central_id, NOW(), 'Leroy Mapunzwana', 'leroymapunzwana@gmail.com', v_pwd_hash, true, true, 0, false, v_athlone_store_id, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_leroy_central_id, 'CENTRAL_STORE_MANAGER');
  END IF;

END $$;
