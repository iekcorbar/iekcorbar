package com.iekcorbar.wildlife;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Zoo-builder advancements in their own tab. Toasts only. */
final class ZooAchievements {
    private ZooAchievements() {
    }

    static Adv create(WildlifePlugin plugin) {
        Adv a = new Adv(plugin, "zoo", "Zoo", "Build your own zoo", "oak_sign", "minecraft:block/oak_planks", true);
        // ---- statue / zoo line (migrated from Wildlife tab)
        a.add("statue", "root", "stone_bricks", "Stone Still", "Get an animal statue.", "task");
        a.add("zoo_prop", "statue", "oak_fence", "Zookeeper", "Get a zoo prop.", "task");
        a.add("kiosk", "zoo_prop", "oak_sign", "Open for Business", "Place a Ticket Kiosk.", "goal");
        a.add("ticket", "kiosk", "paper", "Admit One", "Buy a zoo ticket.", "task");
        a.add("full_zoo", "kiosk", "emerald", "Open to the Public", "Place a Ticket Kiosk, a turnstile and an enclosure.", "challenge").hidden();
        a.add("zoo_collector", "statue", "item_frame", "Statue Collector", "Get five different animal statues.", "goal").hidden();
        a.add("zoo_master_statues", "zoo_collector", "armor_stand", "Master Sculptor", "Get ten different animal statues.", "challenge").hidden();
        // entrance line
        a.add("first_prop", "root", "oak_planks", "First Brick", "Place your first zoo prop.", "task");
        a.add("entrance", "first_prop", "oak_door", "Grand Entrance", "Place a ticket kiosk, a turnstile and an entrance arch.", "goal");
        a.add("full_entrance", "entrance", "gold_ingot", "Open for Business", "Place a ticket kiosk, an entry turnstile, an exit turnstile and an entrance arch.", "challenge");
        // enclosure line
        a.add("first_enclosure", "first_prop", "iron_bars", "Home Sweet Home", "Place a zoo fence and an animal hut.", "task");
        a.add("enclosure_complete", "first_enclosure", "wheat", "Complete Enclosure", "Place a fence, a feeding trough, a water trough and an info board.", "goal");
        a.add("multi_enclosure", "enclosure_complete", "emerald", "Multi-Enclosure", "Build three complete enclosures.", "challenge").hidden();
        // comfort line
        a.add("comfort", "first_prop", "oak_stairs", "Visitor Comfort", "Place a bench, a trash bin and a lamp post.", "task");
        a.add("full_comfort", "comfort", "red_bed", "Full Service", "Place a bench, a trash bin, a lamp post, a toilet and a first aid post.", "goal").hidden();
        // path line
        a.add("first_path", "first_prop", "stone", "Pathway", "Place a cobblestone path tile.", "task");
        a.add("full_path", "first_path", "map", "Connected", "Place 16 path tiles in a connected line.", "goal").hidden();
        // decoration line
        a.add("first_decor", "first_prop", "flower_pot", "Decoration", "Place a flower box, a parasol or a picnic table.", "task");
        a.add("full_decor", "first_decor", "painting", "Fully Decorated", "Place five different decorative props.", "goal").hidden();
        // ticket line
        a.add("first_ticket", "root", "paper", "First Ticket", "Buy a zoo ticket.", "task");
        a.add("ticket_collector", "first_ticket", "book", "Ticket Collector", "Buy ten zoo tickets.", "goal").hidden();
        // ---- feeding & care line (new)
        a.add("first_feed_zoo", "root", "wheat", "Zoo Keeper", "Feed an animal in your zoo for the first time.", "task").hidden();
        a.add("feeder", "first_feed_zoo", "hay_block", "Feeder", "Feed 10 animals in your zoo.", "goal").hidden();
        a.add("master_feeder", "feeder", "golden_apple", "Master Feeder", "Feed 50 animals in your zoo.", "challenge").hidden().global();
        a.add("senior_animal", "first_feed_zoo", "golden_apple", "Wise Old Animal", "Witness an animal become a senior.", "goal").hidden().global();
        a.add("zoo_veterinarian", "first_feed_zoo", "golden_apple", "Zoo Veterinarian", "Heal a sick animal in your zoo.", "goal").hidden();
        a.add("zoo_breeder", "first_feed_zoo", "egg", "Zoo Breeder", "Breed an animal in captivity.", "goal").hidden();
        // master
        a.add("zoo_master", "entrance", "nether_star", "Zoo Master", "Complete every Zoo advancement.", "challenge").hidden().allOf(
                "full_entrance", "multi_enclosure", "full_comfort", "full_path", "full_decor", "master_feeder");

        NamespacedKey decor = new NamespacedKey(plugin, "decor");
        a.scanner(it -> {
            if (!it.hasItemMeta()) return null;
            var pdc = it.getItemMeta().getPersistentDataContainer();
            if (pdc.has(new NamespacedKey(plugin, "ticket"), PersistentDataType.STRING)) return "ticket";
            String d = pdc.get(decor, PersistentDataType.STRING);
            if (d == null) return null;
            return d.startsWith("animal_") ? "statue|zoo_prop" : "zoo_prop";
        });
        a.item("ticket", "ticket").item("statue", "statue").item("zoo_prop", "zoo_prop");
        return a;
    }

    static void grant(WildlifePlugin plugin, Player p, String id) {
        plugin.zooAdv().grant(p, id);
    }
}