package com.iekcorbar.steamworks;

import java.util.Locale;

/** Crafting materials made by Steamworks machines (items only, not placeable). */
public enum MaterialType {
    IRON_PLATE("Iron Plate"),
    COPPER_PLATE("Copper Plate"),
    GOLDEN_PLATE("Golden Plate"),
    BRASS_INGOT("Brass Ingot"),
    BRASS_PLATE("Brass Plate"),
    RAW_ZINC("Raw Zinc"),
    ZINC_INGOT("Zinc Ingot"),
    RAW_TIN("Raw Tin"),
    TIN_INGOT("Tin Ingot"),
    SULFUR("Sulfur"),
    FLOUR("Flour", "Ground from wheat in a millstone.", "Bake it in a heated kettle (mixer)."),
    CANISTER("Empty Canister", "Right-click a tank to fill it (1000 mB)."),
    CRUDE_CANISTER("Crude Oil Canister", "Right-click a tank to pour it in."),
    FUEL_CANISTER("Fuel Canister", "Right-click a locomotive to refuel it,", "or a tank to pour it in."),
    WASTE_CANISTER("Chemical Waste Canister", "Toxic! Store it in a tank or", "neutralize it in a heated kettle."),
    DOWSING_ROD("Oil Dowsing Rod", "Right-click: oil under this chunk", "and where the nearest field is."),
    ARM_GRIPPER("Gripper Head", "Tool head for a Karakuri Arm:", "PICK and DROP items."),
    ARM_PLACER("Placer Head", "Tool head for a Karakuri Arm:", "PICK, DROP, PLACE blocks and USE bone meal."),
    ARM_DRILL("Drill Head", "Tool head for a Karakuri Arm:", "BREAK blocks at its points."),
    PUNCH_CARD("Punch Card", "Right-click a Karakuri Arm: copies its program", "onto a blank card, or loads a punched one.",
            "Programming guide: on the iekcorbar website (/sw help)."),
    WRENCH("Steam Wrench", "Right-click a part: it turns clockwise", "around the face you clicked. Sneak:", "turns it back into an item in your", "inventory, quietly disconnected."),
    ANDESITE_ALLOY("Andesite Alloy", "Hammered andesite with metal speckles -", "the base of every casing."),
    ANDESITE_CASING("Andesite Casing", "A stripped log shrunk around a shaft stub.", "The heart of most machine recipes."),
    BRASS_CASING("Brass Casing", "The smooth, golden cousin of the andesite casing."),
    WHISK("Whisk", "Bends the Mixer's turning into stirring."),
    PROPELLER("Propeller", "Two copper blades on a steel hub - what makes a Fan blow."),
    ZINC_NUGGET("Zinc Nugget", "Nine to a zinc ingot; with andesite it makes alloy.");

    public final String displayName;
    public final String[] lore;

    MaterialType(String displayName, String... lore) {
        this.displayName = displayName;
        this.lore = lore.length == 0 ? new String[]{"Crafting material for machines."} : lore;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String model() {
        return "sw_" + id();
    }

    public static MaterialType byId(String id) {
        for (MaterialType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
