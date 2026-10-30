"""Generate a 16x16 pixel-art texture for the Fan model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

ANDESITE = (132, 130, 126)
ANDESITE_D = (100, 98, 94)
ANDESITE_L = (160, 158, 154)
BRASS = (190, 152, 64)
BRASS_D = (160, 122, 44)
IRON = (90, 92, 98)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

for y in range(H):
    for x in range(W):
        if (x + y) % 3 == 0:
            px[x, y] = ANDESITE_D
        elif (x + y) % 5 == 0:
            px[x, y] = ANDESITE_L
        else:
            px[x, y] = ANDESITE

# Brass center
for y in range(6, 10):
    for x in range(6, 10):
        px[x, y] = BRASS if (x + y) % 2 == 0 else BRASS_D

# Iron accents
for y in range(H):
    px[0, y] = IRON
    px[15, y] = IRON

img.save("textures/fan.png")
print("Saved fan.png")