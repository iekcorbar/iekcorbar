package com.iekcorbar.wildlife;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Biome soundscapes around players who are outdoors: birdsong and a woodpecker
 * in forests by day, crickets and owls at night, frogs in swamps, gulls and
 * waves on beaches, cicadas in warm biomes, wind in the mountains. Sounds come
 * from a random spot 8-24 blocks away, in the Ambient/Environment volume
 * category, so every player can turn them down. Only the player hears them.
 */
final class Ambience {
    private final WildlifePlugin plugin;
    private boolean enabled;
    private float volume;

    Ambience(WildlifePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    void load() {
        enabled = plugin.getConfig().getBoolean("ambience.enabled", true);
        volume = (float) plugin.getConfig().getDouble("ambience.volume", 0.6);
    }

    /** Every 2 seconds. */
    void tick() {
        if (!enabled) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            World w = p.getWorld();
            if (w.getEnvironment() != World.Environment.NORMAL || p.getGameMode() == GameMode.SPECTATOR) continue;
            Block at = p.getLocation().getBlock();
            if (at.getLightFromSky() < 12) continue; // indoors, underground or under a thick canopy roof
            String biome = at.getBiome().getKey().getKey();
            long time = w.getTime();
            boolean night = time > 13000 && time < 23000;
            boolean storm = w.hasStorm();
            String pick = pick(biome, night, storm, r);
            if (pick == null) continue;
            double a = r.nextDouble() * Math.PI * 2, d = 8 + r.nextDouble() * 16;
            Location from = p.getLocation().add(Math.cos(a) * d, 2 + r.nextDouble() * 6, Math.sin(a) * d);
            p.playSound(from, "wildlife:ambient." + pick, SoundCategory.AMBIENT, volume * (0.7f + r.nextFloat() * 0.3f), 0.95f + r.nextFloat() * 0.1f);
        }
    }

    private static boolean has(String biome, String... parts) {
        for (String s : parts) if (biome.contains(s)) return true;
        return false;
    }

    /** One sound for this biome and moment, or null (most ticks stay quiet). */
    private static String pick(String b, boolean night, boolean storm, ThreadLocalRandom r) {
        double x = r.nextDouble();
        if (has(b, "swamp", "mangrove")) {
            if (x < (night || storm ? 0.22 : 0.10)) return "frogs";
            if (night && x < 0.32) return "crickets";
            if (!night && !storm && x < 0.16) return "forest_birds";
            return null;
        }
        if (has(b, "beach", "stony_shore")) {
            if (x < 0.12) return "waves";
            if (!night && !storm && x < 0.19) return "gull";
            return null;
        }
        if (has(b, "jungle", "bamboo")) {
            if (night) return x < 0.16 ? "crickets" : x < 0.21 ? "frogs" : null;
            if (storm) return null;
            return x < 0.10 ? "cicadas" : x < 0.18 ? "jungle_bird" : null;
        }
        if (has(b, "desert", "badlands")) {
            if (x < 0.05) return "wind";
            if (!night && x < 0.10) return "cicadas";
            if (night && x < 0.08) return "crickets";
            return null;
        }
        if (has(b, "peaks", "slopes", "windswept", "snowy", "frozen")) {
            return x < 0.08 ? "wind" : null;
        }
        if (has(b, "savanna")) {
            if (night) return x < 0.16 ? "crickets" : null;
            return storm ? null : x < 0.09 ? "cicadas" : x < 0.14 ? "forest_birds" : null;
        }
        if (has(b, "forest", "taiga", "grove", "meadow", "cherry", "plains", "river")) {
            if (night) return x < 0.15 ? "crickets" : (has(b, "forest", "taiga") && x < 0.19) ? "owl" : null;
            if (storm) return null;
            boolean woods = has(b, "forest", "taiga", "grove", "cherry");
            return x < (woods ? 0.13 : 0.07) ? "forest_birds" : (woods && x < 0.16) ? "woodpecker" : null;
        }
        return null;
    }
}
