package com.iekcorbar.kura;

import java.util.Locale;

/**
 * Every Kura item: a vanilla PAPER with our own item model ({@code item_model kura:<id>},
 * drawn in tools/make_pack.py) and the id in the item's data ({@code kura:item}).
 */
enum KItem {
    INVENTORY_CONNECTOR("Inventory Connector", 64, true, "Was the heart of a network.", "An Inventory Cable does this now: it joins", "every container it touches."),
    STORAGE_TERMINAL("Storage Terminal", 64, "Shows every item in the network.", "Place it against a cable or a chest.", "Double-click an item to pin it as favourite."),
    CRAFTING_TERMINAL("Crafting Terminal", 64, "A Storage Terminal with a crafting grid", "that refills itself from the network."),
    INVENTORY_TRIM("Inventory Trim", 64, true, "Joined chests without storing anything.", "An Inventory Cable does this now.", "Can still be painted."),
    INVENTORY_CABLE("Inventory Cable", 64, "Carries the network.", "Touches a chest, barrel or shulker box,", "and that container is in the network."),
    FRAMED_CABLE("Framed Inventory Cable", 64, "A cable that fills the whole block.", "Can be painted."),
    CABLE_CONNECTOR("Cable Connector", 64, true, "Attached a cable to one container.", "An Inventory Cable joins containers", "by itself now."),
    INVENTORY_HOPPER("Inventory Hopper", 64, "Moves items from behind to the front,", "to or from a whole network. Right-click: filter."),
    LEVEL_EMITTER("Level Emitter", 64, "Gives a redstone signal when the network", "holds more (or less) of an item. Right-click: set up."),
    INVENTORY_INTERFACE("Inventory Interface", 64, "A barrel that feeds the network (import)", "or is kept stocked from it (export,", "N stacks per item - set in its GUI).", "Hoppers, belts and arms can use it."),
    OPEN_CRATE("Open Crate", 64, "Drops everything put into it", "in front of it, as items on the ground."),
    FILING_CABINET("Filing Cabinet", 64, "Holds many variants of one item", "(enchanted books, potions, maps...).", "Keeps its contents when broken."),
    WIRELESS_TERMINAL("Wireless Terminal", 1, "Right-click to open the terminal you look at,", "from up to %range% blocks away.", "Looking at nothing: browse nearby networks."),
    ADV_WIRELESS_TERMINAL("Advanced Wireless Terminal", 1, "Sneak + right-click a terminal to bind it,", "then right-click anywhere within %adv% blocks.", "A beacon next to the terminal extends the range.", "Nothing bound, or looking at nothing: browse."),
    ITEM_FILTER("Item Filter", 16, "Right-click to choose items.", "Put it in a filter slot (Configurator, hopper...)."),
    TAG_FILTER("Tag Filter", 16, "Right-click to choose item groups (tags),", "e.g. all logs or all wool."),
    POLY_FILTER("Polymorphic Filter", 16, "Only lets in items the container", "already holds."),
    CONFIGURATOR("Inventory Configurator", 1, "Right-click a chest in a network to set", "its priority and filter, or leave it out.", "Right-click a part to set it up.", "Sneak + right-click a bound courier to filter it."),
    PAINT_KIT("Paint Kit", 1, "Right-click a block to pick its look,", "then right-click a trim or framed cable.", "Sneak + right-click a part to clear it.");

    final String displayName;
    final int maxStack;
    final boolean legacy;
    final String[] lore;

    KItem(String displayName, int maxStack, String... lore) {
        this(displayName, maxStack, false, lore);
    }

    KItem(String displayName, int maxStack, boolean legacy, String... lore) {
        this.displayName = displayName;
        this.maxStack = maxStack;
        this.legacy = legacy;
        this.lore = lore;
    }

    /** Dropped from crafting and the guides by the 0.1.11 simplification (cables do it all). */
    boolean legacy() {
        return legacy;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean filter() {
        return this == ITEM_FILTER || this == TAG_FILTER || this == POLY_FILTER;
    }

    static KItem byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
