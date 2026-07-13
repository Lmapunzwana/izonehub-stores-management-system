import uuid

def infer_category(desc):
    desc = desc.lower()
    if 'solar' in desc or 'battery' in desc or 'inverter' in desc or 'lithium' in desc:
        return 'SOLAR'
    elif 'cable' in desc or 'wire' in desc or 'electrical' in desc or 'mcb' in desc or 'circuit breaker' in desc or 'lug' in desc or 'spd' in desc or 'switch' in desc or 'db box' in desc or 'trunking' in desc or 'connector' in desc or 'test meter' in desc or 'multimeter' in desc or 'tester' in desc or 'earthrods' in desc:
        return 'ELECTRICAL'
    elif 'drill' in desc or 'grinder' in desc or 'cutter' in desc or 'screw driver' in desc or 'ratchet' in desc or 'allen key' in desc or 'pliers' in desc or 'shifting' in desc or 'tape measure' in desc or 'hammer' in desc or 'hacksaw' in desc or 'crimping tool' in desc or 'pressing tool' in desc or 'long nose' in desc:
        return 'TOOL'
    elif 'angle iron' in desc or 'pole' in desc or 'a frame' in desc or 'foundation' in desc or 'crossarm' in desc or 'anchor' in desc or 'rail' in desc or 'bolt' in desc or 'washer' in desc or 'nut' in desc or 'tie' in desc or 'tape' in desc or 'clamp' in desc or 'stitch' in desc or 'pipe' in desc or 'brass grand' in desc:
        return 'CONSTRUCTION'
    elif 'gas tank' in desc:
        return 'CONSUMABLE'
    else:
        return 'CONSTRUCTION'

with open('docx_output.txt', 'r') as f:
    lines = [l.strip() for l in f.readlines() if l.strip()]

start_idx = -1
for i, line in enumerate(lines):
    if line == 'Active' and lines[i+1].startswith('NSV001'):
        start_idx = i + 1
        break

if start_idx == -1:
    print("Could not find start of items")
    exit(1)

items = []
i = start_idx
while i < len(lines):
    if not lines[i].startswith('NSV'):
        break
    code = lines[i]
    desc = lines[i+1]
    uom = lines[i+2]
    active = lines[i+3].lower() == 'yes'
    items.append({
        'code': code,
        'desc': desc,
        'uom': uom,
        'active': active
    })
    i += 4

sql_statements = []
sql_statements.append("/* V13__seed_initial_items.sql */")
for item in items:
    cat = infer_category(item['desc'])
    uid = str(uuid.uuid4())
    desc_escaped = item['desc'].replace("'", "''")
    uom_escaped = item['uom'].replace("'", "''")
    active_str = 'true' if item['active'] else 'false'
    stmt = f"INSERT INTO item (id, created_at, code, name, description, unit_of_measure, category, reorder_threshold, active) VALUES ('{uid}', NOW(), '{item['code']}', '{desc_escaped}', '{desc_escaped}', '{uom_escaped}', '{cat}', 0, {active_str});"
    sql_statements.append(stmt)

with open('backend/src/main/resources/db/migration/V13__seed_initial_items.sql', 'w') as f:
    f.write('\\n'.join(sql_statements))
    f.write('\\n')

print(f"Generated {len(items)} items.")
