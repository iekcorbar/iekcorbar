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
    private PadStore pads;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pads = new PadStore(getDataFolder());
        pads.laad();
        manager = new AfkCinemaListener(this);
        getServer().getPluginManager().registerEvents(manager, this);
        PluginCommand cine = getCommand("cine");
        if (cine != null) {
            CineCommand cc = new CineCommand(this);
            cine.setExecutor(cc);
            cine.setTabCompleter(cc);
        }
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L, 1L);
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().warning("Citizens ontbreekt: de stand-in valt weg — bij /cine afk "
                + "zie je jezelf dan niet in beeld (wellen om de plek waar je stond).");
        }
        getLogger().info("AfkCinema " + getDescription().getVersion()
            + " aan — AFK na " + manager.afkSeconden() + "s, shot="
            + getConfig().getString("cine.afk-shot", "orbit") + ", pads=" + pads.namen().size() + ".");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.allesStoppen();
    }

    public void reloadAlles() {
        reloadConfig();
        pads.laad();
    }

    public AfkCinemaListener manager() { return manager; }
    public PadStore pads() { return pads; }

    public boolean broadcastAfk() { return getConfig().getBoolean("broadcast-afk", true); }

    public boolean collisionLift() { return getConfig().getBoolean("cine.collision-lift", true); }

    public double padSnelheid() { return getConfig().getDouble("cine.pad-snelheid", 2.0); }

    public String padLus() { return getConfig().getString("cine.pad-lus", "heen-en-terug").toLowerCase(); }

    public boolean padEasing() { return getConfig().getBoolean("cine.pad-easing", true); }
}
