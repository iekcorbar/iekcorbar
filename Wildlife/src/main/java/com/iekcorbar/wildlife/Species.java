package com.iekcorbar.wildlife;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Locale;

/**
 * Wildlife species. Each one is an invisible vanilla base mob (movement, hitbox,
 * swimming/flying) with an FMM model on top (tools/make_animals.py: wl_&lt;id&gt;,
 * wl_&lt;id&gt;_rare). Base mobs are scaled with the SCALE attribute so the hitbox
 * matches the model (real-world size: 1 block = 1 m). Animals are only ever
 * added next to vanilla mobs - nothing vanilla is replaced or removed.
 */
enum Species {
    DEER("Red Deer", EntityType.HORSE, 1.1, 14, 0.055, Habitat.LAND, Time.ANY, 1, 3, 8,
            List.of("forest", "taiga", "meadow", "cherry_grove", "grove"), "leather",
            "A shy grazer of forests and meadows. Bucks carry branching antlers.", "Look in quiet forests and meadows.",
            "wildlife:deer.ambient", "wildlife:deer.snort", "wildlife:deer.snort", 1f),
    BEAR("Brown Bear", EntityType.POLAR_BEAR, 1.15, 30, 0.055, Habitat.LAND, Time.ANY, 1, 1, 3,
            List.of("taiga", "old_growth", "dark_forest", "grove"), "sweet_berries",
            "Strong and usually calm - unless you hurt it. Sleeps at night, fishes the riverbank and loves berries.", "Deep in old taiga forests.",
            "wildlife:bear.ambient", "wildlife:bear.hurt", "wildlife:bear.hurt", 1f),
    BOAR("Wild Boar", EntityType.GOAT, 1.2, 14, 0.18, Habitat.LAND, Time.ANY, 1, 3, 6,
            List.of("forest", "dark_forest", "wooded_badlands", "savanna_plateau"), "porkchop",
            "Tusked and bad-tempered; it charges when it feels cornered.", "Rooting around in dense forests.",
            "wildlife:boar.ambient", "wildlife:boar.hurt", "wildlife:boar.hurt", 1f),
    DUCK("Mallard", EntityType.CHICKEN, 1.4, 6, 0.03, Habitat.WATER, Time.ANY, 2, 4, 8,
            List.of("river", "swamp", "mangrove"), "feather",
            "Dabbles on rivers and ponds. The drake has a glossy green head.", "Rivers and swamp ponds.",
            "wildlife:duck.ambient", "wildlife:duck.hurt", "wildlife:duck.hurt", 1f),
    BUTTERFLY("Monarch Butterfly", EntityType.BEE, 0.42, 3, 0.25, Habitat.AIR, Time.DAY, 1, 3, 6,
            List.of("flower_forest", "meadow", "sunflower_plains", "plains", "cherry_grove"), "orange_tulip",
            "Flutters from flower to flower and helps crops grow.", "Flower fields on sunny days.",
            "wildlife:insect.flutter", null, null, 1f),
    FIREFLY("Firefly", EntityType.BEE, 0.25, 2, 0.2, Habitat.AIR, Time.NIGHT, 3, 6, 12,
            List.of("swamp", "mangrove", "forest", "birch_forest"), "glow_berries",
            "Its glowing tail lights up warm nights.", "Swamps and woods after dark.",
            null, null, null, 1f),
    SNAIL("Garden Snail", EntityType.ARMADILLO, 0.5, 6, 0.01, Habitat.LAND, Time.ANY, 1, 2, 4,
            List.of("forest", "swamp", "dark_forest", "birch_forest", "lush_caves"), "nautilus_shell",
            "Slow and patient. It pulls back into its shell when startled. More come out in the rain.", "Damp forests, especially when it rains.",
            null, "wildlife:snail.hurt", "wildlife:snail.hurt", 1f),
    HEDGEHOG("Hedgehog", EntityType.ARMADILLO, 0.65, 8, 0.025, Habitat.LAND, Time.ANY, 1, 1, 3,
            List.of("forest", "plains", "meadow", "birch_forest", "flower_forest"), "brown_mushroom",
            "Curls into a prickly ball when something comes too close.", "Hedges and forest edges.",
            "wildlife:hedgehog.ambient", "entity.armadillo.hurt", "entity.armadillo.death", 1.5f),
    CAPYBARA("Capybara", EntityType.PIG, 1.4, 12, 0.03, Habitat.WATER, Time.ANY, 2, 4, 6,
            List.of("jungle", "swamp", "mangrove"), "lily_pad",
            "The calmest animal around. Others like to sit on it.", "Riverbanks in jungles and swamps.",
            "wildlife:capybara.ambient", "entity.pig.hurt", "entity.pig.death", 0.55f),
    TORTOISE("Tortoise", EntityType.TURTLE, 0.8, 20, 0.012, Habitat.LAND, Time.DAY, 1, 1, 3,
            List.of("savanna", "jungle", "badlands"), "turtle_scute",
            "Slow, long-lived and armoured. Hides in its shell when you come close.", "Warm savannas and jungle edges.",
            "entity.turtle.ambient_land", "entity.turtle.hurt_land", "entity.turtle.death_land", 0.9f),
    LIZARD("Wall Lizard", EntityType.OCELOT, 0.45, 6, 0.035, Habitat.LAND, Time.DAY, 1, 2, 5,
            List.of("desert", "badlands", "savanna", "stony_shore"), "cactus",
            "Quick and wary. If you catch its tail, it may leave it behind - it grows back.", "Sunny rocks in deserts and badlands.",
            null, "wildlife:lizard.hurt", "wildlife:lizard.hurt", 1f),
    CRAB("Shore Crab", EntityType.PIG, 0.42, 8, 0.02, Habitat.SHORE, Time.ANY, 2, 4, 8,
            List.of("beach", "mangrove", "stony_shore"), "tube_coral",
            "Scuttles sideways along the shore, claws raised.", "Sandy beaches and mangroves.",
            "wildlife:crab.ambient", "entity.armadillo.hurt", "entity.armadillo.death", 1.6f),
    SONGBIRD("Songbird", EntityType.PARROT, 0.65, 4, 0.25, Habitat.AIR, Time.DAY, 1, 3, 8,
            List.of("forest", "birch_forest", "plains", "meadow", "flower_forest", "cherry_grove"), "wheat_seeds",
            "Blue jays, cardinals, canaries and sparrows - each with its own colours.", "Treetops and meadows by day.",
            "wildlife:songbird.ambient", "wildlife:songbird.hurt", "wildlife:songbird.hurt", 1f),
    DRAGONFLY("Emperor Dragonfly", EntityType.BEE, 0.42, 3, 0.3, Habitat.AIR, Time.DAY, 1, 2, 5,
            List.of("river", "swamp", "mangrove"), "sugar_cane",
            "Darts over water hunting midges, wings almost invisible.", "Above rivers and swamps on warm days.",
            "wildlife:dragonfly.buzz", null, null, 1f),
    ELEPHANT("African Elephant", EntityType.COW, 2.1, 60, 0.06, Habitat.LAND, Time.ANY, 2, 4, 3,
            List.of("savanna"), "bone",
            "The biggest animal around. A herd ambles across the savanna and the calves stay close to their mothers.",
            "Wide, dry savannas - listen for the rumbling.",
            "wildlife:elephant.ambient", "wildlife:elephant.hurt", "wildlife:elephant.hurt", 1f),
    ZEBRA("Plains Zebra", EntityType.HORSE, 1.05, 22, 0.07, Habitat.LAND, Time.ANY, 3, 6, 7,
            List.of("savanna"), "black_dye",
            "Every zebra has its own stripes. They graze in groups and bark when something startles them.",
            "Open savanna, usually in a group.",
            "wildlife:zebra.ambient", "wildlife:zebra.hurt", "wildlife:zebra.hurt", 1f),
    TANUKI("Tanuki", EntityType.PIG, 0.8, 10, 0.035, Habitat.LAND, Time.NIGHT, 1, 2, 4,
            List.of("bamboo", "jungle", "forest", "taiga", "old_growth"), "sweet_berries",
            "A night forager with cream cheeks and a ringed tail. It watches you from the edge of the bamboo and waddles off.",
            "Bamboo thickets and old forests, after dark.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.1f),
    CRANE("Red-Crowned Crane", EntityType.CHICKEN, 1.9, 10, 0.035, Habitat.WATER, Time.ANY, 1, 2, 2,
            List.of("swamp", "mangrove", "river"), "wheat",
            "Tall and quiet, it stalks the shallows with slow high steps. A pair stays together for life.",
            "Marsh edges and river flats - often two of them, standing very still.",
            null, "entity.chicken.hurt", "entity.chicken.death", 0.5f),
    KOI("Koi", EntityType.COD, 1.1, 6, 0.05, Habitat.POND, Time.ANY, 2, 4, 6,
            List.of("river", "swamp", "lush_caves", "beach"), "cod_bucket",
            "A pond carp in white, orange and black. Look for the gold one - old keepers say it brings luck.",
            "Gliding just under the surface of rivers and swamp pools.",
            null, "entity.cod.hurt", "entity.cod.death", 0.8f),
    MACAQUE("Japanese Macaque", EntityType.PIG, 0.9, 12, 0.04, Habitat.LAND, Time.DAY, 3, 6, 4,
            List.of("bamboo", "jungle", "taiga", "old_growth", "cherry_grove", "grove", "windswept"), "apple",
            "It sits in a troop, chatters about you, and steals apples the moment you turn around.",
            "Troops of three or more in bamboo thickets and snowy woods.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.35f),
    KANGAROO("Red Kangaroo", EntityType.RABBIT, 2.2, 14, 0.055, Habitat.LAND, Time.ANY, 1, 3, 4,
            List.of("savanna", "plains", "badlands"), "wheat",
            "Two feet and a tail that doubles as a chair. When it does go, it goes.",
            "Grassy plains and savanna edges, hopping along.",
            "entity.rabbit.ambient", "entity.fox.hurt", "entity.fox.death", 0.7f),
    OSTRICH("Common Ostrich", EntityType.CHICKEN, 2.6, 16, 0.085, Habitat.LAND, Time.DAY, 2, 4, 3,
            List.of("savanna", "plains", "badlands", "desert"), "feather",
            "A bird with the wings of an idea it never implemented. It outruns almost everything on two toes.",
            "Open savanna, running like nothing is coming.",
            null, "entity.chicken.hurt", "entity.chicken.death", 0.35f),
    CROCODILE("Siamese Crocodile", EntityType.PIG, 0.9, 20, 0.028, Habitat.WATER, Time.ANY, 1, 2, 3,
            List.of("swamp", "jungle", "mangrove", "river"), "bone",
            "A log with opinions. It lies at the water's edge for hours, and only the eyes move.",
            "Still water in swamps and jungles - look for eyes just above the surface.",
            null, "entity.wolf.hurt", "entity.wolf.death", 0.55f),
    KOALA("Koala", EntityType.OCELOT, 1.0, 10, 0.02, Habitat.LAND, Time.ANY, 1, 2, 3,
            List.of("jungle", "bamboo"), "oak_leaves",
            "Eucalyptus leaves, sleep, repeat. A small bear-shaped cloud of nap.",
            "Asleep at the foot of jungle and bamboo trees.",
            "entity.polar_bear.ambient", "entity.polar_bear.hurt", "entity.polar_bear.death", 1.4f),
    MOLE("European Mole", EntityType.ARMADILLO, 0.35, 6, 0.03, Habitat.LAND, Time.ANY, 1, 1, 5,
            List.of("forest", "plains", "meadow", "birch", "flower", "sunflower", "cherry"), "clay",
            "You mostly meet its work: a fresh pile of dug-up earth. The mole itself does not stay above ground long.",
            "Beside a molehill in lawns and forest edges.",
            null, "entity.armadillo.hurt", "entity.armadillo.death", 1.6f),
    RHINO("White Rhinoceros", EntityType.COW, 1.8, 40, 0.045, Habitat.LAND, Time.ANY, 1, 2, 2,
            List.of("savanna"), "iron_ingot",
            "Armour on four legs, horn out front. Calm until its path is blocked - then the ground is a problem.",
            "Wallowing in the dry savanna dust.",
            "entity.cow.ambient", "entity.cow.hurt", "entity.cow.death", 0.55f),
    PENGUIN("Emperor Penguin", EntityType.CHICKEN, 1.5, 8, 0.035, Habitat.WATER, Time.DAY, 3, 6, 4,
            List.of("frozen", "ice", "snowy", "beach"), "packed_ice",
            "A tuxedo that swims. It waddles in a colony and brays like a donkey about it.",
            "Ice coasts and frozen shores, always several together.",
            "entity.donkey.ambient", "entity.donkey.hurt", "entity.donkey.death", 1.25f),
    ORCA("Orca", EntityType.DOLPHIN, 3.2, 60, 0.09, Habitat.POND, Time.ANY, 1, 3, 1,
            List.of("ocean"), "water_bucket",
            "The wolf of the sea in tuxedo colours. It breaches next to boats for the same reason dolphins do: because it can.",
            "Out in the deep ocean - listen for the blow.",
            "entity.dolphin.ambient", "entity.dolphin.hurt", "entity.dolphin.death", 0.8f),
    PIRANHA("Red-bellied Piranha", EntityType.COD, 0.5, 4, 0.06, Habitat.POND, Time.ANY, 4, 8, 6,
            List.of("river", "swamp", "jungle"), "pufferfish",
            "Small, silver, red-bellied and never alone. One is nothing; eight at your ankle is a decision.",
            "Warm rivers and swamp water, in shimmering shoals.",
            null, "entity.cod.hurt", "entity.cod.death", 1.5f),
    MACAW("Scarlet Macaw", EntityType.PARROT, 1.4, 6, 0.2, Habitat.AIR, Time.DAY, 1, 3, 4,
            List.of("jungle"), "cookie",
            "A tail longer than its body, a beak like bolt cutters, and the vocabulary of a sailor.",
            "Over the jungle canopy - yes, it may land on your shoulder.",
            "entity.parrot.ambient", "entity.parrot.hurt", "entity.parrot.death", 0.85f);

    enum Habitat { LAND, WATER, SHORE, AIR, POND }

    enum Time { ANY, DAY, NIGHT }

    final String displayName;
    final EntityType base;
    final double scale;
    final double health, speed;
    final Habitat habitat;
    final Time time;
    final int groupMin, groupMax;
    /** Spawn weight among the species that fit a biome. */
    final int weight;
    /** Parts of biome keys this species lives in (e.g. "forest" matches birch_forest too). */
    final List<String> biomes;
    final String icon;
    final String notes, hint;
    final String ambient, hurt, death;
    final float pitch;

    Species(String displayName, EntityType base, double scale, double health, double speed, Habitat habitat, Time time,
            int groupMin, int groupMax, int weight, List<String> biomes, String icon, String notes, String hint,
            String ambient, String hurt, String death, float pitch) {
        this.displayName = displayName;
        this.base = base;
        this.scale = scale;
        this.health = health;
        this.speed = speed;
        this.habitat = habitat;
        this.time = time;
        this.groupMin = groupMin;
        this.groupMax = groupMax;
        this.weight = weight;
        this.biomes = biomes;
        this.icon = icon;
        this.notes = notes;
        this.hint = hint;
        this.ambient = ambient;
        this.hurt = hurt;
        this.death = death;
        this.pitch = pitch;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Safari shyness tier (0.6.0). 0 = never bolts (hedgehog curls, tortoise hides, capybara simply
     * doesn't care, the big/dangerous ones don't run from us); 1 = nervous close-up only; 2 = bolts
     * from any unsneaking player within 18 blocks; 3 = bolts from 24 and takes real patience.
     * Also drives how long the spyglass must stay on the animal (Journal.observeChecks).
     */
    int shy() {
        return switch (this) {
            case DEER, ZEBRA, KANGAROO, SONGBIRD, MACAW, LIZARD, MOLE, TANUKI, CRANE -> 3;
            case BOAR, DUCK, CRAB, KOALA, MACAQUE, OSTRICH, ELEPHANT -> 2;
            default -> 0;
        };
    }

    /** Spyglass 5-tick checks needed to record a sighting: longer for shyer animals. */
    int observeChecks() {
        return switch (shy()) {
            case 3 -> 16; // 4 s of steady, undisturbed watching
            case 2 -> 8;  // 2 s
            default -> 4; // 1 s
        };
    }

    /** Flight radius for this animal's tier; 0 = never flees a player. */
    int fleeRadius() {
        return switch (shy()) {
            case 3 -> 24;
            case 2 -> 18;
            case 1 -> 10;
            default -> 0;
        };
    }

    /** Common looks (songbirds come in four colours, macaws in three). */
    List<String> models() {
        if (this == SONGBIRD) return List.of("wl_songbird", "wl_songbird_cardinal", "wl_songbird_canary", "wl_songbird_sparrow");
        if (this == MACAW) return List.of("wl_macaw", "wl_macaw_blue", "wl_macaw_green");
        return List.of("wl_" + id());
    }

    String rareModel() {
        return "wl_" + id() + "_rare";
    }

    String rareName() {
        return switch (this) {
            case DEER -> "Albino Red Deer";
            case BEAR -> "Cinnamon Bear";
            case BOAR -> "Golden Boar";
            case DUCK -> "White Mallard";
            case BUTTERFLY -> "Blue Morpho";
            case FIREFLY -> "Blue Firefly";
            case SNAIL -> "Golden Snail";
            case HEDGEHOG -> "Albino Hedgehog";
            case CAPYBARA -> "Capybara with a Yuzu";
            case TORTOISE -> "Mossy Tortoise";
            case LIZARD -> "Blue Wall Lizard";
            case CRAB -> "Blue Crab";
            case SONGBIRD -> "Golden Songbird";
            case DRAGONFLY -> "Scarlet Dragonfly";
            case ELEPHANT -> "White Elephant";
            case ZEBRA -> "Golden Zebra";
            case TANUKI -> "Charcoal Tanuki";
            case CRANE -> "Golden Crane";
            case KOI -> "Golden Koi";
            case MACAQUE -> "Snow Macaque";
            case KANGAROO -> "Albino Roo";
            case OSTRICH -> "Brown Ostrich";
            case CROCODILE -> "Black Caiman";
            case KOALA -> "Albino Koala";
            case MOLE -> "Golden Mole";
            case RHINO -> "Black Rhino";
            case PENGUIN -> "Golden Penguin";
            case ORCA -> "White Orca";
            case PIRANHA -> "Golden Piranha";
            case MACAW -> "Albino Macaw";
        };
    }

    /** What an adult must be fed to make it want a baby (right-click). */
    boolean isFood(Material m) {
        return switch (this) {
            case DEER -> m == Material.WHEAT || m == Material.APPLE || m == Material.CARROT;
            case BEAR -> m == Material.SWEET_BERRIES || m == Material.SALMON || m == Material.HONEY_BOTTLE;
            case BOAR -> m == Material.CARROT || m == Material.POTATO || m == Material.BEETROOT;
            case DUCK, SONGBIRD -> m == Material.WHEAT_SEEDS || m == Material.BEETROOT_SEEDS || m == Material.MELON_SEEDS || m == Material.PUMPKIN_SEEDS;
            case BUTTERFLY -> Tag.SMALL_FLOWERS.isTagged(m);
            case FIREFLY -> m == Material.GLOW_BERRIES;
            case SNAIL -> m == Material.DRIED_KELP || m == Material.SEAGRASS;
            case HEDGEHOG -> m == Material.BROWN_MUSHROOM || m == Material.RED_MUSHROOM;
            case CAPYBARA -> m == Material.MELON_SLICE || m == Material.SUGAR_CANE;
            case TORTOISE -> m == Material.SEAGRASS;
            case LIZARD -> m == Material.SPIDER_EYE;
            case CRAB -> m == Material.COD || m == Material.KELP;
            case DRAGONFLY -> m == Material.SUGAR;
            case ELEPHANT -> m == Material.WHEAT || m == Material.SUGAR_CANE || m == Material.APPLE;
            case ZEBRA -> m == Material.WHEAT || m == Material.HAY_BLOCK;
            case TANUKI -> m == Material.SWEET_BERRIES || m == Material.APPLE || m == Material.CARROT;
            case CRANE -> m == Material.WHEAT_SEEDS || m == Material.COD || m == Material.SALMON;
            case KOI -> m == Material.KELP || m == Material.DRIED_KELP || m == Material.BREAD;
            case MACAQUE -> m == Material.APPLE || m == Material.CARROT || m == Material.POTATO;
            case KANGAROO -> m == Material.WHEAT || m == Material.HAY_BLOCK || m == Material.CARROT;
            case OSTRICH -> m == Material.WHEAT_SEEDS || m == Material.BEETROOT_SEEDS || m == Material.WHEAT;
            case CROCODILE -> m == Material.COD || m == Material.SALMON || m == Material.BEEF;
            case KOALA -> Tag.LEAVES.isTagged(m) || m == Material.APPLE;
            case MOLE -> m == Material.SPIDER_EYE;
            case RHINO -> m == Material.WHEAT || m == Material.HAY_BLOCK || m == Material.MELON_SLICE;
            case PENGUIN -> m == Material.COD || m == Material.SALMON;
            case ORCA -> m == Material.COD || m == Material.SALMON || m == Material.TROPICAL_FISH;
            case PIRANHA -> m == Material.BEEF || m == Material.PORKCHOP || m == Material.CHICKEN || m == Material.MUTTON;
            case MACAW -> m == Material.WHEAT_SEEDS || m == Material.MELON_SEEDS || m == Material.COCOA_BEANS;
        };
    }

    /** Short text for the wiki / the lore of a spawn egg. */
    String foodText() {
        return switch (this) {
            case DEER -> "wheat, apples or carrots";
            case BEAR -> "sweet berries, salmon or honey";
            case BOAR -> "carrots, potatoes or beetroot";
            case DUCK, SONGBIRD -> "seeds";
            case BUTTERFLY -> "small flowers";
            case FIREFLY -> "glow berries";
            case SNAIL -> "dried kelp or seagrass";
            case HEDGEHOG -> "mushrooms";
            case CAPYBARA -> "melon slices or sugar cane";
            case TORTOISE -> "seagrass";
            case LIZARD -> "spider eyes";
            case CRAB -> "cod or kelp";
            case DRAGONFLY -> "sugar";
            case ELEPHANT -> "wheat, sugar cane or apples";
            case ZEBRA -> "wheat or hay bales";
            case TANUKI -> "sweet berries or apples";
            case CRANE -> "seeds or a fish";
            case KOI -> "kelp or a piece of bread";
            case MACAQUE -> "apples, carrots or potatoes";
            case KANGAROO -> "wheat, hay or carrots";
            case OSTRICH -> "seeds";
            case CROCODILE -> "fish or a piece of beef";
            case KOALA -> "leaves or apples";
            case MOLE -> "spider eyes (it eats grubs)";
            case RHINO -> "wheat, hay or melon";
            case PENGUIN -> "fish";
            case ORCA -> "a big fish";
            case PIRANHA -> "any raw meat";
            case MACAW -> "seeds or cocoa beans";
        };
    }

    boolean livesIn(String biomeKey) {
        for (String b : biomes) if (biomeKey.contains(b)) return true;
        return false;
    }

    static Species byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
