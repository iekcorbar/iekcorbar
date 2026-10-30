"""Generate a 16x16 pixel-art texture for the Shaft model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Colors (andesite/iron like Create)
ANDESITE = (132, 130, 126)
ANDESITE_D = (100, 98, 94)
ANDESITE_L = (160, 158, 154)
IRON = (90, 92, 98)
IRON_D = (60, 62, 68)

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

# Iron flange accents (left and right edges)
for y in range(H):
    px[0, y] = IRON
    px[1, y] = IRON_D
    px[14, y] = IRON_D
    px[15, y] = IRON

img.save("textures/shaft.png")
print("Saved shaft.png")