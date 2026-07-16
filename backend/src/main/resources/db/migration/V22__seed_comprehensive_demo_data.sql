-- V22: Comprehensive Demo Data Seeding
-- This script creates a rich, lived-in history of operations for demo purposes.

DO $$
DECLARE
    -- Users
    v_admin_id UUID;
    v_central_mgr_id UUID;
    v_site_mgr_id UUID;
    
    -- Stores
    v_central_store_id UUID;
    v_site_store_id UUID;
    v_closed_site_store_id UUID;
    
    -- Projects
    v_active_project_id UUID;
    v_closed_project_id UUID;
    v_proj_id UUID;
    
    -- Items array for random selection
    v_items UUID[];
    v_item_id UUID;
    
    -- Items
    v_item_cement UUID := gen_random_uuid();
    v_item_timber UUID := gen_random_uuid();
    v_item_steel UUID := gen_random_uuid();
    v_item_ppe UUID := gen_random_uuid();
    v_item_drill UUID := gen_random_uuid();
    v_item_cable UUID := gen_random_uuid();
    
    -- Loop vars
    i INT;
    v_timestamp TIMESTAMP;
    
    -- Trans IDs
    v_req_id UUID;
    v_cons_id UUID;
    v_grn_id UUID;
    v_count_id UUID;
    
    v_qty INT;
