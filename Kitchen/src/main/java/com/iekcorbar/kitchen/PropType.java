package com.iekcorbar.kitchen;

import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Placed Kitchen stations and props. A prop is a hidden vanilla base block
 * (barrier, or a carpet for flat ones you walk over) with our model drawn by
 * an ItemDisplay (model kitchen:&lt;id&gt; from Kitchen-pack.zip). Positions and
 * contents are saved in the chunk's data; displays respawn with the chunk.
 */
enum PropType {
    CUTTING_BOARD(KItem.CUTTING_BOARD, Behaviour.BOARD, Material.BARRIER, 1, Sound.BLOCK_WOOD_PLACE),
    COOKING_POT(KItem.COOKING_POT, Behaviour.POT, Material.BARRIER, 8, Sound.BLOCK_DECORATED_POT_PLACE),
    KAMADO_STOVE(KItem.KAMADO_STOVE, Behaviour.STOVE, Material.BARRIER, 4, Sound.BLOCK_STONE_PLACE),
    TANSU_CABINET(KItem.TANSU_CABINET, Behaviour.STORAGE, Material.BARRIER, 27, Sound.BLOCK_WOOD_PLACE),
    BAMBOO_BASKET(KItem.BAMBOO_BASKET, Behaviour.BASKET, Material.BARRIER, 9, Sound.BLOCK_BAMBOO_PLACE),
    RICE_SACK(KItem.RICE_SACK, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOL_PLACE),
    SOYBEAN_CRATE(KItem.SOYBEAN_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    NAPA_CABBAGE_CRATE(KItem.NAPA_CABBAGE_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    DAIKON_CRATE(KItem.DAIKON_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    GREEN_ONION_CRATE(KItem.GREEN_ONION_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    TEA_CRATE(KItem.TEA_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    CARROT_CRATE(KItem.CARROT_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    POTATO_CRATE(KItem.POTATO_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    BEETROOT_CRATE(KItem.BEETROOT_CRATE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_WOOD_PLACE),
    STRAW_BALE(KItem.STRAW_BALE, Behaviour.DECOR, Material.BARRIER, 0, Sound.BLOCK_GRASS_PLACE),
    RICH_SOIL(KItem.RICH_SOIL, Behaviour.SOIL, Material.BARRIER, 0, Sound.BLOCK_ROOTED_DIRT_PLACE),
    ORGANIC_COMPOST(KItem.ORGANIC_COMPOST, Behaviour.COMPOST, Material.BARRIER, 0, Sound.BLOCK_ROOTED_DIRT_PLACE),
    TATAMI_MAT(KItem.TATAMI_MAT, Behaviour.DECOR, Material.WHITE_CARPET, 0, Sound.BLOCK_GRASS_PLACE),
    ZABUTON(KItem.ZABUTON, Behaviour.SEAT, Material.WHITE_CARPET, 0, Sound.BLOCK_WOOL_PLACE),
MISO_VAT(KItem.MISO_VAT, Behaviour.STORAGE, Material.BARRIER, 9, Sound.BLOCK_WOOD_PLACE),
TSUKEMONO_POT(KItem.TSUKEMONO_POT, Behaviour.STORAGE, Material.BARRIER, 9, Sound.BLOCK_DECORATED_POT_PLACE),
SMOKER(KItem.SMOKER, Behaviour.STORAGE, Material.BARRIER, 4, Sound.BLOCK_STONE_PLACE),
DRYING_RACK(KItem.DRYING_RACK, Behaviour.STORAGE, Material.BARRIER, 6, Sound.BLOCK_WOOD_PLACE);

    enum Behaviour { BOARD, POT, STOVE, STORAGE, BASKET, DECOR, SOIL, COMPOST, SEAT, FERMENT }

    final KItem item;
    final Behaviour behaviour;
    final Material base;
    /** Saved item slots. */
    final int slots;
    final Sound placeSound;

    PropType(KItem item, Behaviour behaviour, Material base, int slots, Sound placeSound) {
        this.item = item;
        this.behaviour = behaviour;
        this.base = base;
        this.slots = slots;
        this.placeSound = placeSound;
    }

    String model() {
        return item.id();
    }

    boolean flat() {
        return base != Material.BARRIER;
    }

    static PropType of(KItem item) {
        if (item == null) return null;
        for (PropType t : values()) if (t.item == item) return t;
        return null;
    }
}
