"""Generate a 32x32 pixel-art texture for the Autumn Tree model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
BARK = (120, 80, 45)
BARK_D = (90, 60, 30)
BARK_L = (150, 105, 60)
LEAF_ORANGE = (210, 120, 40)
LEAF_ORANGE_D = (180, 90, 25)
LEAF_RED = (190, 50, 40)
LEAF_RED_D = (160, 30, 25)
LEAF_YELLOW = (220, 180, 50)
LEAF_YELLOW_D = (190, 150, 30)
FRUIT = (200, 60, 40)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Bark pattern for trunk (bottom half of texture)
for y in range(16, H):
    for x in range(W):
        if (x + y * 2) % 4 == 0:
            px[x, y] = BARK_D
        elif (x + y * 2) % 4 == 1:
            px[x, y] = BARK_L
        else:
            px[x, y] = BARK

# Autumn leaf canopy (top half of texture) - mix of orange, red, yellow
for y in range(0, 16):
    for x in range(W):
        v = (x * 7 + y * 13) % 12
        if v < 4:
            px[x, y] = LEAF_ORANGE if (x + y) % 2 == 0 else LEAF_ORANGE_D
        elif v < 8:
            px[x, y] = LEAF_RED if (x + y) % 2 == 0 else LEAF_RED_D
        else:
            px[x, y] = LEAF_YELLOW if (x + y) % 2 == 0 else LEAF_YELLOW_D

# Fruit spots
for pos in [(5, 3), (12, 5), (20, 2), (27, 6)]:
    px[pos[0], pos[1]] = FRUIT

img.save("textures/tree_autumn.png")
print("Saved tree_autumn.png")