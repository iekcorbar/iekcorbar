package com.iekcorbar.gouw;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Releases gouwen whose owner has not played for `expire-inactive-days` days (0 = never).
 * Runs an hour after enable and every hour after that (cheap: in-memory map + local usercache),
 * plus manually from the staff menu page. Safety: owners who are online, and owners whose last
 * play time is unknown (freshly imported Lands gouwen of players who never joined this server),
 * are kept - expiry is a housekeeping tool, not a way to rob the recently-returned.
 */
final class Expiry {
    private final GouwPlugin plugin;
    private final ClaimStore store;

    Expiry(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
    }

    /** Config value; 0 = expiry off. */
    int days() {
        return Math.max(0, plugin.getConfig().getInt("expire-inactive-days", 0));
    }

    /** @return the number of gouwen released (0 when off or nothing matured). Main thread only. */
    int check() {
        int d = days();
        if (d == 0) return 0;
        long cutoff = System.currentTimeMillis() - d * 86_400_000L;
        List<Land> gone = new ArrayList<>();
        for (Land l : store.all()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(l.owner);
            if (op.isOnline()) continue;
            long last = op.getLastPlayed();
            if (last <= 0) {
                plugin.getLogger().info("Keeping gouw '" + l.name + "' - no last-played known for its owner.");
                continue;
            }
            if (last < cutoff) gone.add(l);
        }
        for (Land l : gone) {
            int n = l.chunks.size();
            store.unclaimAll(l);
            plugin.getLogger().info("Expired gouw '" + l.name + "' - " + n + " chunk(s) released (owner offline over " + d + " days).");
        }
        return gone.size();
    }
}
