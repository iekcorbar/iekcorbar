"""Generate per-drink textures and item models for Sakagura."""
import json, os, yaml
from PIL import Image

RECIPES = "src/main/resources/recipes.yml"
MODELS_DIR = "models"
TEXTURES_DIR = "resourcepack/assets/sakagura/textures/item"
ITEMS_DIR = "resourcepack/assets/sakagura/items"
MODEL_DIR = "resourcepack/assets/sakagura/models/item"

# Color name -> RGB
COLOR_MAP = {
    "BLACK": (20, 20, 20), "DARK_RED": (139, 0, 0), "RED": (220, 20, 60),
    "GREEN": (34, 139, 34), "ORANGE": (255, 140, 0), "WHITE": (240, 235, 225),
    "BLUE": (30, 100, 200), "PURPLE": (128, 0, 128), "YELLOW": (255, 215, 0),
    "PINK": (255, 182, 193), "BROWN": (139, 69, 19), "GRAY": (128, 128, 128),
    "CYAN": (0, 200, 200), "MAGENTA": (255, 0, 255), "LIME": (50, 205, 50),
}

def parse_color(c):
    c = c.strip().strip('"').strip("'")
    if c in COLOR_MAP:
        return COLOR_MAP[c]
    # hex color
    if c.startswith("#"):
        c = c[1:]
    if len(c) == 6:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16))
    # named colors (fallback)
    return (180, 140, 60)

def make_texture(color, slug):
    """Generate a 16x16 bottle texture with the drink color."""
    W, H = 16, 16
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    r, g, b = color
    # Glass bottle outline
    for y in range(H):
        for x in range(W):
            # Bottle shape
            if 4 <= x <= 11 and 2 <= y <= 13:
                if x == 4 or x == 11 or y == 2 or y == 13:
                    px[x, y] = (180, 200, 220, 200)  # glass outline
                elif 5 <= x <= 10 and 3 <= y <= 12:
                    # Liquid
                    px[x, y] = (r, g, b, 220)
            # Neck
            if 6 <= x <= 9 and 0 <= y <= 2:
                if x == 6 or x == 9 or y == 0:
                    px[x, y] = (180, 200, 220, 200)
                else:
                    px[x, y] = (r, g, b, 220)
            # Cork
            if 6 <= x <= 9 and y == 0:
                px[x, y] = (140, 100, 60, 255)
    os.makedirs(TEXTURES_DIR, exist_ok=True)
    img.save(f"{TEXTURES_DIR}/{slug}.png")
    return f"textures/drinks/{slug}.png"

def main():
    with open(RECIPES) as f:
        data = yaml.safe_load(f)
    
    recipes = data.get("recipes", {})
    os.makedirs(ITEMS_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    
    # Read base model
    with open(f"{MODELS_DIR}/drink_bottle.json") as f:
        base_model = json.load(f)
    
    count = 0
    for slug, recipe in recipes.items():
        color = parse_color(recipe.get("color", "ORANGE"))
        texture_path = make_texture(color, slug)
        
        # Create item definition
        item_def = {
            "model": {
                "type": "minecraft:model",
                "model": f"sakagura:item/drink_{slug}"
            }
        }
        with open(f"{ITEMS_DIR}/{slug}.json", "w") as f:
            json.dump(item_def, f, indent=2)
        
        # Create model (copy of base with texture reference)
        model = json.loads(json.dumps(base_model))
        # Add textures section
        model["textures"] = {
            "0": f"sakagura:item/drink_{slug}"
        }
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
        
        with open(f"{MODEL_DIR}/drink_{slug}.json", "w") as f:
            json.dump(model, f, indent=2)
        
        count += 1
    
    print(f"Generated {count} drink textures and models")

if __name__ == "__main__":
    main()