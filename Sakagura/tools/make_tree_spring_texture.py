"""Generate a 32x32 pixel-art texture for the Spring Tree model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
BARK = (120, 80, 45)
BARK_D = (90, 60, 30)
BARK_L = (150, 105, 60)
LEAF = (80, 160, 70)
LEAF_D = (55, 130, 50)
LEAF_L = (100, 190, 85)
BLOSSOM = (240, 180, 200)
BLOSSOM_D = (220, 150, 175)
BLOSSOM_L = (255, 210, 225)
BUD = (100, 180, 80)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Bark pattern for trunk (bottom half)
for y in range(16, H):
    for x in range(W):
        if (x + y * 2) % 4 == 0:
            px[x, y] = BARK_D
        elif (x + y * 2) % 4 == 1:
            px[x, y] = BARK_L
        else:
            px[x, y] = BARK

# Spring leaf canopy (top half) - fresh green
for y in range(0, 16):
    for x in range(W):
        if (x + y) % 4 == 0:
            px[x, y] = LEAF_D
        elif (x + y) % 4 == 1:
            px[x, y] = LEAF_L
        else:
            px[x, y] = LEAF

# Cherry blossom clusters
for pos in [(3, 2), (8, 1), (15, 3), (22, 0), (27, 4), (5, 8), (18, 6), (25, 7)]:
    for dx in range(2):
        for dy in range(2):
            if 0 <= pos[0]+dx < W and 0 <= pos[1]+dy < 16:
                px[pos[0]+dx, pos[1]+dy] = BLOSSOM if (dx+dy) % 2 == 0 else BLOSSOM_L

# Buds
for pos in [(10, 5), (16, 8), (20, 3)]:
    px[pos[0], pos[1]] = BUD

img.save("textures/tree_spring.png")
print("Saved tree_spring.png")