/* V14__seed_docx_data.sql */
INSERT INTO store (id, created_at, name, type, location, active) VALUES ('1dc8d10f-cc5d-435a-b5f1-d57c27a0c5bf', NOW(), 'Athlone and Head Office', 'SITE', 'Unknown', true);
INSERT INTO store (id, created_at, name, type, location, active) VALUES ('1dc4f6ea-2071-4777-9f0b-7c722373ed66', NOW(), 'Site 1- SABI', 'SITE', 'Unknown', true);
INSERT INTO store (id, created_at, name, type, location, active) VALUES ('5333d6da-8398-4759-be7c-d02ace6fa444', NOW(), 'Site 2- SUNPORTS', 'SITE', 'Unknown', true);
INSERT INTO store (id, created_at, name, type, location, active) VALUES ('e8031e79-9f38-4b4c-93ad-07e2864dcc41', NOW(), 'Site 3-Murombedzi', 'SITE', 'Unknown', true);
INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) VALUES ('35542c6a-8d8b-4d98-ad65-7ff556469ff3', NOW(), 'RC-2026-DOCX-1', 'Athlone and Head Office Project', '1dc8d10f-cc5d-435a-b5f1-d57c27a0c5bf', 0, true);
INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) VALUES ('9d2056dd-f317-4f88-af1f-41ff44220abc', NOW(), 'RC-2026-DOCX-2', 'Site 1- SABI Project', '1dc4f6ea-2071-4777-9f0b-7c722373ed66', 0, true);
INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) VALUES ('f28a5352-97bb-4481-953a-7f26044bd16b', NOW(), 'RC-2026-DOCX-3', 'Site 2- SUNPORTS Project', '5333d6da-8398-4759-be7c-d02ace6fa444', 0, true);
INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) VALUES ('5d904681-4686-4566-9420-caa029272325', NOW(), 'RC-2026-DOCX-4', 'Site 3-Murombedzi Project', 'e8031e79-9f38-4b4c-93ad-07e2864dcc41', 0, true);
INSERT INTO suppliers (id, name, category, status) VALUES ('860b8738-468e-4af4-ad90-ce8cfcf2dece', 'Jinko', 'Solar Panel', 'ACTIVE');
INSERT INTO suppliers (id, name, category, status) VALUES ('098035b6-9999-4435-a40d-926c381d936e', 'Chint', 'Solar Panel', 'ACTIVE');
INSERT INTO suppliers (id, name, category, status) VALUES ('6bcd8dcf-486f-497c-bfed-33b25880b495', 'Lyfra Trading', 'Accessories', 'ACTIVE');
INSERT INTO suppliers (id, name, category, status) VALUES ('b0816c94-80e8-46b6-86da-190556522d75', 'XYZ', 'Unknown', 'ACTIVE');
INSERT INTO suppliers (id, name, category, status) VALUES ('90d2aa8a-8084-46d8-80ab-160d95c09c14', 'ABC', 'Unknown', 'ACTIVE');
