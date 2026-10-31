package com.iekcorbar.kitchen;

import java.util.Locale;

/**
 * Every Kitchen item. All are vanilla items with our own item model
 * ({@code item_model kitchen:<id>}, drawn in tools/make_pack.py) and the id in
 * the item's data ({@code kitchen:item}); foods get vanilla food/consumable
 * components, so eating works like any vanilla food.
 */
enum KItem {
    // ---- seeds and harvest
    RICE("Rice", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on wet farmland (next to water)."),
    SOYBEANS("Soybeans", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland."),
    NAPA_CABBAGE_SEEDS("Napa Cabbage Seeds", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland."),
    NAPA_CABBAGE("Napa Cabbage", Kind.FOOD, 3, 2.4f, Rem.NONE, 64),
    GREEN_ONION("Green Onion", Kind.SEED, 1, 0.6f, Rem.NONE, 64, "Plant on farmland, or eat it raw."),
    DAIKON_SEEDS("Daikon Seeds", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland."),
    DAIKON("Daikon", Kind.FOOD, 3, 3.6f, Rem.NONE, 64),
    TEA_SEEDS("Tea Seeds", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland. The bush regrows after picking."),
    TEA_LEAVES("Tea Leaves", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    CORN("Corn", Kind.SEED, 1, 0.8f, Rem.NONE, 64, "Plant on farmland, or eat it raw. Cut it into cornmeal."),
    CORNMEAL("Cornmeal", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    TOMATO("Tomato", Kind.SEED, 2, 1.2f, Rem.NONE, 64, "Plant on farmland, or eat it straight off the vine."),
    CHILI("Chili Pepper", Kind.SEED, 1, 0.6f, Rem.NONE, 64, "Plant on farmland. Hot."),
    SESAME_SEEDS("Sesame Seeds", Kind.SEED, 1, 0.6f, Rem.NONE, 64, "Plant on farmland."),
    SHISO_SEEDS("Shiso Seeds", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland. Purple or green leaves."),
    SHISO_LEAVES("Shiso Leaves", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    GINGER("Ginger", Kind.SEED, 1, 0.8f, Rem.NONE, 64, "Plant on farmland. Spicy root."),
    WASABI_ROOT("Wasabi Root", Kind.SEED, 2, 2.4f, Rem.NONE, 64, "Plant on wet farmland (next to water). Rare."),
    MIZUNA_SEEDS("Mizuna Seeds", Kind.SEED, 0, 0, Rem.NONE, 64, "Plant on farmland. Peppery leafy green."),
    MIZUNA("Mizuna", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    KABOCHA("Kabocha", Kind.SEED, 3, 3.6f, Rem.NONE, 64, "Plant on farmland. Japanese pumpkin."),
    BAMBOO_SHOOTS("Bamboo Shoots", Kind.SEED, 2, 2.4f, Rem.NONE, 64, "Plant on farmland. Spring delicacy."),
    EGGPLANT("Eggplant", Kind.SEED, 2, 2.0f, Rem.NONE, 64, "Plant on farmland. Nasu dengaku!"),
    // ---- cut on the cutting board
    CABBAGE_LEAF("Cabbage Leaf", Kind.FOOD, 1, 0.6f, Rem.NONE, 64),
    DAIKON_SLICES("Daikon Slices", Kind.FOOD, 1, 1.2f, Rem.NONE, 64),
    CHOPPED_GREEN_ONION("Chopped Green Onion", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    SALMON_SASHIMI("Salmon Sashimi", Kind.FOOD, 2, 1.6f, Rem.NONE, 64),
    TUNA_SASHIMI("Tuna Sashimi", Kind.FOOD, 2, 1.6f, Rem.NONE, 64),
    COD_SLICE("Cod Slice", Kind.FOOD, 1, 0.6f, Rem.NONE, 64),
    FUGU_SASHIMI("Fugu Sashimi", Kind.FOOD, 4, 4.8f, Rem.NONE, 64, "A delicacy. Not every cook gets it right..."),
    BEEF_SLICES("Thin Beef Slices", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    PORK_SLICES("Pork Slices", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    CHICKEN_CUTS("Chicken Cuts", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    KABOCHA_SLICES("Kabocha Slices", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    NORI("Nori", Kind.FOOD, 1, 0.4f, Rem.NONE, 64),
    TREE_BARK("Tree Bark", Kind.INGREDIENT, 0, 0, Rem.NONE, 64, "For compost, or burn it."),
    STRAW("Straw", Kind.INGREDIENT, 0, 0, Rem.NONE, 64, "Cut from tall grass or wheat."),

    // ---- cooked basics
    STEAMED_RICE("Steamed Rice", Kind.FOOD, 3, 2.4f, Rem.NONE, 64),
    EDAMAME("Edamame", Kind.FOOD, 3, 3.0f, Rem.NONE, 64),
    TOFU("Tofu", Kind.FOOD, 2, 2.4f, Rem.NONE, 64),
    MISO("Miso Paste", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    KOJI("Koji Mold", Kind.INGREDIENT, 0, 0, Rem.NONE, 64, "Sprinkle on rice to start fermentation."),
    TSUKEMONO("Tsukemono", Kind.FOOD, 2, 1.6f, Rem.NONE, 64, "Pickled vegetables. Crunchy and salty."),
    SOY_SAUCE("Soy Sauce", Kind.INGREDIENT, 0, 0, Rem.BOTTLE, 16),
    DASHI("Dashi Stock", Kind.INGREDIENT, 0, 0, Rem.BOTTLE, 16),
    RAW_NOODLES("Raw Noodles", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    MOCHI("Mochi", Kind.FOOD, 3, 2.4f, Rem.NONE, 64),
    TAMAGOYAKI("Tamagoyaki", Kind.FOOD, 5, 6f, Rem.NONE, 64),
    TAKUAN("Takuan", Kind.FOOD, 2, 1.6f, Rem.NONE, 64, "Pickled daikon."),
    TEMPURA("Kabocha Tempura", Kind.FOOD, 6, 6f, Rem.NONE, 64),

    // ---- dishes
    ONIGIRI("Onigiri", Kind.FOOD, 5, 6f, Rem.NONE, 64),
    SALMON_NIGIRI("Salmon Nigiri", Kind.FOOD, 4, 5f, Rem.NONE, 64),
    TUNA_NIGIRI("Tuna Nigiri", Kind.FOOD, 4, 5f, Rem.NONE, 64),
    MAKI_ROLL("Maki Roll", Kind.FOOD, 3, 3.6f, Rem.NONE, 64),
    DANGO("Dango", Kind.FOOD, 6, 7.2f, Rem.NONE, 64),
    MISO_SOUP("Miso Soup", Kind.FOOD, 7, 8.4f, Rem.BOWL, 16),
    RAMEN("Ramen", Kind.FOOD, 12, 14f, Rem.BOWL, 16, "Sneak + right-click a block to put it down."),
    GYUDON("Gyudon", Kind.FOOD, 11, 13f, Rem.BOWL, 16),
    OYAKODON("Oyakodon", Kind.FOOD, 11, 13f, Rem.BOWL, 16),
    ODEN("Oden", Kind.FOOD, 9, 10.8f, Rem.BOWL, 16),
    NABE("Nabe Hot Pot", Kind.FOOD, 10, 12f, Rem.BOWL, 16),
    GREEN_TEA("Green Tea", Kind.DRINK, 2, 1f, Rem.BOTTLE, 16, "Speed, and it settles your stomach."),

    // ---- meals to put down and share (see MealType)
    SUSHI_PLATTER("Sushi Platter", Kind.MEAL, 0, 0, Rem.NONE, 1, "Sneak + right-click a block to put it down."),
    BENTO_BOX("Bento Box", Kind.MEAL, 0, 0, Rem.NONE, 1, "Sneak + right-click a block to put it down."),
    DANGO_PLATE("Dango Plate", Kind.MEAL, 0, 0, Rem.NONE, 1, "Sneak + right-click a block to put it down."),
    TEA_SET("Tea Set", Kind.MEAL, 0, 0, Rem.NONE, 1, "Sneak + right-click a block to put it down."),

    // ---- tools
    FLINT_KNIFE("Flint Knife", Kind.TOOL, 0, 0, Rem.NONE, 1, "Cuts food on a cutting board."),
    IRON_KNIFE("Iron Hocho", Kind.TOOL, 0, 0, Rem.NONE, 1, "Cuts food on a cutting board."),
    DIAMOND_KNIFE("Diamond Hocho", Kind.TOOL, 0, 0, Rem.NONE, 1, "Cuts food on a cutting board."),
    NETHERITE_KNIFE("Netherite Hocho", Kind.TOOL, 0, 0, Rem.NONE, 1, "Cuts food on a cutting board."),
    PLATE("Porcelain Plate", Kind.INGREDIENT, 0, 0, Rem.NONE, 16),

    // ---- stations and props (see PropType)
    CUTTING_BOARD("Cutting Board", Kind.PROP, 0, 0, Rem.NONE, 64, "Put food on it, then cut it with a knife."),
    COOKING_POT("Cooking Pot", Kind.PROP, 0, 0, Rem.NONE, 64, "Cooks meals. Needs heat below it:", "campfire, fire, lava, magma or a lit Kamado."),
    KAMADO_STOVE("Kamado Stove", Kind.PROP, 0, 0, Rem.NONE, 64, "Light it with flint and steel.", "Grills food on top, or heats a pot."),
    TANSU_CABINET("Tansu Cabinet", Kind.PROP, 0, 0, Rem.NONE, 64, "Storage cabinet (27 slots)."),
    BAMBOO_BASKET("Bamboo Basket", Kind.PROP, 0, 0, Rem.NONE, 64, "Catches items dropped onto it (9 slots)."),
    RICE_SACK("Rice Sack", Kind.PROP, 0, 0, Rem.NONE, 64, "9 rice, packed."),
    SOYBEAN_CRATE("Soybean Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 soybeans, packed."),
    NAPA_CABBAGE_CRATE("Napa Cabbage Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 napa cabbages, packed."),
    DAIKON_CRATE("Daikon Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 daikon, packed."),
    GREEN_ONION_CRATE("Green Onion Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 green onions, packed."),
    TEA_CRATE("Tea Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 tea leaves, packed."),
    CARROT_CRATE("Carrot Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 carrots, packed."),
    POTATO_CRATE("Potato Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 potatoes, packed."),
    BEETROOT_CRATE("Beetroot Crate", Kind.PROP, 0, 0, Rem.NONE, 64, "9 beetroots, packed."),
    STRAW_BALE("Rice Straw Bale", Kind.PROP, 0, 0, Rem.NONE, 64, "9 straw, bundled."),
    RICH_SOIL("Rich Soil", Kind.PROP, 0, 0, Rem.NONE, 64, "Kitchen crops planted on it grow faster.", "Plant straight on it - no hoe needed."),
    ORGANIC_COMPOST("Organic Compost", Kind.PROP, 0, 0, Rem.NONE, 64, "Turns into Rich Soil after a while.", "Faster with water next to it."),
    TATAMI_MAT("Tatami Mat", Kind.PROP, 0, 0, Rem.NONE, 64, "Floor mat."),
    ZABUTON("Zabuton Cushion", Kind.PROP, 0, 0, Rem.NONE, 64, "Right-click to sit on it."),
    MISO_VAT("Miso Vat", Kind.PROP, 0, 0, Rem.NONE, 64, "Ferments soybeans + salt into miso.", "Takes time - better quality with patience."),
    TSUKEMONO_POT("Tsukemono Pot", Kind.PROP, 0, 0, Rem.NONE, 64, "Pickles vegetables into tsukemono.", "Daikon, napa cabbage, etc."),
    // ---- smoking and drying
    SMOKER("Smoker", Kind.PROP, 0, 0, Rem.NONE, 64, "Smokes meat and fish over a fire.", "Needs heat below it."),
    DRYING_RACK("Drying Rack", Kind.PROP, 0, 0, Rem.NONE, 64, "Dries ingredients in the sun.", "Place under open sky."),
    SMOKED_SALMON("Smoked Salmon", Kind.FOOD, 5, 6f, Rem.NONE, 64),
    SMOKED_BEEF("Smoked Beef", Kind.FOOD, 7, 8f, Rem.NONE, 64),
    DRIED_CHILI("Dried Chili", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    DRIED_HERBS("Dried Herbs", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    SUNDRIED_TOMATO("Sundried Tomato", Kind.FOOD, 2, 1.6f, Rem.NONE, 64),
    // ---- baking
    CLAY_OVEN("Clay Oven", Kind.PROP, 0, 0, Rem.NONE, 64, "Bakes bread and pastries.", "Needs fire below it."),
    STEAMER("Steamer", Kind.PROP, 0, 0, Rem.NONE, 64, "Steams buns and dumplings.", "Place over a heat source."),
    RICE_BREAD("Rice Bread", Kind.FOOD, 5, 6f, Rem.NONE, 64),
    MELONPAN("Melonpan", Kind.FOOD, 6, 7f, Rem.NONE, 64, "Sweet bread with a cookie crust."),
    CASTELLA("Castella", Kind.FOOD, 7, 8f, Rem.NONE, 64, "Honey sponge cake."),
    NIKUMAN("Nikuman", Kind.FOOD, 8, 9f, Rem.NONE, 64, "Steamed meat bun."),
    ANPAN("Anpan", Kind.FOOD, 6, 7f, Rem.NONE, 64, "Sweet bean bun."),
    // ---- mushrooms
    SHIITAKE("Shiitake", Kind.FOOD, 2, 1.6f, Rem.NONE, 64, "Earthy mushroom. Grows on logs."),
    ENOKI("Enoki", Kind.INGREDIENT, 1, 0.8f, Rem.NONE, 64, "Thin white mushroom. Grows in the dark."),
    SHIITAKE_LOG("Shiitake Log", Kind.PROP, 0, 0, Rem.NONE, 64, "Oak log inoculated with shiitake spores.", "Harvest shiitake as they grow."),
    ENOKI_BUNDLE("Enoki Bundle", Kind.PROP, 0, 0, Rem.NONE, 64, "Bundle of enoki. Place in a dark, damp spot.", "Grows more enoki over time."),
    // ---- noodle station
    NOODLE_MAKER("Noodle Maker", Kind.PROP, 0, 0, Rem.NONE, 64, "Makes udon, soba and ramen noodles.", "Needs water below it."),
    UDON("Udon", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    SOBA("Soba", Kind.INGREDIENT, 0, 0, Rem.NONE, 64),
    RAMEN_NOODLES("Ramen Noodles", Kind.INGREDIENT, 0, 0, Rem.NONE, 64);

    enum Kind { SEED, INGREDIENT, FOOD, DRINK, MEAL, TOOL, PROP }

    /** What is left after eating it. */
    enum Rem { NONE, BOWL, BOTTLE }

    final String displayName;
    final Kind kind;
    final int nutrition;
    final float saturation;
    final Rem remainder;
    final int maxStack;
    final String[] lore;

    KItem(String displayName, Kind kind, int nutrition, float saturation, Rem remainder, int maxStack, String... lore) {
        this.displayName = displayName;
        this.kind = kind;
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.remainder = remainder;
        this.maxStack = maxStack;
        this.lore = lore;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean edible() {
        return kind == Kind.DRINK || (nutrition > 0 && kind != Kind.MEAL);
    }

    boolean knife() {
        return name().endsWith("_KNIFE");
    }

    static KItem byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
