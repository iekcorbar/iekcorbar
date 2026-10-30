# Genereert de merchant- + foodhall-quests voor IekQuests/quests.yml
# Data 1:1 uit de BQ-pakketten (30-10 extractie). Alles Engels (spelertekst).
import io

NICE = {
 "STICK": "Sticks", "LEATHER": "Leather", "STRING": "String", "COAL": "Coal",
 "IRON_INGOT": "Iron Ingots", "COPPER_INGOT": "Copper Ingots", "GOLD_INGOT": "Gold Ingots",
 "REDSTONE": "Redstone", "FLINT": "Flint", "GUNPOWDER": "Gunpowder", "DIAMOND": "Diamonds",
 "NETHERITE_SCRAPE": "Netherite Scrap", "NETHERITE_SCRAP": "Netherite Scrap",
 "RAW_IRON": "Raw Iron", "RAW_COPPER": "Raw Copper", "RAW_GOLD": "Raw Gold",
 "AMETHYST_SHARD": "Amethyst Shards", "LAPIS_LAZULI": "Lapis Lazuli", "QUARTZ": "Quartz",
 "EMERALD": "Emeralds", "GLOWSTONE_DUST": "Glowstone Dust", "PRISMARINE_CRYSTALS": "Prismarine Crystals",
 "ENDER_PEARL": "Ender Pearls", "SHULKER_SHELL": "Shulker Shells", "NAUTILUS_SHELL": "Nautilus Shells",
 "HEART_OF_THE_SEA": "a Heart of the Sea", "PHANTOM_MEMBRANE": "Phantom Membranes",
 "ECHO_SHARD": "Echo Shards", "BLAZE_ROD": "Blaze Rods", "ARMADILLO_SCUTE": "Armadillo Scutes",
 "GLOW_INK_SAC": "Glow Ink Sacs", "CHORUS_FRUIT": "Chorus Fruit", "SUGAR": "Sugar",
 "HONEY_BOTTLE": "Honey Bottles", "SUGAR_CANE": "Sugar Cane", "WHEAT": "Wheat",
 "SWEET_BERRIES": "Sweet Berries", "BAMBOO": "Bamboo", "BROWN_MUSHROOM": "Brown Mushrooms",
 "POTATO": "Potatoes", "KELP": "Kelp", "BARREL": "Barrels", "BEEF": "Beef",
 "PORKCHOP": "Porkchops", "CHICKEN": "Chicken", "MUTTON": "Mutton", "CHARCOAL": "Charcoal",
 "COD": "Cod", "SALMON": "Salmon", "DRIED_KELP": "Dried Kelp", "SEAGRASS": "Seagrass",
 "BOWL": "Bowls", "APPLE": "Apples", "GLOW_BERRIES": "Glow Berries", "COCOA_BEANS": "Cocoa Beans",
 "MELON_SLICE": "Melon Slices", "PUMPKIN": "Pumpkins", "CARROT": "Carrots",
 "MILK_BUCKET": "Milk Buckets", "COOKIE": "Cookies", "SNOWBALL": "Snowballs", "ICE": "Ice",
 "PACKED_ICE": "Packed Ice", "BLUE_ICE": "Blue Ice", "PAPER": "Paper",
}

def eco_for(m):
    if m <= 250: return 2
    if m <= 400: return 3
    if m <= 650: return 4
    return 5

CD = 1320  # BQ: delay 1320s per contract

