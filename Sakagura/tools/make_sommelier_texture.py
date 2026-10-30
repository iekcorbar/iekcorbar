"""Generate a 64x64 pixel-art texture for the Sommelier model."""
from PIL import Image

W, H = 64, 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# Color palette
SKIN = (235, 200, 165)
SKIN_D = (210, 175, 140)
HAIR = (80, 50, 30)
HAIR_D = (60, 35, 20)
SHIRT = (240, 235, 225)
SHIRT_D = (210, 205, 195)
VEST = (30, 40, 80)
VEST_D = (20, 30, 60)
APRON = (40, 50, 90)
APRON_D = (30, 40, 70)
PANTS = (40, 40, 50)
PANTS_D = (30, 30, 40)
SHOES = (30, 25, 20)
GLASS = (200, 220, 240, 180)
AMBER = (200, 150, 50)
BOTTLE = (60, 80, 60)
BOTTLE_D = (40, 60, 40)
LABEL = (240, 230, 200)
EYE = (40, 30, 20)
MOUTH = (180, 100, 80)

def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < W and 0 <= j < H:
                px[i, j] = color

# Head (top area, 6x6 on texture for a 4x4x4 cube face)
# Face (front of head) - top-left quadrant
rect(0, 0, 4, 4, HAIR)      # hair top
rect(0, 4, 4, 4, SKIN)       # face
rect(0, 4, 1, 1, EYE)        # left eye
rect(3, 4, 1, 1, EYE)        # right eye
rect(1, 7, 2, 1, MOUTH)      # mouth
# Hair sides
rect(4, 0, 2, 6, HAIR_D)
rect(0, 0, 1, 6, HAIR_D)

# Body (shirt + vest)
rect(8, 0, 4, 10, SHIRT)     # shirt
rect(8, 0, 1, 10, VEST)      # vest left
rect(11, 0, 1, 10, VEST)     # vest right
rect(9, 2, 2, 6, VEST_D)     # vest center

# Apron
rect(16, 0, 4, 8, APRON)
rect(16, 0, 4, 1, APRON_D)   # top band

# Left arm
rect(20, 0, 2, 10, SHIRT)
rect(20, 0, 1, 10, VEST_D)

# Right arm
rect(24, 0, 2, 10, SHIRT)
rect(24, 0, 1, 10, VEST_D)

# Left leg
rect(28, 0, 2, 10, PANTS)
rect(28, 8, 2, 2, SHOES)

# Right leg
rect(32, 0, 2, 10, PANTS)
rect(32, 8, 2, 2, SHOES)

# Glass (right hand area)
rect(36, 0, 2, 3, GLASS)
rect(36, 0, 2, 1, AMBER)     # amber liquid top
rect(37, 1, 1, 2, AMBER)     # amber liquid

# Bottle
rect(40, 0, 1, 5, BOTTLE)
rect(40, 0, 1, 1, BOTTLE_D)  # cap
rect(40, 2, 1, 2, LABEL)     # label

img.save("textures/sommelier.png")
print("Saved sommelier.png")