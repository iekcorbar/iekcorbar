"""Generate a 16x16 pixel-art texture for the Drop model."""
from PIL import Image

W, H = 16, 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Generic drop color (brownish, like a seed/fruit)
for y in range(H):
    for x in range(W):
        if 4 <= x <= 11 and 2 <= y <= 13:
            if (x + y) % 3 == 0:
                px[x, y] = (140, 100, 60, 255)
            elif (x + y) % 3 == 1:
                px[x, y] = (170, 130, 80, 255)
            else:
                px[x, y] = (120, 85, 50, 255)
        # Stem
        if 7 <= x <= 8 and 0 <= y <= 2:
            px[x, y] = (80, 120, 50, 255)

img.save("textures/drop.png")
print("Saved drop.png")