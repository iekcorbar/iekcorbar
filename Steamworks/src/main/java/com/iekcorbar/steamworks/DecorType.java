package com.iekcorbar.steamworks;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Decorative blocks: note blocks in reserved states (instrument custom_head,
 * key 0-24 = powered + note, 25-49 = unpowered + note; 0-6 are the ores).
 * Textures come from tools/decor_textures.py (same ids and keys).
 * Recipe: 3 base + 1 accent in a 2x2 square = 4 blocks.
 */
public enum DecorType {
    RIVETED_COPPER_PLATE("Riveted Copper Plate", 7, 3.5, true, MaterialType.COPPER_PLATE, Material.IRON_NUGGET),
    BRASS_TILES("Brass Tiles", 8, 3.5, true, MaterialType.BRASS_PLATE, Material.BRICK),
    CAST_IRON_GRATE("Cast Iron Grate", 9, 4.0, true, Material.IRON_BARS, MaterialType.IRON_PLATE),
    PIPE_WALL("Pipe Wall", 10, 3.0, true, Material.COPPER_INGOT, MaterialType.COPPER_PLATE),
    GEAR_PANEL("Gear Panel", 11, 3.5, true, MaterialType.IRON_PLATE, PartType.COGWHEEL),
    BOILER_PLATE("Boiler Plate", 12, 3.5, true, MaterialType.COPPER_PLATE, Material.CLOCK),
    BRASS_DIAMOND_PLATE("Brass Diamond Plate", 13, 3.5, true, MaterialType.BRASS_PLATE, Material.IRON_NUGGET),
    VERDIGRIS_PLATE("Verdigris Plate", 14, 3.5, true, MaterialType.COPPER_PLATE, Material.CYAN_DYE),
    LACQUER_BRASS_PANEL("Lacquered Brass-Bound Panel", 15, 2.0, false, Material.MANGROVE_PLANKS, MaterialType.BRASS_PLATE),
    COPPER_SHOJI("Copper Shoji", 16, 1.5, false, Material.PAPER, MaterialType.COPPER_PLATE),
    GEAR_INLAY_WOOD("Gear Inlay Wood", 17, 2.0, false, Material.DARK_OAK_PLANKS, PartType.COGWHEEL),
    BRASS_CREST_LACQUER("Brass Crest Lacquer", 18, 2.0, false, Material.BLACK_CONCRETE, MaterialType.BRASS_PLATE),
    BOILER_TILES("Boiler Tiles", 19, 3.0, true, Material.BRICKS, MaterialType.COPPER_PLATE),
    SAKURA_COPPER_PLANKS("Copper-Seamed Sakura Planks", 20, 2.0, false, Material.CHERRY_PLANKS, MaterialType.COPPER_PLATE),
    PAPER_LANTERN_WALL("Paper Lantern Wall", 21, 1.0, false, Material.PAPER, Material.GLOWSTONE_DUST),
    MAKI_E_LACQUER("Maki-e Lacquer", 22, 2.0, false, Material.BLACK_CONCRETE, Material.GOLD_INGOT),
    SHOJI_SCREEN("Shoji Screen", 23, 1.0, false, Material.PAPER, Material.BIRCH_PLANKS),
    VERMILION_WOOD("Vermilion Lacquered Wood", 24, 2.0, false, Material.MANGROVE_PLANKS, Material.RED_DYE),
    TATAMI("Tatami", 25, 0.8, false, Material.WHEAT, Material.GREEN_DYE),
    WOVEN_BAMBOO("Woven Bamboo", 26, 1.0, false, Material.BAMBOO, Material.STRING),
    YAKISUGI("Yakisugi (Charred Wood)", 27, 2.0, false, Material.DARK_OAK_PLANKS, Material.CHARCOAL),
    SAKURA_PLANKS("Sakura Planks", 28, 2.0, false, Material.CHERRY_PLANKS, Material.PINK_DYE),
    RICE_STRAW_WALL("Rice Straw Wall", 29, 0.8, false, Material.WHEAT, Material.STRING),
    WOODEN_LATTICE("Wooden Lattice", 30, 1.5, false, Material.STICK, Material.OAK_PLANKS),
    KAWARA_ROOF_TILES("Kawara Roof Tiles", 31, 2.0, true, Material.DEEPSLATE_TILES, Material.BRICK),
    GRANITE_PAVERS("Granite Pavers", 32, 2.0, true, Material.STONE, Material.GRANITE),
    MOSSY_GARDEN_STONE("Mossy Garden Stone", 33, 2.0, true, Material.COBBLESTONE, Material.MOSS_BLOCK),
    BANDED_PLASTER_WALL("Wood-Banded Plaster Wall", 34, 1.5, true, Material.CALCITE, Material.DARK_OAK_PLANKS),
    LANTERN_STONE("Lantern Stone", 35, 2.0, true, Material.STONE_BRICKS, Material.TORCH),
    RAKED_GRAVEL_TILE("Raked Gravel Tile", 36, 1.5, true, Material.GRAVEL, Material.STONE),
    DARK_SLATE("Dark Slate", 37, 2.5, true, Material.DEEPSLATE, Material.FLINT),
    WHITE_PLASTER("White Plaster", 38, 1.5, true, Material.CALCITE, Material.WHITE_DYE);

    public final String displayName;
    /** Reserved note-block state (see class comment). */
    public final int key;
    public final double hardness;
    /** Mined fastest with a pickaxe (stone/metal) - otherwise with an axe. */
    public final boolean pickaxe;
    public final Object base, accent;

    DecorType(String displayName, int key, double hardness, boolean pickaxe, Object base, Object accent) {
        this.displayName = displayName;
        this.key = key;
        this.hardness = hardness;
        this.pickaxe = pickaxe;
        this.base = base;
        this.accent = accent;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DecorType byKey(int key) {
        for (DecorType t : values()) if (t.key == key) return t;
        return null;
    }

    public static DecorType byId(String id) {
        for (DecorType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
