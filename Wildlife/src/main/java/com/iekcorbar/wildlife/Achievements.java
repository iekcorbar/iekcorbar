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
        a.add("capy_love", "root", "melon_slice", "Everyone Loves the Capybara", "See a duck or a songbird climb onto a capybara.", "task").hidden();
        a.add("tiny_scare", "root", "sculk_sensor", "Eeek!", "See an elephant jump away from a tiny animal.", "task").hidden();
        a.add("egg", "root", "turtle_spawn_egg", "Gently Does It", "Get a Wildlife spawn egg.", "task");
        a.add("love", "root", "wheat", "Matchmaker", "Feed an animal until it falls in love.", "task");
        a.add("baby", "love", "egg", "It's a Baby!", "Watch two animals have a baby.", "goal");
        a.add("forever_young", "baby", "golden_dandelion", "Forever Young", "Keep a baby small with a golden dandelion.", "goal");
        a.add("kept", "root", "lead", "Keeper", "Tie up a wild animal with a lead.", "task");
        a.add("statue", "root", "stone_bricks", "Stone Still", "Get an animal statue.", "task");
        a.add("zoo_prop", "statue", "oak_fence", "Zookeeper", "Get a zoo prop.", "task");
        a.add("kiosk", "zoo_prop", "oak_sign", "Open for Business", "Place a Ticket Kiosk.", "goal");
        a.add("ticket", "kiosk", "paper", "Admit One", "Buy a zoo ticket.", "task");

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
        a.item("egg", "egg").item("ticket", "ticket").item("statue", "statue").item("zoo_prop", "zoo_prop");
        return a;
    }

    static void grant(WildlifePlugin plugin, Player p, String id) {
        plugin.adv().grant(p, id);
    }
}
