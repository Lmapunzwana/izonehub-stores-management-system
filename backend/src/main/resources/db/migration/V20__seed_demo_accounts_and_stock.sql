-- Demo Data Seeding
-- Adds Admin and Demo accounts with the password: password123
-- Hash: $2a$10$w0f5uN5t32h.5kH5E2l4T.bC2rE3pX02q0l4xW6hD3rI4l0vM9d2q

DO $$
DECLARE
  v_admin_id UUID := gen_random_uuid();
  v_central_mgr_id UUID := gen_random_uuid();
  v_site_mgr_id UUID := gen_random_uuid();
  v_central_store_id UUID;
  v_site_store_id UUID;
  v_item RECORD;
BEGIN
  -- 1. Create a Central Store if one doesn't exist
  SELECT id INTO v_central_store_id FROM store WHERE type = 'CENTRAL' LIMIT 1;
  IF v_central_store_id IS NULL THEN
    v_central_store_id := gen_random_uuid();
    INSERT INTO store (id, created_at, name, type, location, active) 
    VALUES (v_central_store_id, NOW(), 'Main Central Store', 'CENTRAL', 'Harare HQ', true);
  END IF;

  -- 2. Create a Site Store if one doesn't exist
  SELECT id INTO v_site_store_id FROM store WHERE type = 'SITE' LIMIT 1;
  IF v_site_store_id IS NULL THEN
    v_site_store_id := gen_random_uuid();
    INSERT INTO store (id, created_at, name, type, location, active) 
    VALUES (v_site_store_id, NOW(), 'Demo Site Store', 'SITE', 'Site A', true);
    
    INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) 
    VALUES (gen_random_uuid(), NOW(), 'PRJ-DEMO', 'Demo Project', v_site_store_id, 0, true);
  END IF;

  -- 3. Create Admin Account (admin@demo.com)
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'admin@demo.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked)
    VALUES (v_admin_id, NOW(), 'System Admin', 'admin@demo.com', '$2a$10$w0f5uN5t32h.5kH5E2l4T.bC2rE3pX02q0l4xW6hD3rI4l0vM9d2q', true, false, 0, false);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_admin_id, 'SYSTEM_ADMINISTRATOR');
  END IF;

  -- 4. Create Central Manager (central@demo.com)
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'central@demo.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id)
    VALUES (v_central_mgr_id, NOW(), 'Central Manager', 'central@demo.com', '$2a$10$w0f5uN5t32h.5kH5E2l4T.bC2rE3pX02q0l4xW6hD3rI4l0vM9d2q', true, false, 0, false, v_central_store_id);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_central_mgr_id, 'CENTRAL_STORE_MANAGER');
    UPDATE store SET manager_id = v_central_mgr_id WHERE id = v_central_store_id;
  END IF;

  -- 5. Create Site Manager (site@demo.com)
  IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'site@demo.com') THEN
    INSERT INTO app_users (id, created_at, full_name, email, password_hash, active, force_password_change, failed_login_attempts, locked, assigned_store_id)
    VALUES (v_site_mgr_id, NOW(), 'Site Manager', 'site@demo.com', '$2a$10$w0f5uN5t32h.5kH5E2l4T.bC2rE3pX02q0l4xW6hD3rI4l0vM9d2q', true, false, 0, false, v_site_store_id);
    
    INSERT INTO app_user_roles (user_id, role) VALUES (v_site_mgr_id, 'SITE_STORE_MANAGER');
    UPDATE store SET manager_id = v_site_mgr_id WHERE id = v_site_store_id;
  END IF;

  -- 6. Seed initial stock for all existing items into the Central Store
  FOR v_item IN SELECT id FROM item LOOP
    IF NOT EXISTS (SELECT 1 FROM store_inventory WHERE store_id = v_central_store_id AND item_id = v_item.id) THEN
      INSERT INTO store_inventory (id, created_at, store_id, item_id, quantity_on_hand, quantity_reserved, quantity_in_transit, quantity_frozen, last_updated)
      VALUES (gen_random_uuid(), NOW(), v_central_store_id, v_item.id, 500, 0, 0, 0, NOW());
    ELSE
      -- Top up stock if it already exists but is low
      UPDATE store_inventory SET quantity_on_hand = quantity_on_hand + 500 WHERE store_id = v_central_store_id AND item_id = v_item.id AND quantity_on_hand < 100;
    END IF;
  END LOOP;

END $$;
