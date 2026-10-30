import re
content = open('src/main/java/com/iekcorbar/sakagura/Fruit.java').read()
for line in content.split('\n'):
    m = re.match(r'\s+(\w+)\("([^"]+)", "(\w+)", (.+)', line)
    if m:
        rest = m.group(4).strip()
        # The seed is the 3rd parameter, which comes right after produce
        # Format: "produce", "seed" or null, minDrop, ...
        # So seed is either null or "string" right after the produce string
        seed_match = re.match(r'\s*(null|"(\w+)")', rest)
        seed = seed_match.group(2) if seed_match and seed_match.group(2) else None
        if seed is None:
            print(f"{m.group(1)}: seed=null")