package com.iekcorbar.kitchen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Restaurant and kitchen furniture: counters and tables that join their neighbours, chairs and stools you can sit
 * on, a sink with a tap, a stove top with a flame, a fridge, shelves, a wine rack, noren curtain, lanterns, order bell
 * and signs. Props come from the shared engine (Decor), models from the Kitchendecor resource pack. They are for
 * building - the working stations (cutting board, cooking pot, kamado) stay in Props.
 */
final class KitchenDecor {
    final Decor decor;

    KitchenDecor(KitchenPlugin plugin) {
        decor = new Decor(plugin, "kitchendecor", defs(), plugin.getConfig().getInt("kitchen-decor.break-hits", 2));
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        Tag<Material> planks = Tag.PLANKS;
        l.add(new DecorDef("kitchen_counter", "Kitchen Counter", "A counter with a stone top. Joins its neighbours.")
                .connect("kcounter").oriented().recipe(2, planks, planks, Material.STONE_SLAB, Material.STONE_SLAB));
        l.add(new DecorDef("counter_sink", "Kitchen Sink", "Right-click with a glass bottle or bucket to draw water from the tap.")
                .connect("kcounter").oriented().use(this::sink).recipe(1, planks, planks, Material.STONE_SLAB, Material.IRON_INGOT, Material.BUCKET));
        l.add(new DecorDef("stove_top", "Stove Top", "Right-click to light or put out the flame. Joins counters.")
                .connect("kcounter").oriented().use(this::stove).tick(1, this::flame).recipe(1, planks, planks, Material.IRON_INGOT, Material.IRON_INGOT, Material.FLINT_AND_STEEL));
        l.add(new DecorDef("dining_table", "Dining Table", "A wooden table. Joins its neighbours.")
                .connect("ktable").recipe(2, planks, planks, planks, Material.STICK));
        l.add(new DecorDef("chair", "Dining Chair", "Right-click to sit.")
                .seat(0.45f).hit(0.7f, 1.0f).recipe(2, planks, Material.STICK, Material.STICK, Material.RED_WOOL));
        l.add(new DecorDef("stool", "Wooden Stool", "A small round stool. Right-click to sit.")
                .seat(0.4f).hit(0.6f, 0.5f).recipe(2, planks, Material.STICK, Material.STICK));
        l.add(new DecorDef("bar_stool", "Bar Stool", "A tall stool for the counter. Right-click to sit.")
                .seat(0.75f).hit(0.6f, 0.9f).recipe(2, Material.IRON_INGOT, Material.STICK, planks, Material.RED_WOOL));
        l.add(new DecorDef("menu_board", "Menu Board", "A chalkboard menu that hangs on a wall.")
                .wall().hit(0.9f, 0.9f).recipe(1, planks, planks, Material.BLACK_DYE, Material.WHITE_DYE));
        l.add(new DecorDef("order_bell", "Order Bell", "Ding! Right-click to call the staff.")
                .use(this::bell).hit(0.4f, 0.4f).recipe(1, Material.GOLD_INGOT, Material.IRON_NUGGET, Material.STICK));
        l.add(new DecorDef("noren_curtain", "Noren Curtain", "A short cloth curtain over a doorway, hanging from a rail.")
                .hit(0.95f, 0.5f).recipe(1, Material.STICK, Material.WHITE_WOOL, Material.BLUE_DYE, Material.STRING));
        l.add(new DecorDef("paper_lantern", "Paper Lantern", "A round paper lantern that gives light.")
                .light(13).hit(0.5f, 0.8f).recipe(2, Material.PAPER, Material.PAPER, Material.STICK, Material.TORCH));
        l.add(new DecorDef("wine_rack", "Sake and Wine Rack", "A rack full of bottles.")
                .recipe(1, planks, planks, planks, planks, Material.GLASS_BOTTLE, Material.GLASS_BOTTLE));
        l.add(new DecorDef("shelf_unit", "Kitchen Shelf Unit", "Shelves with jars and bowls. Two blocks tall.")
                .height(2).recipe(1, planks, planks, planks, planks, Material.BOWL, Material.GLASS_BOTTLE));
        l.add(new DecorDef("fridge", "Ice Box", "A steampunk ice box, two blocks tall. Right-click to open it.")
                .height(2).openable().use(this::fridge).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.SNOWBALL, Material.COPPER_INGOT));
        l.add(new DecorDef("kotatsu", "Kotatsu", "A low heated table with a blanket.")
                .hit(0.95f, 0.5f).recipe(1, planks, planks, Material.RED_WOOL, Material.RED_WOOL, Material.LANTERN));
        l.add(new DecorDef("serving_cart", "Serving Cart", "A little cart with a tea set.")
                .hit(0.9f, 0.9f).recipe(1, planks, Material.IRON_INGOT, Material.IRON_INGOT, Material.STICK, Material.BOWL));
        l.add(new DecorDef("sake_barrel", "Decorative Sake Barrel", "A rice-straw wrapped barrel (decoration only; the working cask is in Sakagura).")
                .recipe(1, planks, planks, Material.IRON_NUGGET, Material.WHEAT, Material.WHEAT));
        l.add(new DecorDef("restaurant_sign", "Restaurant Sign", "A hanging wooden sign for your restaurant.")
                .wall().hit(0.95f, 0.6f).recipe(1, planks, planks, Material.IRON_CHAIN, Material.RED_DYE));
        return l;
    }

    List<ItemStack> allItems() {
        return decor.allItems();
    }

    // ------------------------------------------------------------ behaviour

    private boolean sink(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (hand == null || hand.isEmpty()) {
            pl.sendActionBar(Component.text("Hold a glass bottle or a bucket.", NamedTextColor.GRAY));
            return true;
        }
        ItemStack out = null;
        if (hand.getType() == Material.GLASS_BOTTLE) out = org.bukkit.Bukkit.getItemFactory().createItemStack("minecraft:potion[potion_contents={potion:\"minecraft:water\"}]");
        else if (hand.getType() == Material.BUCKET) out = new ItemStack(Material.WATER_BUCKET);
        if (out == null) return true;
        if (pl.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        for (ItemStack left : pl.getInventory().addItem(out).values()) pl.getWorld().dropItemNaturally(pl.getLocation(), left);
        p.world.playSound(p.center(), "minecraft:item.bottle.fill", 0.8f, 1f);
        return true;
    }

    private boolean stove(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        boolean lit = "lit".equals(p.data);
        p.data = lit ? "" : "lit";
        d.save(p);
        p.world.playSound(p.center(), lit ? "minecraft:block.fire.extinguish" : "minecraft:item.flintandsteel.use", 0.7f, 1f);
        return true;
    }

    private void flame(Decor d, Decor.Prop p, long tick) {
        if (!"lit".equals(p.data)) return;
        p.world.spawnParticle(Particle.FLAME, p.x + 0.5, p.y + 1.0, p.z + 0.5, 3, 0.18, 0.02, 0.18, 0.005);
        if (ThreadLocalRandom.current().nextInt(4) == 0) p.world.spawnParticle(Particle.SMOKE, p.x + 0.5, p.y + 1.3, p.z + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
    }

    private boolean bell(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        p.world.playSound(p.center(), "minecraft:block.bell.use", 0.6f, 1.8f);
        pl.sendActionBar(Component.text("Ding!", NamedTextColor.GOLD));
        return true;
    }

    private boolean fridge(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        d.open(p, 30);
        p.world.playSound(p.center(), "minecraft:block.iron_door.open", 0.5f, 1.4f);
        return true;
    }
}
