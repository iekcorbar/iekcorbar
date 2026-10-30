package com.iekcorbar.relics;

import org.bukkit.World;

/**
 * The Blood Moon (0.6.0). A Blood Token placed while the crimson moon stands above the
 * horizon is Blessed on the spot. Rise and set come from the lunar phase just like the
 * real sky: a full moon rises at dusk (tick 18000) and each phase shifts that about
 * 1000 ticks later, so a token can only be blessed near full moons - about one night
 * in eight has a moon up during the small-hours window. The window itself is config:
 * blood-moon.start-hour / end-hour on a 24-hour clock (default 20:00 - 04:00), and
 * blood-moon.enabled turns the whole thing off.
 */
final class BloodMoon {
    private BloodMoon() {
    }

    static boolean isBloodMoon(RelicsPlugin plugin, World w) {
        if (w == null) return false;
        var c = plugin.getConfig();
        if (!c.getBoolean("blood-moon.enabled", true)) return false;
        long t = w.getTime();
        long start = hourToTicks(c.getInt("blood-moon.start-hour", 20));
        long end = hourToTicks(c.getInt("blood-moon.end-hour", 4));
        boolean window = start <= end ? (t >= start && t < end) : (t >= start || t < end);
        return window && moonUp(w.getFullTime(), t);
    }

    /** In-game clock hour -> world time; dawn (tick 0) is 06:00. */
    private static long hourToTicks(int hour24) {
        return ((hour24 - 6 + 24) % 24) * 1000L;
    }

    /** Is the moon above the horizon right now? Phase 0 = full moon, rising at dusk. */
    private static boolean moonUp(long fullTime, long dayTime) {
        long phase = Math.floorMod(fullTime / 24000L, 8L);
        long rise = Math.floorMod(18000L + phase * 1000L, 24000L);
        long set = Math.floorMod(rise + 12000L, 24000L);
        return set >= rise ? (dayTime >= rise && dayTime < set) : (dayTime >= rise || dayTime < set);
    }
}
