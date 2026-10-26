package com.iekcorbar.wildlife;

import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds wildlife around players - next to the vanilla animals, never instead of
 * them (no vanilla spawn is touched). Every interval, each player gets one try:
 * a random spot 24-56 blocks away in a loaded chunk, the species that live in
 * that biome at this time of day, and only if few animals are around already.
 * Defaults keep animals scarce: finding them is part of the fun.
 */
final class Spawner {
    private final WildlifePlugin plugin;
    private boolean enabled;
    private double chance, rareChance;
    private int minDist, maxDist, maxNear, nearRadius;
    private final Map<Species, Boolean> on = new EnumMap<>(Species.class);
    private final Map<Species, Integer> weight = new EnumMap<>(Species.class);

    Spawner(WildlifePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("spawning.enabled", true);
        chance = c.getDouble("spawning.chance", 0.55);
        rareChance = c.getDouble("spawning.rare-chance", 0.025);
        minDist = c.getInt("spawning.min-distance", 24);
        maxDist = Math.max(minDist + 4, c.getInt("spawning.max-distance", 56));
        maxNear = c.getInt("spawning.max-animals-near-player", 8);
        nearRadius = c.getInt("spawning.near-radius", 64);
        ConfigurationSection s = c.getConfigurationSection("species");
        for (Species sp : Species.values()) {
            ConfigurationSection one = s == null ? null : s.getConfigurationSection(sp.id());
            on.put(sp, one == null || one.getBoolean("enabled", true));
            int base = one == null ? sp.weight : one.getInt("weight", sp.weight);
            // shyer species are seen less often too (0.6.0): tier 3 ~40% rarer, tier 2 ~20% rarer
            int scaled = sp.shy() == 3 ? Math.max(1, base * 3 / 5) : sp.shy() == 2 ? Math.max(1, base * 4 / 5) : base;
            weight.put(sp, scaled);
        }
    }

    double rareChance() {
        return rareChance;
    }

    void tick() {
        if (!enabled || !plugin.fmm().available()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getWorld().getEnvironment() != World.Environment.NORMAL) continue;
            if (r.nextDouble() >= chance) continue;
            if (plugin.animals().countNear(p.getLocation(), nearRadius, null) >= maxNear) continue;
            trySpawn(p, r);
        }
    }

    private void trySpawn(Player p, ThreadLocalRandom r) {
        World w = p.getWorld();
        double a = r.nextDouble() * Math.PI * 2, d = minDist + r.nextDouble() * (maxDist - minDist);
        int x = (int) (p.getLocation().getX() + Math.cos(a) * d), z = (int) (p.getLocation().getZ() + Math.sin(a) * d);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        String biome = top.getBiome().getKey().getKey();
        long time = w.getTime();
        boolean night = time > 13000 && time < 23000;
        List<Species> fit = new ArrayList<>();
        int total = 0;
        for (Species s : Species.values()) {
            if (!on.get(s) || !s.livesIn(biome)) continue;
            if (s.time == Species.Time.DAY && night || s.time == Species.Time.NIGHT && !night) continue;
            if (spot(s, top) == null) continue;
            int wgt = weight.get(s) * (s == Species.SNAIL && w.hasStorm() ? 3 : 1);
            if (wgt <= 0) continue;
            fit.add(s);
            total += wgt;
        }
        if (fit.isEmpty()) return;
        int pick = r.nextInt(total);
        Species s = fit.get(0);
        for (Species f : fit) {
            pick -= weight.get(f) * (f == Species.SNAIL && w.hasStorm() ? 3 : 1);
            if (pick < 0) {
                s = f;
                break;
            }
        }
        if (plugin.animals().countNear(top.getLocation(), nearRadius, s) >= s.groupMax) return;
        int n = s.groupMin + r.nextInt(s.groupMax - s.groupMin + 1);
        for (int i = 0; i < n; i++) {
            int gx = x + (i == 0 ? 0 : r.nextInt(7) - 3), gz = z + (i == 0 ? 0 : r.nextInt(7) - 3);
            if (!w.isChunkLoaded(gx >> 4, gz >> 4)) continue;
            Location at = spot(s, w.getHighestBlockAt(gx, gz, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            if (at == null) continue;
            boolean baby = n >= 3 && i == n - 1 && s.habitat != Species.Habitat.AIR && r.nextInt(5) == 0;   // sometimes a young one in a bigger group
            plugin.animals().spawn(s, at, r.nextDouble() < rareChance, baby, null);
        }
    }

    /** Where this species can appear above the given top block, or null. */
    private static Location spot(Species s, Block top) {
        Material m = top.getType();
        if (s.habitat == Species.Habitat.POND) {   // koi live IN the water: top must be a water surface with depth
            return m == Material.WATER && top.getRelative(0, -1, 0).getType() == Material.WATER
                    && top.getRelative(0, 1, 0).getType().isAir() ? top.getLocation().add(0.5, 0.5, 0.5) : null;
        }
        Block above = top.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) return null;
        Location on = above.getLocation().add(0.5, 0, 0.5);
        boolean water = m == Material.WATER;
        return switch (s.habitat) {
            case LAND -> !water && m != Material.LAVA && top.getType().isSolid() ? on : null;
            case WATER -> water || (top.getType().isSolid() && nearWater(top, 3)) ? on : null;
            case SHORE -> (m == Material.SAND || m == Material.GRAVEL || m == Material.MUD || m == Material.STONE || m == Material.MUDDY_MANGROVE_ROOTS)
                    && nearWater(top, 4) ? on : null;
            case AIR -> m != Material.LAVA && top.getRelative(0, 2, 0).getType().isAir() ? on.add(0, 1.5, 0) : null;
            case POND -> null;
        };
    }

    private static boolean nearWater(Block b, int r) {
        for (int dx = -r; dx <= r; dx += 2)
            for (int dz = -r; dz <= r; dz += 2)
            {
                Block n = b.getRelative(dx, 0, dz);
                if (!b.getWorld().isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) continue; // never load a chunk for this
                if (n.getType() == Material.WATER) return true;
            }
        return false;
    }
}
