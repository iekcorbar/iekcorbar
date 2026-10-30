"""Writes src/main/resources/recipes.yml for Sakagura: the recipes from iekcorbar.com/brewing
(tools/site-recipes.json, fetched from /api/brewing/recipes) unchanged, plus our own Japanese /
steampunk drinks. Run from the Sakagura folder: python -I tools/make_recipes.py

Step format (same as the website): cook {time minutes, ingredients}, mix {time, ingredients} (a
cauldron without heat), distill {runs}, age {years, wood}. Ingredients: "<name>/<count>", where a
name is a vanilla item ("Sugar", "Milk Bucket"), a finished drink ("Gin"), kitchen:<id> or
sakagura:<id>.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
site = json.load(open(os.path.join(HERE, "site-recipes.json"), encoding="utf-8"))["recipes"]

OWN = [
    # slug, names (poor/normal/good), colour, difficulty, alcohol, lore, effects, steps
    ("sake", ["Rough Sake", "Sake", "Daiginjo Sake"], "f2efe0", 6, 15, ["<gray>Rice, water and patience"], ["REGENERATION/0-1/20-40"],
     [{"type": "cook", "time": 8, "ingredients": ["kitchen:steamed_rice/6", "Sugar/1"]}, {"type": "age", "years": 2, "wood": "spruce"}]),
    ("nigori", ["Watery Nigori", "Nigori Sake", "Creamy Nigori"], "ffffff", 3, 12, ["<gray>Cloudy and unfiltered"], ["SATURATION/1/2"],
     [{"type": "cook", "time": 6, "ingredients": ["kitchen:steamed_rice/5", "Sugar/2"]}]),
    ("shochu", ["Harsh Shochu", "Shochu", "Smooth Shochu"], "e8e4d8", 6, 25, ["<gray>Distilled twice"], ["RESISTANCE/0/30-60"],
     [{"type": "cook", "time": 7, "ingredients": ["sakagura:barley/6", "Potato/2"]}, {"type": "distill", "runs": 2}]),
    ("umeshu", ["Sour Umeshu", "Umeshu", "Aged Umeshu"], "c98a3a", 5, 14, ["<gray>Plum wine, sweet and tart"], ["REGENERATION/1/20-40"],
     [{"type": "mix", "time": 3, "ingredients": ["sakagura:ume/6", "Sugar/4", "Shochu/1"]}, {"type": "age", "years": 3, "wood": "cherry"}]),
    ("yuzu_chuhai", ["Flat Chuhai", "Yuzu Chuhai", "Sparkling Yuzu Chuhai"], "f7e04a", 3, 8, ["<gray>Shochu, yuzu and bubbles"], ["SPEED/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:yuzu/3", "Sugar/2", "Shochu/1"]}]),
    ("momo_sake", ["Bland Momo Sake", "Momo Sake", "Blushing Momo Sake"], "f7b2a2", 5, 10, ["<gray>Peach sake"], ["HEALTH_BOOST/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:peach/4", "Sake/1"]}, {"type": "age", "years": 1, "wood": "birch"}]),
    ("sakura_liqueur", ["Wilted Liqueur", "Sakura Liqueur", "Cherry Blossom Liqueur"], "ffb7c5", 6, 18, ["<light_purple>Smells of spring"], ["LUCK/0/120-300"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:cherry/6", "Sugar/3", "Pink Petals/4"]}, {"type": "distill", "runs": 1}, {"type": "age", "years": 2, "wood": "cherry"}]),
    ("steam_whisky", ["Sooty Whisky", "Steam Whisky", "Brass Reserve Whisky"], "b06a2c", 8, 32, ["<gold>Distilled in a copper still"], ["STRENGTH/0/60-120"],
     [{"type": "cook", "time": 10, "ingredients": ["sakagura:barley/10"]}, {"type": "distill", "runs": 3}, {"type": "age", "years": 8, "wood": "copper"}]),
    ("hop_ale", ["Flat Ale", "Hop Ale", "Golden Hop Ale"], "d9a441", 3, 6, ["<gray>Bitter and fresh"], ["HASTE/0/60-120"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:barley/6", "sakagura:hops/3"]}, {"type": "age", "years": 2, "wood": "oak"}]),
    ("red_wine", ["Harsh Red Wine", "Red Wine", "Full-Bodied Red Wine"], "7b1e2b", 6, 12, ["<gray>From our own vines"], ["REGENERATION/0/20-40"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:grape/8"]}, {"type": "age", "years": 4, "wood": "oak"}]),
    ("juniper_gin", ["Pine-Needle Gin", "Juniper Gin", "Botanical Gin"], "dff3f0", 6, 30, ["<gray>Juniper and a little ginger"], ["NIGHT_VISION/0/120-300"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:juniper/6", "sakagura:ginger/1", "Wheat/3"]}, {"type": "distill", "runs": 3}]),
    ("berry_wine", ["Sour Berry Wine", "Berry Wine", "Forest Berry Wine"], "6a2a7a", 4, 10, ["<gray>Blueberries and strawberries"], ["REGENERATION/0/20-30"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:blueberry/4", "sakagura:strawberry/4", "Sugar/2"]}, {"type": "age", "years": 2, "wood": "birch"}]),
    ("apple_liquor", ["Weak Apple Liquor", "Apple Liquor", "Fine Apple Liquor"], "e8b04a", 5, 25, ["<gray>Needed for a good sangria"], [],
     [{"type": "cook", "time": 5, "ingredients": ["Apple/6", "Sugar/2"]}, {"type": "distill", "runs": 2}]),
    # staff drinks, promoted 27-10 from the hand-edited server config into the generator so they
    # survive every rebuild. Two repairs on the way in: zombiezestzinger's colour "00800" was a
    # five-digit typo (the parser reads it as neither hex nor a name and silently falls back to
    # WHITE) and now says 008000; its lore tag <00800> is not valid MiniMessage and shows as
    # literal garbage, now <dark_green>. Bloody Mary's colour "RED" is NOT a typo: named colours
    # are house style (absinthe/vodka/hot_choc use GREEN/WHITE/DARK_RED on the website too).
    ("bloody_mary", ["Poor Bloody Mary", "Bloody Mary", "Strong Bloody Mary"], "RED", 2, 42,
     ["+++<dark_red>A hair of the dog"], ["POISON/15-25"],
     [{"type": "cook", "time": 3, "ingredients": ["Red Dye/15", "Potato/12"]}, {"type": "distill", "runs": 8}]),
    ("zombiezestzinger", ["Rotten Zombie Zeste Zinger", "Zombie Zeste Zinger", "Gulping Zombie Zeste Zinger"], "008000", 8, 80,
     ["<dark_green>Cloudy and unfiltered"], ["SATURATION/1/4"],
     [{"type": "cook", "time": 12, "ingredients": ["kitchen:steamed_rice/5", "Sugar/2"]}]),
    # ---- seasonal set (feestdagen, user wish 27-10). All vanilla ingredients except the relics:
    # garnish; "Rabbit Foot" is spelled without the apostrophe on purpose (Material name).
    ("shamrock_shake", ["Flat Clover", "Shamrock Shake", "Emerald Isle"], "7cfc00", 4, 9,
     ["<green>Lucky to be drunk"], ["LUCK/0/120-240", "GLOWING/0/20-60"],
     [{"type": "cook", "time": 4, "ingredients": ["sakagura:barley/4", "Milk Bucket/1", "Glow Berries/1", "Sugar/2"]}]),
    ("crock_o_gold", ["Fool's Gold", "Potato Gold", "Crock O' Gold"], "f2c14e", 6, 22,
     ["<gold>Aged with a treasure. Still tastes like potato"], ["RESISTANCE/0-1/60-120"],
     [{"type": "cook", "time": 8, "ingredients": ["Potato/8", "sakagura:barley/4", "Gold Nugget/1"]}, {"type": "distill", "runs": 3}]),
    ("bog_kelp_collins", ["Pondwater", "Bog-Kelp Collins", "Sea's Green Secret"], "3fd66e", 3, 11,
     ["<aqua>Gills included"], ["WATER_BREATHING/0/60-240"],
     [{"type": "mix", "time": 3, "ingredients": ["Sake/1", "Dried Kelp/4", "Sea Pickle/1", "Sugar/1"]}]),
    ("pumpkin_hollow", ["Gloom Juice", "Pumpkin Gloom", "Head of the Hollow"], "e87a1a", 6, 18,
     ["<dark_purple>The candle inside is still lit"], ["FIRE_RESISTANCE/0/60-120"],
     [{"type": "cook", "time": 6, "ingredients": ["Pumpkin/4", "Sugar/2", "Gunpowder/1"]}, {"type": "age", "years": 1, "wood": "oak"}]),
    ("widows_kiss", ["Crawler Cider", "Spider Sour", "Widow's Kiss"], "4a1e5c", 7, 16,
     ["<light_purple>The spider that does not bite"], ["INVISIBILITY/0/15-30", "SATURATION/0/2"],
     [{"type": "cook", "time": 5, "ingredients": ["Spider Eye/3", "Apple/3", "Sugar/2"]}, {"type": "age", "years": 2, "wood": "cherry"}]),
    ("sculk_swill", ["Graveyard Dregs", "Midnight Vine", "Sculk Swill"], "0d1f2b", 9, 5,
     ["<dark_aqua>Lighter than a gravestone"], ["SLOW_FALLING/0/60-120"],
     [{"type": "cook", "time": 4, "ingredients": ["Sculk Sensor/1", "sakagura:blueberry/4"]}, {"type": "age", "years": 3, "wood": "any"}]),
    ("yule_log_reserve", ["Sooty Sip", "Mulled Mirth", "Yule Log Reserve"], "8c2f2f", 4, 14,
     ["<red>Mulled with cocoa and a sprig of fir"], ["REGENERATION/0/20-60", "FIRE_RESISTANCE/0/30-60"],
     [{"type": "mix", "time": 3, "ingredients": ["Red Wine/1", "Cocoa Beans/2", "Sugar/2", "Spruce Leaves/1"]}]),
    ("reindeer_run", ["Dasher's Draught", "Reindeer Run", "Santa's Reserve"], "f8c894", 2, 4,
     ["<gray>Faster than the sleigh bell"], ["SPEED/0/120-240", "SATURATION/0/2"],
     [{"type": "mix", "time": 2, "ingredients": ["Carrot/3", "Golden Carrot/1", "Milk Bucket/1", "Sugar/2"]}]),
    ("bishops_knog", ["Bland Knog", "Fir Knog", "Bishop's Eggnog"], "f5e6c8", 5, 12,
     ["<yellow>From the wrong kind of egg"], ["HEALTH_BOOST/0-1/60-120"],
     [{"type": "mix", "time": 3, "ingredients": ["Turtle Egg/2", "Milk Bucket/1", "Sugar/2", "Juniper Gin/1"]}]),
    ("torchflower_spritz", ["Wilted Spritz", "Petal Pilsner", "Torchflower Spritz"], "ffc0cb", 4, 6,
     ["<pink>The first bloom of spring"], ["HASTE/0/60-120", "LUCK/0/60-120"],
     [{"type": "cook", "time": 5, "ingredients": ["Torchflower Seeds/2", "sakagura:barley/5", "sakagura:hops/2", "Pink Petals/2"]},
      {"type": "age", "years": 1, "wood": "birch"}]),
    ("warren_weizen", ["Hutch Water", "Bunny Bounce", "Warren Weizen"], "f5e6b8", 3, 7,
     ["<gold>Concours de saut"], ["JUMP_BOOST/0/120-240", "SPEED/0/60-120"],
     [{"type": "cook", "time": 4, "ingredients": ["Wheat/4", "sakagura:barley/3", "Rabbit Foot/1", "Sugar/1"]}, {"type": "distill", "runs": 1}]),
    ("chocolate_heart_flip", ["Tangled Flip", "Cocoa Heart Flip", "Valentine's Flip"], "b3687a", 6, 13,
     ["<red>A Relics heart, melted into Sakagura foam"], ["ABSORPTION/0/120-240", "REGENERATION/0/10-30"],
     [{"type": "mix", "time": 3, "ingredients": ["Milk Bucket/1", "Cocoa Beans/2", "Sugar/2", "relics:chocolate_heart/1"]}]),
    # ---- open bar (user wish 27-10 evening: "meer drankjes, creatief, effecten zelf bedenken").
    # Every ingredient token really exists: kitchen:/sakagura:/sw:/relics: ids, vanilla items,
    # or a finished drink by name (resolves via Ingredients.key). Effects are registry effect
    # names; "TYPE/level/duration" with a/b range scales with quality and harmful effects get
    # MILDER at high quality (see Recipes.effects).
    ("wasabi_fire", ["Watery Burn", "Wasabi Fire", "Dragon's Nostrils"], "ff6a00", 7, 22,
     ["<red>Clears the head. Both nostrils."], ["SPEED/0-1/60-120", "NAUSEA/0/3-8"],
     [{"type": "cook", "time": 4, "ingredients": ["kitchen:chili/2", "kitchen:daikon_slices/3", "Sugar/2"]}, {"type": "distill", "runs": 2}]),
    ("miso_martini", ["Cloudy Martini", "Miso Martini", "Umami Silk"], "efe3c2", 4, 17,
     ["<gray>The savoury side of the bar"], ["ABSORPTION/0/60-120", "SATURATION/0/5"],
     [{"type": "mix", "time": 3, "ingredients": ["kitchen:miso/2", "Shochu/1", "Milk Bucket/1"]}, {"type": "age", "years": 1, "wood": "any"}]),
    ("blank_slate", ["Brackish Cup", "Charcoal Cleanse", "The Blank Slate"], "1a1a1e", 2, 0,
     ["<gray>It tastes like nothing at all. That is the point."], [],
     [{"type": "mix", "time": 2, "ingredients": ["Ink Sac/1", "sakagura:mikan/3", "Sugar/2"]}]),
    ("molten_face", ["Pale Sweat", "Ghost Pepper Cider", "Molten Face"], "ff4500", 7, 12,
     ["<gold>Apple cider. Probably not chili."], ["FIRE_RESISTANCE/0/120-240", "NAUSEA/0/2-6"],
     [{"type": "cook", "time": 5, "ingredients": ["kitchen:chili/3", "Apple/4", "Sugar/3"]}, {"type": "distill", "runs": 1}]),
    ("blightcap_bock", ["Toadspill", "Mushroom Bock", "Blightcap Bock"], "9b7653", 5, 9,
     ["<gray>Two mushrooms walk into a barrel..."], ["NIGHT_VISION/0/90-240", "NAUSEA/0/3-9"],
     [{"type": "cook", "time": 6, "ingredients": ["Brown Mushroom/3", "Red Mushroom/2", "Honey Bottle/2"]}, {"type": "age", "years": 2, "wood": "oak"}]),
    ("glow_grog", ["Dim Grog", "Glow Grog", "Lantern Light"], "8ffe9a", 6, 20,
     ["<green>You will be the lighthouse"], ["NIGHT_VISION/0-1/120-240", "GLOWING/0/10-30"],
     [{"type": "cook", "time": 4, "ingredients": ["Glow Berries/4", "Dried Kelp/2", "Juniper Gin/1"]}]),
    ("dragon_breath_tea", ["Lukewarm Leaf", "Dragon Breath Tea", "Fire-Eater's Cup"], "d8a13c", 3, 5,
     ["<gray>Green tea with a dark side"], ["SPEED/0/120-240", "FIRE_RESISTANCE/0/30-60"],
     [{"type": "mix", "time": 2, "ingredients": ["kitchen:green_tea/1", "kitchen:chili/1", "Sugar/2", "sakagura:yuzu/1"]}]),
    ("midnight_stout", ["Dusk Water", "Midnight Stout", "Eclipse Reserve"], "1c1208", 7, 11,
     ["<dark_gray>Breakfast of the night shift"], ["NIGHT_VISION/0/180-300", "RESISTANCE/0/60-120"],
     [{"type": "cook", "time": 8, "ingredients": ["sakagura:barley/8", "Cocoa Beans/3", "Sugar/2"]}, {"type": "distill", "runs": 1}, {"type": "age", "years": 3, "wood": "oak"}]),
    ("pear_greyhound", ["Wobbly Pear", "Pear Greyhound", "Silver Pear"], "dfe8cf", 4, 16,
     ["<gray>Bon appétit, good luck"], ["LUCK/0/60-120"],
     [{"type": "mix", "time": 3, "ingredients": ["sakagura:pear/4", "Juniper Gin/1", "Sugar/1"]}]),
    ("fool_gold", ["Tin Foil", "Midas Wash", "Sunstone Liqueur"], "ffb75e", 6, 22,
     ["+++<gold>The gold was a garnish. You ate it. Good"], ["ABSORPTION/0-1/120-240", "REGENERATION/0/5-15"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:apricot/5", "Sugar/3", "Gold Nugget/1"]}, {"type": "distill", "runs": 1}, {"type": "age", "years": 2, "wood": "cherry"}]),
    ("sunrise_reserve", ["Weak Sunrise", "Mikan Screwdriver", "Sunrise Reserve"], "ff8c1a", 3, 14,
     ["<yellow>A whole sunrise in a glass"], ["SATURATION/0/2", "SPEED/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:mikan/5", "Shochu/1"]}]),
    ("hanabi_bomb", ["Fizzle", "Cherry Bomb", "Hanabi Bomb"], "dc143c", 6, 25,
     ["<red>Counts double in a shootout"], ["STRENGTH/0/10-30", "NAUSEA/0/2-5"],
     [{"type": "cook", "time": 4, "ingredients": ["sakagura:cherry/6", "Sugar/2", "Gunpowder/1"]}, {"type": "distill", "runs": 1}]),
    ("lagoon_colada", ["Grey Slush", "Kiwi Colada", "Lagoon Colada"], "9fe870", 3, 8,
     ["<aqua>Tastes like a beach you will visit later"], ["WATER_BREATHING/0/60-180", "SATURATION/0/4"],
     [{"type": "cook", "time": 3, "ingredients": ["sakagura:kiwi/4", "Milk Bucket/1", "Sugar/2", "Snowball/2"]}]),
    ("cedar_smoke", ["Flat Highball", "Ume Highball", "Cedar Smoke Highball"], "d98a4a", 4, 10,
     ["<gray>Pine needles optional, but traditional"], ["SPEED/0/60-120", "FIRE_RESISTANCE/0/15-40"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:ume/3", "Sake/1", "Snowball/2", "Spruce Leaves/1"]}]),
    ("sunday_golden", ["Watery Posset", "Golden Posset", "Sunday's Golden"], "edc94f", 3, 6,
     ["<yellow>Grandmother's recipe, grandmother's nap"], ["REGENERATION/0-1/40-80", "SATURATION/0/3"],
     [{"type": "cook", "time": 4, "ingredients": ["Dandelion/2", "Poppy/1", "Milk Bucket/1", "Honey Bottle/1"]}]),
    ("sable_silk", ["Greywash", "Black Sesame Latte", "Sable Silk"], "2a2422", 3, 0,
     ["<gray>Midnight for breakfast"], ["NIGHT_VISION/0/60-180", "SATURATION/0/6"],
     [{"type": "cook", "time": 3, "ingredients": ["kitchen:sesame_seeds/4", "Milk Bucket/1", "Sugar/2"]}]),
    ("firehouse_ginger", ["Slack Shandy", "Ginger Beer", "Firehouse Ginger"], "c46f17", 4, 5,
     ["<gold>The first sip is the last calm one"], ["SPEED/0/60-150", "WEAKNESS/0/2-8"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:ginger/3", "Sugar/3", "Wheat/2"]}, {"type": "age", "years": 1, "wood": "any"}]),
    ("gazpacho_grande", ["Tomato Water", "Bisque Broth", "Gazpacho Grande"], "e35d3a", 3, 10,
     ["<gray>A salad that went to the wrong party"], ["SATURATION/0/8", "RESISTANCE/0/30-60"],
     [{"type": "mix", "time": 3, "ingredients": ["kitchen:tomato/3", "kitchen:chopped_green_onion/1", "Sugar/1", "Shochu/1"]}]),
    ("zen_garden", ["Bean Water", "Edamame Martini", "Zen Garden"], "88c46a", 5, 18,
     ["<green>One mouthful, one breath, no worries"], ["LUCK/0/120-240", "SLOW_FALLING/0/30-90"],
     [{"type": "mix", "time": 2, "ingredients": ["kitchen:edamame/3", "Shochu/1", "Sugar/1"]}]),
    ("wartshine", ["Hag's Juice", "Wartshine", "Crimson Lightning"], "8a2c2c", 8, 40,
     ["<dark_red>Nether in name only. And in colour."], ["STRENGTH/0-1/30-90", "POISON/0/3-8"],
     [{"type": "cook", "time": 6, "ingredients": ["Nether Wart/4", "Sugar/4"]}, {"type": "distill", "runs": 3}]),
    ("harvest_flip", ["Pale Nog", "Sweet Potato Flip", "Harvest Flip"], "e8a87a", 4, 7,
     ["<gray>Autumn that stayed for seconds"], ["SATURATION/0/8", "REGENERATION/0/10-30"],
     [{"type": "cook", "time": 4, "ingredients": ["Potato/3", "Egg/1", "Milk Bucket/1", "Sugar/2"]}]),
    ("kogen_mule", ["Cloudy Mule", "Matcha Mule", "Kogen Mule"], "a4c86a", 4, 12,
     ["<green>Green, fizzy, polite"], ["SPEED/0/60-120", "SATURATION/0/2"],
     [{"type": "mix", "time": 2, "ingredients": ["kitchen:tea_leaves/3", "sakagura:ginger/1", "Sugar/2", "Sake/1"]}]),
    ("stone_gold", ["Cloudy Leaf", "Amber Rocks", "Stone-Gold Oolong"], "c9a05a", 5, 0,
     ["<gray>Tea with boulders. Fancy."], ["HASTE/0/60-120", "FIRE_RESISTANCE/0/15-45"],
     [{"type": "cook", "time": 4, "ingredients": ["kitchen:tea_leaves/4", "Snowball/3"]}, {"type": "age", "years": 1, "wood": "any"}]),
    ("scorched_silk", ["Squeaker", "Burnt Butter Bourbon", "Scorched Silk"], "b87333", 7, 30,
     ["<gold>Butter first, apology later"], ["ABSORPTION/0-1/120-180", "RESISTANCE/0/30-60"],
     [{"type": "cook", "time": 5, "ingredients": ["Milk Bucket/1", "Sugar/3", "sakagura:barley/5"]}, {"type": "distill", "runs": 2}, {"type": "age", "years": 4, "wood": "oak"}]),
    ("prickly_paradise", ["Gutrot", "Cactus Cooler", "Prickly Paradise"], "4f9d6b", 4, 6,
     ["<green>Desert island, one glass wide"], ["FIRE_RESISTANCE/0/60-120", "SATURATION/0/5"],
     [{"type": "mix", "time": 3, "ingredients": ["Cactus/3", "Sugar/2", "sakagura:yuzu/2", "Snowball/2"]}]),
    ("crimson_elixir", ["Dishwater", "Beetroot Blood", "Crimson Elixir"], "8e1b3a", 5, 12,
     ["<dark_red>Iron will, beetroot body"], ["REGENERATION/0-1/40-90", "STRENGTH/0/15-45"],
     [{"type": "cook", "time": 4, "ingredients": ["Beetroot/4", "Sugar/3", "Red Wine/1"]}]),
    ("sleepweave", ["Bed of Nettle", "Honey Lavender", "Sleepweave"], "b57edc", 3, 5,
     ["<light_purple>Sip, then nap"], ["SLOWNESS/0/20-60", "REGENERATION/0/20-60", "SATURATION/0/4"],
     [{"type": "cook", "time": 3, "ingredients": ["Honey Bottle/2", "Lilac/2", "Sugar/2"]}, {"type": "age", "years": 1, "wood": "birch"}]),
    ("grove_bitter", ["Grass Water", "Bamboo Bitter", "Grove Bitter"], "9caf88", 5, 18,
     ["<gray>Bitters: the medicine you take on purpose"], ["RESISTANCE/0/60-120", "WEAKNESS/0/2-6"],
     [{"type": "mix", "time": 3, "ingredients": ["Bamboo/3", "sakagura:juniper/3", "Sugar/2", "Shochu/1"]}]),
    ("pounders_punch", ["Soggy Punch", "Pounder's Punch", "Mochi Marvel"], "ffd9e0", 4, 8,
     ["<pink>Pounded, pounded, pounded"], ["JUMP_BOOST/0-1/90-180", "SATURATION/0/6"],
     [{"type": "cook", "time": 4, "ingredients": ["kitchen:mochi/1", "sakagura:strawberry/3", "Sugar/2", "Milk Bucket/1"]}]),
    ("stone_brewed", ["Gravel Water", "Rock Tea", "Stone-Brewed Comfort"], "6b6b70", 8, 3,
     ["<gray>You chewed it anyway."], ["RESISTANCE/0-1/120-300", "SLOWNESS/0/5-15"],
     [{"type": "cook", "time": 6, "ingredients": ["Cobbled Deepslate/2", "kitchen:tea_leaves/2", "Sugar/1"]}]),
    # non-alcoholic
    ("amazake", ["Thin Amazake", "Amazake", "Rich Amazake"], "f6efe0", 2, 0, ["<gray>Sweet rice drink, no alcohol"], ["SATURATION/0/3"],
     [{"type": "cook", "time": 4, "ingredients": ["kitchen:steamed_rice/3", "sakagura:ginger/1"]}]),
    ("matcha_latte", ["Watery Matcha", "Matcha Latte", "Ceremonial Matcha Latte"], "8fbf5a", 2, 0, ["<green>Green and gentle"], ["HASTE/0/120-240"],
     [{"type": "cook", "time": 2, "ingredients": ["kitchen:tea_leaves/3", "Milk Bucket/1", "Sugar/1"]}]),
    ("mugicha", ["Weak Mugicha", "Mugicha", "Roasted Mugicha"], "a8743a", 1, 0, ["<gray>Cold barley tea"], ["FIRE_RESISTANCE/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:barley/3", "Snowball/2"]}]),
    ("ramune", ["Flat Ramune", "Ramune", "Fizzy Ramune"], "9fd8f0", 2, 0, ["<aqua>The marble rattles"], ["JUMP_BOOST/0-1/60-120"],
     [{"type": "mix", "time": 1, "ingredients": ["Sugar/3", "sakagura:yuzu/1", "Glass/1"]}]),
    # ---- the grove set (user wish 30-10: "grappige drankjes op basis van de 0.5.0-aanwinsten,
    # creatief, nieuwe effecten als dat kan"). Every token exists (Fruit.java ids, vanilla items,
    # finished drinks); four effects are NEW to the bar: BLINDNESS, MINING_FATIGUE,
    # DOLPHIN_GRACE, LEVITATION - they ride Registry.EFFECT like the rest.
    ("blind_date", ["Dim Date", "Blind Date", "Sight-Seeing Romance"], "8a1f3d", 4, 9,
     ["<dark_red>You said yes without looking."], ["BLINDNESS/0/20-40", "LUCK/0/240-480"],
     [{"type": "mix", "time": 3, "ingredients": ["sakagura:date/4", "sakagura:pomegranate/2", "Honey Bottle/1"]}]),
    ("persephone", ["Winter Seed", "Six of Persephone", "Queen of the Underworld"], "6e0f22", 7, 14,
     ["<purple>Six seeds. Do not eat six seeds."], ["NIGHT_VISION/0/180-300", "MINING_FATIGUE/0/60-120"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:pomegranate/6", "Sugar/3"]}, {"type": "age", "years": 6, "wood": "dark_oak"}]),
    ("lychee_cooler", ["Tepid Lychee", "Lychee & Chill", "Absolute Zero Chill"], "bfe8ff", 2, 5,
     ["<aqua>The panic simply drifts away."], ["SLOW_FALLING/0/120-240", "SATURATION/0/4"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:lychee/6", "Sugar/3", "Snowball/2"]}]),
    ("unrequited", ["One-Sided", "Unrequited", "Mutual at Last"], "f06aa5", 4, 8,
     ["<pink>Chasing them, and somehow enjoying it."], ["NAUSEA/0/20-45", "SPEED/0/120-240"],
     [{"type": "mix", "time": 3, "ingredients": ["sakagura:passionfruit/4", "sakagura:yuzu/2", "Sugar/2"]}]),
    ("clear_channels", ["Watery Eyes", "Clear Channels", "The Seventh Sinus"], "9be564", 5, 6,
     ["<green>Nose: evacuated. Lungs: ocean."], ["WATER_BREATHING/0/240-420", "GLOWING/0/2-6"],
     [{"type": "cook", "time": 3, "ingredients": ["sakagura:wasabi/2", "kitchen:daikon_slices/2", "Sugar/1"]}]),
    ("mango_tango", ["Solo Tango", "Mango-Tango", "Two Left Feet, Gone"], "ffb437", 3, 7,
     ["<gold>Commit to the step you misremembered."], ["SPEED/0/120-240", "JUMP_BOOST/0/60-120"],
     [{"type": "cook", "time": 3, "ingredients": ["sakagura:mango/4", "sakagura:papaya/2", "Sugar/2", "Milk Bucket/1"]}]),
    ("hairy_narwhal", ["Bald Yeti", "The Hairy Narwhal", "Unicorn of the Deep"], "6f8fae", 6, 13,
     ["<aqua>Hairy outside, majestic inside."], ["WATER_BREATHING/0/120-240", "DOLPHIN_GRACE/0/120-240"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:rambutan/4", "sakagura:longan/2", "sakagura:ginger/1"]}, {"type": "distill", "runs": 2}]),
    ("wyrms_regret", ["Hiccup", "Wyrm's Regret", "Dragon's Apology"], "8e2418", 8, 17,
     ["<red>The fire went down. The apology did not."], ["FIRE_RESISTANCE/0/180-300", "STRENGTH/0/60-120"],
     [{"type": "cook", "time": 7, "ingredients": ["sakagura:dragonfruit/3", "kitchen:chili/2", "sakagura:mangosteen/2"]}, {"type": "age", "years": 3, "wood": "copper"}]),
    ("zest_for_life", ["Flat Soda", "Zest for Life", "Second Wind"], "d9e86a", 3, 10,
     ["<yellow>The garden's smallest citrus, loudest effect."], ["SATURATION/0/6", "SPEED/0/120-240"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:sudachi/4", "sakagura:shiso/2", "Sugar/2", "Sake/1"]}]),
    ("silk_road", ["Threadbare", "Silk Road", "Loom Reserve"], "e8d7b8", 5, 9,
     ["<gray>Woven by worms, aged by merchants."], ["SLOW_FALLING/0/180-360", "RESISTANCE/0/60-120"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:mulberry/5", "Honey Bottle/1"]}, {"type": "age", "years": 4, "wood": "oak"}]),
    ("dark_rye", ["Groaner", "Dark Rye Comedy", "Killer Punchline"], "4a3a28", 8, 16,
     ["<gray>It gets funnier the higher you float."], ["LEVITATION/0/10-25", "REGENERATION/0/60-120"],
     [{"type": "cook", "time": 9, "ingredients": ["sakagura:rye/8", "Sugar/2"]}, {"type": "distill", "runs": 2}, {"type": "age", "years": 6, "wood": "spruce"}]),
    ("plain_vanilla", ["Plain Vanilla", "Plain Vanilla", "Plain Vanilla"], "f5f0e6", 2, 3,
     ["<gray>A five-star score changes absolutely nothing."], ["SATURATION/0/3"],
     [{"type": "cook", "time": 4, "ingredients": ["sakagura:vanilla/2", "Milk Bucket/1", "Sugar/2"]}]),
]


def ing(s):
    return f"{s['name']}/{s['count']}"


def site_steps(steps):
    out = []
    for s in steps:
        if s["type"] in ("cook", "mix"):
            out.append({"type": s["type"], "time": s.get("time", 1), "ingredients": [ing(i) for i in s.get("ingredients", [])]})
        elif s["type"] == "distill":
            out.append({"type": "distill", "runs": s.get("runs", 1)})
        elif s["type"] == "age":
            out.append({"type": "age", "years": s.get("years", 1), "wood": s.get("barrel", "any")})
    return out


def yq(v):
    """YAML scalar: quote strings that need it."""
    if isinstance(v, (int, float)):
        return str(v)
    return json.dumps(v, ensure_ascii=False)


lines = ["# Sakagura recipes. The first group comes from iekcorbar.com/brewing (keep them in sync with the",
         "# website); the second group is Sakagura's own. Regenerate with tools/make_recipes.py, or edit freely",
         "# and /brew reload. Names: poor / normal / good quality (one name = always that one).",
         "# Effects: TYPE/level/seconds, ranges like 0-1 scale with quality (harmful ones get milder).",
         "# Lore lines starting with + / ++ / +++ only show on poor / normal / good brews.", "recipes:"]


def emit(slug, names, color, diff, alcohol, lore, effects, steps):
    lines.append(f"  {slug}:")
    lines.append(f"    names: [{', '.join(yq(n) for n in names)}]")
    lines.append(f"    color: {yq(color)}")
    lines.append(f"    difficulty: {diff}")
    lines.append(f"    alcohol: {alcohol or 0}")
    lines.append(f"    lore: [{', '.join(yq(l) for l in lore)}]")
    lines.append(f"    effects: [{', '.join(yq(e) for e in effects)}]")
    lines.append("    steps:")
    for st in steps:
        parts = [f"type: {st['type']}"]
        for k in ("time", "runs", "years", "wood"):
            if k in st:
                parts.append(f"{k}: {yq(st[k])}")
        if "ingredients" in st:
            parts.append("ingredients: [" + ", ".join(yq(i) for i in st["ingredients"]) + "]")
        lines.append("      - {" + ", ".join(parts) + "}")


lines.append("  # ---- from iekcorbar.com/brewing")
for x in site:
    emit(x["slug"], x["names"], x["color"], x.get("difficulty", 5), x.get("alcohol"), x.get("lore", []), x.get("effects", []), site_steps(x["steps"]))
lines.append("  # ---- Sakagura's own")
for o in OWN:
    emit(*o)
out = os.path.join(HERE, "..", "src", "main", "resources", "recipes.yml")
open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("wrote", len(site), "site +", len(OWN), "own recipes to", os.path.normpath(out))


# ---- the wiki recipe page (docs/wiki-sakagura/03-recipes.md), from the same data

def pretty(token):
    name, _, count = token.partition("/")
    low = name.lower()
    if ":" in low:
        name = low.split(":", 1)[1].replace("_", " ").title()
    return f"{count} {name}" if count else name


def steps_text(steps):
    parts = []
    for st in steps:
        if st["type"] in ("cook", "mix"):
            verb = "Cook" if st["type"] == "cook" else "Mix (no heat)"
            parts.append(f"{verb} {st.get('time', 1)} min: " + ", ".join(pretty(i) for i in st.get("ingredients", [])))
        elif st["type"] == "distill":
            parts.append(f"Distill {st.get('runs', 1)}x")
        elif st["type"] == "age":
            wood = st.get("wood", "any")
            parts.append(f"Age {st.get('years', 1)} years" + ("" if wood == "any" else f" ({wood.replace('_', ' ')} cask)"))
    return " -> ".join(parts)


rows = []
for x in site:
    rows.append((x["names"][min(1, len(x["names"]) - 1)], steps_text(site_steps(x["steps"])), x.get("alcohol") or 0))
for o in OWN:
    rows.append((o[1][min(1, len(o[1]) - 1)], steps_text(o[7]), o[4]))
md = ["---", "slug: sakagura-recipes", "title: Sakagura Recipes", "category: Sakagura", "parent: sakagura", "---",
      "## Recipes", "", "Follow a recipe closely for the best quality (up to 5 stars). Cooking needs heat under the cauldron; mixing is done in a cauldron without heat. One minute means one real minute; one cask year is 20 real minutes.", "",
      "| Drink | How to make it | Alcohol |", "|---|---|---|"]
for name, how, alc in rows:
    md.append(f"| {name} | {how} | {str(alc) + '%' if alc and alc > 0 else '-'} |")
docs = os.path.join(HERE, "..", "..", "docs", "wiki-sakagura")
os.makedirs(docs, exist_ok=True)
open(os.path.join(docs, "03-recipes.md"), "w", encoding="utf-8").write(chr(10).join(md) + chr(10))
print("wrote the wiki recipe page with", len(rows), "drinks")

# De voormalige "website seed" voor de importknop op /brewing is verwijderd (27-10): de
# website-brewingpagina is op 25-10 uit de repo gehaald (zie SAKAGURA-DEV-NOTES) en het
# wiki-receptenblad (03-recipes.md, hierboven gegenereerd) is de levende receptenlijst.