MERCHANTS = [
 dict(qid="vorkan", title="&2Weapon Merchant Vorkan Kross", icon="IRON_SWORD", npc=79, order=30,
      rep="vorkan_friendship", who="Vorkan",
      sub="Weapon stall - Contraband Bay", color="&2",
      intro=["&fVorkan: &7Materials, not excuses. The forge eats",
             "&7ore and the apprentices eat insults. Bring me what",
             "&7I list and the coin follows - reputation too."],
      shops=["shop-weapon-1", "shop-weapon-2", "shop-weapon-3"],
      shop_lbl=["&2Browse the weapon stall", "&2Premium weapon rack", "&2Legendary weapon case"],
      contracts=[("STICK",24,250),("LEATHER",16,250),("STRING",12,250),("COAL",16,250),
                 ("IRON_INGOT",10,400),("COPPER_INGOT",12,400),("GOLD_INGOT",6,400),
                 ("REDSTONE",6,400),("FLINT",10,400),("GUNPOWDER",6,650),("DIAMOND",1,650),
                 ("NETHERITE_SCRAP",1,900)]),
 dict(qid="brakka", title="&3Armor Merchant Brakka Stonewall", icon="DIAMOND_CHESTPLATE", npc=95, order=31,
      rep="brakka_friendship", who="Brakka",
      sub="Armor stall - Contraband Bay", color="&3",
      intro=["&fBrakka: &7Plate doesn't polish itself. Carry me",
             "&7hides, bars and whatever glitters, and you walk",
             "&7away richer than your armor."],
      shops=["shop-armor-1", "shop-armor-2", "shop-armor-3"],
      shop_lbl=["&3Browse the armor stall", "&3Premium armor rack", "&3Legendary armor case"],
      contracts=[("STICK",24,250),("LEATHER",16,250),("STRING",12,250),("COAL",16,250),
                 ("IRON_INGOT",10,400),("COPPER_INGOT",12,400),("GOLD_INGOT",6,400),
                 ("REDSTONE",6,400),("FLINT",10,400),("RAW_IRON",48,650),("DIAMOND",1,650),
                 ("NETHERITE_SCRAP",1,900)]),
 dict(qid="petra", title="&dGemologist Petra", icon="DIAMOND", npc=78, order=32,
      rep="petra_friendship", who="Petra",
      sub="Gem stall - Contraband Bay", color="&d",
      intro=["&fPetra: &7Every stone has a story; the pretty ones",
             "&7have price tags. Fill my display cases and I'll",
             "&7fill your purse. Carefully. Nothing scratched."],
      shops=["shop-gem-1", "shop-gem-2", "shop-gem-3"],
      shop_lbl=["&dBrowse the gem stall", "&dPremium gem rack", "&dRare gem vault"],
      contracts=[("COAL",16,250),("RAW_COPPER",12,250),("RAW_IRON",10,250),("RAW_GOLD",8,400),
                 ("AMETHYST_SHARD",6,400),("LAPIS_LAZULI",4,400),("REDSTONE",4,400),
                 ("QUARTZ",3,400),("EMERALD",2,650),("DIAMOND",1,650),("GLOWSTONE_DUST",8,400),
                 ("PRISMARINE_CRYSTALS",4,650)]),
 dict(qid="aster", title="&5Upgrade Merchant Aster Veyl", icon="ENDER_EYE", npc=96, order=33,
      rep="aster_friendship", who="Aster",
      sub="Upgrade stall - Contraband Bay", color="&5",
      intro=["&fAster: &7Rarities only, darling. Pearls, shells,",
             "&7hearts that still beat. Bring me the deep and the",
             "&7end and I'll pay like a royal jeweller."],
      shops=["shop-specialupgrade-1", "shop-specialupgrade-2", "shop-specialupgrade-3"],
      shop_lbl=["&5Browse the upgrade stall", "&5Premium upgrade rack", "&5Master upgrade bench"],
      contracts=[("ENDER_PEARL",8,300),("SHULKER_SHELL",2,450),("NAUTILUS_SHELL",4,350),
                 ("HEART_OF_THE_SEA",1,600),("PHANTOM_MEMBRANE",6,350),("ECHO_SHARD",2,550),
                 ("BLAZE_ROD",8,350),("ARMADILLO_SCUTE",12,300),("AMETHYST_SHARD",16,300),
                 ("GLOW_INK_SAC",10,300),("CHORUS_FRUIT",16,350)]),
]

