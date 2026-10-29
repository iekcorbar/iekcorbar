package com.iekcorbar.satchel;

/** The "Satchel" tab of the advancement screen. Toasts only; items are awarded by holding them, a few by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(SatchelPlugin plugin, Items items) {
        Adv a = new Adv(plugin, "satchel", "Satchel", "Carry the world on your back.", "bundle", "minecraft:block/brown_wool", true);
        a.add("canvas", null, "bundle", "Packing Up", "Get your first backpack. Wandering traders sell them.", "task");
        a.add("opened", "canvas", "chest", "Rummaging", "Open a backpack.", "task");
        a.add("copper", "canvas", "copper_ingot", "Copper Straps", "Own a Copper Backpack.", "task");
        a.add("iron", "copper", "iron_ingot", "Iron Reinforced", "Own an Iron Backpack.", "task");
        a.add("gold", "iron", "gold_ingot", "Gilded Pack", "Own a Gold Backpack.", "goal");
        a.add("diamond", "gold", "diamond", "Crystal Clasps", "Own a Diamond Backpack.", "goal");
        a.add("netherite", "diamond", "netherite_ingot", "Lava-Proof Luggage", "Own a Netherite Backpack.", "challenge");
        a.add("base", "opened", "iron_nugget", "Blank Plate", "Craft an Upgrade Base.", "task");
        a.add("pickup", "base", "hopper", "Sticky Fingers", "Get a Pickup Upgrade.", "task");
        a.add("magnet", "pickup", "ender_pearl", "Attractive Personality", "Get a Magnet Upgrade.", "task");
        a.add("feeding", "base", "golden_carrot", "Packed Lunch", "Get a Feeding Upgrade.", "task");
        a.add("fed", "feeding", "bread", "Mum Would Be Proud", "Let your backpack feed you when you are hungry.", "goal");
        a.add("deposit", "base", "piston", "Tidy Up", "Get a Deposit Upgrade.", "task");
        a.add("restock", "deposit", "sticky_piston", "Restocked", "Get a Restock Upgrade.", "task");
        a.add("refill", "base", "comparator", "Always Ready", "Get a Refill Upgrade.", "task");
        a.add("everlasting", "base", "nether_star", "Built to Last", "Get an Everlasting Upgrade.", "goal");
        a.add("full", "base", "nether_star", "Fully Loaded", "Fill every upgrade slot of one backpack.", "challenge").glint();
        a.add("wizard", "opened", "ender_pearl", "Nice Try, Wizard", "Try to put a backpack inside a backpack.", "task").hidden();
        a.add("back_day", "opened", "anvil", "Back Day", "Close a completely full netherite backpack.", "task").hidden();
        a.add("sink", "opened", "lava_bucket", "It Goes Down With Dignity", "Watch an unprotected backpack sink into lava.", "task").hidden();
        a.scanner(it -> {
            SItem t = items.typeOf(it);
            return t == null ? null : t.id();
        });
        a.item("canvas_backpack", "canvas").item("copper_backpack", "copper").item("iron_backpack", "iron").item("gold_backpack", "gold")
                .item("diamond_backpack", "diamond").item("netherite_backpack", "netherite").item("upgrade_base", "base")
                .item("pickup_upgrade", "pickup").item("magnet_upgrade", "magnet").item("feeding_upgrade", "feeding")
                .item("deposit_upgrade", "deposit").item("restock_upgrade", "restock").item("refill_upgrade", "refill")
                .item("everlasting_upgrade", "everlasting");
        return a;
    }
}
