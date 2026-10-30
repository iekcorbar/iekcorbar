"""Generate a 32x32 pixel-art texture for the Steam Still model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
COPPER = (180, 110, 60)
COPPER_D = (140, 80, 40)
COPPER_L = (210, 140, 80)
GLASS = (180, 210, 230, 200)
GLASS_D = (140, 170, 200, 200)
WOOD = (150, 110, 65)
WOOD_D = (120, 85, 45)
LIQUID = (180, 140, 60)
GLOWSTONE = (240, 220, 100)
METAL = (100, 100, 105)
METAL_D = (70, 70, 75)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Wood base
for y in range(2):
    for x in range(W):
        px[x, y] = WOOD if (x % 2 == 0) else WOOD_D

# Copper pot
for y in range(2, 8):
    for x in range(4, 12):
        px[x, y] = COPPER if (x + y) % 3 != 0 else COPPER_D

# Pot rim
for x in range(3, 13):
    px[x, 8] = COPPER_L

# Glass column
for y in range(9, 18):
    for x in range(6, 10):
        px[x, y] = GLASS if (x + y) % 2 == 0 else GLASS_D

# Column cap
for x in range(5, 11):
    px[x, 18] = METAL

# Condenser arm
for y in range(14, 15):
    for x in range(10, 16):
        px[x, y] = COPPER

# Condenser spiral
for y in range(10, 14):
    for x in range(14, 16):
        px[x, y] = COPPER if (y % 2 == 0) else COPPER_D

# Condenser output
for y in range(8, 10):
    for x in range(14, 16):
        px[x, y] = COPPER

# Liquid
for y in range(3, 6):
    for x in range(5, 11):
        if px[x, y][3] != 0:
            px[x, y] = LIQUID

# Glowstone slot
rect(6, 2, 4, 2, GLOWSTONE)

img.save("textures/still.png")
print("Saved still.png")