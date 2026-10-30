"""Generate a 32x32 pixel-art texture for the Garden Fountain model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
STONE = (160, 155, 145)
STONE_D = (130, 125, 115)
STONE_L = (190, 185, 175)
WATER = (100, 160, 220, 180)
WATER_D = (70, 130, 190, 180)
WATER_L = (140, 190, 240, 180)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Stone pattern
for y in range(H):
    for x in range(W):
        if (x + y * 3) % 7 == 0:
            px[x, y] = STONE_D
        elif (x + y * 3) % 7 == 1:
            px[x, y] = STONE_L
        else:
            px[x, y] = STONE

# Water areas (bottom basin and top basin)
for y in range(4, 5):
    for x in range(5, 11):
        px[x, y] = WATER if (x + y) % 2 == 0 else WATER_D

for y in range(11, 12):
    for x in range(6, 10):
        px[x, y] = WATER if (x + y) % 2 == 0 else WATER_D

img.save("textures/fountain.png")
print("Saved fountain.png")