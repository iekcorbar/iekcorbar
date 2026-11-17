package com.iekcorbar.xmas;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Xmas Town building blocks: the materials the village is actually built from.
 *
 * These are props from the shared engine (propkit {@code Decor}): a hidden barrier with our model
 * drawn by an ItemDisplay. A barrier is a real full block, so these walk, stack, hold torches and
 * survive a restart through the chunk PDC - and the model fills 0..16 = one block, so
 * {@code DecorDef.scale} stays 1.0. Only the village lamp is two blocks tall.
 *
 * The models come from tools/make_pack.py (namespace {@code xmas:}). Every id here must have a
 * model AND an {@code assets/xmas/items/<id>.json} definition in the pack: in Paper/Minecraft 26.2
 * an item_model without one renders as a magenta cube, which is exactly how this plugin sat broken
 * for weeks (it was never in scripts/merge-packs.py and its generator wrote no definitions).
 * scripts/audit-pack.py check 6 now compares both sides and fails the build.
 *
 * Every block has a shapeless recipe from vanilla things, so a village is buildable in survival.
 */
final class XmasDecor {
    final Decor decor;

    XmasDecor(XmasPlugin plugin) {
        decor = new Decor(plugin, "xmas", defs(), plugin.getConfig().getInt("xmas-blocks.break-hits", 2));
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        // ---- walls and floor
        l.add(new DecorDef("snow_block", "Pressed Snow Block",
                "Packed snow with a cool rim, so a wall of these keeps reading as blocks.")
                .recipe(4, Material.SNOW_BLOCK, Material.SNOW_BLOCK, Material.SNOWBALL, Material.GUNPOWDER)
                .sound("minecraft:block.snow.place"));
        l.add(new DecorDef("ice_block", "Clear Ice Block",
                "Blue-white village ice. It glows faintly in the dark, like real ice.")
                .glow(4)
                .recipe(4, Material.ICE, Material.ICE, Material.GLASS_PANE, Material.QUARTZ)
                .sound("minecraft:block.glass.place"));
        // ---- wood
        l.add(new DecorDef("pine_log", "Pine Log",
                "A pine trunk with bark ribs and growth rings on the top face.")
                .recipe(4, Material.SPRUCE_LOG, Material.SPRUCE_LOG, Material.HAY_BLOCK)
                .sound("minecraft:block.wood.place"));
        l.add(new DecorDef("pine_planks", "Pine Planks",
                "Bright pine boards with visible seams - the walls of every good holiday hut.")
                .recipe(8, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS)
                .sound("minecraft:block.wood.place"));
        l.add(new DecorDef("roof_tile", "Roof Tile",
                "Red tile with a last dusting of snow. Lay it on a roof and it reads like one.")
                .recipe(4, Material.RED_TERRACOTTA, Material.BRICKS, Material.SNOWBALL)
                .sound("minecraft:block.stone.place"));
        l.add(new DecorDef("lit_window", "Lit Window Block",
                "A warm window in a pine frame. Twelve of these make a street feel like Christmas Eve.")
                .glow(12)
                .recipe(2, Material.GLASS_PANE, Material.LANTERN, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS)
                .sound("minecraft:block.glass.place"));
        // ---- sweets and gifts, used as cubes for plinths, pillars and market stalls
        l.add(new DecorDef("present_red", "Gift Cube (Red)",
                "A wrapped present the size of a block. Stack them into a pile or a wall.")
                .recipe(4, Material.RED_WOOL, Material.RED_WOOL, Material.GOLD_NUGGET, Material.GOLD_NUGGET)
                .sound("minecraft:block.wool.place"));
        l.add(new DecorDef("present_green", "Gift Cube (Green)",
                "The green one. Same ribbon, better taste.")
                .recipe(4, Material.GREEN_WOOL, Material.GREEN_WOOL, Material.GOLD_NUGGET, Material.GOLD_NUGGET)
                .sound("minecraft:block.wool.place"));
        l.add(new DecorDef("candy_block", "Candy Cane Block",
                "Five stripes of peppermint. Fine for a pillar, reckless for a wall.")
                .recipe(4, Material.SUGAR, Material.SUGAR, Material.RED_DYE, Material.WHITE_DYE)
                .sound("minecraft:block.honey_block.place"));
        l.add(new DecorDef("gingerbread_block", "Gingerbread Block",
                "Biscuit with an icing rim and three sweets pressed in. Build a house, eat the door.")
                .recipe(4, Material.COOKIE, Material.COOKIE, Material.COOKIE, Material.EGG)
                .sound("minecraft:block.honey_block.place"));
        // ---- street furniture (two blocks tall, so height(2))
        l.add(new DecorDef("village_lamp", "Village Street Lamp",
                "Brass on iron with a warm pane and a snow cap. Two blocks tall.")
                .height(2).glow(14)
                .recipe(1, Material.IRON_INGOT, Material.STICK, Material.STICK, Material.LANTERN, Material.SNOWBALL)
                .sound("minecraft:block.lantern.place"));
        return l;
    }

    List<ItemStack> allItems() {
        return decor.allItems();
    }
}
