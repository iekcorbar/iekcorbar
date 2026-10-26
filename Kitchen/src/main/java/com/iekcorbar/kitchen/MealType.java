package com.iekcorbar.kitchen;

/**
 * Meals you put down and eat bite by bite (anyone can share). Each piece is
 * its own display model (kitchen:meal_&lt;piece&gt;); one bite = one piece, lifted
 * by the chopsticks (or the cup itself for tea) and eaten. Offsets are in
 * blocks from the block centre, before turning toward the player who put it down.
 */
enum MealType {
    SUSHI_PLATTER(KItem.SUSHI_PLATTER, "meal_plate", KItem.PLATE, true, 4, 5f, new Piece[]{
            new Piece("meal_salmon_nigiri", -0.18, -0.1), new Piece("meal_salmon_nigiri", -0.18, 0.1),
            new Piece("meal_tuna_nigiri", 0, -0.1), new Piece("meal_tuna_nigiri", 0, 0.1),
            new Piece("meal_maki", 0.18, -0.1), new Piece("meal_maki", 0.18, 0.1)}),
    RAMEN(KItem.RAMEN, "meal_ramen_bowl", null, true, 3, 3.5f, new Piece[]{
            new Piece("meal_ramen_pork", -0.07, -0.06), new Piece("meal_ramen_egg", 0.07, -0.06),
            new Piece("meal_ramen_negi", 0.06, 0.07), new Piece("meal_ramen_noodles", 0, 0)}),
    BENTO_BOX(KItem.BENTO_BOX, "meal_bento_box", null, true, 4, 4.5f, new Piece[]{
            new Piece("meal_bento_rice", -0.12, 0), new Piece("meal_bento_tamagoyaki", 0.1, -0.08),
            new Piece("meal_bento_tempura", 0.1, 0.08), new Piece("meal_bento_takuan", -0.12, 0.12)}),
    DANGO_PLATE(KItem.DANGO_PLATE, "meal_plate", KItem.PLATE, true, 5, 6f, new Piece[]{
            new Piece("meal_dango", 0, -0.12), new Piece("meal_dango", 0, 0), new Piece("meal_dango", 0, 0.12)}),
    TEA_SET(KItem.TEA_SET, "meal_tea_tray", KItem.PLATE, false, 2, 1f, new Piece[]{
            new Piece("meal_tea_cup", -0.18, -0.15), new Piece("meal_tea_cup", 0.18, -0.15),
            new Piece("meal_tea_cup", -0.18, 0.15), new Piece("meal_tea_cup", 0.18, 0.15)});

    record Piece(String model, double dx, double dz) {}

    final KItem item;
    final String base;
    /** Left behind (dropped) when the last bite is gone; null = nothing (ramen gives its bowl, see Meals). */
    final KItem leftover;
    final boolean chopsticks;
    final int nutrition;
    final float saturation;
    final Piece[] pieces;

    MealType(KItem item, String base, KItem leftover, boolean chopsticks, int nutrition, float saturation, Piece[] pieces) {
        this.item = item;
        this.base = base;
        this.leftover = leftover;
        this.chopsticks = chopsticks;
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.pieces = pieces;
    }

    static MealType of(KItem item) {
        if (item == null) return null;
        for (MealType t : values()) if (t.item == item) return t;
        return null;
    }
}
