package com.iekcorbar.bouw;

/** The "Bouw" advancement tab: open a door, hold a door, hold a fence. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(BouwPlugin plugin) {
        Adv a = new Adv(plugin, "bouw", "Bouw", "Doors and fences for the builder.", "oak_door", "minecraft:block/oak_planks", true);
        a.add("door", null, "oak_door", "Come On In", "Open an Bouw door.", "task");
        a.add("fence", null, "oak_fence", "Good Neighbours", "Hold an Bouw fence.", "task");
        a.add("any_door", "door", "jungle_door", "Behind Every Door", "Hold an Bouw door.", "task");
        a.scanner(it -> {
            DecorDef d = plugin.bouw.decor.defOf(it);
            if (d == null) return null;
            return d.height > 1 ? "any_door" : "fence";
        });
        a.item("any_door", "any_door").item("fence", "fence");
        return a;
    }
}
