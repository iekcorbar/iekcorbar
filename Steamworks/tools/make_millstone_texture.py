"""Generate a 16x16 pixel-art texture for the Millstone model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
STONE = (132, 130, 126)
STONE_D = (100, 98, 94)
STONE_L = (160, 158, 154)
DARK_STONE = (88, 86, 84)
GRAIN = (200, 180, 100)
GRAIN_D = (170, 150, 70)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Stone pattern
for y in range(H):
    for x in range(W):
        if (x + y * 2) % 4 == 0:
            px[x, y] = STONE_D
        elif (x + y * 2) % 4 == 1:
            px[x, y] = STONE_L
        else:
            px[x, y] = STONE

# Darker center (shaft hole area)
for y in range(6, 10):
    for x in range(6, 10):
        px[x, y] = DARK_STONE

# Grain spots
for pos in [(3, 3), (12, 5), (7, 10), (10, 2)]:
    px[pos[0], pos[1]] = GRAIN
    if pos[0]+1 < W:
        px[pos[0]+1, pos[1]] = GRAIN_D

img.save("textures/millstone.png")
print("Saved millstone.png")