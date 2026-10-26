package com.iekcorbar.sakagura;

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
 * Garden decoration for the Sakagura garden: planters and flower beds, fences, hedges and trellises that join
 * their neighbours, a fountain and bird bath, a sundial, a rain barrel that fills bottles and buckets, and a
 * garden gnome. Props come from the shared engine (Decor), models from the Garden resource pack.
 */
final class GardenDecor {
    final Decor decor;

    GardenDecor(Sakagura plugin) {
        decor = new Decor(plugin, "garden", defs(), plugin.getConfig().getInt("garden-decor.break-hits", 2));
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        Tag<Material> planks = Tag.PLANKS;
        l.add(new DecorDef("planter_box", "Planter Box", "A wooden box full of flowers. Comes in several colours.")
                .hit(0.9f, 0.7f).variants("planter_box", "planter_box_red", "planter_box_yellow", "planter_box_blue", "planter_box_pink")
                .recipe(1, planks, planks, Material.DIRT, Tag.SMALL_FLOWERS).sound("minecraft:block.grass.place"));
        l.add(new DecorDef("flower_bed", "Flower Bed", "A low bed of flowers you can walk over.")
                .hit(0.95f, 0.3f).variants("flower_bed", "flower_bed_red", "flower_bed_yellow", "flower_bed_blue", "flower_bed_pink")
                .recipe(2, Material.DIRT, Tag.SMALL_FLOWERS, Tag.SMALL_FLOWERS, Material.BONE_MEAL).sound("minecraft:block.grass.place"));
        l.add(new DecorDef("garden_fence", "Picket Fence", "A white picket fence. Joins its neighbours.")
                .connect("gardenfence").recipe(4, planks, planks, Material.STICK, Material.WHITE_DYE));
        l.add(new DecorDef("hedge", "Garden Hedge", "A trimmed hedge, two blocks tall. Joins its neighbours.")
                .height(2).connect("hedge").recipe(2, Material.OAK_LEAVES, Material.OAK_LEAVES, Material.OAK_LEAVES, Material.STICK).sound("minecraft:block.grass.place"));
        l.add(new DecorDef("trellis", "Trellis", "Wooden lattice with climbing vines. Joins its neighbours.")
                .height(2).connect("gardenfence").recipe(2, Material.STICK, Material.STICK, Material.STICK, Material.STICK, Material.VINE));
        l.add(new DecorDef("pergola", "Pergola", "An arch with vines to walk through. Three blocks tall.")
                .scale(1.5f).hit(0.9f, 3.0f).recipe(1, planks, planks, Material.STICK, Material.STICK, Material.VINE));
        l.add(new DecorDef("garden_bench", "Garden Bench", "A wooden bench. Right-click to sit.")
                .seat(0.45f).hit(0.9f, 0.7f).recipe(1, planks, planks, Material.STICK, Material.STICK, Material.WHITE_DYE));
        l.add(new DecorDef("bird_bath", "Bird Bath", "A stone basin on a pedestal.")
                .hit(0.7f, 1.0f).tick(2, this::splash).recipe(1, Material.STONE, Material.STONE, Material.STONE_SLAB, Material.WATER_BUCKET));
        l.add(new DecorDef("fountain", "Garden Fountain", "A big tiered stone fountain, three times life-size, with running water.")
                .height(2).scale(3f).tick(1, this::fountain).recipe(1, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.STONE_BRICK_SLAB, Material.WATER_BUCKET));
        l.add(new DecorDef("garden_lamp", "Garden Lamp", "A little lantern on a post.")
                .height(2).glow(13).recipe(1, Material.IRON_NUGGET, Material.STICK, Material.STICK, Material.LANTERN));
        l.add(new DecorDef("garden_gnome", "Garden Gnome", "Right-click for a word of wisdom.")
                .use(this::gnome).hit(0.5f, 0.8f).recipe(1, Material.RED_WOOL, Material.CLAY_BALL, Material.CLAY_BALL, Material.WHITE_WOOL, Material.STONE));
        l.add(new DecorDef("scarecrow", "Scarecrow", "Keeps the crows out. Two blocks tall.")
                .height(2).scale(1.125f).recipe(1, Material.HAY_BLOCK, Material.STICK, Material.STICK, Material.CARVED_PUMPKIN));
        l.add(new DecorDef("wheelbarrow", "Wheelbarrow", "Full of soil and seedlings.")
                .hit(0.9f, 0.7f).recipe(1, planks, planks, Material.IRON_INGOT, Material.DIRT, Material.WHEAT_SEEDS));
        l.add(new DecorDef("stepping_stones", "Stepping Stones", "Flat stones to walk over.")
                .hit(0.9f, 0.1f).recipe(3, Material.STONE, Material.STONE, Material.GRAVEL).sound("minecraft:block.stone.place"));
        l.add(new DecorDef("sundial", "Sundial", "Right-click to read the time of day.")
                .use(this::sundial).hit(0.7f, 0.8f).recipe(1, Material.STONE, Material.STONE, Material.GOLD_NUGGET, Material.CLOCK));
        l.add(new DecorDef("mailbox", "Garden Mailbox", "A little post box. Decoration.")
                .hit(0.6f, 1.0f).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.STICK, Material.RED_DYE));
        l.add(new DecorDef("rain_barrel", "Rain Barrel", "Right-click with a glass bottle or a bucket to fill it with water.")
                .use(this::barrel).recipe(1, planks, planks, planks, Material.IRON_NUGGET, Material.BUCKET));
        l.add(new DecorDef("compost_bin", "Compost Bin", "A slatted wooden bin. Decoration.")
                .recipe(1, planks, planks, planks, planks, Material.BONE_MEAL));
        l.add(new DecorDef("tool_shed", "Tool Shed", "A small garden shed. Two blocks tall.")
                .height(2).recipe(1, planks, planks, planks, planks, Material.IRON_INGOT, Material.IRON_HOE));
        l.add(new DecorDef("sprinkler", "Sprinkler", "A pop-up sprinkler that mists the flowers around it.")
                .hit(0.4f, 0.6f).tick(1, this::mist).recipe(1, Material.IRON_INGOT, Material.IRON_NUGGET, Material.IRON_NUGGET, Material.BUCKET));
        l.add(new DecorDef("pot_shelf", "Pot Shelf", "A wall shelf with potted plants.")
                .wall().hit(0.9f, 0.6f).recipe(1, planks, Material.STICK, Material.FLOWER_POT, Material.FLOWER_POT));
        return l;
    }

    List<ItemStack> allItems() {
        return decor.allItems();
    }

    // ------------------------------------------------------------ effects

    private void splash(Decor d, Decor.Prop p, long tick) {
        p.world.spawnParticle(Particle.SPLASH, p.x + 0.5, p.y + 0.95, p.z + 0.5, 2, 0.15, 0.02, 0.15, 0);
        if (ThreadLocalRandom.current().nextInt(5) == 0) p.world.playSound(p.center(), "minecraft:block.water.ambient", 0.3f, 1.3f);
    }

    private void fountain(Decor d, Decor.Prop p, long tick) {
        p.world.spawnParticle(Particle.FALLING_WATER, p.x + 0.5, p.y + 5.4, p.z + 0.5, 20, 0.75, 0.05, 0.75, 0);
        p.world.spawnParticle(Particle.SPLASH, p.x + 0.5, p.y + 2.7, p.z + 0.5, 14, 1.2, 0.02, 1.2, 0);
        if (ThreadLocalRandom.current().nextInt(4) == 0) p.world.playSound(p.center(), "minecraft:block.water.ambient", 1.2f, 0.9f);
    }

    private void mist(Decor d, Decor.Prop p, long tick) {
        p.world.spawnParticle(Particle.FALLING_WATER, p.x + 0.5, p.y + 0.7, p.z + 0.5, 10, 1.2, 0.2, 1.2, 0);
        p.world.spawnParticle(Particle.SPLASH, p.x + 0.5, p.y + 0.5, p.z + 0.5, 4, 0.8, 0.1, 0.8, 0);
    }

    private static final String[] QUIPS = {
            "Water your plants. And yourself.", "A weed in time saves nine.", "I stand guard. Mostly I stand.", "The best fertiliser is the gardener's shadow.",
            "Barley today, sake tomorrow.", "Have you tried talking to the hops?", "Yuzu: good for the soul, bad for the tongue.", "Do not tell the snails where I am."};

    private boolean gnome(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        pl.sendMessage(Component.text("Gnome: " + QUIPS[ThreadLocalRandom.current().nextInt(QUIPS.length)], NamedTextColor.GOLD));
        p.world.playSound(p.center(), "minecraft:entity.villager.celebrate", 0.7f, 1.4f);
        p.world.spawnParticle(Particle.HAPPY_VILLAGER, p.center().add(0, 0.4, 0), 4, 0.2, 0.2, 0.2, 0);
        return true;
    }

    private boolean sundial(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        long t = (p.world.getTime() + 6000) % 24000; // tick 0 = 6:00
        int minutes = (int) (t * 1440 / 24000);
        pl.sendActionBar(Component.text(String.format("About %02d:%02d.", minutes / 60, minutes % 60), NamedTextColor.GOLD));
        return true;
    }

    private boolean barrel(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (hand == null || hand.isEmpty()) {
            pl.sendActionBar(Component.text("Right-click with a glass bottle or a bucket.", NamedTextColor.GRAY));
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
}
