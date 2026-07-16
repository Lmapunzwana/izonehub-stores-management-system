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
    
    -- Items
    v_item_cement UUID := gen_random_uuid();
    v_item_timber UUID := gen_random_uuid();
    v_item_steel UUID := gen_random_uuid();
    v_item_ppe UUID := gen_random_uuid();
    v_item_drill UUID := gen_random_uuid();
    v_item_cable UUID := gen_random_uuid();
    
    -- Transaction IDs
    v_req_id_1 UUID := gen_random_uuid();
    v_req_id_2 UUID := gen_random_uuid();
    v_req_id_3 UUID := gen_random_uuid();
    
    v_miv_id_1 UUID := gen_random_uuid();
    v_grn_id_1 UUID := gen_random_uuid();
    v_cons_id_1 UUID := gen_random_uuid();
    
    v_count_id_1 UUID := gen_random_uuid();
    v_adj_id_1 UUID := gen_random_uuid();
    
    -- Inventory
    v_inv_central_cement UUID := gen_random_uuid();
    v_inv_site_cement UUID := gen_random_uuid();
    
    v_timestamp TIMESTAMP;
BEGIN
    -- 1. Look up existing core demo data
    SELECT id INTO v_admin_id FROM app_users WHERE email = 'admin@demo.com' LIMIT 1;
    SELECT id INTO v_central_mgr_id FROM app_users WHERE email = 'central@demo.com' LIMIT 1;
    SELECT id INTO v_site_mgr_id FROM app_users WHERE email = 'site@demo.com' LIMIT 1;
    
    SELECT id INTO v_central_store_id FROM store WHERE type = 'CENTRAL' LIMIT 1;
    SELECT id INTO v_site_store_id FROM store WHERE name = 'Demo Site Store' LIMIT 1;

    -- Exit gracefully if core data is missing
    IF v_central_store_id IS NULL OR v_admin_id IS NULL THEN
        RAISE NOTICE 'Core demo data not found. Skipping V22 comprehensive seed.';
        RETURN;
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

    -- 3. Create a historical closed project and store
    v_closed_site_store_id := gen_random_uuid();
    INSERT INTO store (id, created_at, name, type, location, active, closing)
    VALUES (v_closed_site_store_id, NOW() - INTERVAL '100 days', 'Downtown Plaza Site', 'SITE', 'City Center', false, false);
    
    v_closed_project_id := gen_random_uuid();
    INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active)
    VALUES (v_closed_project_id, NOW() - INTERVAL '100 days', 'PRJ-OLD-1', 'Downtown Plaza Renovation', v_closed_site_store_id, 250000, false);

    -- 4. Get active project for the demo site store
    SELECT id INTO v_active_project_id FROM project WHERE site_store_id = v_site_store_id LIMIT 1;

    -- 5. Seed Inventory Levels (Central Store gets high stock, Site gets some)
    -- Central Store Stock
    INSERT INTO store_inventory (id, created_at, store_id, item_id, quantity_on_hand, quantity_reserved, quantity_in_transit, quantity_frozen, last_updated)
    VALUES 
        (gen_random_uuid(), NOW(), v_central_store_id, v_item_cement, 1500, 0, 0, 0, NOW()),
        (gen_random_uuid(), NOW(), v_central_store_id, v_item_timber, 800, 0, 0, 0, NOW()),
        (gen_random_uuid(), NOW(), v_central_store_id, v_item_steel, 2000, 0, 0, 0, NOW()),
        (gen_random_uuid(), NOW(), v_central_store_id, v_item_ppe, 150, 0, 0, 0, NOW())
    ON CONFLICT DO NOTHING;

    -- Site Store Stock
    INSERT INTO store_inventory (id, created_at, store_id, item_id, quantity_on_hand, quantity_reserved, quantity_in_transit, quantity_frozen, last_updated)
    VALUES 
        (gen_random_uuid(), NOW(), v_site_store_id, v_item_cement, 250, 0, 0, 0, NOW()),
        (gen_random_uuid(), NOW(), v_site_store_id, v_item_timber, 100, 0, 0, 0, NOW()),
        (gen_random_uuid(), NOW(), v_site_store_id, v_item_ppe, 10, 0, 0, 0, NOW())
    ON CONFLICT DO NOTHING;

    -- 6. Simulate a Fulfilled Material Request (History)
    v_timestamp := NOW() - INTERVAL '30 days';
    INSERT INTO material_request (id, created_at, request_no, project_id, destination_store_id, requested_by_id, status, required_date, notes)
    VALUES (v_req_id_1, v_timestamp, 'MR-001-OLD', v_active_project_id, v_site_store_id, v_site_mgr_id, 'FULFILLED', v_timestamp + INTERVAL '5 days', 'Initial site mobilization materials');
    
    INSERT INTO material_request_line (id, created_at, request_id, item_id, requested_quantity, approved_quantity)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_req_id_1, v_item_cement, 100, 100),
        (gen_random_uuid(), v_timestamp, v_req_id_1, v_item_timber, 50, 50);

    -- Simulated MIV for the fulfilled request
    INSERT INTO material_issue_voucher (id, created_at, issue_no, request_id, source_store_id, destination_store_id, issued_by_id, status, notes)
    VALUES (v_miv_id_1, v_timestamp + INTERVAL '1 day', 'MIV-001', v_req_id_1, v_central_store_id, v_site_store_id, v_central_mgr_id, 'RECEIVED', 'Dispatch via Truck A');
    
    INSERT INTO miv_line (id, created_at, miv_id, item_id, requested_quantity, issued_quantity)
    VALUES 
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_miv_id_1, v_item_cement, 100, 100),
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_miv_id_1, v_item_timber, 50, 50);

    -- Simulated GRN for the MIV
    INSERT INTO expected_receipt (id, created_at, receipt_no, miv_id, destination_store_id, supplier, status_index, created_by_id)
    VALUES (v_grn_id_1, v_timestamp + INTERVAL '1 day', 'GRN-001', v_miv_id_1, v_site_store_id, 'Central Warehouse', 2, v_central_mgr_id);
    
    INSERT INTO expected_receipt_line (id, created_at, expected_receipt_id, item_id, expected_quantity, received_quantity, condition)
    VALUES 
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_grn_id_1, v_item_cement, 100, 100, 'GOOD'),
        (gen_random_uuid(), v_timestamp + INTERVAL '1 day', v_grn_id_1, v_item_timber, 50, 50, 'GOOD');

    -- 7. Simulate an Approved (but pending dispatch) Material Request
    v_timestamp := NOW() - INTERVAL '2 days';
    INSERT INTO material_request (id, created_at, request_no, project_id, destination_store_id, requested_by_id, status, required_date, notes)
    VALUES (v_req_id_2, v_timestamp, 'MR-002-PND', v_active_project_id, v_site_store_id, v_site_mgr_id, 'APPROVED', v_timestamp + INTERVAL '3 days', 'Phase 2 materials');
    
    INSERT INTO material_request_line (id, created_at, request_id, item_id, requested_quantity, approved_quantity)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_req_id_2, v_item_steel, 500, 500),
        (gen_random_uuid(), v_timestamp, v_req_id_2, v_item_ppe, 20, 20);

    -- 8. Simulate a Pending Material Request
    v_timestamp := NOW() - INTERVAL '5 hours';
    INSERT INTO material_request (id, created_at, request_no, project_id, destination_store_id, requested_by_id, status, required_date, notes)
    VALUES (v_req_id_3, v_timestamp, 'MR-003-NEW', v_active_project_id, v_site_store_id, v_site_mgr_id, 'PENDING_APPROVAL', v_timestamp + INTERVAL '2 days', 'Urgent cement top-up required');
    
    INSERT INTO material_request_line (id, created_at, request_id, item_id, requested_quantity, approved_quantity)
    VALUES (gen_random_uuid(), v_timestamp, v_req_id_3, v_item_cement, 50, 0);

    -- 9. Simulate Consumption at Site Store
    v_timestamp := NOW() - INTERVAL '15 days';
    INSERT INTO material_issue_voucher (id, created_at, issue_no, source_store_id, issued_by_id, status, notes)
    VALUES (v_cons_id_1, v_timestamp, 'CONS-001', v_site_store_id, v_site_mgr_id, 'CONSUMED', 'Daily material usage');
    
    INSERT INTO miv_line (id, created_at, miv_id, item_id, requested_quantity, issued_quantity)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_cons_id_1, v_item_cement, 40, 40),
        (gen_random_uuid(), v_timestamp, v_cons_id_1, v_item_timber, 10, 10);

    -- 10. Simulate a Historical Stock Count
    v_timestamp := NOW() - INTERVAL '20 days';
    INSERT INTO stock_count (id, created_at, store_id, initiated_by_id, status)
    VALUES (v_count_id_1, v_timestamp, v_site_store_id, v_site_mgr_id, 'COMPLETED');
    
    INSERT INTO stock_count_line (id, created_at, stock_count_id, item_id, system_quantity_snapshot, physical_quantity, variance_quantity, status)
    VALUES 
        (gen_random_uuid(), v_timestamp, v_count_id_1, v_item_cement, 200, 198, -2, 'ADJUSTMENT_RAISED'),
        (gen_random_uuid(), v_timestamp, v_count_id_1, v_item_timber, 50, 50, 0, 'ZERO_VARIANCE_CONFIRMED');

    -- Adjustments from the stock count
    INSERT INTO stock_adjustment (id, created_at, store_id, item_id, type, quantity, reason, requested_by_id, approved_by_id, status)
    VALUES (v_adj_id_1, v_timestamp + INTERVAL '1 day', v_site_store_id, v_item_cement, 'WRITE_OFF', 2, 'Stock count shortage', v_site_mgr_id, v_admin_id, 'APPROVED');

    -- 11. Generate lots of Audit Logs
    INSERT INTO audit_log (id, timestamp, actor_id, actor_name, action, entity_type, entity_id, details)
    VALUES 
        (gen_random_uuid(), NOW() - INTERVAL '30 days', v_site_mgr_id, 'Site Manager', 'CREATE', 'MATERIAL_REQUEST', v_req_id_1::text, 'Created material request MR-001-OLD'),
        (gen_random_uuid(), NOW() - INTERVAL '29 days', v_central_mgr_id, 'Central Manager', 'APPROVE', 'MATERIAL_REQUEST', v_req_id_1::text, 'Approved material request MR-001-OLD'),
        (gen_random_uuid(), NOW() - INTERVAL '29 days', v_central_mgr_id, 'Central Manager', 'DISPATCH', 'MIV', v_miv_id_1::text, 'Dispatched MIV-001'),
        (gen_random_uuid(), NOW() - INTERVAL '28 days', v_site_mgr_id, 'Site Manager', 'RECEIVE', 'GRN', v_grn_id_1::text, 'Confirmed GRN-001'),
        (gen_random_uuid(), NOW() - INTERVAL '20 days', v_site_mgr_id, 'Site Manager', 'INITIATE', 'STOCK_COUNT', v_count_id_1::text, 'Initiated stock count'),
        (gen_random_uuid(), NOW() - INTERVAL '19 days', v_admin_id, 'System Admin', 'APPROVE', 'ADJUSTMENT', v_adj_id_1::text, 'Approved write-off for 2 units of CEM-50KG'),
        (gen_random_uuid(), NOW() - INTERVAL '15 days', v_site_mgr_id, 'Site Manager', 'CONSUME', 'CONSUMPTION', v_cons_id_1::text, 'Logged consumption of materials'),
        (gen_random_uuid(), NOW() - INTERVAL '2 days', v_site_mgr_id, 'Site Manager', 'CREATE', 'MATERIAL_REQUEST', v_req_id_2::text, 'Created material request MR-002-PND'),
        (gen_random_uuid(), NOW() - INTERVAL '1 day', v_central_mgr_id, 'Central Manager', 'APPROVE', 'MATERIAL_REQUEST', v_req_id_2::text, 'Approved material request MR-002-PND'),
        (gen_random_uuid(), NOW() - INTERVAL '5 hours', v_site_mgr_id, 'Site Manager', 'CREATE', 'MATERIAL_REQUEST', v_req_id_3::text, 'Created material request MR-003-NEW');

END $$;
