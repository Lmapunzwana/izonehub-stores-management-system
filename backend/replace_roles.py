import os, re

dir_path = '/home/lmapunzwana/Downloads/stores-mgt/backend/src/main/java'

for root, dirs, files in os.walk(dir_path):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()
            
            def replace_roles(match):
                roles_str = match.group(1)
                roles = [r.strip().strip("'") for r in roles_str.split(',')]
                new_roles = set()
                for r in roles:
                    if r in ['FINANCE', 'EXECUTIVE_MANAGEMENT', 'PROCUREMENT_OFFICER']:
                        new_roles.add('CENTRAL_STORE_MANAGER')
                    else:
                        new_roles.add(r)
                
                # Put SYSTEM_ADMINISTRATOR first if present
                sorted_roles = list(new_roles)
                sorted_roles.sort(key=lambda x: (x != 'SYSTEM_ADMINISTRATOR', x))
                
                if len(sorted_roles) == 1:
                    return f"@PreAuthorize(\"hasRole('{sorted_roles[0]}')\")"
                else:
                    roles_formatted = ",".join([f"'{r}'" for r in sorted_roles])
                    return f"@PreAuthorize(\"hasAnyRole({roles_formatted})\")"
            
            new_content = re.sub(r'@PreAuthorize\("has(?:Any)?Role\((.*?)\)"\)', replace_roles, content)
            
            if new_content != content:
                with open(filepath, 'w') as f:
                    f.write(new_content)
                print(f"Updated {filepath}")
