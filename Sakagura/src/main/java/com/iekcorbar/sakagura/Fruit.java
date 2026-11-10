package com.iekcorbar.sakagura;

import java.util.List;
import java.util.Locale;

/**
 * The garden: crops and their items. Each crop has 3 growth stages (young, growing, ripe);
 * the plant block is a structure void above farmland and the type + stage live in the chunk's
 * plant list (see Garden), the visible plant being a small ItemDisplay with the
 * sakagura:block/&lt;id&gt;_stage&lt;N&gt; model from tools/make_pack.py. Bushes, vines and dwarf fruit
 * trees regrow after picking; the grains (barley, rice, rye, maize) and ginger are dug up
 * and replanted.
 */
enum Fruit {
    BARLEY("Barley", "barley", null, 2, 4, false, 18, List.of("plains", "meadow")),
    HOPS("Hops", "hops", "hop_cuttings", 2, 3, true, 20, List.of("forest", "river")),
    GRAPE("Grapes", "grape", "grape_seeds", 2, 4, true, 24, List.of("savanna", "plains", "badlands")),
    UME("Ume Plums", "ume", "ume_pit", 2, 3, true, 24, List.of("cherry_grove", "forest")),
    YUZU("Yuzu", "yuzu", "yuzu_seeds", 1, 3, true, 24, List.of("jungle", "bamboo")),
    PEACH("Peaches", "peach", "peach_pit", 1, 3, true, 24, List.of("meadow", "flower_forest")),
    CHERRY("Cherries", "cherry", "cherry_pit", 2, 4, true, 22, List.of("cherry_grove")),
    STRAWBERRY("Strawberries", "strawberry", null, 2, 4, true, 16, List.of("forest", "meadow", "flower")),
    BLUEBERRY("Blueberries", "blueberry", null, 2, 4, true, 16, List.of("taiga", "grove")),
    JUNIPER("Juniper Berries", "juniper", null, 2, 3, true, 20, List.of("taiga", "snowy", "windswept")),
    GINGER("Ginger", "ginger", null, 1, 3, false, 18, List.of("jungle", "swamp")),
    APPLE("Apples", "apple", "apple_pip", 2, 4, true, 26, List.of("plains", "meadow", "forest")),
    PEAR("Pears", "pear", "pear_pip", 2, 3, true, 26, List.of("forest", "flower_forest")),
    APRICOT("Apricots", "apricot", "apricot_kernel", 1, 3, true, 24, List.of("savanna", "badlands", "plains")),
    MIKAN("Mikan Oranges", "mikan", "mikan_seeds", 2, 4, true, 22, List.of("jungle", "bamboo")),
    KIWI("Kiwis", "kiwi", "kiwi_seeds", 2, 3, true, 22, List.of("jungle", "forest")),
    RICE("Rice", "rice", null, 3, 5, false, 16, List.of("swamp", "river", "jungle")),
    RYE("Rye", "rye", null, 2, 4, false, 18, List.of("plains", "taiga")),
    MAIZE("Maize", "maize", null, 1, 3, false, 20, List.of("plains", "savanna")),
    TOMATO("Tomatoes", "tomato", "tomato_seeds", 2, 4, true, 14, List.of("plains", "sunflower")),
    RASPBERRY("Raspberries", "raspberry", null, 2, 4, true, 16, List.of("forest", "taiga")),
    BLACKCURRANT("Blackcurrants", "blackcurrant", null, 2, 4, true, 18, List.of("forest", "grove")),
    ELDERBERRY("Elderberries", "elderberry", null, 2, 5, true, 20, List.of("forest", "dark_forest")),
    CRANBERRY("Cranberries", "cranberry", null, 2, 4, true, 18, List.of("swamp", "grove")),
    GOOSEBERRY("Gooseberries", "gooseberry", null, 2, 4, true, 18, List.of("forest")),
    MULBERRY("Mulberries", "mulberry", null, 2, 5, true, 20, List.of("plains", "savanna")),
    PERSIMMON("Persimmons", "persimmon", "persimmon_pip", 1, 3, true, 24, List.of("forest")),
    LOQUAT("Loquats", "loquat", "loquat_stone", 1, 3, true, 22, List.of("jungle")),
    POMEGRANATE("Pomegranates", "pomegranate", "pomegranate_seeds", 1, 3, true, 24, List.of("savanna", "badlands")),
    FIG("Figs", "fig", "fig_cuttings", 1, 3, true, 22, List.of("savanna", "plains")),
    QUINCE("Quinces", "quince", "quince_pip", 1, 3, true, 24, List.of("forest", "flower_forest")),
    OLIVE("Olives", "olive", "olive_stone", 2, 4, true, 24, List.of("savanna", "badlands")),
    DATE("Dates", "date", "date_pit", 1, 3, true, 26, List.of("badlands", "jungle")),
    LYCHEE("Lychees", "lychee", "lychee_seed", 2, 4, true, 24, List.of("jungle", "bamboo")),
    PASSIONFRUIT("Passion Fruits", "passionfruit", "passionfruit_seeds", 1, 3, true, 22, List.of("jungle")),
    VANILLA("Vanilla Pods", "vanilla", "vanilla_cuttings", 1, 2, true, 30, List.of("jungle", "bamboo")),
    // 30-10 golf 0.5.0: twaalf nieuwe gewassen. BELANGRIJK: alleen ACHTERAPPENDEN,
    // de enum-volgorde staat in de chunk-data als ordinal (zie Garden.encode).
    SUDACHI("Sudachi", "sudachi", "sudachi_seeds", 2, 3, true, 22, List.of("jungle", "bamboo")),
    SHISO("Shiso Leaves", "shiso", null, 2, 4, true, 14, List.of("forest", "jungle")),
    WASABI("Wasabi Roots", "wasabi", null, 1, 2, false, 20, List.of("swamp", "river")),
    DAIKON("Daikon", "daikon", null, 1, 2, false, 22, List.of("plains", "flower_forest")),
    CHILI("Chili Peppers", "chili", "chili_seeds", 2, 4, true, 16, List.of("jungle", "savanna")),
    MANGO("Mangoes", "mango", "mango_pit", 1, 3, true, 26, List.of("jungle")),
    PAPAYA("Papayas", "papaya", "papaya_seeds", 1, 2, true, 24, List.of("jungle")),
    GUAVA("Guavas", "guava", "guava_seeds", 2, 3, true, 22, List.of("savanna", "jungle")),
    RAMBUTAN("Rambutan", "rambutan", "rambutan_seed", 2, 3, true, 24, List.of("jungle")),
    LONGAN("Longans", "longan", "longan_seed", 2, 4, true, 24, List.of("jungle")),
    MANGOSTEEN("Mangosteens", "mangosteen", "mangosteen_seeds", 1, 3, true, 26, List.of("jungle")),
    DRAGONFRUIT("Dragon Fruits", "dragonfruit", "dragonfruit_seeds", 1, 2, true, 24, List.of("swamp", "jungle"));

    static final int STAGES = 3;

    final String displayName, produce, seed;
    final int minDrop, maxDrop;
    /** Picked by hand it drops back to stage 1 (bushes / trees) instead of being replanted. */
    final boolean regrows;
    final int minutes;
    final List<String> wildBiomes;

    Fruit(String displayName, String produce, String seed, int minDrop, int maxDrop, boolean regrows, int minutes, List<String> wildBiomes) {
        this.displayName = displayName;
        this.produce = produce;
        this.seed = seed;
        this.minDrop = minDrop;
        this.maxDrop = maxDrop;
        this.regrows = regrows;
        this.minutes = minutes;
        this.wildBiomes = wildBiomes;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The item planted: its own seed item, or the harvest itself (barley grain, berries, ginger root). */
    String plantItem() {
        return seed != null ? seed : produce;
    }

    static Fruit byId(String id) {
        if (id == null) return null;
        for (Fruit f : values()) if (f.produce.equals(id) || f.id().equals(id)) return f;
        return null;
    }

    static Fruit byPlantItem(String itemId) {
        if (itemId == null) return null;
        for (Fruit f : values()) if (f.plantItem().equals(itemId)) return f;
        return null;
    }
}
