import re
content = open('src/main/java/com/iekcorbar/sakagura/Fruit.java').read()
crops = []
for line in content.split('\n'):
    m = re.match(r'\s+(\w+)\("([^"]+)", "(\w+)", (.+)', line)
    if m:
        crops.append(m.group(1))
print(len(crops))
print(crops[:10])