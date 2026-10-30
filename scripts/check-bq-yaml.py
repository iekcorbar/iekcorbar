import io, re, sys

def scan(path):
    bad = []
    in_block = False
    for i, line in enumerate(io.open(path, encoding='utf-8'), 1):
        s = line.rstrip('\n')
        if re.match(r'^\s*[\w-]+:\s*\|', s):
            in_block = True
            continue
        if in_block:
            if s.strip() and not s.startswith('    ') and not s.startswith('      ') and not re.match(r'^\s+\S', s):
                in_block = False
            else:
                continue
        m = re.match(r'^(\s+)([\w-]+):\s+(.+)$', s)
        if not m:
            continue
        val = m.group(3)
        if val.startswith(("'", '"')) or val.startswith('|'):
            continue
        if ': ' in val:
            bad.append((i, s.strip()[:90]))
    return bad

for p in sys.argv[1:]:
    b = scan(p)
    print('==', p, '==', 'CLEAN' if not b else f'{len(b)} probleem-regels')
    for i, t in b:
        print(f'  L{i}: {t}')
