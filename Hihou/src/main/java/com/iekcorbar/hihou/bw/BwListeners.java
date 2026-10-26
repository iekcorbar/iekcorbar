package com.iekcorbar.hihou.bw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * The thin event edge of the BedWars module. Etappe 0.3.0 needs three hooks: people leaving the
 * server or dying hand their goods back, and PvP respects teams (own-team hits bounce, the lobby
 * is a no-fight zone) - exactly the behavior BedWars1058's listener brij enforces around its
 * addPlayer/removePlayer, minus the 40 listeners the full game needs later.
 */
public final class BwListeners implements Listener {
    private final BwGameManager mgr;

    public BwListeners(BwGameManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.handleQuit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        mgr.handleDeath(e.getEntity());
    }

    /**
     * Friendly fire and lobby protection. The original also checks world and coordinates; we key
     * everything off membership (arenaOf) because our arenas can overlap ordinary survival space.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPvp(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;
        String deny = mgr.pvpDeny(victim, attacker);
        if (deny == null) return;
        e.setCancelled(true);
        attacker.sendActionBar(Component.text("You can't hit that - " + deny + ".", NamedTextColor.RED));
    }

    private static Player attackerOf(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }
}
