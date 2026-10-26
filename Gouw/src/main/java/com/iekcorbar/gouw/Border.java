package com.iekcorbar.gouw;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;

/** Gold dust outlines: one chunk when you walk into a gouw, the whole gouw for /gouw border. */
final class Border {
    private Border() {
    }

    static void chunk(World w, int cx, int cz) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(225, 180, 90), 1.1f);
        int x0 = cx << 4, z0 = cz << 4;
        for (int i = 0; i <= 16; i += 2) {
            at(w, x0 + i, z0, dust);
            at(w, x0 + i, z0 + 16, dust);
            at(w, x0, z0 + i, dust);
            at(w, x0 + 16, z0 + i, dust);
        }
    }

    /** Every outer side of every chunk, capped so even a huge gouw stays cheap. */
    static void draw(Land l) {
        World w = Bukkit.getWorld(l.world);
        if (w == null) return;
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 210, 110), 1.2f);
        int drawn = 0;
        for (long k : l.chunks) {
            int cx = Land.kx(k), cz = Land.kz(k);
            if (!l.has(cx + 1, cz)) edge(w, cx + 1, cz, true, gold);
            if (!l.has(cx - 1, cz)) edge(w, cx, cz, true, gold);
            if (!l.has(cx, cz + 1)) edge(w, cx, cz + 1, false, gold);
            if (!l.has(cx, cz - 1)) edge(w, cx, cz, false, gold);
            if (++drawn > 160) return;
        }
    }

    private static void edge(World w, int cx, int cz, boolean vertical, Particle.DustOptions d) {
        int x = cx << 4, z = cz << 4;
        for (int i = 0; i <= 16; i += 2) {
            if (vertical) at(w, x, z + i, d);
            else at(w, x + i, z, d);
        }
    }

    private static void at(World w, int x, int z, Particle.DustOptions d) {
        int y = w.getHighestBlockYAt(x, z) + 1;
        w.spawnParticle(Particle.DUST, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, d);
    }
}
