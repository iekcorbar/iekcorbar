package com.iekcorbar.steamworks;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.Locale;

/** Every Steamworks part. The model ids are the FreeMinecraftModels files in fmm-models/steamworks/. */
public enum PartType {
    SHAFT("Shaft", Material.COPPER_BLOCK, Kind.METAL,
            "Carries rotation along its length.", "Place against a face to point it that way,", "and turn it later with a Steam Wrench."),
    COGWHEEL("Cogwheel", Material.OAK_PLANKS, Kind.WOOD,
            "Turns its neighbours: cogs side by side spin", "opposite ways at the same speed."),
    LARGE_COGWHEEL("Large Cogwheel", Material.OAK_PLANKS, Kind.WOOD,
            "Meshes diagonally with a small cogwheel:", "the small one turns twice as fast."),
    GEARBOX("Gearbox", Material.SPRUCE_PLANKS, Kind.WOOD,
            "Sends rotation around a corner.", "Connects on its four sides,", "turns the corner it faces with a Steam Wrench."),
    HAND_CRANK("Hand Crank", Material.OAK_PLANKS, Kind.WOOD,
            "Power source: right-click (or hold) to turn it.", "Sneak to turn it the other way. Makes you hungry."),
    WATER_WHEEL("Water Wheel", Material.SPRUCE_PLANKS, Kind.WOOD,
            "Power source: needs flowing water along", "its paddles. More water sides = faster."),
    SPEED_GAUGE("Speed Gauge", Material.GOLD_BLOCK, Kind.METAL,
            "Shows how fast the shaft through it turns.", "Right-click it for the exact numbers."),
    STRESS_GAUGE("Stress Gauge", Material.COPPER_BLOCK, Kind.METAL,
            "Shows how much of the network's power is used.", "Right-click it for the exact numbers."),
    MILLSTONE("Millstone", Material.STONE, Kind.STONE,
            "Grinds items. Power the wheel from the side.", "Right-click to put items in or take results out."),
    STEAM_HAMMER("Steam Hammer", Material.IRON_BLOCK, Kind.METAL,
            "Presses items lying in the block under it.", "Power it with a shaft from the side."),
    CLUTCH("Clutch", Material.IRON_BLOCK, Kind.METAL,
            "Passes rotation on like a shaft.", "A redstone signal disconnects it."),
    GEARSHIFT("Gearshift", Material.COPPER_BLOCK, Kind.METAL,
            "Passes rotation on like a shaft.", "A redstone signal reverses the far side."),
    BELLOWS_FAN("Bellows Fan", Material.COPPER_BLOCK, Kind.METAL,
            "Blows air out of its front (power it from behind).", "Air through lava smelts, fire smokes, water washes."),
    KETTLE("Kettle", Material.IRON_BLOCK, Kind.METAL,
            "Holds ingredients and water for a Mixer above.", "Put fire, a campfire or lava under it for heat."),
    MIXER("Mixer", Material.IRON_BLOCK, Kind.METAL,
            "Stirs the Kettle under it into new items.", "Power it with a shaft from above."),
    STATION("Station", Material.SPRUCE_PLANKS, Kind.WOOD,
            "Put it next to the rails: trains on autopilot", "stop here. Rename it in an anvil to name it."),
    SIGNAL("Signal", Material.IRON_BLOCK, Kind.METAL,
            "Put it next to the rails: red while the track", "after it holds a train (or with redstone)."),
    PUMPJACK("Pumpjack", Material.IRON_BLOCK, Kind.METAL,
            "Pumps crude oil out of the ground into pipes.", "Power it with a shaft from the side."),
    PIPE("Pipe", Material.COPPER_BLOCK, Kind.METAL,
            "Connects pumpjacks, tanks and refineries.", "Fluids flow through on their own."),
    TANK("Tank", Material.IRON_BLOCK, Kind.METAL,
            "Stores 16 buckets of one fluid.", "Right-click with a canister to fill or empty it."),
    REFINERY("Refinery", Material.COPPER_BLOCK, Kind.METAL,
            "Turns crude oil into fuel + chemical waste.", "Needs heat under it. Store the waste - or it pollutes!"),
    DRILL("Mechanical Drill", Material.IRON_BLOCK, Kind.METAL,
            "Breaks the block in front of it. Power it", "from behind. Faster = quicker."),
    SAW("Mechanical Saw", Material.IRON_BLOCK, Kind.METAL,
            "Cuts wood, leaves and crops in front of it.", "A log fells the whole tree. Power it from behind."),
    BELT("Conveyor Belt", Material.BLACK_WOOL, Kind.WOOD,
            "Carries items, mobs and players. Power it", "from the side; turning direction = belt direction."),
    ARM("Karakuri Arm", Material.COPPER_BLOCK, Kind.METAL,
            "Programmable arm: power it from below, sneak +", "right-click to teach points, click a book to program."),
    SPROCKET("Chain Sprocket", Material.IRON_BLOCK, Kind.METAL,
            "Carries rotation like a shaft. Click two sprockets", "with chains to link them (up to 12 blocks)."),
    WINDMILL("Windmill", Material.SPRUCE_PLANKS, Kind.WOOD,
            "Power source: big sails, faster high up and in", "rain. Needs free space round it. Shaft behind it."),
    STEAM_BOILER("Steam Boiler", Material.COPPER_BLOCK, Kind.METAL,
            "Burns coal, charcoal or fuel (pipes) next to a", "water block. Put a Steam Engine on top."),
    STEAM_ENGINE("Steam Engine", Material.IRON_BLOCK, Kind.METAL,
            "Power source on top of a burning Steam Boiler.", "The strongest one. Shaft through its side."),
    CRUSHING_WHEEL("Crushing Wheel", Material.STONE, Kind.STONE,
            "Place two side by side: items dropped on them", "are crushed (ores give double). Output falls below."),
    CHUTE("Chute", Material.IRON_BLOCK, Kind.METAL,
            "A belt's quieter cousin: powered it slides", "items along it, unpowered they just fall through."),
    FUNNEL("Funnel", Material.IRON_BLOCK, Kind.METAL,
            "A chute with a mouth: items landing on top", "suck in and ride along. At the end it feeds", "containers, kettles and millstones."),
    DEPOT("Depot", Material.POLISHED_ANDESITE, Kind.STONE,
            "A small pedestal: items delivered here wait", "for the Weighted Ejector facing them."),
    EJECTOR("Weighted Ejector", Material.GOLD_BLOCK, Kind.METAL,
            "Powered, it flings the item on top of it (or on", "the depot it looks at) to where it faces."),
    PUMP("Mechanical Pump", Material.COPPER_BLOCK, Kind.METAL,
            "In a pipe line: right-click with a canister to", "fill or empty it from the connected network.", "A closed valve anywhere on the line stops it."),
    VALVE("Fluid Valve", Material.COPPER_BLOCK, Kind.METAL,
            "A pipe gate: fluid passes only while this", "turns. Stop the network, cut the line."),
    WHISTLE("Steam Whistle", Material.COPPER_BLOCK, Kind.METAL,
            "Right-click: toot. A machine works better", "when it can say so."),
    SIEVE("Sieve", Material.IRON_BLOCK, Kind.METAL,
            "Sifts items: gravel gives flint, sand gives", "stones. Powered from the side, faster = better."),
    ITEM_FILTER("Item Filter", Material.IRON_BLOCK, Kind.METAL,
            "On a belt: only lets matching items through.", "Right-click to set the filter item."),
    HARVESTER("Mechanical Harvester", Material.IRON_BLOCK, Kind.METAL,
            "Harvests crops in front of it. Power it", "from behind. Faster = quicker."),
    PLOUGH("Mechanical Plough", Material.IRON_BLOCK, Kind.METAL,
            "Tills dirt into farmland in front of it.", "Power it from behind."),
    ROLLER("Mechanical Roller", Material.IRON_BLOCK, Kind.METAL,
            "Compacts concrete powder into concrete.", "Power it from behind."),
    DEPLOYER("Deployer", Material.IRON_BLOCK, Kind.METAL,
            "Places or breaks the block in front of it.", "Power it from behind. Filter sets what it deploys."),
    /**
     * Not a real part: nodes with this type get their answers from the {@code com.iekcorbar.steamworks.api}
     * registry, so other plugins (add-ons) can add machines to the network. Nothing builds or gives CUSTOM.
     */
    CUSTOM("Add-on Machine", Material.IRON_BLOCK, Kind.METAL, "A machine from an add-on.");

    public final String displayName;
    public final Material breakParticle;
    public final Kind kind;
    public final String[] lore;

    PartType(String displayName, Material breakParticle, Kind kind, String... lore) {
        this.displayName = displayName;
        this.breakParticle = breakParticle;
        this.kind = kind;
        this.lore = lore;
    }

    enum Kind { METAL, WOOD, STONE }

    /** Looked up when used (Sound needs the server's registry). */
    public Sound placeSound() {
        return switch (kind) {
            case METAL -> Sound.BLOCK_COPPER_PLACE;
            case WOOD -> Sound.BLOCK_WOOD_PLACE;
            case STONE -> Sound.BLOCK_STONE_PLACE;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String model() {
        return "sw_" + id();
    }

    public boolean isGauge() {
        return this == SPEED_GAUGE || this == STRESS_GAUGE;
    }

    /** Parts that react to redstone. */
    public boolean isSwitch() {
        return this == CLUTCH || this == GEARSHIFT;
    }

    public static PartType byId(String id) {
        for (PartType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
