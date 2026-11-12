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
    DEER("Red Deer", EntityType.HORSE, 1.15, 14, 0.055, Habitat.LAND, Time.ANY, 1, 3, 8,
            List.of("forest", "taiga", "meadow", "cherry_grove", "grove"), "leather",
            "A shy grazer of forests and meadows. Bucks carry branching antlers.", "Look in quiet forests and meadows.",
            "wildlife:deer.ambient", "wildlife:deer.snort", "wildlife:deer.snort", 1f),
    BEAR("Brown Bear", EntityType.POLAR_BEAR, 0.9, 30, 0.055, Habitat.LAND, Time.ANY, 1, 1, 3,
            List.of("taiga", "old_growth", "dark_forest", "grove"), "sweet_berries",
            "Strong and usually calm - unless you hurt it. Sleeps at night, fishes the riverbank and loves berries.", "Deep in old taiga forests.",
            "wildlife:bear.ambient", "wildlife:bear.hurt", "wildlife:bear.hurt", 1f),
    BOAR("Wild Boar", EntityType.GOAT, 0.83, 14, 0.18, Habitat.LAND, Time.ANY, 1, 3, 6,
            List.of("forest", "dark_forest", "wooded_badlands", "savanna_plateau"), "porkchop",
            "Tusked and bad-tempered; it charges when it feels cornered.", "Rooting around in dense forests.",
            "wildlife:boar.ambient", "wildlife:boar.hurt", "wildlife:boar.hurt", 1f),
    DUCK("Mallard", EntityType.CHICKEN, 0.52, 6, 0.03, Habitat.WATER, Time.ANY, 2, 4, 8,
            List.of("river", "swamp", "mangrove"), "feather",
            "Dabbles on rivers and ponds. The drake has a glossy green head.", "Rivers and swamp ponds.",
            "wildlife:duck.ambient", "wildlife:duck.hurt", "wildlife:duck.hurt", 1f),
    BUTTERFLY("Monarch Butterfly", EntityType.BEE, 0.56, 3, 0.25, Habitat.AIR, Time.DAY, 1, 3, 6,
            List.of("flower_forest", "meadow", "sunflower_plains", "plains", "cherry_grove"), "orange_tulip",
            "Flutters from flower to flower and helps crops grow.", "Flower fields on sunny days.",
            "wildlife:insect.flutter", null, null, 1f),
    FIREFLY("Firefly", EntityType.BEE, 0.86, 2, 0.2, Habitat.AIR, Time.NIGHT, 3, 6, 12,
            List.of("swamp", "mangrove", "forest", "birch_forest"), "glow_berries",
            "Its glowing tail lights up warm nights.", "Swamps and woods after dark.",
            null, null, null, 1f),
    SNAIL("Garden Snail", EntityType.ARMADILLO, 0.63, 6, 0.01, Habitat.LAND, Time.ANY, 1, 2, 4,
            List.of("forest", "swamp", "dark_forest", "birch_forest", "lush_caves"), "nautilus_shell",
            "Slow and patient. It pulls back into its shell when startled. More come out in the rain.", "Damp forests, especially when it rains.",
            null, "wildlife:snail.hurt", "wildlife:snail.hurt", 1f),
    HEDGEHOG("Hedgehog", EntityType.ARMADILLO, 0.52, 8, 0.025, Habitat.LAND, Time.ANY, 1, 1, 3,
            List.of("forest", "plains", "meadow", "birch_forest", "flower_forest"), "brown_mushroom",
            "Curls into a prickly ball when something comes too close.", "Hedges and forest edges.",
            "wildlife:hedgehog.ambient", "entity.armadillo.hurt", "entity.armadillo.death", 1.5f),
    CAPYBARA("Capybara", EntityType.PIG, 0.85, 12, 0.03, Habitat.WATER, Time.ANY, 2, 4, 6,
            List.of("jungle", "swamp", "mangrove"), "lily_pad",
            "The calmest animal around. Others like to sit on it.", "Riverbanks in jungles and swamps.",
            "wildlife:capybara.ambient", "entity.pig.hurt", "entity.pig.death", 0.55f),
    TORTOISE("Tortoise", EntityType.TURTLE, 0.5, 20, 0.012, Habitat.LAND, Time.DAY, 1, 1, 3,
            List.of("savanna", "jungle", "badlands"), "turtle_scute",
            "Slow, long-lived and armoured. Hides in its shell when you come close.", "Warm savannas and jungle edges.",
            "entity.turtle.ambient_land", "entity.turtle.hurt_land", "entity.turtle.death_land", 0.9f),
    LIZARD("Wall Lizard", EntityType.OCELOT, 0.35, 6, 0.035, Habitat.LAND, Time.DAY, 1, 2, 5,
            List.of("desert", "badlands", "savanna", "stony_shore"), "cactus",
            "Quick and wary. If you catch its tail, it may leave it behind - it grows back.", "Sunny rocks in deserts and badlands.",
            null, "wildlife:lizard.hurt", "wildlife:lizard.hurt", 1f),
    CRAB("Shore Crab", EntityType.PIG, 0.32, 8, 0.02, Habitat.SHORE, Time.ANY, 2, 4, 8,
            List.of("beach", "mangrove", "stony_shore"), "tube_coral",
            "Scuttles sideways along the shore, claws raised.", "Sandy beaches and mangroves.",
            "wildlife:crab.ambient", "entity.armadillo.hurt", "entity.armadillo.death", 1.6f),
    SONGBIRD("Songbird", EntityType.PARROT, 0.4, 4, 0.25, Habitat.AIR, Time.DAY, 1, 3, 8,
            List.of("forest", "birch_forest", "plains", "meadow", "flower_forest", "cherry_grove"), "wheat_seeds",
            "Blue jays, cardinals, canaries and sparrows - each with its own colours.", "Treetops and meadows by day.",
            "wildlife:songbird.ambient", "wildlife:songbird.hurt", "wildlife:songbird.hurt", 1f),
    DRAGONFLY("Emperor Dragonfly", EntityType.BEE, 0.68, 3, 0.3, Habitat.AIR, Time.DAY, 1, 2, 5,
            List.of("river", "swamp", "mangrove"), "sugar_cane",
            "Darts over water hunting midges, wings almost invisible.", "Above rivers and swamps on warm days.",
            "wildlife:dragonfly.buzz", null, null, 1f),
    ELEPHANT("African Elephant", EntityType.COW, 1.75, 60, 0.06, Habitat.LAND, Time.ANY, 2, 4, 3,
            List.of("savanna"), "bone",
            "The biggest animal around. A herd ambles across the savanna and the calves stay close to their mothers.",
            "Wide, dry savannas - listen for the rumbling.",
            "wildlife:elephant.ambient", "wildlife:elephant.hurt", "wildlife:elephant.hurt", 1f),
    ZEBRA("Plains Zebra", EntityType.HORSE, 1.33, 22, 0.07, Habitat.LAND, Time.ANY, 3, 6, 7,
            List.of("savanna"), "black_dye",
            "Every zebra has its own stripes. They graze in groups and bark when something startles them.",
            "Open savanna, usually in a group.",
            "wildlife:zebra.ambient", "wildlife:zebra.hurt", "wildlife:zebra.hurt", 1f),
    TANUKI("Tanuki", EntityType.PIG, 0.61, 10, 0.035, Habitat.LAND, Time.NIGHT, 1, 2, 4,
            List.of("bamboo", "jungle", "forest", "taiga", "old_growth"), "sweet_berries",
            "A night forager with cream cheeks and a ringed tail. It watches you from the edge of the bamboo and waddles off.",
            "Bamboo thickets and old forests, after dark.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.1f),
    CRANE("Red-Crowned Crane", EntityType.CHICKEN, 1.0, 10, 0.035, Habitat.WATER, Time.ANY, 1, 2, 2,
            List.of("swamp", "mangrove", "river"), "wheat",
            "Tall and quiet, it stalks the shallows with slow high steps. A pair stays together for life.",
            "Marsh edges and river flats - often two of them, standing very still.",
            null, "entity.chicken.hurt", "entity.chicken.death", 0.5f),
    KOI("Koi", EntityType.COD, 0.86, 6, 0.05, Habitat.POND, Time.ANY, 2, 4, 6,
            List.of("river", "swamp", "lush_caves", "beach"), "cod_bucket",
            "A pond carp in white, orange and black. Look for the gold one - old keepers say it brings luck.",
            "Gliding just under the surface of rivers and swamp pools.",
            null, "entity.cod.hurt", "entity.cod.death", 0.8f),
    MACAQUE("Japanese Macaque", EntityType.PIG, 0.54, 12, 0.04, Habitat.LAND, Time.DAY, 3, 6, 4,
            List.of("bamboo", "jungle", "taiga", "old_growth", "cherry_grove", "grove", "windswept"), "apple",
            "It sits in a troop, chatters about you, and steals apples the moment you turn around.",
            "Troops of three or more in bamboo thickets and snowy woods.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.35f),
    KANGAROO("Red Kangaroo", EntityType.RABBIT, 1.3, 14, 0.055, Habitat.LAND, Time.ANY, 1, 3, 4,
            List.of("savanna", "plains", "badlands"), "wheat",
            "Two feet and a tail that doubles as a chair. When it does go, it goes.",
            "Grassy plains and savanna edges, hopping along.",
            "entity.rabbit.ambient", "entity.fox.hurt", "entity.fox.death", 0.7f),
    OSTRICH("Common Ostrich", EntityType.CHICKEN, 1.12, 16, 0.085, Habitat.LAND, Time.DAY, 2, 4, 3,
            List.of("savanna", "plains", "badlands", "desert"), "feather",
            "A bird with the wings of an idea it never implemented. It outruns almost everything on two toes.",
            "Open savanna, running like nothing is coming.",
            null, "entity.chicken.hurt", "entity.chicken.death", 0.35f),
    CROCODILE("Siamese Crocodile", EntityType.PIG, 1.45, 20, 0.028, Habitat.WATER, Time.ANY, 1, 2, 3,
            List.of("swamp", "jungle", "mangrove", "river"), "bone",
            "A log with opinions. It lies at the water's edge for hours, and only the eyes move.",
            "Still water in swamps and jungles - look for eyes just above the surface.",
            null, "entity.wolf.hurt", "entity.wolf.death", 0.55f),
    KOALA("Koala", EntityType.OCELOT, 1.1, 10, 0.02, Habitat.LAND, Time.ANY, 1, 2, 3,
            List.of("jungle", "bamboo"), "oak_leaves",
            "Eucalyptus leaves, sleep, repeat. A small bear-shaped cloud of nap.",
            "Asleep at the foot of jungle and bamboo trees.",
            "entity.polar_bear.ambient", "entity.polar_bear.hurt", "entity.polar_bear.death", 1.4f),
    MOLE("European Mole", EntityType.ARMADILLO, 0.39, 6, 0.03, Habitat.LAND, Time.ANY, 1, 1, 5,
            List.of("forest", "plains", "meadow", "birch", "flower", "sunflower", "cherry"), "clay",
            "You mostly meet its work: a fresh pile of dug-up earth. The mole itself does not stay above ground long.",
            "Beside a molehill in lawns and forest edges.",
            null, "entity.armadillo.hurt", "entity.armadillo.death", 1.6f),
    RHINO("White Rhinoceros", EntityType.COW, 1.44, 40, 0.045, Habitat.LAND, Time.ANY, 1, 2, 2,
            List.of("savanna"), "iron_ingot",
            "Armour on four legs, horn out front. Calm until its path is blocked - then the ground is a problem.",
            "Wallowing in the dry savanna dust.",
            "entity.cow.ambient", "entity.cow.hurt", "entity.cow.death", 0.55f),
    PENGUIN("Emperor Penguin", EntityType.CHICKEN, 1.36, 8, 0.035, Habitat.WATER, Time.DAY, 3, 6, 4,
            List.of("frozen", "ice", "snowy", "beach"), "packed_ice",
            "A tuxedo that swims. It waddles in a colony and brays like a donkey about it.",
            "Ice coasts and frozen shores, always several together.",
            "entity.donkey.ambient", "entity.donkey.hurt", "entity.donkey.death", 1.25f),
    ORCA("Orca", EntityType.DOLPHIN, 2.27, 60, 0.09, Habitat.POND, Time.ANY, 1, 3, 1,
            List.of("ocean"), "water_bucket",
            "The wolf of the sea in tuxedo colours. It breaches next to boats for the same reason dolphins do: because it can.",
            "Out in the deep ocean - listen for the blow.",
            "entity.dolphin.ambient", "entity.dolphin.hurt", "entity.dolphin.death", 0.8f),
    PIRANHA("Red-bellied Piranha", EntityType.COD, 0.5, 4, 0.06, Habitat.POND, Time.ANY, 4, 8, 6,
            List.of("river", "swamp", "jungle"), "pufferfish",
            "Small, silver, red-bellied and never alone. One is nothing; eight at your ankle is a decision.",
            "Warm rivers and swamp water, in shimmering shoals.",
            null, "entity.cod.hurt", "entity.cod.death", 1.5f),
    MACAW("Scarlet Macaw", EntityType.PARROT, 0.69, 6, 0.2, Habitat.AIR, Time.DAY, 1, 3, 4,
            List.of("jungle"), "cookie",
            "A tail longer than its body, a beak like bolt cutters, and the vocabulary of a sailor.",
            "Over the jungle canopy - yes, it may land on your shoulder.",
            "entity.parrot.ambient", "entity.parrot.hurt", "entity.parrot.death", 0.85f),
    // Savanna set (0.9.15): three grazers/predators of the open savanna.
    GIRAFFE("Giraffe", EntityType.HORSE, 2.3, 30, 0.07, Habitat.LAND, Time.ANY, 1, 3, 6,
            List.of("savanna", "savanna_plateau", "windswept_savanna"), "apple",
            "The tallest animal around. It browses the high leaves and watches the plains from above.",
            "Open savanna - look up, not down.",
            "entity.horse.ambient", "entity.horse.hurt", "entity.horse.death", 0.7f),
    LION("Lion", EntityType.OCELOT, 3.0, 24, 0.12, Habitat.LAND, Time.ANY, 1, 3, 4,
            List.of("savanna", "savanna_plateau"), "beef",
            "The king of the savanna. It rests in the shade and hunts when the sun drops.",
            "Savanna grass and acacia shade - listen for the roar.",
            "entity.ravager.ambient", "entity.ravager.hurt", "entity.ravager.death", 1.0f),
    HYENA("Spotted Hyena", EntityType.WOLF, 0.9, 16, 0.13, Habitat.LAND, Time.ANY, 2, 4, 5,
            List.of("savanna", "savanna_plateau", "windswept_savanna"), "bone",
            "A laughing scavenger of the plains. It lives in a clan and never travels alone.",
            "Open savanna at dusk - you hear the laugh before you see them.",
            "entity.wolf.ambient", "entity.wolf.hurt", "entity.wolf.death", 0.9f),
    // Cold set (0.9.16): animals of snow and ice.
    ARCTICFOX("Arctic Fox", EntityType.FOX, 0.8, 10, 0.14, Habitat.LAND, Time.ANY, 1, 2, 5,
            List.of("snowy", "ice", "frozen", "grove", "taiga"), "white_wool",
            "A small fox in a white winter coat. It disappears into the snow when you blink.",
            "Snowy tundra and frozen rivers - watch for the moving snow.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.3f),
    SNOWYOWL("Snowy Owl", EntityType.PARROT, 0.9, 8, 0.18, Habitat.AIR, Time.DAY, 1, 2, 4,
            List.of("snowy", "ice", "frozen", "taiga", "grove"), "feather",
            "A silent white hunter of the tundra. It sits still for hours, then drops like a stone.",
            "Snowy plains and taiga by day - look for a white shape on a fence post.",
            "entity.parrot.ambient", "entity.parrot.hurt", "entity.parrot.death", 0.7f),
    REINDEER("Reindeer", EntityType.HORSE, 1.0, 20, 0.09, Habitat.LAND, Time.ANY, 2, 5, 6,
            List.of("snowy", "ice", "frozen", "taiga", "grove"), "leather",
            "A sturdy grazer of the cold north. The herd moves together across the snow.",
            "Snowy taiga and frozen plains - listen for the crunch of hooves.",
            "entity.horse.ambient", "entity.horse.hurt", "entity.horse.death", 0.8f),
    // Desert set (0.9.16): animals of sand and heat.
    FENNEC("Fennec Fox", EntityType.FOX, 0.6, 8, 0.15, Habitat.LAND, Time.NIGHT, 1, 2, 5,
            List.of("desert", "badlands", "savanna"), "rabbit_foot",
            "A tiny fox with enormous ears. It hunts at night and sleeps in the sand by day.",
            "Deserts and badlands after dark - listen for the high-pitched bark.",
            "entity.fox.ambient", "entity.fox.hurt", "entity.fox.death", 1.6f),
    MEERKAT("Meerkat", EntityType.RABBIT, 0.5, 6, 0.16, Habitat.LAND, Time.DAY, 3, 6, 6,
            List.of("desert", "badlands", "savanna"), "rabbit_hide",
            "A small sentinel that stands on its hind legs to watch for danger. Always in a group.",
            "Deserts and badlands by day - look for the sentinels on the rocks.",
            "entity.rabbit.ambient", "entity.rabbit.hurt", "entity.rabbit.death", 1.2f),
    VULTURE("Vulture", EntityType.PARROT, 1.35, 10, 0.15, Habitat.AIR, Time.DAY, 1, 3, 4,
            List.of("desert", "badlands", "savanna", "plains"), "feather",
            "A broad-winged scavenger of the open sky. It circles high and lands where something has fallen.",
            "Deserts and open plains by day - look up for the circling shadow.",
            "entity.parrot.ambient", "entity.parrot.hurt", "entity.parrot.death", 0.5f),
    // Steam Dragon (0.8.0): the rideable companion. PARROT base = proven 3D flight (all gravity
    // off in setup(): it hovers, never falls, takes no fall damage). Coal to tame like a wolf;
    // two of one kind in love make a baby like every other animal; the lead works as always.
    // The look is chosen at random from five trait colours (models()); rare = the gilded one.
    // Cave spawning is the Spawner's underground pass - biomes list stays empty on purpose so
    // the surface loop never picks it. Taming/riding live in DragonAI; statues/journal/eggs
    // follow the species loop.
    DRAGON("Steam Dragon", EntityType.PARROT, 1.5, 26, 0.09, Habitat.CAVE, Time.ANY, 1, 2, 3,
            List.of(), "coal",
            "Brass over an iron frame, a furnace ticking behind its ribs. It drifts through the deep "
            + "caverns on slow wingbeats and heats its own steam. Feed it coal and it is yours: it will "
            + "follow, sit when told, and let you ride - caves, sky, whatever you like.",
            "It is not found - it is heard. Follow the hiss through the big caverns deep down.",
            "wildlife:dragon.ambient", "wildlife:dragon.hurt", "wildlife:dragon.death", 0.5f),

    // Sfeer-wezen, GEEN dier (0.7.0): niet fokbaar, geen baby, geen rare-kleur, buiten het
    // journaal, geen statue en geen recept - zie animal(). Basemob BEE (PARROT is wel bewezen
    // maar past slecht: parrot zit op schouders, hopt op de grond en danst bij jukeboxes;
    // ALLAY/PHANTOM/VEX zijn in deze repo ongebruikt dus onbewezen). BEE zweeft 3D (bewezen
    // bij de AIR-insekten), kan via Animals.onTarget nooit targetten, woede wordt elke tick
    // gezeroed en Brain stript de hive/flower/aanval-goals. De OptiFine-wraith uit Downloads
    // is alleen als VOORBEELD bestudeerd (zwevende romp, uitlopende tolge, hangende mouwen);
    // maten, vorm en palette-kleuren zijn eigen werk. Schaal: bee-hitbox 0.9 m x 1.8 = 1.62 m,
    // model 26 px = 1.63 m - binnen de meetnauwigheid van de andere soorten.
    WRAITH("Wraith", EntityType.BEE, 1.8, 20, 0.08, Habitat.AIR, Time.NIGHT, 1, 1, 0,
            List.of("dark_forest", "mushroom_fields", "swamp", "old_growth"), "soul_lantern",
            "A cold shape in a torn robe, drifting a hand above the ground. It does not hunt and does not flee - it watches, and the morning takes it away.",
            "Not found in the wild: staff place them at dark, quiet spots.",
            "wildlife:wraith.ambient", "wildlife:wraith.hurt", "wildlife:wraith.death", 0.65f),
    // Sfeerwezens (0.9.x): custom creatures, not animals - no journal, no rare, no baby, no spawn egg, no statue.
    // Steve = 1.8 m is the size reference (1 block = 1 m).
    WILLOWISP("Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.12, Habitat.AIR, Time.NIGHT, 1, 3, 0,
            List.of("swamp", "mangrove", "dark_forest"), "glowstone_dust",
            "A drifting blue light over the marsh. It lures the curious deeper and vanishes the moment you reach it.",
            "Swamps and mangroves after dark - follow the light, if you dare.",
            null, "entity.allay.hurt", "entity.allay.death", 1.6f),
    STEAMGOLEM("Steam Golem", EntityType.IRON_GOLEM, 1.0, 60, 0.04, Habitat.LAND, Time.ANY, 1, 1, 0,
            List.of(), "copper_ingot",
            "A brass-and-iron hulk left by the old engineers. It patrols the ruins on slow, heavy steps and vents steam when disturbed.",
            "Deep in caves and old ruins - listen for the hiss of steam.",
            "entity.iron_golem.step", "entity.iron_golem.hurt", "entity.iron_golem.death", 0.55f),
    ONI("Oni", EntityType.VINDICATOR, 1.0, 30, 0.14, Habitat.LAND, Time.NIGHT, 1, 2, 0,
            List.of(), "redstone",
            "A red-skinned demon of the deep woods. It does not attack unprovoked, but its laugh carries far on a still night.",
            "Dark forests at night - you hear it before you see it.",
            "entity.vindicator.ambient", "entity.vindicator.hurt", "entity.vindicator.death", 0.75f),
    GLOWSPRITE("Glow Sprite", EntityType.ALLAY, 0.4, 3, 0.1, Habitat.CAVE, Time.ANY, 1, 3, 0,
            List.of(), "glowstone",
            "A tiny floating light in the deep dark. It drifts through caverns and vanishes in the sun.",
            "Deep caves - follow the light.",
            null, "entity.allay.hurt", "entity.allay.death", 1.8f),
    KITSUNE("Kitsune", EntityType.WOLF, 1.1, 18, 0.15, Habitat.LAND, Time.NIGHT, 1, 1, 0,
            List.of("forest", "taiga", "dark_forest", "cherry_grove", "bamboo", "old_growth", "grove", "flower_forest"),
            "amethyst_shard",
            "A fox with more than one tail, glowing faintly in the dark. It watches from the tree line and never quite lets you close.",
            "Old forests and cherry groves after dark - count the tails.",
            "entity.fox.ambient", "entity.wolf.hurt", "entity.wolf.death", 1.2f),
    KAPPA("Kappa", EntityType.TURTLE, 1.0, 16, 0.06, Habitat.WATER, Time.ANY, 1, 1, 0,
            List.of("river", "swamp", "mangrove", "beach", "ocean"), "nautilus_shell",
            "A water spirit with a bowl of water on its head. Tip the bowl and it loses its power - and its nerve.",
            "Rivers, swamps and mangroves - look for ripples that move against the current.",
            "entity.turtle.ambient_land", "entity.turtle.hurt_land", "entity.turtle.death_land", 0.9f),
    TENGU("Tengu", EntityType.PHANTOM, 1.2, 20, 0.2, Habitat.AIR, Time.NIGHT, 1, 1, 0,
            List.of("windswept", "grove", "taiga", "snowy", "cherry_grove", "old_growth", "mountain"), "phantom_membrane",
            "A mountain spirit with a long nose and red skin. It rides the wind above the peaks and laughs at those below.",
            "High mountain peaks after dark - listen for the wind and the laugh.",
            "entity.phantom.ambient", "entity.phantom.hurt", "entity.phantom.death", 1.0f),
    AUTOMATON("Automaton", EntityType.IRON_GOLEM, 1.0, 40, 0.05, Habitat.LAND, Time.ANY, 1, 1, 0,
            List.of(), "copper_ingot",
            "A clockwork soldier left by the old engineers. It patrols on heavy steps, hissing steam, and never sleeps.",
            "Deep in caves and old ruins - listen for the ticking.",
            "block.piston.contract", "entity.iron_golem.hurt", "entity.iron_golem.death", 0.5f),
    WILLOWISP_GREEN("Green Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.12, Habitat.AIR, Time.NIGHT, 1, 2, 0,
            List.of("dark_forest", "swamp", "mangrove"), "glowstone_dust",
            "A sickly green light over the marsh. Monsters gather where it drifts.",
            "Dark forests and swamps after dark - it draws the hungry.",
            null, "entity.allay.hurt", "entity.allay.death", 1.5f),
    WILLOWISP_RED("Red Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.14, Habitat.AIR, Time.NIGHT, 1, 2, 0,
            List.of("dark_forest", "forest", "taiga"), "glowstone_dust",
            "A burning red light that hunts the living. It stings when it reaches you.",
            "Dark forests at night - it comes to you, not the other way around.",
            null, "entity.allay.hurt", "entity.allay.death", 1.6f),
    WILLOWISP_GOLD("Golden Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.1, Habitat.AIR, Time.NIGHT, 1, 1, 0,
            List.of("plains", "meadow", "flower_forest", "sunflower_plains"), "glowstone_dust",
            "A warm golden light over the open fields. Follow it and it leaves a gift.",
            "Open fields and meadows at night - follow the gold.",
            null, "entity.allay.hurt", "entity.allay.death", 1.4f),
    WILLOWISP_WHITE("White Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.08, Habitat.AIR, Time.NIGHT, 1, 1, 0,
            List.of("snowy", "ice", "frozen", "grove"), "glowstone_dust",
            "A pale, calm light over the snow. It soothes those who stand near it.",
            "Snowy peaks and ice at night - it heals the cold.",
            null, "entity.allay.hurt", "entity.allay.death", 1.3f),
    WILLOWISP_PURPLE("Purple Will-o'-wisp", EntityType.ALLAY, 0.9, 4, 0.16, Habitat.AIR, Time.NIGHT, 1, 2, 0,
            List.of("cherry_grove", "flower_forest", "birch_forest"), "glowstone_dust",
            "A violet light that hums with magic. It quickens the step of those near it.",
            "Cherry groves and flower forests at night - it lends you speed.",
            null, "entity.allay.hurt", "entity.allay.death", 1.7f);

    enum Habitat { LAND, WATER, SHORE, AIR, POND,
        /** The steam dragon: 3D flight like AIR, but spawned by the Spawner's underground pass, never on the surface. */
        CAVE }

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
     * False for custom creatures that are NOT animals (the wraith). They are decorative
     * placements only: no journal page or advancement, no rare look, no baby, no spawn egg
     * and no craftable statue, and they never enter the wild (config + Spawner keep them off).
     */
    /** Custom creatures (sfeerwezens): not animals - no journal, rare, baby, spawn egg, statue or breeding. */
    boolean animal() {
        return this != WRAITH && this != WILLOWISP && this != STEAMGOLEM && this != ONI && this != KITSUNE
            && this != KAPPA && this != TENGU && this != AUTOMATON && this != GLOWSPRITE
            && this != WILLOWISP_GREEN && this != WILLOWISP_RED && this != WILLOWISP_GOLD
            && this != WILLOWISP_WHITE && this != WILLOWISP_PURPLE;
    }

    /** Species that the Field Journal actually tracks: everything that is an animal. Keeps the
     *  "Living Encyclopedia" denominator honest when non-animals join the enum. */
    static int journalCount() {
        int n = 0;
        for (Species s : values()) if (s.animal()) n++;
        return n;
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
            case BOAR, DUCK, CRAB, KOALA, MACAQUE, OSTRICH, ELEPHANT, GIRAFFE, ARCTICFOX, SNOWYOWL, REINDEER, FENNEC, MEERKAT -> 2;
            case HYENA, VULTURE -> 1;
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
        if (this == DRAGON) return List.of("wl_dragon", "wl_dragon_sakagura", "wl_dragon_kitchen", "wl_dragon_kura", "wl_dragon_verdigris");
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
            case GIRAFFE -> "White Giraffe";
            case LION -> "White Lion";
            case HYENA -> "Golden Hyena";
            case ARCTICFOX -> "Blue Arctic Fox";
            case SNOWYOWL -> "Golden Snowy Owl";
            case REINDEER -> "White Reindeer";
            case FENNEC -> "Black Fennec";
            case MEERKAT -> "Golden Meerkat";
            case VULTURE -> "White Vulture";
            case DRAGON -> "Gilded Dragon";   // the gilded one: every brass cell turned to gold
            case WRAITH, WILLOWISP, STEAMGOLEM, ONI, KITSUNE, KAPPA, TENGU, AUTOMATON, GLOWSPRITE,
                 WILLOWISP_GREEN, WILLOWISP_RED, WILLOWISP_GOLD, WILLOWISP_WHITE, WILLOWISP_PURPLE -> displayName;
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
            case GIRAFFE -> Tag.LEAVES.isTagged(m) || m == Material.APPLE || m == Material.HAY_BLOCK;
            case LION -> m == Material.BEEF || m == Material.PORKCHOP || m == Material.MUTTON || m == Material.CHICKEN || m == Material.RABBIT;
            case HYENA -> m == Material.BEEF || m == Material.PORKCHOP || m == Material.MUTTON || m == Material.CHICKEN || m == Material.ROTTEN_FLESH;
            case ARCTICFOX -> m == Material.COD || m == Material.SALMON || m == Material.RABBIT || m == Material.SWEET_BERRIES;
            case SNOWYOWL -> m == Material.RABBIT || m == Material.CHICKEN || m == Material.COD || m == Material.SALMON;
            case REINDEER -> m == Material.WHEAT || m == Material.HAY_BLOCK || m == Material.APPLE || m == Material.CARROT;
            case FENNEC -> m == Material.RABBIT || m == Material.CHICKEN || m == Material.SPIDER_EYE;
            case MEERKAT -> m == Material.SPIDER_EYE || m == Material.WHEAT_SEEDS || m == Material.MELON_SEEDS;
            case VULTURE -> m == Material.ROTTEN_FLESH || m == Material.BEEF || m == Material.MUTTON || m == Material.CHICKEN;
            case DRAGON -> m == Material.COAL || m == Material.CHARCOAL;   // coal: it is a furnace
            case WRAITH, WILLOWISP, STEAMGOLEM, ONI, KITSUNE, KAPPA, TENGU, AUTOMATON, GLOWSPRITE,
                 WILLOWISP_GREEN, WILLOWISP_RED, WILLOWISP_GOLD, WILLOWISP_WHITE, WILLOWISP_PURPLE -> false;
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
            case GIRAFFE -> "leaves, apples or hay";
            case LION -> "any raw meat";
            case HYENA -> "meat or rotten flesh";
            case ARCTICFOX -> "fish, rabbit or sweet berries";
            case SNOWYOWL -> "rabbit, chicken or fish";
            case REINDEER -> "wheat, hay, apples or carrots";
            case FENNEC -> "rabbit, chicken or spider eyes";
            case MEERKAT -> "spider eyes or seeds";
            case VULTURE -> "rotten flesh or raw meat";
            case DRAGON -> "coal or charcoal";
            case WRAITH, WILLOWISP, STEAMGOLEM, ONI, KITSUNE, KAPPA, TENGU, AUTOMATON, GLOWSPRITE,
                 WILLOWISP_GREEN, WILLOWISP_RED, WILLOWISP_GOLD, WILLOWISP_WHITE, WILLOWISP_PURPLE -> "nothing - it is not an animal and cannot be bred";
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
