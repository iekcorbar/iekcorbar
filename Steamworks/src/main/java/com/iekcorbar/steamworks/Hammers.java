package com.iekcorbar.steamworks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Steam hammer: presses item entities lying in the block under it, one item per
 * strike. Work per tick = speed / speed-divisor (clamped); a strike needs
 * "cycle" work. It only builds up work while there is something to press.
 * A hopper right under the hammer collects the results: while the hammer
 * runs, it is not allowed to pick up items the hammer can still press.
 */
final class Hammers {
    private final SteamworksPlugin plugin;
    final MachineRecipes recipes;

    Hammers(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.recipes = new MachineRecipes(plugin, "steam_hammer");
    }

    void load(File file) {
        recipes.load(file);
    }

    /** Called every 5 ticks. */
    void tick() {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().hammers) {
            double speed = Math.abs(n.speed());
            if (speed <= 0) {
                n.hammerProgress = 0;
                continue;
            }
            Item target = findInput(n);
            if (target == null) {
                n.hammerProgress = 0;
                continue;
            }
            double rate = rate(cfg, speed);
            n.hammerProgress += 5 * rate;
            if (n.hammerProgress < cfg.hammerCycle) continue;
            n.hammerProgress = Math.min(n.hammerProgress - cfg.hammerCycle, cfg.hammerCycle);
            strike(n, target, (int) Math.round(cfg.hammerCycle / rate));
        }
    }

    static double rate(Settings cfg, double speed) {
        return Math.max(cfg.hammerMinRate, Math.min(cfg.hammerMaxRate, speed / cfg.hammerDivisor));
    }

    /** The block the hammer works on. */
    static Block workBlock(KNode n) {
        return n.block().getRelative(BlockFace.DOWN);
    }

    private Item findInput(KNode n) {
        Block w = workBlock(n);
        BoundingBox box = BoundingBox.of(w);
        for (var e : n.world.getNearbyEntities(box, en -> en instanceof Item)) {
            Item i = (Item) e;
            if (i.isDead() || recipes.find(i.getItemStack()) == null) continue;
            return i;
        }
        return null;
    }

    private void strike(KNode n, Item target, int interval) {
        ItemStack stack = target.getItemStack();
        MachineRecipes.Recipe r = recipes.find(stack);
        if (r == null) return;
        ItemStack one = stack.asOne();
        if (stack.getAmount() <= 1) target.remove();
        else target.setItemStack(stack.subtract());

        Block w = workBlock(n);
        double floor = floorHeight(w);
        Location drop = new Location(n.world, w.getX() + 0.5, w.getY() + floor + 0.05, w.getZ() + 0.5);
        for (MachineRecipes.Output o : r.outputs()) {
            if (o.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= o.chance()) continue;
            Item out = n.world.dropItem(drop, o.create(plugin.items()));
            out.setVelocity(new Vector());
        }

        plugin.visuals().strike(n, (float) strikeDepth(floor), interval);
        Location top = n.center().add(0, 0.45, 0);
        n.world.spawnParticle(Particle.ITEM, drop.clone().add(0, 0.1, 0), 8, 0.15, 0.05, 0.15, 0.08, one);
        n.world.spawnParticle(Particle.CLOUD, top, 3, 0.2, 0.05, 0.2, 0.01);
        n.world.playSound(drop, "steamworks:hammer.strike", 0.7f, 1f);
        
    }

    /** Where items rest in the work block (0 = its bottom). */
    static double floorHeight(Block w) {
        if (w.getType() == Material.HOPPER) return 0.625;
        if (w.isPassable() || w.isEmpty()) return 0;
        BoundingBox bb = w.getBoundingBox();
        return Math.max(0, Math.min(1, bb.getMaxY() - w.getY()));
    }

    /** How far the head travels down (blocks): to just above the items. */
    static double strikeDepth(double floor) {
        return Math.max(0.1, Math.min(0.8125, 1 - floor - 0.1875));
    }

    /** True if a running hammer is above this hopper and can still press the item. */
    boolean holdsBack(Block hopper, ItemStack item) {
        KNode n = plugin.kinetics().get(hopper.getWorld(), hopper.getX(), hopper.getY() + 1, hopper.getZ());
        return n != null && n.type == PartType.STEAM_HAMMER && n.speed() != 0 && recipes.find(item) != null;
    }
}
