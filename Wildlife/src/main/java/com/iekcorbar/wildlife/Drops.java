package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Raw materials the animals leave behind (0.9.1). Plain vanilla base items with our own name, lore and the PDC tag
 * {@code wildlife:drop = <id>}, so other plugins (Sakagura, Kitchen, Zukan) can recognise them without depending on us.
 * Only adults that did not starve drop them.
 */
final class Drops {
    record Def(String id, Material base, String name, String lore) {}

    /** One line of a species' loot table: what, chance in percent, amount range. */
    record Roll(String id, int percent, int min, int max) {}

    final NamespacedKey key;
    final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<Species, List<Roll>> table = new java.util.EnumMap<>(Species.class);

    Drops(WildlifePlugin plugin) {
        key = new NamespacedKey(plugin, "drop");
        def("ivory", Material.BONE, "Ivory", "A heavy, smooth tusk. Carvers and decorators pay well.");
        def("antler", Material.POINTED_DRIPSTONE, "Antler", "Shed by a stag. Branching and surprisingly strong.");
        def("bear_pelt", Material.RABBIT_HIDE, "Bear Pelt", "Thick brown fur, warm as a hearth.");
        def("croc_hide", Material.LEATHER, "Crocodile Hide", "Scaled leather that shrugs off the rain.");
        def("striped_hide", Material.LEATHER, "Striped Hide", "Black and white, and no two the same.");
        def("roo_hide", Material.LEATHER, "Kangaroo Hide", "Supple, light and tough.");
        def("ostrich_plume", Material.FEATHER, "Ostrich Plume", "A long, soft plume for hats and pens.");
        def("crane_plume", Material.FEATHER, "Crane Plume", "Pure white with a black tip. Cranes are said to live a thousand years.");
        def("macaw_feather", Material.FEATHER, "Macaw Feather", "Scarlet, gold and blue in a single feather.");
        def("down", Material.FEATHER, "Penguin Down", "Dense, warm and waterproof.");
        def("rhino_horn", Material.BONE, "Rhino Horn", "Only ever found on an animal that has already fallen.");
        def("koi_scale", Material.PRISMARINE_CRYSTALS, "Koi Scale", "A tiny scale that flashes gold. Brewers like it.");
        def("orca_tooth", Material.PRISMARINE_SHARD, "Orca Tooth", "A conical tooth from the deep.");
        def("piranha_tooth", Material.FLINT, "Piranha Tooth", "Small, triangular and razor sharp.");
        def("brass_scrap", Material.COPPER_NUGGET, "Brass Scrap", "A bit of a dragon's plating. Steamworks smiths can use it.");
        def("giraffe_hide", Material.LEATHER, "Giraffe Hide", "Long, patterned leather. Tanners pay well for it.");
        def("lion_pelt", Material.RABBIT_HIDE, "Lion Pelt", "Golden fur with a dark mane. Hunters prize it.");
        def("hyena_pelt", Material.RABBIT_HIDE, "Hyena Pelt", "Coarse spotted fur. Smells of the plains.");

        roll(Species.ELEPHANT, "ivory", 60, 1, 2);
        roll(Species.DEER, "antler", 50, 1, 1);
        roll(Species.BEAR, "bear_pelt", 70, 1, 1);
        roll(Species.CROCODILE, "croc_hide", 80, 1, 2);
        roll(Species.ZEBRA, "striped_hide", 60, 1, 1);
        roll(Species.KANGAROO, "roo_hide", 60, 1, 1);
        roll(Species.OSTRICH, "ostrich_plume", 70, 1, 3);
        roll(Species.CRANE, "crane_plume", 40, 1, 1);
        roll(Species.MACAW, "macaw_feather", 60, 1, 2);
        roll(Species.PENGUIN, "down", 50, 1, 2);
        roll(Species.RHINO, "rhino_horn", 35, 1, 1);
        roll(Species.KOI, "koi_scale", 50, 1, 2);
        roll(Species.ORCA, "orca_tooth", 60, 1, 2);
        roll(Species.PIRANHA, "piranha_tooth", 40, 1, 1);
        roll(Species.DRAGON, "brass_scrap", 80, 1, 3);
        roll(Species.GIRAFFE, "giraffe_hide", 70, 1, 2);
        roll(Species.LION, "lion_pelt", 75, 1, 1);
        roll(Species.HYENA, "hyena_pelt", 65, 1, 2);
    }

    private void def(String id, Material base, String name, String lore) {
        defs.put(id, new Def(id, base, name, lore));
    }

    private void roll(Species s, String id, int percent, int min, int max) {
        table.computeIfAbsent(s, k -> new ArrayList<>()).add(new Roll(id, percent, min, max));
    }

    ItemStack item(String id, int amount) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.base(), Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(d.name(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(d.lore(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Wildlife", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    /** The drops of one adult kill: every roll of the species' table; a rare look doubles the amount. */
    List<ItemStack> roll(Species s, boolean rare) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Roll l : table.getOrDefault(s, List.of())) {
            if (r.nextInt(100) >= l.percent()) continue;
            int n = l.min() + r.nextInt(l.max() - l.min() + 1);
            ItemStack it = item(l.id(), rare ? n * 2 : n);
            if (it != null) out.add(it);
        }
        return out;
    }

    List<ItemStack> all() {
        List<ItemStack> l = new ArrayList<>();
        for (String id : defs.keySet()) l.add(item(id, 1));
        return l;
    }
}
