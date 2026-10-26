package com.iekcorbar.steamworks;

/** The "Steamworks" tab of the advancement screen. Toasts only; items are awarded by holding them, a few by action. */
final class Achievements {
    private Achievements() {
    }

    static Adv create(SteamworksPlugin plugin) {
        Adv a = new Adv(plugin, "steamworks", "Steamworks", "Gears, steam and brass.", "iron_pickaxe", "minecraft:block/copper_block", true);
        // first steps
        a.add("shaft", null, "stick", "Turning Point", "Get a Shaft.", "task");
        a.add("placed", "shaft", "piston", "Engineer's Eye", "Place a machine part.", "task");
        a.add("crank", "placed", "lever", "Elbow Grease", "Get a Hand Crank.", "task");
        a.add("cogwheel", "shaft", "iron_nugget", "In Gear", "Get a Cogwheel.", "task");
        a.add("large_cogwheel", "cogwheel", "iron_ingot", "Big Teeth", "Get a Large Cogwheel.", "task");
        a.add("gearbox", "cogwheel", "dispenser", "Gearhead", "Get a Gearbox.", "task");
        a.add("sprocket", "gearbox", "chain", "Chain Reaction", "Get a Chain Sprocket.", "task");
        a.add("shift", "gearbox", "repeater", "Shifting Gears", "Get a Clutch or a Gearshift.", "task");
        a.add("gauge", "cogwheel", "clock", "Measure Twice", "Get a Speed or a Stress Gauge.", "task");
        // power
        a.add("water_wheel", "placed", "water_bucket", "Going With the Flow", "Get a Water Wheel.", "task");
        a.add("windmill", "placed", "white_banner", "Tilting at Windmills", "Get a Windmill.", "task");
        a.add("boiler", "placed", "furnace", "Steam Power", "Get a Steam Boiler.", "goal");
        a.add("engine", "boiler", "blast_furnace", "Full Steam Ahead", "Get a Steam Engine.", "goal");
        // processing
        a.add("millstone", "placed", "grindstone", "Grist for the Mill", "Get a Millstone.", "task");
        a.add("hammer", "placed", "anvil", "Hammer Time", "Get a Steam Hammer.", "task");
        a.add("plates", "hammer", "iron_ingot", "Plated", "Get an Iron, Copper or Golden Plate.", "task");
        a.add("crusher", "placed", "iron_block", "Crushing It", "Get a Crushing Wheel.", "task");
        a.add("fan", "placed", "feather", "Bellows", "Get a Bellows Fan.", "task");
        a.add("kettle", "placed", "cauldron", "Kettle On", "Get a Kettle.", "task");
        a.add("mixer", "kettle", "brewing_stand", "Mixed Metals", "Get a Mixer.", "task");
        a.add("brass", "mixer", "gold_ingot", "The Brass Age", "Get a Brass Ingot.", "goal");
        a.add("brass_plate", "brass", "gold_block", "Polished", "Get a Brass Plate.", "task");
        // ores
        a.add("zinc_ore", null, "light_blue_dye", "Unearthed", "Find zinc ore.", "task");
        a.add("tin_ore", "zinc_ore", "white_dye", "Tin Can Alley", "Find tin ore.", "task");
        a.add("sulfur_ore", "zinc_ore", "yellow_dye", "Smells Like Trouble", "Find sulfur ore.", "task");
        a.add("raw_zinc", "zinc_ore", "raw_iron", "Raw Zinc", "Mine raw zinc.", "task");
        a.add("raw_tin", "tin_ore", "raw_copper", "Raw Tin", "Mine raw tin.", "task");
        a.add("sulfur", "sulfur_ore", "glowstone_dust", "Brimstone", "Collect sulfur.", "task");
        a.add("prospector", "zinc_ore", "golden_pickaxe", "Prospector", "Collect raw zinc, raw tin and sulfur.", "goal")
                .allOf("raw_zinc", "raw_tin", "sulfur");
        a.add("ingots", "prospector", "iron_ingot", "Smelted", "Smelt a zinc or a tin ingot.", "task");
        // oil
        a.add("dowsing", null, "stick", "Divining Rod", "Craft an Oil Dowsing Rod.", "task");
        a.add("pumpjack", "dowsing", "iron_bars", "Black Gold", "Get a Pumpjack.", "goal");
        a.add("pipe", "pumpjack", "copper_ingot", "Plumber", "Get a Pipe.", "task");
        a.add("tank", "pipe", "barrel", "Tank Up", "Get a Tank.", "task");
        a.add("refinery", "tank", "blast_furnace", "Cracking Up", "Get a Refinery.", "goal");
        a.add("canister", "refinery", "bucket", "Canned Fuel", "Get a fuel canister.", "task");
        // machines
        a.add("drill", "placed", "iron_pickaxe", "Boring", "Get a Mechanical Drill.", "task");
        a.add("saw", "placed", "iron_axe", "Lumberjack's Dream", "Get a Mechanical Saw.", "task");
        a.add("belt", "placed", "black_carpet", "Conveyor", "Get a Conveyor Belt.", "task");
        a.add("arm", "belt", "iron_hoe", "Karakuri", "Get a Karakuri Arm.", "goal");
        a.add("punch_card", "arm", "paper", "Programmed", "Get a Punch Card.", "task");
        // trains
        a.add("station", null, "oak_sign", "Next Stop", "Get a Station board.", "task");
        a.add("signal", "station", "redstone_lamp", "Green Light", "Get a Signal.", "task");
        a.add("car", "station", "minecart", "All Aboard", "Get a train car.", "goal");
        a.add("ride", "car", "saddle", "Choo Choo", "Ride a train.", "goal");
        // decoration
        a.add("decor", null, "stone_bricks", "Fine Finish", "Get a decorative building block.", "task");
        // jokes
        a.add("tired_arm", "crank", "cooked_chicken", "Arm Day", "Turn a crank for thirty seconds without a break.", "task").hidden();
        a.add("mind_the_gap", "ride", "note_block", "Mind the Gap", "Whistle three times in a row.", "task").hidden();
        a.add("dry_run", "mixer", "bucket", "Dry Run", "Watch a mixer spin above an empty kettle.", "task").hidden();
        a.add("sneeze", "prospector", "gunpowder", "Alchemist's Sneeze", "Carry raw zinc, raw tin and sulfur at once.", "task").hidden();
        a.add("master", "placed", "nether_star", "Master Engineer", "Get a Steam Engine, a Refinery and a Karakuri Arm.", "challenge")
                .allOf("engine", "refinery", "arm").glint();

        a.scanner(it -> {
            PartType p = plugin.items().typeOf(it);
            if (p != null) return "p:" + p.id();
            MaterialType m = plugin.items().materialOf(it);
            if (m != null) return "m:" + m.id();
            if (plugin.items().carOf(it) != null) return "car";
            OreType o = plugin.ores() == null ? null : plugin.ores().itemType(it);
            if (o != null) return "o:" + o.id();
            DecorType d = plugin.decor() == null ? null : plugin.decor().itemType(it);
            return d != null ? "d:" + d.id() : null;
        });
        a.item("p:shaft", "shaft").item("p:hand_crank", "crank").item("p:cogwheel", "cogwheel").item("p:large_cogwheel", "large_cogwheel")
                .item("p:gearbox", "gearbox").item("p:sprocket", "sprocket").item("p:clutch", "shift").item("p:gearshift", "shift")
                .item("p:speed_gauge", "gauge").item("p:stress_gauge", "gauge").item("p:water_wheel", "water_wheel")
                .item("p:windmill", "windmill").item("p:steam_boiler", "boiler").item("p:steam_engine", "engine")
                .item("p:millstone", "millstone").item("p:steam_hammer", "hammer").item("p:crushing_wheel", "crusher")
                .item("p:bellows_fan", "fan").item("p:kettle", "kettle").item("p:mixer", "mixer")
                .item("m:iron_plate", "plates").item("m:copper_plate", "plates").item("m:golden_plate", "plates")
                .item("m:brass_ingot", "brass").item("m:brass_plate", "brass_plate")
                .item("m:raw_zinc", "raw_zinc").item("m:raw_tin", "raw_tin").item("m:sulfur", "sulfur")
                .item("m:zinc_ingot", "ingots").item("m:tin_ingot", "ingots")
                .item("m:dowsing_rod", "dowsing").item("p:pumpjack", "pumpjack").item("p:pipe", "pipe").item("p:tank", "tank")
                .item("p:refinery", "refinery").item("m:fuel_canister", "canister")
                .item("p:drill", "drill").item("p:saw", "saw").item("p:belt", "belt").item("p:arm", "arm")
                .item("m:punch_card", "punch_card").item("p:station", "station").item("p:signal", "signal").item("car", "car");
        for (OreType o : OreType.values()) {
            String n = o.id();
            a.item("o:" + n, n.contains("zinc") ? "zinc_ore" : n.contains("tin") ? "tin_ore" : "sulfur_ore");
        }
        for (DecorType d : DecorType.values()) a.item("d:" + d.id(), "decor");
        return a;
    }
}
