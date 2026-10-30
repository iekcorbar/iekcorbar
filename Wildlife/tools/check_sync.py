import re
size_content = open('tools/make_animals.py').read()
size_match = re.search(r'SIZE = \{([^}]+)\}', size_content, re.DOTALL)
size_str = size_match.group(1)
size_dict = {}
for part in size_str.split(','):
    part = part.strip()
    if ':' in part:
        key, val = part.split(':')
        key = key.strip().strip('"')
        val = val.strip()
        val = val.split('#')[0].strip()
        try:
            size_dict[key] = float(val)
        except:
            pass

species_content = open('src/main/java/com/iekcorbar/wildlife/Species.java').read()
for sp in ['DEER', 'BEAR', 'BOAR', 'DUCK', 'ELEPHANT', 'ZEBRA', 'CAPYBARA', 'RHINO', 'ORCA']:
    for line in species_content.split('\n'):
        if line.strip().startswith(sp + '('):
            parts = line.split(',')
            scale = float(parts[2].strip())
            size_key = sp.lower()
            size_val = size_dict.get(size_key, '?')
            ratio = scale / size_val if isinstance(size_val, float) else 0
            print(f'{sp:12s} SIZE={size_val:5.2f}  Species.scale={scale:5.2f}  ratio={ratio:.2f}')
            break