package com.iekcorbar.sakagura;

import java.util.List;
import java.util.Locale;

/**
 * Orchard trees (0.5.0): five species that grow from a sapling over three stages -
 * sprout, young tree, full tree - and, for the fruiting ones, regrow fruit on the canopy.
 * Like garden plants a tree lives as a chunk-data record (see Trees.java); what you see is
 * a stack of ItemDisplays, one model per block level. The base block is a barrier (trunk
 * you bump into), the top of the model may hang over the edges freely.
 */
enum Tree {
    SAKURA("Sakura", "cherry", true, false),
    UME("Ume Plum", "ume", false, false),
    APPLE("Apple", "apple", false, false),
    ORANGE("Orange", "mikan", false, false),
    MAPLE("Red Maple", null, false, true);

    final String displayName;
    /** The Fruit produce id the ripe canopy drops, or null for the decorative maple. */
    final String fruitId;
    /** Pink petals drift off the canopy while players watch. */
    final boolean petals;
    /** Red leaves drift off instead. */
    final boolean leafFall;

    Tree(String displayName, String fruitId, boolean petals, boolean leafFall) {
        this.displayName = displayName;
        this.fruitId = fruitId;
        this.petals = petals;
        this.leafFall = leafFall;
    }

    static final int STAGES = 3;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The sapling item id that plants this tree. */
    String sapling() {
        return id() + "_sapling";
    }

    Fruit fruit() {
        return fruitId == null ? null : Fruit.byId(fruitId);
    }

    /** ItemDisplay models per block level for a stage; the last entry of stage 2 swaps when fruit is ready. */
    String[] levels(int stage, boolean ready) {
        return switch (stage) {
            case 0 -> new String[]{"tree_" + id() + "_0"};
            case 1 -> new String[]{"tree_" + id() + "_1_0", "tree_" + id() + "_1_1"};
            default -> new String[]{"tree_" + id() + "_2_0", "tree_" + id() + "_2_1",
                    ready && fruit() != null ? "tree_" + id() + "_2f_2" : "tree_" + id() + "_2_2"};
        };
    }

    /** Interaction hitbox height: the clickable trunk column. */
    float hitHeight(int stage) {
        return switch (stage) {
            case 0 -> 0.9f;
            case 1 -> 1.9f;
            default -> 3f;
        };
    }

    static Tree byId(String id) {
        if (id == null) return null;
        for (Tree t : values()) if (t.id().equals(id)) return t;
        return null;
    }

    static Tree bySapling(String itemId) {
        if (itemId == null) return null;
        for (Tree t : values()) if (t.sapling().equals(itemId)) return t;
        return null;
    }

    static List<String> saplingIds() {
        return java.util.Arrays.stream(values()).map(Tree::sapling).toList();
    }
}
