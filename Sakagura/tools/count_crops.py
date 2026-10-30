import re
content = open('src/main/java/com/iekcorbar/sakagura/Fruit.java').read()
crops = re.findall(r'^\s+([A-Z_]+)\("[^"]+", "(\w+)"', content, re.MULTILINE)
print(len(crops))
print(crops[:5])