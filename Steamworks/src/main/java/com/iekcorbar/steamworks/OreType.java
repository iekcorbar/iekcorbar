package com.iekcorbar.steamworks;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Custom ore blocks. In the world each one is a note block in a state vanilla
 * never shows (instrument custom_head, powered, note = noteId); the
 * Steamworks-blocks resource pack gives those states the ore textures.
 */
public enum OreType {
    ZINC_ORE("Zinc Ore", 0, Material.STONE, MaterialType.RAW_ZINC, 1, 1, 1, 2, 3.0),
    DEEPSLATE_ZINC_ORE("Deepslate Zinc Ore", 1, Material.DEEPSLATE, MaterialType.RAW_ZINC, 1, 1, 1, 2, 4.5),
    TIN_ORE("Tin Ore", 2, Material.STONE, MaterialType.RAW_TIN, 1, 1, 1, 2, 3.0),
    DEEPSLATE_TIN_ORE("Deepslate Tin Ore", 3, Material.DEEPSLATE, MaterialType.RAW_TIN, 1, 1, 1, 2, 4.5),
    SULFUR_ORE("Sulfur Ore", 4, Material.STONE, MaterialType.SULFUR, 2, 4, 2, 2, 3.0),
    DEEPSLATE_SULFUR_ORE("Deepslate Sulfur Ore", 5, Material.DEEPSLATE, MaterialType.SULFUR, 2, 4, 2, 2, 4.5),
    NETHER_SULFUR_ORE("Nether Sulfur Ore", 6, Material.NETHERRACK, MaterialType.SULFUR, 2, 5, 1, 2, 2.0);

    public final String displayName;
    public final int noteId;
    /** The stone it sits in (and drops-look like). */
    public final Material host;
    public final MaterialType drop;
    public final int minDrop, maxDrop;
    /** 1 = stone pickaxe or better, 2 = iron pickaxe or better. */
    public final int tier;
    public final int xp;
    /** Like vanilla hardness: 3 = iron ore, 4.5 = deepslate iron ore. */
    public final double hardness;

    OreType(String displayName, int noteId, Material host, MaterialType drop, int minDrop, int maxDrop, int tier, int xp, double hardness) {
        this.displayName = displayName;
        this.noteId = noteId;
        this.host = host;
        this.drop = drop;
        this.minDrop = minDrop;
        this.maxDrop = maxDrop;
        this.tier = tier;
        this.xp = xp;
        this.hardness = hardness;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OreType byNote(int note) {
        for (OreType t : values()) if (t.noteId == note) return t;
        return null;
    }

    public static OreType byId(String id) {
        for (OreType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
