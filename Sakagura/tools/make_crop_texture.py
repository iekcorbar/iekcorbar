"""Generate a 16x16 pixel-art texture for the Crop Base model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
STEM = (80, 140, 50)
STEM_D = (60, 110, 35)
LEAF = (60, 160, 50)
LEAF_D = (40, 130, 35)
LEAF_L = (80, 190, 65)
FRUIT = (200, 80, 50)
FRUIT_L = (230, 110, 75)
DIRT = (139, 90, 43)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Stem
for y in range(6):
    px[7, y] = STEM if y % 2 == 0 else STEM_D
    px[8, y] = STEM if y % 2 == 0 else STEM_D

# Leaves
for y in range(2, 11):
    for x in range(4, 12):
        if abs(x - 7.5) + abs(y - 6) < 5:
            px[x, y] = LEAF if (x + y) % 2 == 0 else LEAF_D

# Fruit
rect(6, 11, 4, 2, FRUIT)
px[7, 11] = FRUIT_L
px[8, 11] = FRUIT_L

img.save("textures/crop_base.png")
print("Saved crop_base.png")