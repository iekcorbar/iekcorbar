package com.iekcorbar.steamworks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crushing wheels work in pairs: two wheels side by side on the same axis
 * (they drive each other the opposite way). Items lying on top of the pair
 * are crushed one at a time (recipes: "crusher" in machines.yml); results
 * drop out underneath, between the wheels - a hopper below catches them.
 */
final class Crushers {
    private final SteamworksPlugin plugin;
    final MachineRecipes recipes;

    Crushers(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.recipes = new MachineRecipes(plugin, "crusher");
    }

    void load(File file) {
        recipes.load(file);
    }

    private KNode partner(KNode n) {
        for (BlockFace f : Kinetics.FACES) {
            if (Kinetics.axisOf(f) == n.axis || Kinetics.axisOf(f) == org.bukkit.Axis.Y) continue;
            KNode m = plugin.kinetics().rel(n, f.getModX(), f.getModY(), f.getModZ());
            if (m != null && m.type == PartType.CRUSHING_WHEEL && m.axis == n.axis) return m;
        }
        return null;
    }

    /** Called every 5 ticks. Each pair is handled once (by its wheel with the lower key). */
    void tick() {
        Settings cfg = plugin.settings();
        Set<KNode> done = new HashSet<>();
        for (KNode n : plugin.kinetics().crushers) {
            if (!done.add(n)) continue;
            KNode m = partner(n);
            if (m == null) continue;
            done.add(m);
            double s = Math.abs(n.speed());
            if (s <= 0) {
                n.oilWork = 0;
                continue;
            }
            BoundingBox box = BoundingBox.of(n.block()).union(BoundingBox.of(m.block())).shift(0, 1, 0).expand(0, -0.1, 0);
            Item target = null;
            MachineRecipes.Recipe r = null;
            for (Entity e : n.world.getNearbyEntities(box, en -> en instanceof Item)) {
                r = recipes.find(((Item) e).getItemStack());
                if (r != null) {
                    target = (Item) e;
                    break;
                }
            }
            if (target == null) {
                n.oilWork = 0;
                continue;
            }
            n.oilWork += 5 * s / cfg.crusherDivisor;
            Location gap = n.center().add(m.center()).multiply(0.5);
            if (n.oilWork < r.time()) {
                n.world.spawnParticle(Particle.ITEM, gap.clone().add(0, 0.6, 0), 2, 0.2, 0.05, 0.2, 0.02, target.getItemStack().asOne());
                continue;
            }
            n.oilWork = 0;
            ItemStack stack = target.getItemStack();
            if (stack.getAmount() <= 1) target.remove();
            else target.setItemStack(stack.asQuantity(stack.getAmount() - 1));
            Location out = gap.clone().add(0, -0.75, 0);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (MachineRecipes.Output o : r.outputs()) {
                if (o.chance() < 1 && rnd.nextDouble() >= o.chance()) continue;
                Item drop = n.world.dropItem(out, o.create(plugin.items()));
                drop.setVelocity(new Vector(0, -0.1, 0));
            }
            if (plugin.settings().machineVolume > 0)
                n.world.playSound(gap, "steamworks:millstone.grind", 0.6f * plugin.settings().machineVolume, 0.75f);
            n.world.spawnParticle(Particle.BLOCK, gap.clone().add(0, 0.5, 0), 10, 0.3, 0.1, 0.3,
                    stack.getType().isBlock() ? stack.getType().createBlockData() : org.bukkit.Material.STONE.createBlockData());
        }
    }
}
