package com.iekcorbar.kitchen;

import java.util.Locale;

/**
 * Kitchen crops. A growing crop is a structure void above farmland or Rich Soil; its type and
 * stage live in the chunk's crop list (see Crops) and the visible plant is an ItemDisplay with
 * the model kitchen:block/&lt;id&gt;_stage&lt;0-3&gt; (or _wild) that tools/make_pack.py draws.
 */
enum CropType {
    RICE(KItem.RICE, KItem.RICE, 2, 4, null, 0, 0, true, -1),
    SOYBEAN(KItem.SOYBEANS, KItem.SOYBEANS, 2, 4, null, 0, 0, false, -1),
    NAPA_CABBAGE(KItem.NAPA_CABBAGE_SEEDS, KItem.NAPA_CABBAGE, 1, 1, KItem.NAPA_CABBAGE_SEEDS, 0, 2, false, -1),
    GREEN_ONION(KItem.GREEN_ONION, KItem.GREEN_ONION, 2, 3, null, 0, 0, false, -1),
    DAIKON(KItem.DAIKON_SEEDS, KItem.DAIKON, 1, 1, KItem.DAIKON_SEEDS, 0, 2, false, -1),
    TEA(KItem.TEA_SEEDS, KItem.TEA_LEAVES, 2, 3, KItem.TEA_SEEDS, 0, 1, false, 2),
    CORN(KItem.CORN, KItem.CORN, 2, 4, null, 0, 0, false, -1),
    TOMATO(KItem.TOMATO, KItem.TOMATO, 1, 3, null, 0, 0, false, -1),
    CHILI(KItem.CHILI, KItem.CHILI, 1, 3, null, 0, 0, false, -1),
    SESAME(KItem.SESAME_SEEDS, KItem.SESAME_SEEDS, 2, 3, null, 0, 0, false, -1),
    SHISO(KItem.SHISO_SEEDS, KItem.SHISO_LEAVES, 1, 2, KItem.SHISO_SEEDS, 0, 1, false, -1),
    GINGER(KItem.GINGER, KItem.GINGER, 1, 2, null, 0, 0, false, -1),
    WASABI(KItem.WASABI_ROOT, KItem.WASABI_ROOT, 1, 1, null, 0, 0, true, -1),
    MIZUNA(KItem.MIZUNA_SEEDS, KItem.MIZUNA, 1, 2, KItem.MIZUNA_SEEDS, 0, 1, false, -1),
    KABOCHA(KItem.KABOCHA, KItem.KABOCHA, 1, 2, null, 0, 0, false, -1),
    BAMBOO_SHOOTS(KItem.BAMBOO_SHOOTS, KItem.BAMBOO_SHOOTS, 1, 2, null, 0, 0, false, -1),
    EGGPLANT(KItem.EGGPLANT, KItem.EGGPLANT, 2, 3, null, 0, 0, false, -1);

    static final int STAGES = 4;

    final KItem seed, produce, extra;
    final int minProduce, maxProduce, minExtra, maxExtra;
    /** Rice needs wet farmland. */
    final boolean needsWater;
    /** Stage it drops back to when picked by hand (tea bush regrows); -1 = replanted at stage 0. */
    final int regrowStage;

    CropType(KItem seed, KItem produce, int minProduce, int maxProduce, KItem extra, int minExtra, int maxExtra,
             boolean needsWater, int regrowStage) {
        this.seed = seed;
        this.produce = produce;
        this.minProduce = minProduce;
        this.maxProduce = maxProduce;
        this.extra = extra;
        this.minExtra = minExtra;
        this.maxExtra = maxExtra;
        this.needsWater = needsWater;
        this.regrowStage = regrowStage;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static CropType bySeed(KItem seed) {
        if (seed == null) return null;
        for (CropType t : values()) if (t.seed == seed) return t;
        return null;
    }

    static CropType byId(String id) {
        for (CropType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
