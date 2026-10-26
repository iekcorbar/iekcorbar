package com.iekcorbar.steamworks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mechanical drill and saw: break the block in front of them, quicker the
 * faster they turn (time = (hardness + 0.2) x factor / speed). The drill
 * breaks anything a pickaxe-ish tool can (not containers, not unbreakable
 * blocks); the saw only wood, leaves and plants, and a cut log fells every
 * log connected above it. Drops are what an iron pickaxe / iron axe would get.
 *
 * Protection: a machine only breaks where the player who placed it may break
 * (a BlockBreakEvent probe as that player). While that player is offline the
 * answers given while they were online (10 minutes) are reused, otherwise it waits.
 */
final class Breakers {
    private static final long CACHE_TICKS = 20 * 60 * 10;

    private record Answer(boolean allowed, long until) {}

    private final SteamworksPlugin plugin;
    private final Map<UUID, Map<Long, Answer>> allowed = new HashMap<>();
    private final ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE), axe = new ItemStack(Material.IRON_AXE);

    Breakers(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean sawable(Material m) {
        return Tag.MINEABLE_AXE.isTagged(m) || Tag.LEAVES.isTagged(m) || Tag.CROPS.isTagged(m) || m == Material.CACTUS
                || m == Material.SUGAR_CANE || m == Material.BAMBOO || m == Material.MELON || m == Material.PUMPKIN
                || m == Material.VINE || m == Material.KELP_PLANT || m == Material.SWEET_BERRY_BUSH || m == Material.COCOA;
    }

    private boolean breakable(KNode n, Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m == Material.BARRIER || m.getHardness() < 0) return false;
        if (plugin.kinetics().at(b) != null) return false;           // never our own parts
        if (b.getState(false) instanceof Container) return false;     // no chest emptying
        if (n.type == PartType.SAW) return sawable(m);
        return true;
    }

    /** Called every 2 ticks. */
    void tick(long tick) {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().breakers) {
            double s = Math.abs(n.speed());
            Block target = n.block().getRelative(n.facing);
            long key = KNode.key(target.getX(), target.getY(), target.getZ());
            if (s <= 0 || !breakable(n, target)) {
                reset(n, target);
                continue;
            }
            if (n.breakTarget != key) {
                reset(n, target);
                n.breakTarget = key;
            }
            double ticks = Math.max(4, (target.getType().getHardness() + 0.2) * cfg.drillHardnessFactor / s);
            n.breakProgress += 2 / ticks;
            if (tick % 4 == 0) crack(n, target, (float) Math.min(1, n.breakProgress));
            if (n.breakProgress < 1) continue;
            Boolean ok = mayBreak(n, target);
            if (ok == null) continue;          // owner offline and no earlier answer: wait
            if (!ok) {
                n.breakProgress = 0.99;
                continue;
            }
            breakBlock(n, target);
            reset(n, target);
        }
    }

    private void reset(KNode n, Block target) {
        if (n.breakProgress > 0) crack(n, target, 0);
        n.breakProgress = 0;
        n.breakTarget = Long.MIN_VALUE;
    }

    private void crack(KNode n, Block b, float progress) {
        Location l = b.getLocation();
        int source = (int) (n.key ^ (n.key >>> 32)); // any stable id per machine
        for (Player p : b.getWorld().getNearbyPlayers(l, 32)) p.sendBlockDamage(l, progress, source);
        if (progress > 0) b.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0.5, 0.5, 0.5).subtract(Kinetics.vec(n.facing).multiply(0.45)),
                3, 0.2, 0.2, 0.2, b.getBlockData());
    }

    /** True/false from the owner's protection, or null when we can't ask right now. */
    Boolean mayBreak(KNode n, Block b) {
        if (n.owner == null) return false;
        long key = KNode.key(b.getX(), b.getY(), b.getZ());
        Map<Long, Answer> cache = allowed.computeIfAbsent(n.owner, k -> new HashMap<>());
        long now = plugin.kinetics().tick;
        Player p = plugin.getServer().getPlayer(n.owner);
        if (p != null) {
            Answer a = cache.get(key);
            if (a != null && a.until() > now) return a.allowed();
            PartListener.ProbeBreakEvent probe = new PartListener.ProbeBreakEvent(b, p);
            plugin.getServer().getPluginManager().callEvent(probe);
            boolean ok = !probe.isCancelled();
            cache.put(key, new Answer(ok, now + CACHE_TICKS));
            if (cache.size() > 4096) cache.clear();
            return ok;
        }
        Answer a = cache.get(key);
        return a == null ? null : a.allowed();
    }

    private void breakBlock(KNode n, Block b) {
        Location drop = b.getLocation().add(0.5, 0.5, 0.5);
        if (n.type == PartType.SAW && Tag.LOGS.isTagged(b.getType())) {
            fell(n, b);
            return;
        }
        OreType ore = Ores.of(b);
        b.getWorld().playSound(drop, b.getBlockData().getSoundGroup().getBreakSound(), 0.8f, 1f);
        b.getWorld().spawnParticle(Particle.BLOCK, drop, 20, 0.3, 0.3, 0.3, b.getBlockData());
        if (ore != null) {
            int count = ore.minDrop + java.util.concurrent.ThreadLocalRandom.current().nextInt(ore.maxDrop - ore.minDrop + 1);
            b.getWorld().dropItemNaturally(drop, plugin.items().createMaterial(ore.drop, count));
        } else {
            for (ItemStack s : b.getDrops(n.type == PartType.SAW ? axe : pickaxe)) b.getWorld().dropItemNaturally(drop, s);
        }
        b.setType(Material.AIR);
    }

    /** Tree felling: every log connected to this one (including diagonals) at its height or above. */
    private void fell(KNode n, Block start) {
        int max = plugin.settings().sawMaxLogs, baseY = start.getY();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        int cut = 0;
        while (!q.isEmpty() && cut < max) {
            Block b = q.poll();
            if (b != start) {
                Boolean ok = mayBreak(n, b);
                if (ok == null || !ok) continue;
            }
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack s : b.getDrops(axe)) b.getWorld().dropItemNaturally(at, s);
            b.getWorld().spawnParticle(Particle.BLOCK, at, 8, 0.3, 0.3, 0.3, b.getBlockData());
            b.setType(Material.AIR);
            cut++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block o = b.getRelative(dx, dy, dz);
                        if (o.getY() < baseY || !Tag.LOGS.isTagged(o.getType()) || !seen.add(o)) continue;
                        if (plugin.kinetics().at(o) != null) continue;
                        q.add(o);
                    }
                }
            }
        }
        start.getWorld().playSound(start.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
    }
}
