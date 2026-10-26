package com.iekcorbar.hihou.bw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * The event edge of the BedWars module (a slice of what BedWars1058 spreads over its listener
 * brij). 0.3.0 brought quit/death/PvP; 0.4.0 adds the bed guards (break, sleep, explosions,
 * pistons all bounce off tracked bed blocks), respawn routing, the ReJoin hand-over on login
 * and placement tracking for the end-of-game map tidy-up.
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
    public void onJoin(PlayerJoinEvent e) {
        mgr.handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        mgr.handleDeath(e);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        mgr.handleRespawn(e);
    }

    /**
     * Friendly fire, lobby protection and respawn guard. The original also checks world and
     * coordinates; we key everything off membership (arenaOf) because our arenas overlap the
     * ordinary survival world.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;
        String deny = mgr.pvpDeny(victim, attacker);
        if (deny == null) return;
        e.setCancelled(true);
        attacker.sendActionBar(Component.text("You can't hit that - " + deny + ".", NamedTextColor.RED));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBedBreak(BlockBreakEvent e) {
        String deny = mgr.breakBed(e.getPlayer(), e.getBlock());
        if (deny == null) return;
        e.setCancelled(true);
        if (!deny.isEmpty()) e.getPlayer().sendActionBar(Component.text("Not that bed - " + deny + ".", NamedTextColor.RED));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBedSleep(PlayerBedEnterEvent e) {
        Block bed = e.getBed();
        String deny = mgr.sleepBed(e.getPlayer(), bed);
        if (deny == null) return;
        e.setCancelled(true);
        if (!deny.isEmpty()) e.getPlayer().sendActionBar(Component.text("Not that bed - " + deny + ".", NamedTextColor.RED));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        mgr.trackPlacement(e.getPlayer(), e.getBlock());
    }

    /** Beds sit in the middle of the map: blast-proof, piston-proof, whatever arena state. */
    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(mgr::bedProtected);
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        e.blockList().removeIf(mgr::bedProtected);
    }

    @EventHandler
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(mgr::bedProtected)) e.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(mgr::bedProtected)) e.setCancelled(true);
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
