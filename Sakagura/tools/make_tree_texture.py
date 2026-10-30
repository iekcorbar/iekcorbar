"""Generate a 32x32 pixel-art texture for the Orchard Tree model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
BARK = (120, 80, 45)
BARK_D = (90, 60, 30)
BARK_L = (150, 105, 60)
LEAF = (60, 140, 50)
LEAF_D = (40, 110, 35)
LEAF_L = (80, 170, 65)
FRUIT = (200, 50, 60)
FRUIT_L = (230, 80, 90)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Bark pattern for trunk
for y in range(H):
    for x in range(W):
        if (x + y * 2) % 4 == 0:
            px[x, y] = BARK_D
        elif (x + y * 2) % 4 == 1:
            px[x, y] = BARK_L
        else:
            px[x, y] = BARK

# Leaf canopy (top half of texture)
for y in range(0, 16):
    for x in range(W):
        if (x + y) % 3 == 0:
            px[x, y] = LEAF_D
        elif (x + y) % 5 == 0:
            px[x, y] = LEAF_L
        else:
            px[x, y] = LEAF

# Fruit spots
for pos in [(5, 3), (12, 5), (20, 2), (27, 6), (8, 8), (18, 7)]:
    px[pos[0], pos[1]] = FRUIT
    if pos[0]+1 < W:
        px[pos[0]+1, pos[1]] = FRUIT_L

img.save("textures/tree.png")
print("Saved tree.png")