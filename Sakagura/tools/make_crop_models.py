"""Generate per-crop 3D models and textures for Sakagura."""
import json, os, re

# Read Fruit.java to get crop list
fruit_file = "src/main/java/com/iekcorbar/sakagura/Fruit.java"
with open(fruit_file) as f:
    content = f.read()

# Parse crop IDs (first string param in enum constructor)
crops = []
for line in content.split("\n"):
    m = re.match(r'\s+(\w+)\("([^"]+)", "(\w+)"', line)
    if m:
        name, display, produce = m.group(1), m.group(2), m.group(3)
        crops.append((name.lower(), produce, display))

print(f"Found {len(crops)} crops")

# Read base model
with open("models/crop_base.json") as f:
    base_model = json.load(f)

# Color mapping for crops (produce -> color)
CROP_COLORS = {
    "barley": (214, 182, 96), "hops": (150, 196, 90), "grape": (96, 40, 120),
    "ume": (176, 196, 70), "yuzu": (246, 204, 50), "peach": (246, 160, 130),
    "cherry": (186, 20, 40), "strawberry": (220, 40, 50), "blueberry": (60, 80, 170),
    "juniper": (70, 90, 140), "ginger": (210, 170, 110), "apple": (186, 20, 40),
    "pear": (150, 196, 90), "apricot": (246, 160, 130), "mikan": (246, 204, 50),
    "kiwi": (150, 110, 70), "rice": (232, 226, 198), "rye": (196, 158, 96),
    "maize": (238, 200, 78), "tomato": (208, 52, 42), "raspberry": (214, 74, 104),
    "blackcurrant": (48, 34, 66), "elderberry": (64, 44, 82), "cranberry": (188, 32, 48),
    "gooseberry": (168, 198, 96), "mulberry": (96, 24, 48), "persimmon": (232, 118, 44),
    "loquat": (238, 172, 84), "pomegranate": (198, 42, 64), "fig": (118, 62, 102),
    "quince": (224, 190, 84), "olive": (122, 132, 62), "date": (142, 86, 42),
    "lychee": (218, 98, 92), "passionfruit": (128, 62, 142), "vanilla": (228, 210, 172),
    "sudachi": (176, 196, 70), "shiso": (152, 56, 112), "wasabi": (168, 198, 96),
    "daikon": (232, 226, 198), "chili": (216, 72, 34), "mango": (232, 118, 44),
    "papaya": (246, 142, 62), "guava": (246, 160, 130), "rambutan": (220, 40, 50),
    "longan": (150, 110, 70), "mangosteen": (96, 40, 120), "dragonfruit": (230, 62, 140),
}

ITEMS_DIR = "resourcepack/assets/sakagura/items"
MODEL_DIR = "resourcepack/assets/sakagura/models/block"
TEXTURE_DIR = "resourcepack/assets/sakagura/textures/block"

os.makedirs(ITEMS_DIR, exist_ok=True)
os.makedirs(MODEL_DIR, exist_ok=True)
os.makedirs(TEXTURE_DIR, exist_ok=True)

count = 0
for crop_id, produce, display in crops:
    color = CROP_COLORS.get(produce, (100, 150, 60))
    r, g, b = color
    
    # Generate 3 stage models
    for stage in range(3):
        scale = 0.4 + stage * 0.3
        model = json.loads(json.dumps(base_model))
        model["textures"] = {"0": f"sakagura:block/{crop_id}_stage{stage}"}
        
        # Replace #missing with #0
        def replace_missing(obj):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if v == "#missing":
                        obj[k] = "#0"
                    else:
                        replace_missing(v)
            elif isinstance(obj, list):
                for item in obj:
                    replace_missing(item)
        replace_missing(model)
        
        # Scale elements based on stage
        for el in model.get("elements", []):
            # Scale from center
            for coord in ["from", "to"]:
                if coord in el:
                    for i in range(3):
                        center = 8.0
                        el[coord][i] = center + (el[coord][i] - center) * scale
        
        with open(f"{MODEL_DIR}/{crop_id}_stage{stage}.json", "w") as f:
            json.dump(model, f, indent=2)
        
        # Generate texture (16x16)
        from PIL import Image
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(16):
            for x in range(16):
                if (x + y) % 3 == 0:
                    px[x, y] = (int(r*0.7), int(g*0.7), int(b*0.7), 255)
                elif (x + y) % 5 == 0:
                    px[x, y] = (min(255, int(r*1.2)), min(255, int(g*1.2)), min(255, int(b*1.2)), 255)
                else:
                    px[x, y] = (r, g, b, 255)
        # Stem at bottom
        for y in range(14, 16):
            for x in range(7, 9):
                px[x, y] = (80, 140, 50, 255)
        img.save(f"{TEXTURE_DIR}/{crop_id}_stage{stage}.png")
        
        count += 1
    
    # Generate item definition for the crop produce
    item_def = {
        "model": {
            "type": "minecraft:model",
            "model": f"sakagura:block/{crop_id}_stage2"
        }
    }
    with open(f"{ITEMS_DIR}/{produce}.json", "w") as f:
        json.dump(item_def, f, indent=2)

print(f"Generated {count} crop stage models + textures")
print(f"Generated {len(crops)} item definitions")