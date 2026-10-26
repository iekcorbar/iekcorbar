package com.iekcorbar.additions;

/** The "Steamworks Additions" tab of the advancement screen: toasts only. One task per prop,
 *  a hidden one for standing near the clock as it strikes, and a challenge for the whole set. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(AdditionsPlugin plugin) {
        Adv a = new Adv(plugin, "additions", "Steamworks Additions", "Brass details for the town.", "clock", "minecraft:block/copper_block", true);
        a.add("steam_clock", null, "clock", "Time For Everything", "Hold a Steam Clock.", "task");
        a.add("brass_railing", null, "iron_bars", "Mind The Step", "Hold a Brass Railing.", "task");
        a.add("vent_grille", null, "lightning_rod", "Something Steams Here", "Hold a Vent Grille.", "task");
        a.add("heard", "steam_clock", "note_block", "Hear The Bell", "Stand near a Steam Clock while it strikes the hour.", "task").hidden();
        a.add("town", "brass_railing", "bell", "Town Beautiful", "Own all three props and hear the clock strike.", "challenge")
                .allOf("steam_clock", "brass_railing", "vent_grille", "heard").glint();
        a.scanner(it -> {
            DecorDef d = plugin.additions.decor.defOf(it);
            return d == null ? null : d.id;
        });
        a.item("steam_clock", "steam_clock").item("brass_railing", "brass_railing").item("vent_grille", "vent_grille");
        return a;
    }
}
