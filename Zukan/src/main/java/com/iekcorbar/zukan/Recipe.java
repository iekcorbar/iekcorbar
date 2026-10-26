package com.iekcorbar.zukan;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One way to make something: ingredients (each cell = the items that fit), results, extra text. */
final class Recipe {
    /** A kind of recipe (Crafting, Smelting, a Steamworks machine...). Shown as a tab. */
    static final class Category {
        final String id, name;
        final ItemStack icon;
        final int order;

        Category(String id, String name, ItemStack icon, int order) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.order = order;
        }
    }

    final Category category;
    /** Grid recipes: exactly 9 cells (row-major 3x3), empty list = empty cell. Others: one cell per ingredient. */
    final List<List<ItemStack>> inputs;
    final List<ItemStack> outputs;
    final List<String> info;
    final boolean grid, shapeless;
    /** The machine/station that does it (shown under the recipe), or null. */
    final ItemStack station;

    Recipe(Category category, List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> info, boolean grid, boolean shapeless, ItemStack station) {
        this.category = category;
        this.inputs = inputs;
        this.outputs = outputs;
        this.info = info;
        this.grid = grid;
        this.shapeless = shapeless;
        this.station = station;
    }
}
