package com.iekcorbar.kitchen;

/** The "Kitchen" tab of the advancement screen. Toasts only; items are awarded by holding them, a few by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(KitchenPlugin plugin, Items items) {
        Adv a = new Adv(plugin, "kitchen", "Kitchen", "From the paddy to the table.", "bowl", "minecraft:block/bamboo_planks", true);
        // farming
        a.add("seeds", null, "wheat_seeds", "Seeds of Spring", "Get seeds of one of the new crops.", "task");
        a.add("planted", "seeds", "iron_hoe", "Green Thumb", "Plant a kitchen crop.", "task");
        a.add("harvest", "planted", "wheat", "First Harvest", "Pick a ripe crop by hand.", "task");
        a.add("rice", "harvest", "wheat", "Paddy Fields", "Harvest rice.", "task");
        a.add("soybeans", "harvest", "beetroot_seeds", "Beans Means", "Harvest soybeans.", "task");
        a.add("napa", "harvest", "short_grass", "Crunchy Leaves", "Harvest napa cabbage.", "task");
        a.add("onion", "harvest", "green_dye", "Spring Onion", "Harvest green onions.", "task");
        a.add("daikon", "harvest", "carrot", "Long Root", "Harvest daikon.", "task");
        a.add("tea", "harvest", "fern", "Tea Leaves Blowing", "Harvest tea leaves.", "task");
        a.add("corn", "harvest", "golden_carrot", "King of the Kernel", "Harvest corn.", "task");
        a.add("tomato", "harvest", "melon_slice", "Straight Off The Vine", "Harvest tomatoes.", "task");
        a.add("chili", "harvest", "fire_charge", "Bring The Heat", "Harvest chili peppers.", "task");
        a.add("sesame", "harvest", "sugar", "Open Sesame", "Harvest sesame.", "task");
        a.add("cannabis", "harvest", "green_dye", "Green Thumb", "Harvest cannabis.", "task");
        a.add("hemp", "harvest", "string", "Fiber and Thread", "Harvest hemp.", "task");
        a.add("magic_mushroom", "harvest", "red_mushroom", "Trip", "Harvest a magic mushroom.", "task");
        a.add("hash", "cannabis", "coal", "Compressed", "Make hash from dried cannabis.", "task");
        a.add("joint", "hash", "paper", "Roll It Up", "Roll a joint.", "task");
        a.add("hemp_rope", "hemp", "lead", "Strong Rope", "Craft hemp rope.", "task");
        a.add("hemp_cloth", "hemp", "white_wool", "Woven", "Craft hemp cloth.", "task");
        a.add("six_crops", "harvest", "golden_hoe", "The Whole Garden", "Harvest all six new crops.", "challenge").allOf("rice", "soybeans", "napa", "onion", "daikon", "tea");
        a.add("soil", "planted", "podzol", "Rich Soil", "Make Rich Soil from Organic Compost.", "task");
        // tools and stations
        a.add("knife", "seeds", "iron_sword", "Sharp Start", "Get a Flint Knife.", "task");
        a.add("board", "knife", "oak_pressure_plate", "Cutting Board", "Get a Cutting Board.", "task");
        a.add("cut", "board", "iron_axe", "Slice of Life", "Cut something on the Cutting Board.", "task");
        a.add("pot", "board", "cauldron", "Pot Luck", "Get a Cooking Pot.", "task");
        a.add("stove", "pot", "furnace", "Hearth and Home", "Get a Kamado Stove.", "task");
        // dishes
        a.add("tofu", "pot", "white_dye", "Bean Curd", "Make tofu.", "task");
        a.add("steamed_rice", "pot", "bowl", "Plain and Perfect", "Make steamed rice.", "task");
        a.add("onigiri", "steamed_rice", "dried_kelp", "Rice Ball", "Make an onigiri.", "task");
        a.add("nigiri", "onigiri", "cod", "Fresh Cut", "Make salmon or tuna nigiri.", "task");
        a.add("maki", "nigiri", "kelp", "Roll With It", "Make a maki roll.", "task");
        a.add("miso_soup", "tofu", "mushroom_stew", "Morning Bowl", "Make miso soup.", "task");
        a.add("ramen", "miso_soup", "rabbit_stew", "Slurp", "Make ramen.", "goal");
        a.add("gyudon", "steamed_rice", "cooked_beef", "Beef Bowl", "Make gyudon.", "task");
        a.add("oyakodon", "steamed_rice", "cooked_chicken", "Parent and Child", "Make oyakodon.", "task");
        a.add("tempura", "pot", "golden_carrot", "Crispy", "Make tempura.", "task");
        a.add("mochi", "steamed_rice", "snowball", "Pounded Rice", "Make mochi.", "task");
        a.add("dango", "mochi", "slime_ball", "Three on a Stick", "Make dango.", "task");
        a.add("tamagoyaki", "pot", "egg", "Rolled Omelette", "Make tamagoyaki.", "task");
        a.add("oden", "pot", "suspicious_stew", "Winter Warmer", "Make oden.", "task");
        a.add("nabe", "oden", "campfire", "One Pot, Many Friends", "Make nabe.", "goal");
        a.add("green_tea", "tea", "potion", "A Quiet Cup", "Make green tea.", "task");
        a.add("bento", "gyudon", "chest", "Packed Lunch", "Make a bento box.", "goal");
        a.add("washoku", "pot", "enchanted_golden_apple", "Washoku", "Make a bento box, ramen, nabe, a sushi platter and a tea set.", "challenge").allOf("bento", "ramen", "nabe", "sushi_platter", "tea_set").glint();
        // sharing meals and furniture
        a.add("sushi_platter", "maki", "nautilus_shell", "Sushi Platter", "Make a sushi platter.", "goal");
        a.add("tea_set", "green_tea", "flower_pot", "Tea Ceremony", "Get a tea set.", "goal");
        a.add("shared", "sushi_platter", "cake", "Itadakimasu", "Take a bite from a meal that was put down on the table.", "goal");
a.add("comp_winner", null, "nether_star", "Master Chef", "Win a cooking competition.", "challenge").glint().hidden();
a.add("sake_pairing", "shared", "potion", "Sake Pairing", "Eat a meal while drunk from Sakagura sake.", "task").hidden();
a.add("coop_cook", "shared", "campfire", "Two Cooks", "Cook a co-op recipe with a friend.", "task").hidden();
a.add("xp_5", "harvest", "experience_bottle", "Skilled Cook", "Reach cooking level 5.", "task").hidden();
a.add("xp_10", "xp_5", "enchanted_book", "Master of the Kitchen", "Reach cooking level 10.", "challenge").glint().hidden();
a.add("spring_dish", "harvest", "bamboo", "Spring Bamboo", "Cook takenoko gohan in spring.", "task").hidden();
a.add("summer_dish", "harvest", "water_bucket", "Summer Noodles", "Cook hiyashi chuka in summer.", "task").hidden();
a.add("autumn_dish", "harvest", "brown_mushroom", "Autumn Harvest", "Cook matsutake soup in autumn.", "task").hidden();
a.add("winter_dish", "harvest", "campfire", "Winter Warmth", "Cook yaki imo in winter.", "task").hidden();
a.add("spring_salad", "harvest", "green_dye", "Spring Salad", "Cook sansai salad in spring.", "task").hidden();
a.add("summer_somen", "harvest", "water_bucket", "Summer Somen", "Cook somen in summer.", "task").hidden();
a.add("autumn_kinoko", "harvest", "brown_mushroom", "Autumn Kinoko", "Cook kinoko rice in autumn.", "task").hidden();
a.add("winter_zoni", "harvest", "snowball", "Winter Zoni", "Cook zoni in winter.", "task").hidden();
        a.add("tatami", "pot", "brown_carpet", "Tatami Floor", "Get a Tatami Mat.", "task");
        a.add("tears", "cut", "wheat_seeds", "Too Many Tears", "Slice ten daikon in a row.", "task").hidden();
        a.add("plain", "seeds", "bowl", "Plain. Bold.", "Put only rice in a cooking pot.", "task").hidden();
        a.add("sit", "tatami", "white_carpet", "Take a Seat", "Sit on a zabuton.", "task");
        a.add("purr", "sit", "cod", "Cat Cushion", "Sit on a zabuton for five minutes.", "task").hidden();
        a.add("noodle", "plain", "water_bucket", "Waiting for Noodles", "Keep a pot of pure water bubbling for five minutes.", "task").hidden();
        a.add("sitter", "purr", "white_carpet", "The Cat Disagrees", "Stand on a zabuton for five minutes without sitting down.", "task").hidden();
        a.add("furnace", "shared", "cooked_beef", "You Are Not a Furnace", "Eat ten meals in two minutes.", "task").hidden();
        a.add("tansu", "pot", "barrel", "Tansu", "Get a Tansu Cabinet.", "task");
        a.add("basket", "tansu", "bamboo", "Bamboo Basket", "Get a Bamboo Basket.", "task");
        a.scanner(it -> {
            KItem t = items.typeOf(it);
            return t == null ? null : t.id();
        });
        a.item("rice", "rice").item("soybeans", "soybeans").item("napa_cabbage", "napa").item("green_onion", "onion")
                .item("daikon", "daikon").item("tea_leaves", "tea")
                .item("corn", "corn").item("tomato", "tomato").item("chili", "chili").item("sesame_seeds", "sesame")
                .item("napa_cabbage_seeds", "seeds").item("daikon_seeds", "seeds").item("tea_seeds", "seeds")
                .item("rich_soil", "soil").item("flint_knife", "knife").item("iron_knife", "knife").item("cutting_board", "board")
                .item("cooking_pot", "pot").item("kamado_stove", "stove").item("tofu", "tofu").item("steamed_rice", "steamed_rice")
                .item("onigiri", "onigiri").item("salmon_nigiri", "nigiri").item("tuna_nigiri", "nigiri").item("maki_roll", "maki")
                .item("miso_soup", "miso_soup").item("ramen", "ramen").item("gyudon", "gyudon").item("oyakodon", "oyakodon")
                .item("tempura", "tempura").item("mochi", "mochi").item("dango", "dango").item("tamagoyaki", "tamagoyaki")
                .item("oden", "oden").item("nabe", "nabe").item("green_tea", "green_tea").item("bento_box", "bento")
                .item("sushi_platter", "sushi_platter").item("tea_set", "tea_set").item("tatami_mat", "tatami")
                .item("tansu_cabinet", "tansu").item("bamboo_basket", "basket");
        return a;
    }
}
