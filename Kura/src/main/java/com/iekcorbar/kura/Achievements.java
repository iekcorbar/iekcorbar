package com.iekcorbar.kura;

/** The "Kura" tab of the advancement screen. Toasts only; items are awarded by holding them, a few by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(KuraPlugin plugin, Items items) {
        Adv a = new Adv(plugin, "kura", "Kura Storage", "One network for all your chests.", "chest", "minecraft:block/stone_bricks", true);
        a.add("connector", null, "chest", "Joined Up", "Get an Inventory Connector.", "task");
        a.add("terminal", "connector", "spyglass", "One Terminal to Find Them All", "Get a Storage Terminal.", "task");
        a.add("opened", "terminal", "compass", "Where Did the Iron Go?", "Open a Storage Terminal.", "task");
        a.add("crafting", "terminal", "crafting_table", "Crafting From the Wall", "Get a Crafting Terminal.", "goal");
        a.add("cable", "connector", "string", "Long Distance", "Get an Inventory Cable.", "task");
        a.add("cable_connector", "cable", "lever", "Plug and Play", "Get a Cable Connector.", "task");
        a.add("trim", "connector", "oak_trapdoor", "Seamless", "Get an Inventory Trim.", "task");
        a.add("framed", "cable", "item_frame", "Dressed Up", "Get a Framed Inventory Cable.", "task");
        a.add("hopper", "connector", "hopper", "Network Plumbing", "Get an Inventory Hopper.", "task");
        a.add("emitter", "hopper", "comparator", "Level Headed", "Get a Level Emitter.", "task");
        a.add("interface", "hopper", "barrel", "The Front Door", "Get an Inventory Interface.", "task");
        a.add("crate", "interface", "dropper", "Open Handed", "Get an Open Crate.", "task");
        a.add("cabinet", "terminal", "bookshelf", "File Under Everything", "Get a Filing Cabinet.", "goal");
        a.add("wireless", "terminal", "ender_pearl", "No Strings Attached", "Get a Wireless Terminal.", "goal");
        a.add("adv_wireless", "wireless", "ender_eye", "Across the World", "Get an Advanced Wireless Terminal.", "challenge");
        a.add("filter", "connector", "paper", "Picky Eater", "Get an Item Filter.", "task");
        a.add("tag_filter", "filter", "name_tag", "By Category", "Get a Tag Filter.", "task");
        a.add("poly_filter", "filter", "chest_minecart", "Just Like the Others", "Get a Polymorphic Filter.", "task");
        a.add("configurator", "connector", "stick", "Fine Tuning", "Get an Inventory Configurator.", "task");
        a.add("paint", "configurator", "brush", "Interior Design", "Get a Paint Kit.", "task");
        a.add("cookie", "opened", "cookie", "Cookie Monster", "Search the terminal for cookies.", "task").hidden();
        a.add("courier", "interface", "copper_ingot", "Copper Courier", "Bind a copper golem to an Inventory Interface.", "goal");
        a.add("dobby", "courier", "name_tag", "A Free Golem", "Name a courier Dobby and let it deliver.", "task").hidden();
        a.scanner(it -> {
            KItem t = items.typeOf(it);
            return t == null ? null : t.id();
        });
        a.item("inventory_connector", "connector").item("storage_terminal", "terminal").item("crafting_terminal", "crafting")
                .item("inventory_cable", "cable").item("cable_connector", "cable_connector").item("inventory_trim", "trim")
                .item("framed_cable", "framed").item("inventory_hopper", "hopper").item("level_emitter", "emitter")
                .item("inventory_interface", "interface").item("open_crate", "crate").item("filing_cabinet", "cabinet")
                .item("wireless_terminal", "wireless").item("adv_wireless_terminal", "adv_wireless").item("item_filter", "filter")
                .item("tag_filter", "tag_filter").item("poly_filter", "poly_filter").item("configurator", "configurator")
                .item("paint_kit", "paint");
        return a;
    }
}
