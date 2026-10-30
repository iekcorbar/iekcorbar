"""Generate a 32x32 pixel-art texture for the Winter Tree model."""
from PIL import Image

W, H = 32, 32
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors
BARK = (120, 80, 45)
BARK_D = (90, 60, 30)
BARK_L = (150, 105, 60)
SNOW = (240, 245, 250)
SNOW_D = (210, 220, 235)
SNOW_L = (250, 252, 255)
ICE = (200, 220, 240)

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

# Snow pattern (top half) - white with subtle blue shading
for y in range(0, 16):
    for x in range(W):
        if (x + y) % 5 == 0:
            px[x, y] = SNOW_D
        elif (x + y) % 7 == 0:
            px[x, y] = ICE
        else:
            px[x, y] = SNOW

# Snow speckles
for pos in [(3, 2), (8, 5), (15, 3), (22, 7), (28, 4), (10, 9), (18, 6)]:
    px[pos[0], pos[1]] = SNOW_L

img.save("textures/tree_winter.png")
print("Saved tree_winter.png")