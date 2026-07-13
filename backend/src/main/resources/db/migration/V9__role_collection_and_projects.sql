-- 1. Migrate Roles
CREATE TABLE app_user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id),
    role VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, role)
);
INSERT INTO app_user_roles (user_id, role) SELECT id, role FROM app_users WHERE role IS NOT NULL;
ALTER TABLE app_users DROP COLUMN role;

-- 2. Project Employees
CREATE TABLE project_employees (
    project_id UUID NOT NULL REFERENCES project(id),
    app_user_id UUID NOT NULL REFERENCES app_users(id),
    PRIMARY KEY (project_id, app_user_id)
);

-- 3. Store Inventory Reserved Quantity
ALTER TABLE store_inventory ADD COLUMN quantity_reserved DECIMAL(19,4) DEFAULT 0 NOT NULL;

-- 4. Projects FK on Material Request & Issue Vouchers
ALTER TABLE material_request ADD COLUMN project_id UUID REFERENCES project(id);

-- Ensure a default project exists to link old records
DO $$
DECLARE
  v_store_id UUID;
  v_default_prj_id UUID;
BEGIN
  SELECT id INTO v_store_id FROM store LIMIT 1;
  IF v_store_id IS NOT NULL THEN
     IF NOT EXISTS (SELECT 1 FROM project WHERE code = 'DEFAULT-PRJ') THEN
        INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active)
        VALUES (gen_random_uuid(), current_timestamp, 'DEFAULT-PRJ', 'Default Project', v_store_id, 0, true);
     END IF;
     SELECT id INTO v_default_prj_id FROM project WHERE code = 'DEFAULT-PRJ';
     
     UPDATE material_request SET project_id = v_default_prj_id WHERE project_id IS NULL;
  END IF;
END $$;

ALTER TABLE material_request ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE material_request DROP COLUMN project_code;

ALTER TABLE material_issue_voucher ADD COLUMN project_id UUID REFERENCES project(id);

DO $$
DECLARE
  v_default_prj_id UUID;
BEGIN
  SELECT id INTO v_default_prj_id FROM project WHERE code = 'DEFAULT-PRJ';
  IF v_default_prj_id IS NOT NULL THEN
     UPDATE material_issue_voucher SET project_id = v_default_prj_id WHERE project_id IS NULL;
  END IF;
END $$;

ALTER TABLE material_issue_voucher ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE material_issue_voucher DROP COLUMN project_code;

-- 5. Discrepancy GRN linkage
ALTER TABLE discrepancy ADD COLUMN grn_id UUID REFERENCES goods_received_note(id);
ALTER TABLE discrepancy ALTER COLUMN receipt_id DROP NOT NULL;
