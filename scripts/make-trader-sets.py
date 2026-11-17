"""Writes the WanderingTrades trade files for Kitchen, Sakagura, Steamworks and Kura into
pluginset-aangepast/WanderingTrades/trades/ (the live backup is never touched). Run from the repo root: python -I scripts/make-trader-sets.py
Every result is a plain PAPER named exactly like the item, last lore line = plugin name; the plugin's TraderSwap
turns it into the real item when the trade is taken."""
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pluginset-aangepast", "WanderingTrades", "trades")

# marker, file, chance, how many trades one trader gets, header comment, trades
# trade = (key, display name, lore, amount, [(material, amount), ...], maxUses)
SETS = {
    "Kitchen": ("kitchen", 0.10, 1, "Kitchen: a few rare Japanese kitchen things.", [
        ("tea_seeds", "Tea Seeds", ["Plant on farmland. The bush regrows after picking."], 4, [("EMERALD", 8)], 2),
        ("iron_hocho", "Iron Hocho", ["Cuts food on a cutting board."], 1, [("EMERALD", 10), ("IRON_INGOT", 2)], 1),
        ("tea_set", "Tea Set", ["Sneak + right-click a block to put it down."], 1, [("EMERALD", 12), ("GOLD_INGOT", 1)], 1),
        ("kamado_stove", "Kamado Stove", ["Light it with flint and steel."], 1, [("EMERALD", 14), ("BRICK", 8)], 1),
        ("diamond_hocho", "Diamond Hocho", ["Cuts food on a cutting board."], 1, [("EMERALD", 16), ("DIAMOND", 1)], 1),
    ]),
    "Sakagura": ("sakagura", 0.08, 1, "Sakagura: seeds and pits for the fruit garden.", [
        ("yuzu_seeds", "Yuzu Seeds", ["Plant on farmland."], 3, [("EMERALD", 10)], 2),
        ("peach_pit", "Peach Pit", ["Plant on farmland."], 3, [("EMERALD", 10)], 2),
        ("cherry_pit", "Cherry Pit", ["Plant on farmland."], 3, [("EMERALD", 12)], 2),
        ("ume_pit", "Ume Pit", ["Plant on farmland."], 3, [("EMERALD", 12)], 2),
        ("hop_cuttings", "Hop Cuttings", ["Plant on farmland."], 4, [("EMERALD", 8)], 2),
    ]),
    "Steamworks": ("steamworks", 0.07, 1, "Steamworks: a rare machine part or tool.", [
        ("dowsing_rod", "Oil Dowsing Rod", ["Right-click: oil under this chunk", "and where the nearest field is."], 1, [("EMERALD", 24), ("DIAMOND", 1)], 1),
        ("brass_plate", "Brass Plate", [], 6, [("EMERALD", 16), ("COPPER_INGOT", 8)], 1),
        ("hand_crank", "Hand Crank", [], 1, [("EMERALD", 8), ("IRON_INGOT", 4)], 1),
        ("bellows_fan", "Bellows Fan", [], 1, [("EMERALD", 20), ("COPPER_INGOT", 12)], 1),
    ]),
    "Kura": ("kura", 0.06, 1, "Kura: a rare storage-network part.", [
        ("wireless_terminal", "Wireless Terminal", ["Right-click to open the terminal you look at,", "from up to 16 blocks away."], 1, [("EMERALD", 30), ("DIAMOND", 1), ("ENDER_PEARL", 1)], 1),
        ("filing_cabinet", "Filing Cabinet", [], 1, [("EMERALD", 18), ("IRON_INGOT", 8)], 1),
        ("framed_cable", "Framed Inventory Cable", ["Full-block cable, can be painted."], 2, [("EMERALD", 12), ("COPPER_INGOT", 4)], 1),
    ]),
}

for marker, (fname, chance, amount, blurb, trades) in SETS.items():
    lines = [
        "# WanderingTrades 1.9.6 trade config: " + blurb + " (iekcorbar)",
        "#",
        "# The trade result is a plain paper named exactly like the item with the LAST lore line exactly \"%s\"; the %s plugin" % (marker, marker),
        "# swaps it for the real item when the trade is taken. Keep the names and the last lore line as they are.",
        "# Rare on purpose: chance %.2f per trader, %d trade(s) per trader." % (chance, amount),
        "",
        "enabled: true",
        "chance: %.2f" % chance,
        "randomized: true",
        "randomAmount: %d" % amount,
        "invincible: false",
        "customName: NONE",
        "disableHeroOfTheVillageGifts: false",
        "",
        "trades:",
    ]
    for key, name, lore, n, ing, uses in trades:
        lines += ["  %s:" % key, "    maxUses: %d" % uses, "    experienceReward: true", "    ingredients:"]
        for i, (mat, a) in enumerate(ing, 1):
            lines += ["      %d:" % i, "        customname: NONE", "        material: %s" % mat, "        amount: %d" % a]
        lines += ["    result:", "      customname: \"<gold>%s\"" % name, "      material: PAPER", "      amount: %d" % n, "      lore:"]
        for l in lore:
            lines.append("        - \"<gray>%s\"" % l)
        lines.append("        - \"<dark_aqua>%s\"" % marker)
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, fname + ".yml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", fname + ".yml", len(trades), "trades, chance", chance)
