"""Generate a 32x32 pixel-art texture for the Cask model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
WOOD = (160, 120, 70)
WOOD_D = (130, 95, 50)
WOOD_L = (190, 150, 95)
HOOP = (90, 90, 95)
HOOP_D = (60, 60, 65)
TAP = (180, 140, 60)
TAP_D = (140, 105, 40)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Wood grain pattern
for y in range(H):
    for x in range(W):
        if (x + y) % 3 == 0:
            px[x, y] = WOOD_D
        elif (x + y) % 5 == 0:
            px[x, y] = WOOD_L
        else:
            px[x, y] = WOOD

# Hoop areas (top/bottom/middle bands)
for x in range(W):
    for y in [3, 4, 7, 8, 12, 13]:
        px[x, y] = HOOP if (x % 2 == 0) else HOOP_D

# Tap
rect(13, 6, 3, 3, TAP)
rect(14, 7, 1, 1, TAP_D)

img.save("textures/cask.png")
print("Saved cask.png")