HALLS = [
 dict(qid="teahouse", title="&aTea House - Aiko", icon="HONEY_BOTTLE", npc=82, order=40,
      rep="aiko_friendship", who="Aiko", shop="mmo-food-shop-tea-house",
      intro=["&fAiko: &7Welcome, welcome! The kettle never sleeps,",
             "&7but my pantry does. Restock me and every cup",
             "&7sold pays a little interest back to you."],
      contracts=[("SUGAR",12),("HONEY_BOTTLE",4),("SUGAR_CANE",20),("WHEAT",24),
                 ("SWEET_BERRIES",16),("BAMBOO",18)]),
 dict(qid="sakebrewery", title="&bSake Brewery - Goro", icon="POTION", npc=83, order=41,
      rep="goro_friendship", who="Goro", shop="mmo-food-shop-sake-brewery",
      intro=["&fGoro: &7You smell that? Rice, heat, barrels and",
             "&7trouble. The vats are hungry - feed them supplies",
             "&7and I will pay properly."],
      contracts=[("WHEAT",32),("SUGAR",16),("BROWN_MUSHROOM",12),("POTATO",24),
                 ("KELP",20),("BARREL",3)]),
 dict(qid="bbqmaster", title="&6BBQ Master - Taro", icon="COOKED_BEEF", npc=84, order=42,
      rep="taro_friendship", who="Taro", shop="mmo-food-shop-bbq-master",
      intro=["&fTaro: &7The grill is a hungry dragon and I am its",
             "&7tamer. Meat, coal, patience - bring the first two",
             "&7and the third comes free."],
      contracts=[("BEEF",16),("PORKCHOP",16),("CHICKEN",20),("MUTTON",14),
                 ("COAL",24),("CHARCOAL",18)]),
 dict(qid="sushimaster", title="&eSushi Master - Daichi", icon="COD", npc=85, order=43,
      rep="daichi_friendship", who="Daichi", shop="mmo-food-shop-sushi-master",
      intro=["&fDaichi: &7Fresh fish, sharp knife, steady hand.",
             "&7The sea delivers, you carry, I create. Deal?"],
      contracts=[("COD",12),("SALMON",10),("DRIED_KELP",24),("SEAGRASS",24),
                 ("KELP",32),("BOWL",12)]),
 dict(qid="candymerchant", title="&dCandy Merchant - Momo", icon="SUGAR", npc=86, order=44,
      rep="momo_friendship", who="Momo", shop="mmo-food-shop-candy-merchant",
      intro=["&fMomo: &7Sugar is organized happiness! My candy",
             "&7lines are long and my jars are empty. Fill them",
             "&7and sweetness pays the courier."],
      contracts=[("SUGAR",32),("HONEY_BOTTLE",6),("APPLE",18),("GLOW_BERRIES",12),
                 ("COCOA_BEANS",20),("PAPER",24)]),
 dict(qid="fruitmarket", title="&2Fruit Market - Sora", icon="MELON_SLICE", npc=87, order=45,
      rep="sora_friendship", who="Sora", shop="mmo-food-shop-fruit-market",
      intro=["&fSora: &7Nothing fancy - just the orchard's best",
             "&7piled in crates. Pick fresh, deliver fast, and",
             "&7the fruit business feeds both of us."],
      contracts=[("APPLE",24),("MELON_SLICE",32),("SWEET_BERRIES",24),("GLOW_BERRIES",16),
                 ("PUMPKIN",8),("CARROT",28)]),
 dict(qid="coffeehouse", title="&6Coffee House - Ren", icon="COCOA_BEANS", npc=88, order=46,
      rep="ren_friendship", who="Ren", shop="mmo-food-shop-coffee-house",
      intro=["&fRen: &7The city drinks before it wakes. Beans,",
             "&7milk, cups by the hundred. Keep the machine",
             "&7fed and keep the city grateful."],
      contracts=[("COCOA_BEANS",24),("SUGAR",18),("MILK_BUCKET",4),("COOKIE",16),
                 ("PAPER",20),("COPPER_INGOT",10)]),
 dict(qid="icecreamcart", title="&bIce Cream Cart - Yuki", icon="PACKED_ICE", npc=89, order=47,
      rep="yuki_friendship", who="Yuki", shop="mmo-food-shop-ice-cream-cart",
      intro=["&fYuki: &7Cold is an art form. Snow, ice, milk,",
             "&7and just enough sugar to forgive the winter.",
             "&7Restock my cart and summer pays up."],
      contracts=[("SNOWBALL",32),("ICE",16),("PACKED_ICE",8),("MILK_BUCKET",5),
                 ("SUGAR",24),("BLUE_ICE",2)]),
]

