package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AfkCinema — AFK-detectie + bioscoopcamera met camerapaden (26.2).
 *
 * /cine afk       : camera draait om JOUW personage (via een Citizens-stand-in,
 *                   dus je ziet jezelf in beeld — niet als onzichtbare spectator)
 * /cine opname x  : vrij rondvliegen en met /cine pad add waypoints zetten
 * /cine pad ...   : pads beheren en afspelen (Catmull-Rom drone-vluchten)
 *
 * Inspired by the ji-afk-cinematic client mod, entirely reimplemented server-side.
 */
public final class AfkCinemaPlugin extends JavaPlugin {

    private AfkCinemaListener manager;
    private SpatialMenu menus;
    private PadStore pads;
    private Adv adv;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pads = new PadStore(getDataFolder());
        pads.laad();
        adv = new Adv(this);
        adv.register();
        manager = new AfkCinemaListener(this);
        menus = new SpatialMenu(this);
        getServer().getPluginManager().registerEvents(manager, this);
        getServer().getPluginManager().registerEvents(menus, this);
        PluginCommand cine = getCommand("cine");
        if (cine != null) {
            CineCommand cc = new CineCommand(this);
            cine.setExecutor(cc);
            cine.setTabCompleter(cc);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> { manager.tick(); menus.tick(); }, 20L, 1L);
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().warning("Citizens not found: no stand-in — at /cine afk the player "
                + "will orbit an empty spot instead of their own character.");
        }
        getLogger().info("AfkCinema " + getDescription().getVersion()
            + " enabled — AFK after " + manager.afkSeconden() + "s, shot="
            + getConfig().getString("cine.afk-shot", "orbit") + ", paths=" + pads.namen().size() + ".");
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.clearAlles();
        if (manager != null) manager.allesStoppen();
    }

    public void reloadAlles() {
        reloadConfig();
        pads.laad();
    }

    public AfkCinemaListener manager() { return manager; }
    public SpatialMenu menus() { return menus; }
    public PadStore pads() { return pads; }
    public Adv adv() { return adv; }

    public boolean broadcastAfk() { return getConfig().getBoolean("broadcast-afk", true); }

    public boolean collisionLift() { return getConfig().getBoolean("cine.collision-lift", true); }

    public double padSnelheid() { return getConfig().getDouble("cine.pad-speed", 2.0); }

    public String padLus() { return getConfig().getString("cine.pad-loop", "back-and-forth").toLowerCase(); }

    public boolean padEasing() { return getConfig().getBoolean("cine.pad-easing", true); }
}
