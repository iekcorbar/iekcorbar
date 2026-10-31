package com.iekcorbar.wildlife;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Extra advancements in the Wildlife tab (the tab root and the species entries are made by {@link Journal}). Toasts only. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(WildlifePlugin plugin) {
        Adv a = new Adv(plugin, "wildlife", "Wildlife", "", "spyglass", "", false);

        // ============================================================
        // 1. OBSERVATION LINE (root → watcher → naturalist → encyclopedia)
        // ============================================================
        a.add("first_sighting", "root", "spyglass", "First Sighting", "Record your first animal in the Field Journal.", "task");
        a.add("observer", "first_sighting", "book", "Patient Observer", "Record five different species.", "task");
        a.add("naturalist", "observer", "filled_map", "Naturalist", "Record half of all species.", "goal");
        a.add("encyclopedia", "naturalist", "enchanted_book", "Living Encyclopedia", "Record every species.", "challenge").global();

        // rare sightings
        a.add("first_rare", "first_sighting", "golden_apple", "Rare Find", "Record a rare colour variant.", "task").hidden();
        a.add("rare_collector", "first_rare", "gold_ingot", "Colour Collector", "Record five different rare variants.", "goal").hidden();
        a.add("rare_master", "rare_collector", "gold_block", "Master of Colours", "Record every rare variant.", "challenge").hidden();

        // baby sightings
        a.add("first_baby", "first_sighting", "egg", "Tiny Tracks", "Record a baby animal.", "task").hidden();
        a.add("baby_collector", "first_baby", "turtle_egg", "Nursery Watcher", "Record five different baby species.", "goal").hidden();

        // habitat exploration
        a.add("forest_walker", "first_sighting", "oak_sapling", "Forest Walker", "Record an animal in a forest biome.", "task").hidden();
        a.add("water_watcher", "first_sighting", "water_bucket", "Water Watcher", "Record an animal in a water biome.", "task").hidden();
        a.add("night_owl", "first_sighting", "torch", "Night Owl", "Record a nocturnal animal at night.", "task").hidden();
        a.add("cave_explorer", "first_sighting", "deepslate", "Cave Explorer", "Record an animal in a cave.", "task").hidden();
        a.add("savanna_scout", "first_sighting", "dead_bush", "Savanna Scout", "Record an animal in a savanna.", "task").hidden();

        // ============================================================
        // 2. SPECIAL SIGHTINGS (hidden, hang off root)
        // ============================================================
        a.add("capy_love", "root", "melon_slice", "Everyone Loves the Capybara", "See a duck or a songbird climb onto a capybara.", "task").hidden();
        a.add("tiny_scare", "root", "sculk_sensor", "Eeek!", "See an elephant jump away from a tiny animal.", "task").hidden();
        a.add("fisher", "root", "salmon", "Fishing Bear", "Watch a bear catch a fish at the riverbank.", "task").hidden();
        a.add("pollinator", "root", "wheat", "Butterfly Garden", "See a butterfly ripen your crops.", "task").hidden();
        a.add("trophy", "root", "armor_stand", "Hunter's Trophy", "Take a statue home from an animal you had recorded first.", "goal").hidden();
        a.add("pond_stop", "root", "bell", "Next Stop: Pond", "See five birds ride one capybara at once.", "task").hidden();
        a.add("tanuki_wink", "root", "sweet_berries", "Charcoal Deal", "Stand very still near a tanuki at night.", "task").hidden();
        a.add("dragon_wild", "root", "sculk_sensor", "Follow the Hiss", "Stand close enough to a wild dragon to hear it roar.", "task").hidden();
        a.add("elephant_trunk", "root", "bamboo", "Trunk Call", "Watch an elephant use its trunk to pick up water.", "task").hidden();
        a.add("crab_wave", "root", "prismarine_shard", "Wave Back", "Wave at a crab and have it wave back.", "task").hidden();
        a.add("turtle_hide", "root", "turtle_scute", "Peek-a-Boo", "Make a tortoise hide in its shell.", "task").hidden();
        a.add("hedgehog_curl", "root", "brown_mushroom", "Prickly Situation", "Make a hedgehog curl into a ball.", "task").hidden();
        a.add("koala_nap", "root", "oak_leaves", "Nap Time", "Find a sleeping koala.", "task").hidden();
        a.add("bear_sleep", "root", "sweet_berries", "Hibernation", "Find a sleeping bear at night.", "task").hidden();
        a.add("firefly_night", "root", "glow_berries", "Firefly Night", "See fireflies light up a swamp at night.", "task").hidden();
        a.add("wraith_dawn", "root", "soul_lantern", "Morning Mist", "Watch a wraith dissolve at dawn.", "task").hidden();

        // ============================================================
        // 3. BREEDING LINE (root → love → baby → grown_up → ...)
        // ============================================================
        a.add("love", "root", "wheat", "Matchmaker", "Feed an animal until it falls in love.", "task");
        a.add("baby", "love", "egg", "It's a Baby!", "Watch two animals have a baby.", "goal");
        a.add("forever_young", "baby", "golden_dandelion", "Forever Young", "Keep a baby small with a golden dandelion.", "goal");
        a.add("grown_up", "baby", "bone_meal", "All Grown Up", "Watch a baby grow into an adult.", "task");
        a.add("multi_gen", "grown_up", "golden_apple", "Generations", "Breed an animal whose parents were both bred by you.", "challenge").hidden();
        a.add("breed_all", "love", "wheat_seeds", "Matchmaker Supreme", "Breed five different species.", "goal").hidden();
        a.add("breed_ten", "breed_all", "hay_block", "Prolific Breeder", "Breed ten different species.", "challenge").hidden();
        a.add("baby_collector_breed", "baby", "egg", "Baby Boom", "Have five babies at the same time.", "task").hidden();

        // ============================================================
        // 4. EGGS AND KEEPING (root → egg → kept → ...)
        // ============================================================
        a.add("egg", "root", "turtle_spawn_egg", "Gently Does It", "Get a Wildlife spawn egg.", "task");
        a.add("kept", "root", "lead", "Keeper", "Tie up a wild animal with a lead.", "task");
        a.add("named", "kept", "name_tag", "A Name for Everything", "Name a wild animal with a name tag.", "task");
        a.add("zoo_builder", "kept", "oak_fence", "Zoo Builder", "Keep three different species on leads at once.", "goal").hidden();
        a.add("zoo_master", "zoo_builder", "chain", "Zoo Master", "Keep five different species on leads at once.", "challenge").hidden();
        a.add("released", "kept", "shears", "Set Free", "Release a leashed animal.", "task").hidden();

        // ============================================================
        // 5. STATUE / ZOO LINE — moved to Zoo tab (ZooAchievements)
        // ============================================================

        // ============================================================
        // 6. DRAGON LINE (root → dragon_wild → dragon_tame → dragon_rider → ...)
        // ============================================================
        a.add("dragon_tame", "root", "coal", "Soot and Seat", "Tame a wild dragon with a lump of coal.", "goal");
        a.add("dragon_rider", "dragon_tame", "feather", "Up She Goes", "Ride a tamed dragon.", "goal");
        a.add("dragon_flight", "dragon_rider", "elytra", "A New Machine in the Sky", "Fly a dragon above the clouds (Y 100+).", "challenge").global();
        a.add("dragon_sit", "dragon_tame", "oak_sign", "Sit, Girl", "Teach a tamed dragon to sit.", "task");
        a.add("dragon_hoarder", "dragon_tame", "charcoal", "Two Seats", "Tame two different dragons.", "goal");
        a.add("dragon_caver", "dragon_rider", "deepslate", "Underground Express", "Ride a dragon below Y 40 - through the caverns it calls home.", "goal");
        a.add("dragon_reunion", "dragon_tame", "ender_pearl", "Loyal Engine", "Let a tamed dragon catch up the long way - watch it find you in the dark.", "task").hidden();
        a.add("dragon_breeder", "dragon_tame", "egg", "Dragon Nursery", "Breed two tamed dragons.", "goal").hidden();
        a.add("dragon_speed", "dragon_rider", "sugar", "Full Steam", "Ride a dragon at sprinting speed.", "task").hidden();
        a.add("dragon_night", "dragon_rider", "torch", "Night Flight", "Ride a dragon at night.", "task").hidden();
        a.add("dragon_feed", "dragon_tame", "coal", "Fuel for the Furnace", "Feed a tamed dragon coal.", "task").hidden();
        a.add("dragon_follow", "dragon_tame", "lead", "Following You", "Have a tamed dragon follow you.", "task").hidden();
        a.add("dragon_roar", "dragon_tame", "note_block", "Hear Me Roar", "Hear a tamed dragon roar.", "task").hidden();
        a.add("dragon_landing", "dragon_rider", "feather", "Smooth Landing", "Dismount a dragon safely.", "task").hidden();

        // wraith line (root, hidden - sfeer-wezen)
        a.add("wraith_night", "root", "soul_lantern", "Night Visitor", "See a wraith at night.", "task").hidden();
        a.add("wraith_dissolve", "wraith_night", "sunflower", "Morning Mist", "Watch a wraith dissolve at dawn.", "task").hidden();

        // ============================================================
        // 7. BEHAVIOR TREE (root → various behaviors)
        // ============================================================
        a.add("first_feed", "root", "wheat", "Feeding Time", "Feed any animal.", "task");
        a.add("first_pet", "first_feed", "golden_apple", "Gentle Touch", "Right-click an animal with an empty hand.", "task").hidden();
        a.add("first_kill", "root", "iron_sword", "Nature's Way", "Witness an animal die of natural causes.", "task").hidden();
        a.add("first_birth", "root", "egg", "New Life", "Witness an animal being born.", "task");
        a.add("first_death", "root", "bone", "Circle of Life", "Witness an animal die.", "task").hidden();
        a.add("first_flee", "root", "feather", "Run Away", "Make an animal flee from you.", "task").hidden();
        a.add("first_curl", "root", "brown_mushroom", "Defensive", "Make an animal curl up or hide.", "task").hidden();
        a.add("first_sleep", "root", "bed", "Sweet Dreams", "Find a sleeping animal.", "task").hidden();
        a.add("first_fish", "root", "fishing_rod", "Gone Fishing", "Watch an animal fish.", "task").hidden();

        // ============================================================
        // 8. COLLECTION TREE (root → collector → hoarder → master)
        // ============================================================
        a.add("first_item", "root", "paper", "Souvenir", "Pick up an item dropped by an animal.", "task").hidden();
        a.add("collector", "first_item", "chest", "Collector", "Collect five different animal drops.", "goal").hidden();
        a.add("hoarder", "collector", "shulker_box", "Hoarder", "Collect ten different animal drops.", "challenge").hidden();
        a.add("first_egg_collect", "root", "turtle_egg", "Egg Hunter", "Collect a spawn egg from an animal.", "task").hidden();
        a.add("egg_collector", "first_egg_collect", "egg", "Egg Collector", "Collect five different spawn eggs.", "goal").hidden();

        // ============================================================
        // 9. EXPLORATION TREE (root → explorer → adventurer → master)
        // ============================================================
        a.add("first_biome", "root", "grass_block", "New Territory", "Find an animal in a new biome.", "task").hidden();
        a.add("biome_explorer", "first_biome", "map", "Biome Explorer", "Find animals in five different biomes.", "goal").hidden();
        a.add("biome_master", "biome_explorer", "compass", "Biome Master", "Find animals in ten different biomes.", "challenge").hidden();
        a.add("night_explorer", "root", "torch", "Night Explorer", "Find an animal at night.", "task").hidden();
        a.add("day_explorer", "root", "sunflower", "Day Explorer", "Find an animal during the day.", "task").hidden();

        // ============================================================
        // 11. HUNTING & EVENTS (root → hunter → master_hunter → ...)
        // ============================================================
        a.add("first_hunt", "root", "iron_sword", "First Blood", "Hunt your first wild animal.", "task").hidden();
        a.add("hunter", "first_hunt", "bow", "Hunter", "Hunt five different species.", "goal").hidden();
        a.add("master_hunter", "hunter", "crossbow", "Master Hunter", "Hunt ten different species.", "challenge").hidden().global();
        a.add("hunt_ten", "first_hunt", "arrow", "Ten Down", "Hunt ten animals in total.", "task").hidden();
        a.add("hunt_fifty", "hunt_ten", "spectral_arrow", "Fifty Down", "Hunt fifty animals in total.", "goal").hidden();
        a.add("hunt_hundred", "hunt_fifty", "trident", "Centurion", "Hunt one hundred animals in total.", "challenge").hidden();

        // photography
        a.add("first_photo", "root", "camera", "Say Cheese", "Take your first wildlife photo.", "task").hidden();
        a.add("photographer", "first_photo", "filled_map", "Wildlife Photographer", "Take photos of five different species.", "goal").hidden();
        a.add("master_photographer", "photographer", "map", "Master Photographer", "Take photos of ten different species.", "challenge").hidden().global();

        // conservation
        a.add("first_rescue", "root", "lead", "Animal Rescue", "Rescue a trapped animal.", "task").hidden();
        a.add("conservationist", "first_rescue", "emerald", "Conservationist", "Rescue five animals.", "goal").hidden();
        a.add("master_conservationist", "conservationist", "golden_apple", "Master Conservationist", "Rescue ten animals.", "challenge").hidden().global();

        // seasonal events
        a.add("spring_breeder", "root", "pink_petals", "Spring Breeder", "Breed an animal in spring.", "task").hidden();
        a.add("summer_explorer", "root", "sunflower", "Summer Explorer", "Find a new species in summer.", "task").hidden();
        a.add("autumn_hunter", "root", "orange_wool", "Autumn Hunter", "Hunt an animal in autumn.", "task").hidden();
        a.add("winter_watcher", "root", "snowball", "Winter Watcher", "Find a sleeping animal in winter.", "task").hidden();

        // ============================================================
        // 10. COMPOSITE: MASTER NATURALIST
        // ============================================================
        a.add("master", "encyclopedia", "nether_star", "Master Naturalist", "Complete every Wildlife advancement.", "challenge").hidden().global().allOf(
                "encyclopedia", "full_zoo", "dragon_flight", "multi_gen", "hoarder", "biome_master");

        NamespacedKey decor = new NamespacedKey(plugin, "decor");
        a.scanner(it -> {
            String id = plugin.items().idOf(it);
            if (id != null && id.startsWith("egg:")) return "egg";
            if (!it.hasItemMeta()) return null;
            var pdc = it.getItemMeta().getPersistentDataContainer();
            if (pdc.has(new NamespacedKey(plugin, "ticket"), PersistentDataType.STRING)) return "ticket";
            String d = pdc.get(decor, PersistentDataType.STRING);
            if (d == null) return null;
            return d.startsWith("animal_") ? "statue|zoo_prop" : "zoo_prop";
        });
        a.item("egg", "egg");
        return a;
    }

    static void grant(WildlifePlugin plugin, Player p, String id) {
        plugin.adv().grant(p, id);
    }
}