out = []
w = out.append

w("# IekQuests - quest definitions (migrated from BetonQuest 30-10)")
w("# Players see English only. Data sources: BQ packages vorkan_kross,")
w("# Armorshop, Shop_gemologist, Upgradestone, mmoitems_food_shop.")
w("# Cooldown 1320s = same timer BQ used. Rod_merchant (FishingRodney, npc 81)")
w("# waits for MMOItems item matching - see docs/quests-migratie.md.")
w("")
w("quests:")

def emit_merchant(m, default_money=None):
    w(f"  {m['qid']}:")
    w(f"    title: \"{m['title']}\"")
    w(f"    subtitle: \"{m['sub']}\"")
    w(f"    icon: {m['icon']}")
    w(f"    npc-id: {m['npc']}")
    w(f"    order: {m['order']}")
    w(f"    journal:")
    w(f"      - \"Standard contracts, {CD//60} min cooldown after each delivery.\"")
    w(f"      - \"Reputation: {m['rep']}\"")
    w(f"    stages:")
    w(f"      intro:")
    w(f"        type: talk")
    w(f"        label: \"&aLet's do business.\"")
    w(f"        lines:")
    for l in m["intro"]:
        w(f"          - \"{l}\"")
    w(f"        choices:")
    for lbl, shop in zip(m["shop_lbl"], m["shops"]):
        w(f"          - label: \"{lbl}\"")
        w(f"            say: \"&7{m['who']}: &8The stall is open. Look, don't breathe on the glass.\"")
        w(f"            do: [\"shop mi stations open {shop} %player%\"]")
    w(f"    rewards: []")
    w(f"    repeat:")
    for i, (mat, n, *rest) in enumerate(m["contracts"], 1):
        money = rest[0] if rest else default_money
        eco = eco_for(money)
        nice = NICE[mat]
        w(f"      c{i}:")
        w(f"        label: \"&a{n}x {nice}\"")
        w(f"        material: {mat}")
        w(f"        amount: {n}")
        w(f"        cooldown: {CD}")
        w(f"        money: {money}")
        w(f"        eco: {eco}")
        w(f"        rewards:")
        w(f"          - \"point {m['rep']} +1\"")
        w(f"          - \"say &7+{money} coins, +{eco} elite coins, +1 reputation with {m['who']}\"")
        w(f"          - \"title &aCONTRACT COMPLETE|&7{m['who']} pays {money} coins\"")
        w(f"          - \"sound steamworks:quest.complete\"")

for m in MERCHANTS:
    emit_merchant(m)
for h in HALLS:
    hh = dict(h)
    hh["sub"] = h["title"].split("-")[-1].strip()
    hh["color"] = "&a"
    hh["shop_lbl"] = [h["title"].split("-")[0].strip() + " shop"]
    hh["shops"] = [h["shop"]]
    emit_merchant(hh, default_money=400)

w("")
w("sequences: {}")

io.open(r"C:\Users\baaye\AppData\Local\Temp\opencode\merchants-part.yml", "w",
        encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
print("parts:", len(out), "lines -> merchants-part.yml (tijdelijk)")
