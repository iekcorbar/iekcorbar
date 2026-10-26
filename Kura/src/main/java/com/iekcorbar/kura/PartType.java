package com.iekcorbar.kura;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Placed network parts. Most sit on a hidden BARRIER with our model drawn by an
 * ItemDisplay; the Interface and Open Crate are real vanilla barrels (so hoppers,
 * Steamworks belts and arms can use them) covered by our model, and the Level
 * Emitter swaps between a barrier and a redstone block to give its signal.
 */
enum PartType {
    CONNECTOR(KItem.INVENTORY_CONNECTOR, Material.BARRIER, Kind.CONNECTOR, false, Sound.BLOCK_COPPER_PLACE),
    TERMINAL(KItem.STORAGE_TERMINAL, Material.BARRIER, Kind.DEVICE, true, Sound.BLOCK_COPPER_PLACE),
    CRAFTING_TERMINAL(KItem.CRAFTING_TERMINAL, Material.BARRIER, Kind.DEVICE, true, Sound.BLOCK_COPPER_PLACE),
    TRIM(KItem.INVENTORY_TRIM, Material.BARRIER, Kind.TRIM, false, Sound.BLOCK_WOOD_PLACE),
    CABLE(KItem.INVENTORY_CABLE, Material.BARRIER, Kind.CABLE, false, Sound.BLOCK_COPPER_PLACE),
    FRAMED_CABLE(KItem.FRAMED_CABLE, Material.BARRIER, Kind.CABLE, false, Sound.BLOCK_WOOD_PLACE),
    CABLE_CONNECTOR(KItem.CABLE_CONNECTOR, Material.BARRIER, Kind.CABLE_CONNECTOR, true, Sound.BLOCK_COPPER_PLACE),
    INVENTORY_HOPPER(KItem.INVENTORY_HOPPER, Material.BARRIER, Kind.DEVICE, true, Sound.BLOCK_METAL_PLACE),
    LEVEL_EMITTER(KItem.LEVEL_EMITTER, Material.BARRIER, Kind.DEVICE, true, Sound.BLOCK_METAL_PLACE),
    INTERFACE(KItem.INVENTORY_INTERFACE, Material.BARREL, Kind.DEVICE, false, Sound.BLOCK_WOOD_PLACE),
    OPEN_CRATE(KItem.OPEN_CRATE, Material.BARREL, Kind.DEVICE, true, Sound.BLOCK_WOOD_PLACE),
    FILING_CABINET(KItem.FILING_CABINET, Material.BARRIER, Kind.STORE, true, Sound.BLOCK_WOOD_PLACE);

    /** How a part takes part in the network search (see Networks.links). */
    enum Kind { CONNECTOR, TRIM, CABLE, CABLE_CONNECTOR, STORE, DEVICE }

    final KItem item;
    final Material base;
    final Kind kind;
    /** Turned towards a side (all six for connectors/hoppers, the four horizontal ones for terminals). */
    final boolean directional;
    final Sound placeSound;

    PartType(KItem item, Material base, Kind kind, boolean directional, Sound placeSound) {
        this.item = item;
        this.base = base;
        this.kind = kind;
        this.directional = directional;
        this.placeSound = placeSound;
    }

    boolean barrel() {
        return base == Material.BARREL;
    }

    boolean paintable() {
        return this == TRIM || this == FRAMED_CABLE;
    }

    boolean terminal() {
        return this == TERMINAL || this == CRAFTING_TERMINAL;
    }

    /** Points at any of the six sides (else only horizontal). */
    boolean anySide() {
        return this == CABLE_CONNECTOR || this == INVENTORY_HOPPER || this == OPEN_CRATE;
    }

    static PartType of(KItem item) {
        if (item == null) return null;
        for (PartType t : values()) if (t.item == item) return t;
        return null;
    }
}
