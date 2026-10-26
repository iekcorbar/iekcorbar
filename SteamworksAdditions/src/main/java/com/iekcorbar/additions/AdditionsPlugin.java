package com.iekcorbar.additions;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Steamworks Additions - the atmosphere add-on. First set: the Steam Clock (strikes every
 * in-game hour, with a floating time plate), the Brass Railing and the Vent Grille.
 * Props run on the shared propkit engine; models come from the additions resource pack.
 */
public final class AdditionsPlugin extends JavaPlugin {
    Additions additions;
    private Adv adv;
    private int decorTick;

    /** config sounds.clock-bell - the Steam Clock's hourly strike (bell + steam puff). */
    boolean bellEnabled() { return getConfig().getBoolean("sounds.clock-bell", true); }

    /** config sounds.clock-night-rest - no bell between 23:00 and 05:00 (the plate keeps time). */
    boolean nightRest() { return getConfig().getBoolean("sounds.clock-night-rest", false); }

    /** config clock.time-source - "real" (wall clock, one strike per real hour) or "game" (world clock). */
    boolean realClock() { return !getConfig().getString("clock.time-source", "real").equalsIgnoreCase("game"); }

    /** config clock.timezone - an IANA zone like Europe/Amsterdam; blank = the server's own time. */
    java.time.ZoneId zone() {
        String z = getConfig().getString("clock.timezone", "").trim();
        if (!z.isEmpty()) try {
            return java.time.ZoneId.of(z);
        } catch (RuntimeException ignored) {
            getLogger().warning("clock.timezone '" + z + "' is not a valid zone - using server time.");
        }
        return java.time.ZoneId.systemDefault();
    }

    Adv adv() { return adv; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        additions = new Additions(this);
        getServer().getPluginManager().registerEvents(additions.decor, this);
        additions.decor.registerRecipes();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(additions), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        adv = Achievements.create(this);
        adv.start();
        for (org.bukkit.World w : getServer().getWorlds())
            for (org.bukkit.Chunk c : w.getLoadedChunks()) additions.decor.loadChunk(c);
        AdditionsCommand cmd = new AdditionsCommand(this);
        getCommand("additions").setExecutor(cmd);
        getCommand("additions").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> additions.decor.tick(decorTick += 10), 20L, 10L);
        getLogger().info("Steamworks Additions loaded - the Steam Clock is ready to strike.");
    }

    @Override
    public void onDisable() {
        if (additions != null && !getServer().isStopping()) additions.decor.removeAllDisplays();
    }
}
