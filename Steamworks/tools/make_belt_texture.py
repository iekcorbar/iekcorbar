"""Generate a 16x16 pixel-art texture for the Belt model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
LEATHER = (100, 70, 40)
LEATHER_D = (75, 50, 25)
LEATHER_L = (130, 95, 55)
IRON = (90, 92, 98)
IRON_D = (60, 62, 68)
BRASS = (190, 152, 64)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Leather belt pattern
for y in range(H):
    for x in range(W):
        if y in (0, 1, 14, 15):
            px[x, y] = LEATHER_D
        elif (x + y) % 3 == 0:
            px[x, y] = LEATHER_D
        elif (x + y) % 3 == 1:
            px[x, y] = LEATHER_L
        else:
            px[x, y] = LEATHER

# Iron roller accents
for y in range(4, 12):
    px[0, y] = IRON
    px[1, y] = IRON_D
    px[14, y] = IRON_D
    px[15, y] = IRON

# Brass pins
for pos in [(2, 2), (2, 13), (13, 2), (13, 13)]:
    px[pos[0], pos[1]] = BRASS

img.save("textures/belt.png")
print("Saved belt.png")