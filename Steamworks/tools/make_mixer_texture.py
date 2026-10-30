"""Generate a 16x16 pixel-art texture for the Mixer model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
ANDESITE = (132, 130, 126)
ANDESITE_D = (100, 98, 94)
ANDESITE_L = (160, 158, 154)
BRASS = (190, 152, 64)
BRASS_D = (160, 122, 44)
IRON = (90, 92, 98)
LIQUID = (180, 140, 60)
LIQUID_D = (150, 110, 40)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Base andesite pattern
for y in range(H):
    for x in range(W):
        if (x + y) % 3 == 0:
            px[x, y] = ANDESITE_D
        elif (x + y) % 5 == 0:
            px[x, y] = ANDESITE_L
        else:
            px[x, y] = ANDESITE

# Brass center (shaft area)
for y in range(5, 11):
    for x in range(5, 11):
        if (x + y) % 2 == 0:
            px[x, y] = BRASS
        else:
            px[x, y] = BRASS_D

# Iron rim accent
for x in range(W):
    px[x, 5] = IRON
    px[x, 10] = IRON

img.save("textures/mixer.png")
print("Saved mixer.png")