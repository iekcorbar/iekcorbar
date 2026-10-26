package com.iekcorbar.zukan;

import org.bukkit.Material;

/** The "Zukan" tab of the advancement screen. Toasts only. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(ZukanPlugin plugin) {
        Adv a = new Adv(plugin, "zukan", "Zukan", "Every recipe, one book away.", "book", "minecraft:block/bookshelf", true);
        a.add("book", null, "book", "Open Book Exam", "Get a Zukan book (craft it or buy it from a wandering trader).", "task");
        a.add("opened", "book", "enchanted_book", "Curious Mind", "Open the Zukan.", "task");
        a.add("search", "opened", "spyglass", "Looking For Something", "Search for an item.", "task");
        a.add("recipe", "opened", "crafting_table", "Show Me How", "Look up a recipe.", "task");
        a.add("uses", "recipe", "writable_book", "What Is It Good For?", "Look up what an item is used for.", "task");
        a.add("bookmark", "opened", "name_tag", "Dog-Eared", "Bookmark an item.", "task");
        a.add("hoarder", "bookmark", "chest", "Bookmark Hoarder", "Bookmark fifty items.", "task").hidden();
        a.add("disaster", "search", "tnt", "Recipe for Disaster", "Search for exactly that.", "task").hidden();
        a.add("filled", "recipe", "crafting_table", "Hands-Free Crafting", "Fill a Kura Crafting Terminal from a recipe.", "goal");
        a.add("cheat", "opened", "command_block", "Behind the Curtain", "Use the staff cheat mode.", "challenge").hidden();
        a.scanner(it -> it.getType() == Material.BOOK && plugin.isBookItem(it) ? "book" : null);
        a.item("book", "book");
        return a;
    }
}
