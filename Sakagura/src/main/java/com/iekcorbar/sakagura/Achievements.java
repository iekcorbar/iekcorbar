package com.iekcorbar.sakagura;

/** The "Sakagura" tab of the advancement screen: toasts only. Held items are checked every few seconds, a few things by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(Sakagura plugin) {
        Adv a = new Adv(plugin, "sakagura", "Sakagura", "Brewing, ageing and the fruit garden.", "potion", "minecraft:block/barrel_side", true);
        // brewing
        a.add("cauldron", null, "cauldron", "Bubbling Away", "Draw a bottle from a brewing cauldron.", "task");
        a.add("murky", "cauldron", "fermented_spider_eye", "Murky Waters", "Get a Murky Brew.", "task");
        a.add("drink", "cauldron", "potion", "First Pour", "Get a finished drink.", "task");
        a.add("distilled", "drink", "glowstone_dust", "Spirit Level", "Hold a distilled drink.", "task");
        a.add("steam_still", "distilled", "campfire", "A Still That Works", "Get a Steam Still.", "task");
        a.add("cask", "drink", "barrel", "In the Cask", "Get a Sakagura cask.", "task");
        a.add("aged", "cask", "clock", "Patience Pays", "Hold an aged drink.", "goal");
        a.add("good", "drink", "gold_nugget", "Good Taste", "Make a drink of at least four stars.", "goal");
        a.add("perfect", "good", "nether_star", "Master Brewer", "Make a five-star drink.", "challenge").glint();
        a.add("tasted", "drink", "honey_bottle", "Cheers", "Drink a Sakagura drink.", "task");
        a.add("comp_winner", null, "nether_star", "Master of the Still", "Win a brewing competition.", "challenge").glint();
        a.add("sommelier_perfect", "drink", "golden_apple", "Perfect Palate", "Have the sommelier rate your drink 10/10.", "challenge").glint();
        a.add("tipsy", "tasted", "fermented_spider_eye", "Tipsy", "Get drunk enough to stumble.", "task").hidden();
        a.add("passed_out", "tipsy", "red_bed", "Lights Out", "Pass out from drinking.", "goal").hidden();
        a.add("definitely_fine", "tipsy", "player_head", "Definitely Fine", "Get so drunk your name changes.", "task").hidden();
        a.add("kanpai", "tasted", "glass_bottle", "Kanpai!", "Sneak next to another drinker, both holding a drink.", "task").hidden();
        a.add("table", "kanpai", "lantern", "A Table for Three", "Cheers with three or more drinkers at the same time.", "task").hidden();
        a.add("terroir", "cauldron", "water_bucket", "Terroir", "Start a brew next to open river or sea water.", "task").hidden();
        a.add("dont_tell", "murky", "cat_spawn_egg", "Don't Tell the Cat", "Drink a Murky Brew.", "task").hidden();
        // garden
        a.add("planted", null, "wheat_seeds", "Green Fingers", "Plant a garden seed.", "task");
        a.add("harvest", "planted", "sweet_berries", "First Harvest", "Pick a ripe garden crop.", "task");
        a.add("sapling", "harvest", "oak_sapling", "Tree Planting", "Plant an orchard sapling.", "task");
        a.add("tree_harvest", "sapling", "apple", "From the Canopy", "Pick fruit off a grown tree.", "task");
        for (Fruit f : Fruit.values()) {
            a.add("f_" + f.id(), "harvest", "sweet_berries", f.displayName, "Collect " + f.displayName.toLowerCase(java.util.Locale.ROOT) + ".", "task");
        }
        String[] all = new String[Fruit.values().length];
        for (int i = 0; i < all.length; i++) all[i] = "f_" + Fruit.values()[i].id();
        a.add("orchard", "harvest", "golden_apple", "Full Orchard", "Collect every garden crop.", "challenge").allOf(all).glint();

        a.scanner(it -> {
            Brew b = plugin.goods().brewOf(it);
            if (b != null) {
                Recipes.Match m = plugin.recipes().match(b);
                StringBuilder k = new StringBuilder();
                if (m == null || (m.finished() && m.quality() == 0)) return "murky";
                if (m.finished()) {
                    k.append("drink");
                    if (m.quality() >= 8) k.append("|good");
                    if (m.quality() >= 10) k.append("|perfect");
                }
                if (b.distills > 0) k.append("|distilled");
                if (b.years > 0.05) k.append("|aged");
                return k.toString();
            }
            if (plugin.goods().caskWood(it) != null) return "cask";
            if (plugin.goods().isStillItem(it)) return "still";
            String id = plugin.goods().idOf(it);
            if (id == null) id = KitchenShared.sidOf(it);          // shared crops: the canonical item is Kitchen's
            if (id == null) return null;
            Fruit f = Fruit.byId(id);
            return f != null ? "f:" + f.id() : null;
        });
        a.item("murky", "murky").item("drink", "drink").item("good", "good").item("perfect", "perfect")
                .item("distilled", "distilled").item("aged", "aged").item("cask", "cask").item("still", "steam_still");
        for (Fruit f : Fruit.values()) a.item("f:" + f.id(), "f_" + f.id());
        return a;
    }
}
