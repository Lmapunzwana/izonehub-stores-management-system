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
    v_miv_id UUID;
    v_grn_id UUID;
    v_cons_id UUID;
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

    -- Fallback: if no active site store exists, create one!
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
    
    -- Load item array for random picking
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

    -- 5. Seed Inventory Levels (Central Store gets high stock, Site gets some)
    -- Loop over items to seed Central
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
        
        -- A. Create Material Request
        INSERT INTO material_request (id, created_at, request_no, project_id, destination_store_id, requested_by_id, status, required_date, notes)
        VALUES (v_req_id, v_timestamp, 'MR-SIM-' || i, v_active_project_id, v_site_store_id, v_site_mgr_id, 
            CASE 
                WHEN i > 35 THEN 'PENDING_APPROVAL' 
                WHEN i = 35 THEN 'APPROVED'
                ELSE 'FULFILLED' 
            END, 
            v_timestamp + INTERVAL '3 days', 'Routine site requisition');
            
        -- Add lines to the request
        v_item_id := v_items[1 + (random() * 5)::int];
        v_qty := 10 + (random() * 50)::int;
        INSERT INTO material_request_line (id, created_at, request_id, item_id, requested_quantity, approved_quantity)
        VALUES (gen_random_uuid(), v_timestamp, v_req_id, v_item_id, v_qty, CASE WHEN i <= 35 THEN v_qty ELSE 0 END);
        
        -- Add audit log
        INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
        VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'CREATE', 'MATERIAL_REQUEST', v_req_id::text, 'Created material request MR-SIM-' || i);

        -- B. If Fulfilled, simulate MIV and GRN
        IF i < 35 THEN
            v_miv_id := gen_random_uuid();
            v_timestamp := v_timestamp + INTERVAL '12 hours';
            
            INSERT INTO material_issue_voucher (id, created_at, issue_no, request_id, source_store_id, destination_store_id, issued_by_id, status, notes)
            VALUES (v_miv_id, v_timestamp, 'MIV-SIM-' || i, v_req_id, v_central_store_id, v_site_store_id, v_central_mgr_id, 
                CASE WHEN i > 30 THEN 'DISPATCHED' ELSE 'RECEIVED' END, 'Standard dispatch');
                
            INSERT INTO miv_line (id, created_at, miv_id, item_id, requested_quantity, issued_quantity)
            VALUES (gen_random_uuid(), v_timestamp, v_miv_id, v_item_id, v_qty, v_qty);

            -- Audit log
            INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
            VALUES (gen_random_uuid(), v_timestamp, v_central_mgr_id, 'Central Manager', 'DISPATCH', 'MIV', v_miv_id::text, 'Dispatched MIV-SIM-' || i);

            -- C. If Received, simulate GRN and Consumption
            IF i <= 30 THEN
                v_grn_id := gen_random_uuid();
                v_timestamp := v_timestamp + INTERVAL '1 day';
                
                INSERT INTO expected_receipt (id, created_at, receipt_no, miv_id, destination_store_id, supplier, status_index, created_by_id)
                VALUES (v_grn_id, v_timestamp, 'GRN-SIM-' || i, v_miv_id, v_site_store_id, 'Central Warehouse', 2, v_site_mgr_id);
                
                INSERT INTO expected_receipt_line (id, created_at, expected_receipt_id, item_id, expected_quantity, received_quantity, condition)
                VALUES (gen_random_uuid(), v_timestamp, v_grn_id, v_item_id, v_qty, v_qty, 'GOOD');
                
                -- Audit log
                INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
                VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'RECEIVE', 'GRN', v_grn_id::text, 'Confirmed GRN-SIM-' || i);

                -- D. Simulate Consumption
                IF (i % 3 = 0) THEN
                    v_cons_id := gen_random_uuid();
                    v_timestamp := v_timestamp + INTERVAL '2 days';
                    INSERT INTO material_issue_voucher (id, created_at, issue_no, source_store_id, issued_by_id, status, notes)
                    VALUES (v_cons_id, v_timestamp, 'CONS-SIM-' || i, v_site_store_id, v_site_mgr_id, 'CONSUMED', 'Daily material usage');
                    
                    INSERT INTO miv_line (id, created_at, miv_id, item_id, requested_quantity, issued_quantity)
                    VALUES (gen_random_uuid(), v_timestamp, v_cons_id, v_item_id, v_qty / 2, v_qty / 2);
                    
                    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
                    VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'CONSUME', 'CONSUMPTION', v_cons_id::text, 'Logged consumption of materials');
                END IF;
            END IF;
        END IF;
    END LOOP;

    -- 7. Add a big Stock Count with Variances
    v_count_id := gen_random_uuid();
    v_timestamp := NOW() - INTERVAL '15 days';
    INSERT INTO stock_count (id, created_at, store_id, initiated_by_id, status, count_no)
    VALUES (v_count_id, v_timestamp, v_site_store_id, v_site_mgr_id, 'COMPLETED', 'SC-SITE-001');
    
    INSERT INTO stock_count_line (id, created_at, stock_count_id, item_id, system_quantity_snapshot, physical_quantity, variance_quantity, status)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_cement, 100, 95, -5, 'ADJUSTMENT_RAISED'),
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_timber, 50, 50, 0, 'ZERO_VARIANCE_CONFIRMED'),
        (gen_random_uuid(), v_timestamp, v_count_id, v_item_steel, 200, 202, 2, 'ADJUSTMENT_RAISED');

    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
    VALUES (gen_random_uuid(), v_timestamp, v_site_mgr_id, 'Site Manager', 'INITIATE', 'STOCK_COUNT', v_count_id::text, 'Initiated stock count');

    -- Adjustments for the count
    INSERT INTO stock_adjustment (id, created_at, store_id, item_id, type, quantity, reason, requested_by_id, approved_by_id, status)
    VALUES 
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_site_store_id, v_item_cement, 'WRITE_OFF', 5, 'Damaged cement bags found during stock count', v_site_mgr_id, v_admin_id, 'APPROVED'),
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_site_store_id, v_item_steel, 'FOUND', 2, 'Extra steel bars found in yard', v_site_mgr_id, v_admin_id, 'APPROVED');

    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
    VALUES (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_admin_id, 'System Admin', 'APPROVE', 'ADJUSTMENT', v_count_id::text, 'Approved stock count adjustments');

END $$;
