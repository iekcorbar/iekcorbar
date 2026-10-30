import re
content = open('src/main/java/com/iekcorbar/sakagura/Fruit.java').read()
count_null = 0
count_str = 0
for line in content.split('\n'):
    m = re.match(r'\s+(\w+)\("([^"]+)", "(\w+)", (.+)', line)
    if m:
        rest = m.group(4).strip()
        seed_match = re.search(r'"(\w+)"', rest)
        if seed_match:
            count_str += 1
        else:
            count_null += 1
print(f"With seed string: {count_str}")
print(f"With null seed: {count_null}")