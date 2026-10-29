package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AfkCinema — AFK-detectie + server-gestuurde bioscoopcamera.
 *
 * Het cliënt-concept van de ji-afk-cinematic-mod (camera draait om een AFK
 * speler) is hier nagemaakt zoals het KAN zonder client-mod: de AFK-speler
 * zelf gaat in spectator en krijgt via teleport een rustige, ademende
 * baan om zijn eigen personage. Elke input beeindigt de film en zet terug.
 */
public final class AfkCinemaPlugin extends JavaPlugin {

    private AfkCinemaListener manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new AfkCinemaListener(this);
        getServer().getPluginManager().registerEvents(manager, this);
        PluginCommand cine = getCommand("cine");
        if (cine != null) cine.setExecutor(new CineCommand(this));
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L, 1L);
        getLogger().info("AfkCinema " + getDescription().getVersion()
            + " aan — AFK na " + manager.afkSeconden() + "s, bioscoop aan.");
    }

    @Override
    public void onDisable() {
        for (CineSession s : manager.sessies().values()) {
            if (s.filmLoopt && s.speler.isOnline()) {
                s.speler.setGameMode(s.vorigeGameMode);
                s.speler.teleport(s.vorigeLoc);
            }
        }
    }

    public AfkCinemaListener manager() { return manager; }

    public boolean broadcastAfk() { return getConfig().getBoolean("broadcast-afk", true); }

    public boolean collisionLift() { return getConfig().getBoolean("cine.collision-lift", true); }
}
