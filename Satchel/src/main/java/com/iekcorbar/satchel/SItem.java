package com.iekcorbar.satchel;

import java.util.Locale;

/**
 * Every Satchel item: a vanilla PAPER with our own item model ({@code item_model satchel:<id>}, drawn in
 * tools/make_pack.py) and the id in the item's data ({@code satchel:item}).
 * Backpacks have {@code rows} storage rows (9 slots each) and {@code upgrades} upgrade slots.
 */
enum SItem {
    CANVAS_BACKPACK("Canvas Backpack", 2, 1, "A sturdy canvas pack.", "Right-click to open it."),
    COPPER_BACKPACK("Copper Backpack", 3, 2, "Riveted copper straps.", "Craft it from a Canvas Backpack to keep the contents."),
    IRON_BACKPACK("Iron Backpack", 4, 3, "Reinforced with iron plates.", "Craft it from a Copper Backpack to keep the contents."),
    GOLD_BACKPACK("Gold Backpack", 5, 3, "Gilded buckles and a big belly.", "Craft it from an Iron Backpack to keep the contents."),
    DIAMOND_BACKPACK("Diamond Backpack", 5, 4, "Crystal-set clasps.", "Craft it from a Gold Backpack to keep the contents."),
    NETHERITE_BACKPACK("Netherite Backpack", 5, 5, "Survives fire and lava.", "Craft it from a Diamond Backpack to keep the contents."),

    UPGRADE_BASE("Upgrade Base", 0, 0, "The blank plate every upgrade is made from."),
    PICKUP_UPGRADE("Pickup Upgrade", 0, 0, "Items you walk over go straight into the backpack."),
    MAGNET_UPGRADE("Magnet Upgrade", 0, 0, "Pulls dropped items towards you,", "and into the backpack with a Pickup Upgrade."),
    FEEDING_UPGRADE("Feeding Upgrade", 0, 0, "Eats food from the backpack when you get hungry."),
    EVERLASTING_UPGRADE("Everlasting Upgrade", 0, 0, "The dropped backpack never despawns,", "burns or falls into the void."),
    DEPOSIT_UPGRADE("Deposit Upgrade", 0, 0, "Sneak + right-click a chest with the backpack:", "items the chest already holds move into it."),
    RESTOCK_UPGRADE("Restock Upgrade", 0, 0, "Sneak + right-click a chest with the backpack:", "items the backpack already holds are taken from it."),
    REFILL_UPGRADE("Refill Upgrade", 0, 0, "Tops up the stacks in your hotbar", "from the backpack when they run low.");

    final String displayName;
    final int rows;
    final int upgradeSlots;
    final String[] lore;

    SItem(String displayName, int rows, int upgradeSlots, String... lore) {
        this.displayName = displayName;
        this.rows = rows;
        this.upgradeSlots = upgradeSlots;
        this.lore = lore;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean backpack() {
        return rows > 0;
    }

    /** An upgrade that can sit in an upgrade slot (the base plate cannot). */
    boolean upgrade() {
        return !backpack() && this != UPGRADE_BASE;
    }

    int storage() {
        return rows * 9;
    }

    /** The tier below this one (the backpack that is turned into it when crafting), or null. */
    SItem previous() {
        return switch (this) {
            case COPPER_BACKPACK -> CANVAS_BACKPACK;
            case IRON_BACKPACK -> COPPER_BACKPACK;
            case GOLD_BACKPACK -> IRON_BACKPACK;
            case DIAMOND_BACKPACK -> GOLD_BACKPACK;
            case NETHERITE_BACKPACK -> DIAMOND_BACKPACK;
            default -> null;
        };
    }

    static SItem byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