BEGIN
    -- 1. Look up existing core demo data
    SELECT id INTO v_admin_id FROM app_users WHERE email = 'admin@demo.com' LIMIT 1;
    SELECT id INTO v_central_mgr_id FROM app_users WHERE email = 'central@demo.com' LIMIT 1;
    SELECT id INTO v_site_mgr_id FROM app_users WHERE email = 'site@demo.com' LIMIT 1;
    
    SELECT id INTO v_central_store_id FROM store WHERE type = 'CENTRAL' AND active = true LIMIT 1;
    SELECT id INTO v_site_store_id FROM store WHERE type = 'SITE' AND active = true LIMIT 1;

    -- Exit gracefully if core data is missing
    IF v_central_store_id IS NULL OR v_admin_id IS NULL THEN
        RAISE NOTICE 'Core demo data not found. Skipping V22 comprehensive seed.';
        RETURN;
    END IF;

    -- Fallback: if no active site store exists, create one
    IF v_site_store_id IS NULL THEN
        v_site_store_id := gen_random_uuid();
        INSERT INTO store (id, created_at, name, type, location, active, manager_id)
        VALUES (v_site_store_id, NOW(), 'Main Demo Site', 'SITE', 'Primary Construction Site', true, v_site_mgr_id);
    END IF;

    -- 2. Create additional items for a richer inventory
    INSERT INTO item (id, created_at, code, name, description, unit_of_measure, category, reorder_threshold, active)
    VALUES 
        (v_item_cement, NOW(), 'CEM-50KG', 'Portland Cement 50kg', 'Standard construction cement', 'Bag', 'CONSTRUCTION', 100, true),
        (v_item_timber, NOW(), 'TMB-2X4', 'Timber 2x4 3m', 'Treated structural timber', 'Piece', 'CONSTRUCTION', 50, true),
        (v_item_steel, NOW(), 'STL-12MM', 'Steel Rebar 12mm x 6m', 'Deformed steel reinforcement bar', 'Piece', 'CONSTRUCTION', 200, true),
        (v_item_ppe, NOW(), 'PPE-GLV', 'Safety Gloves Heavy Duty', 'Leather reinforced safety gloves', 'Pair', 'SAFETY', 20, true),
        (v_item_drill, NOW(), 'TL-DRL', 'Cordless Drill 18V', 'Industrial cordless power drill', 'Set', 'TOOL', 5, true),
        (v_item_cable, NOW(), 'CBL-2.5', 'Copper Cable 2.5mm Twin & Earth', '100m roll electrical cable', 'Roll', 'ELECTRICAL', 10, true)
    ON CONFLICT (code) DO NOTHING;

    -- Re-fetch IDs just in case they already existed
    SELECT id INTO v_item_cement FROM item WHERE code = 'CEM-50KG';
    SELECT id INTO v_item_timber FROM item WHERE code = 'TMB-2X4';
    SELECT id INTO v_item_steel FROM item WHERE code = 'STL-12MM';
    SELECT id INTO v_item_ppe FROM item WHERE code = 'PPE-GLV';
    SELECT id INTO v_item_drill FROM item WHERE code = 'TL-DRL';
    SELECT id INTO v_item_cable FROM item WHERE code = 'CBL-2.5';
    
    v_items := ARRAY[v_item_cement, v_item_timber, v_item_steel, v_item_ppe, v_item_drill, v_item_cable];

    -- 3. Create a historical closed project and store
    v_closed_site_store_id := gen_random_uuid();
    INSERT INTO store (id, created_at, name, type, location, active, closing)
    VALUES (v_closed_site_store_id, NOW() - INTERVAL '100 days', 'Downtown Plaza Site', 'SITE', 'City Center', false, false);
    
    v_closed_project_id := gen_random_uuid();
    INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active)
    VALUES (v_closed_project_id, NOW() - INTERVAL '100 days', 'PRJ-OLD-1', 'Downtown Plaza Renovation', v_closed_site_store_id, 250000, false);

    -- 4. Get active project for the demo site store, or create one if missing
    SELECT id INTO v_active_project_id FROM project WHERE site_store_id = v_site_store_id LIMIT 1;
    IF v_active_project_id IS NULL THEN
        v_active_project_id := gen_random_uuid();
        INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active)
        VALUES (v_active_project_id, NOW(), 'PRJ-MAIN', 'Main Infrastructure Project', v_site_store_id, 500000, true);
    END IF;

    -- 5. Seed Inventory Levels
    FOR i IN 1..array_length(v_items, 1) LOOP
        INSERT INTO store_inventory (id, created_at, store_id, item_id, quantity_on_hand, quantity_reserved, quantity_in_transit, quantity_frozen, last_updated)
        VALUES (gen_random_uuid(), NOW(), v_central_store_id, v_items[i], 1000 + (random() * 2000)::int, 0, 0, 0, NOW())
        ON CONFLICT (store_id, item_id) DO UPDATE SET quantity_on_hand = store_inventory.quantity_on_hand + 1000;
        
        INSERT INTO store_inventory (id, created_at, store_id, item_id, quantity_on_hand, quantity_reserved, quantity_in_transit, quantity_frozen, last_updated)
        VALUES (gen_random_uuid(), NOW(), v_site_store_id, v_items[i], 50 + (random() * 150)::int, 0, 0, 0, NOW())
        ON CONFLICT (store_id, item_id) DO UPDATE SET quantity_on_hand = store_inventory.quantity_on_hand + 50;
    END LOOP;

    -- 6. MASSIVE SIMULATION LOOP (Last 60 days)
    FOR i IN 1..40 LOOP
        v_timestamp := NOW() - (60 - i) * INTERVAL '1 day' - (random() * 12) * INTERVAL '1 hour';
        v_req_id := gen_random_uuid();
        v_item_id := v_items[1 + (random() * 5)::int];
        v_qty := 10 + (random() * 50)::int;
        
        -- A. Create Material Request (Site requests from Central)
        INSERT INTO material_request (id, created_at, requesting_store_id, source_store_id, project_id, status, raised_by_id, approved_by_id)
        VALUES (v_req_id, v_timestamp, v_site_store_id, v_central_store_id, v_active_project_id, 
            CASE 
                WHEN i > 35 THEN 'PENDING_APPROVAL' 
                WHEN i = 35 THEN 'APPROVED'
                ELSE 'COMPLETED' 
            END, v_site_mgr_id, v_central_mgr_id);
            
        INSERT INTO material_request_line (id, created_at, material_request_id, item_id, requested_quantity, approved_quantity, dispatched_quantity, received_quantity)
        VALUES (gen_random_uuid(), v_timestamp, v_req_id, v_item_id, v_qty, 
            CASE WHEN i <= 35 THEN v_qty ELSE 0 END,
            CASE WHEN i < 35 THEN v_qty ELSE 0 END,
            CASE WHEN i <= 30 THEN v_qty ELSE 0 END);
        
        INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
        VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'CREATE', 'MATERIAL_REQUEST', v_req_id::text, 'Created material request');

        -- B. If Dispatched
        IF i < 35 THEN
            v_timestamp := v_timestamp + INTERVAL '12 hours';
            INSERT INTO dispatch (id, created_at, material_request_id, dispatched_by_id, collector_name, collector_employee_id, dispatched_at)
            VALUES (gen_random_uuid(), v_timestamp, v_req_id, v_central_mgr_id, 'Truck Driver Bob', 'EMP-001', v_timestamp);

            INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
            VALUES (gen_random_uuid(), v_timestamp, v_central_mgr_id, 'Central Manager', 'DISPATCH', 'MATERIAL_REQUEST', v_req_id::text, 'Dispatched materials to site');

            -- C. If Received
            IF i <= 30 THEN
                v_timestamp := v_timestamp + INTERVAL '1 day';
                INSERT INTO receipt (id, created_at, material_request_id, received_by_id, received_at, status)
                VALUES (gen_random_uuid(), v_timestamp, v_req_id, v_site_mgr_id, v_timestamp, 'COMPLETED');

                INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
                VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'RECEIVE', 'MATERIAL_REQUEST', v_req_id::text, 'Confirmed receipt of materials');

                -- D. Simulate Consumption (MIV) out of site inventory
                IF (i % 3 = 0) THEN
                    v_cons_id := gen_random_uuid();
                    v_timestamp := v_timestamp + INTERVAL '2 days';
                    INSERT INTO material_issue_voucher (id, created_at, reference_number, store_id, project_id, issued_by_id, issued_at, status)
                    VALUES (v_cons_id, v_timestamp, 'MIV-SIM-' || i, v_site_store_id, v_active_project_id, v_site_mgr_id, v_timestamp, 'ACTIVE');
                    
                    INSERT INTO miv_line (id, created_at, miv_id, item_id, issued_quantity, returned_quantity)
                    VALUES (gen_random_uuid(), v_timestamp, v_cons_id, v_item_id, v_qty / 2, 0);
                    
                    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
                    VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'ISSUE', 'MATERIAL_ISSUE_VOUCHER', v_cons_id::text, 'Issued materials for project consumption');
                END IF;
            END IF;
        END IF;

        -- E. Simulate Supplier Order (Expected Receipt) for Central Store occasionally
        IF (i % 5 = 0) THEN
            v_grn_id := gen_random_uuid();
            v_timestamp := v_timestamp - INTERVAL '5 days';
            
            INSERT INTO expected_receipt (id, created_at, store_id, supplier_name, expected_date, status, created_by_id)
            VALUES (v_grn_id, v_timestamp, v_central_store_id, 'Mega Builders Suppliers', (v_timestamp + INTERVAL '2 days')::date, 'COMPLETED', v_central_mgr_id);
            
            INSERT INTO expected_receipt_line (id, created_at, expected_receipt_id, item_id, expected_quantity, received_quantity, condition)
            VALUES (gen_random_uuid(), v_timestamp, v_grn_id, v_item_id, v_qty * 5, v_qty * 5, 'GOOD');
            
            INSERT INTO goods_received_note (id, created_at, reference_number, expected_receipt_id, store_id, received_by_id, received_at, status)
            VALUES (gen_random_uuid(), v_timestamp + INTERVAL '2 days', 'GRN-SUPP-' || i, v_grn_id, v_central_store_id, v_central_mgr_id, v_timestamp + INTERVAL '2 days', 'COMPLETED');
            
            INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
            VALUES (gen_random_uuid(), v_timestamp + INTERVAL '2 days', v_central_mgr_id, 'Central Manager', 'RECEIVE', 'EXPECTED_RECEIPT', v_grn_id::text, 'Received supplies from Mega Builders');
        END IF;
    END LOOP;

    -- 7. Add a big Stock Count with Variances
    v_count_id := gen_random_uuid();
    v_timestamp := NOW() - INTERVAL '15 days';
    INSERT INTO stock_count (id, created_at, store_id, initiated_by_id, status)
    VALUES (v_count_id, v_timestamp, v_site_store_id, v_site_mgr_id, 'COMPLETED');
    
    INSERT INTO stock_count_line (id, created_at, stock_count_id, item_id, system_quantity_snapshot, physical_quantity, variance_quantity, status)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_cement, 100, 95, -5, 'ADJUSTMENT_RAISED'),
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_timber, 50, 50, 0, 'ZERO_VARIANCE_CONFIRMED'),
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_steel, 200, 202, 2, 'ADJUSTMENT_RAISED');

    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
    VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'INITIATE', 'STOCK_COUNT', v_count_id::text, 'Initiated stock count');

    -- Adjustments for the count
    INSERT INTO stock_adjustment (id, created_at, reference_number, store_id, item_id, adjusted_by_id, reason_code, quantity_before, quantity_after, notes, requires_countersignature, countersigned_by_id)
    VALUES 
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', 'ADJ-1', v_site_store_id, v_item_cement, v_site_mgr_id, 'DAMAGED_WRITE_OFF', 100, 95, 'Damaged cement bags found', true, v_admin_id),
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', 'ADJ-2', v_site_store_id, v_item_steel, v_site_mgr_id, 'SURPLUS_FOUND', 200, 202, 'Extra steel bars found in yard', true, v_admin_id);

    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
    VALUES (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_admin_id, 'System Admin', 'APPROVE', 'STOCK_ADJUSTMENT', v_count_id::text, 'Approved stock count adjustments');

END $$;
