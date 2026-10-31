package com.iekcorbar.relics;

import org.bukkit.entity.Player;

/** The "Relics" tab of the advancement screen: toasts only. Items are awarded by holding them, uses by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(RelicsPlugin plugin) {
        Adv a = new Adv(plugin, "relics", "Relics", "Props from the old world and the festivals.", "lantern", "minecraft:block/blackstone", true);
        a.add("first", null, "armor_stand", "Interior Decorator", "Place a relic.", "task");
        a.add("shadow", "first", "bone_block", "Shoutlander's Shadow", "Get a relic of Shoutlander's Shadow.", "task");
        a.add("xmas", "first", "spruce_sapling", "Xmas Town", "Get an Xmas Town relic.", "task");
        a.add("new_year", "first", "firework_rocket", "New Year", "Get a New Year relic.", "task");
        a.add("easter", "first", "egg", "Easter", "Get an Easter relic.", "task");
        a.add("patrick", "first", "lime_dye", "Luck of the Irish", "Get a St. Patrick's Day relic.", "task");
        a.add("valentine", "first", "pink_dye", "Valentine's", "Get a Valentine's relic.", "task");
        a.add("wish", "valentine", "gold_nugget", "Wish Upon a Heart", "Drop a coin in the wishing well.", "task");
        a.add("curio", "first", "spyglass", "Curiosities", "Get a curiosity.", "task");
        a.add("wish_tree", "curio", "cherry_sapling", "Tanabata", "Hang a wish in the Wish Tree.", "task");
        a.add("onsen", "first", "snow_block", "Winter Onsen", "Get a Winter Onsen relic.", "task");
        a.add("halloween", "first", "jack_o_lantern", "Spooky Season", "Get a Halloween Party relic.", "task");
        a.add("blood_token", "halloween", "amethyst_shard", "Coin of the Crimson Hour", "Place a Blood Token.", "task");
        a.add("blood_normal", "blood_token", "brick", "A Plain One", "Hold a plain Blood Token in your hand.", "task");
        a.add("blood_blessed", "blood_token", "glowstone", "Blessed by the Moon", "Hold a Blessed Blood Token - place one while the blood moon is up.", "task");
        a.add("blood_set", "blood_token", "nether_star", "Under the Crimson Moon", "Hold a plain and a Blessed Blood Token at the same time.", "challenge").allOf("blood_normal", "blood_blessed").glint();
        a.add("treat", "first", "cookie", "Sweet Tooth", "Eat or drink a treat.", "task");
        a.add("disc", "first", "music_disc_cat", "Needle Drop", "Get a Relics music disc.", "task");
        // actions
        a.add("sit", "first", "oak_stairs", "Take a Seat", "Sit on a relic.", "task");
        a.add("gift", "xmas", "chest", "Wrapped Up", "Use a gift box.", "task");
        a.add("advent", "xmas", "clock", "Counting Down", "Open an advent calendar door.", "task");
        a.add("music", "first", "note_block", "Play It Again", "Make a relic play music.", "task");
        a.add("egg_found", "easter", "turtle_egg", "Egg Hunter", "Find an egg that someone else hid.", "task");
        a.add("gold", "patrick", "gold_nugget", "End of the Rainbow", "Take gold from a pot of gold.", "task");
        a.add("telescope", "curio", "glass_pane", "Stargazer", "Look through the brass telescope.", "task");
        a.add("firework", "new_year", "firework_star", "Ring It In", "Light a firework launcher.", "task");
        a.add("bell", "new_year", "bell", "Joya no Kane", "Ring the temple bell.", "task");
        a.add("skip", "music", "jukebox", "Skip... Skip... Skip", "Click the gramophone a hundred times.", "task").hidden();
        a.add("umbrella", "curio", "lightning_rod", "Rain or Shine", "Stand next to a tesla coil holding a wagasa stand.", "task").hidden();
        a.add("lucky_paw", "curio", "gold_ingot", "Lucky Paw", "Click the maneki-neko seven times.", "task").hidden();
        a.add("trap", "egg_found", "gunpowder", "It Was a Trap", "Find an egg while the hider watches.", "task").hidden();
        a.add("collector", "first", "nether_star", "Collector", "Get relics from all collections.", "challenge")
                .allOf("shadow", "xmas", "new_year", "easter", "patrick", "valentine", "curio", "halloween", "onsen").glint();

        a.scanner(it -> {
            RelicType t = plugin.relics().typeOf(it);
            if (t == RelicType.BLOOD_TOKEN)                        // the crimson coin counts double (0.6.0)
                return plugin.relics().blessedOf(it) ? "token_blessed" : "token_plain";
            if (t != null) return "c:" + t.collection.id();
            if (plugin.treats().typeOf(it) != null) return "treat";
            return plugin.discs().isDisc(it) ? "disc" : null;
        });
        for (RelicType.Collection c : RelicType.Collection.values()) {
            a.add("full_" + c.id(), "collector", "chest", "Complete: " + c.title, "Own every relic of " + c.title + ".", "challenge");
        }
        for (RelicType.Collection c : RelicType.Collection.values()) a.item("c:" + c.id(), c.id());
        a.item("treat", "treat").item("disc", "disc");
        a.item("token_plain", "blood_normal").item("token_blessed", "blood_blessed");
        return a;
    }

    /** Called when a player right-clicks a placed relic. */
    static void used(Adv adv, Player p, RelicType t) {
        String id = switch (t.use) {
            case SEAT -> "sit";
            case GIFT -> "gift";
            case CALENDAR -> "advent";
            case MUSIC, BONES, HARP, GRAMOPHONE -> "music";
            case EGG -> "egg_found";
            case GOLD -> "gold";
            case TELESCOPE -> "telescope";
            case LAUNCH -> "firework";
            case BELL -> "bell";
            case WISH -> "wish";
            default -> null;
        };
        if (id != null) adv.grant(p, id);
    }
}
