import uuid

stores = [
    "Athlone and Head Office",
    "Site 1- SABI",
    "Site 2- SUNPORTS",
    "Site 3-Murombedzi"
]

suppliers = [
    ("Jinko", "Solar Panel"),
    ("Chint", "Solar Panel"),
    ("Lyfra Trading", "Accessories"),
    ("XYZ", "Unknown"),
    ("ABC", "Unknown")
]

sql_statements = []
sql_statements.append("/* V14__seed_docx_data.sql */")

# Insert Stores
store_ids = []
for name in stores:
    uid = str(uuid.uuid4())
    store_ids.append((uid, name))
    sql_statements.append(f"INSERT INTO store (id, created_at, name, type, location, active) VALUES ('{uid}', NOW(), '{name}', 'SITE', 'Unknown', true);")

# Insert Projects linked to the stores
for idx, (uid, name) in enumerate(store_ids):
    proj_id = str(uuid.uuid4())
    code = f"RC-2026-DOCX-{idx+1}"
    sql_statements.append(f"INSERT INTO project (id, created_at, code, name, site_store_id, budget_ceiling, active) VALUES ('{proj_id}', NOW(), '{code}', '{name} Project', '{uid}', 0, true);")

# Insert Suppliers
for name, cat in suppliers:
    uid = str(uuid.uuid4())
    sql_statements.append(f"INSERT INTO suppliers (id, name, category, status) VALUES ('{uid}', '{name}', '{cat}', 'ACTIVE');")

with open('backend/src/main/resources/db/migration/V14__seed_docx_data.sql', 'w') as f:
    f.write('\n'.join(sql_statements))
    f.write('\n')

print(f"Generated V14__seed_docx_data.sql with location field.")
