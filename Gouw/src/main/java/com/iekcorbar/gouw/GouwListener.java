package com.iekcorbar.gouw;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * What a gouw actually protects, per flag: BUILD (break / place / bucket), ACCESS (chests, doors, buttons),
 * MOB (hitting mobs and other things inside), PVP (hitting players). Explosions never take claimed blocks
 * and pistons may not drag blocks across a border. Strangers walking in or out get an action bar and a
 * pulse of gold dust along the chunk line.
 *
 * Everything is chunk-level, exactly like the old Lands behaviour players know; the owner and bypassing
 * staff are always allowed.
 */
final class GouwListener implements Listener {
    private final GouwPlugin plugin;
    private final ClaimStore store;

    GouwListener(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
    }

    private String ownerName(Block b) {
        Land l = store.land(b.getWorld(), b.getX(), b.getZ());
        return l == null ? "?" : l.name;
    }

    private boolean blocked(Player p, Block b, Land.Flag f, String what) {
        if (store.may(p, b.getWorld(), b.getX(), b.getZ(), f)) return false;
        p.sendActionBar(Component.text("This is " + ownerName(b) + "'s gouw - only members may " + what + ".", NamedTextColor.RED));
        return true;
    }

    // ------------------------------------------------------------ build / break / use

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (blocked(e.getPlayer(), e.getBlock(), Land.Flag.BUILD, "break blocks")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocked(e.getPlayer(), e.getBlockPlaced(), Land.Flag.BUILD, "build here")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (blocked(e.getPlayer(), e.getBlock(), Land.Flag.BUILD, "use buckets here")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (blocked(e.getPlayer(), e.getBlock(), Land.Flag.BUILD, "use buckets here")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || store.may(e.getPlayer(), b.getWorld(), b.getX(), b.getZ(), Land.Flag.ACCESS)) return;
        if ((e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK) && interactive(b)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            e.getPlayer().sendActionBar(Component.text("This is " + ownerName(b) + "'s gouw - ask " + ownerName(b) + " to trust you.", NamedTextColor.RED));
        }
    }

    private static boolean interactive(Block b) {
        BlockState s = b.getState();
        if (s instanceof InventoryHolder) return true;
        var t = b.getType();
        if (t == org.bukkit.Material.ENDER_CHEST || t == org.bukkit.Material.CAULDRON || t == org.bukkit.Material.COMPOSTER
                || t == org.bukkit.Material.JUKEBOX || t == org.bukkit.Material.NOTE_BLOCK) return true;
        return Tag.DOORS.isTagged(t) || Tag.TRAPDOORS.isTagged(t) || Tag.FENCE_GATES.isTagged(t)
                || Tag.BUTTONS.isTagged(t) || Tag.BEDS.isTagged(t) || Tag.ANVIL.isTagged(t);
    }

    // ------------------------------------------------------------ fighting

    /** Strangers cannot hit what lives inside a gouw - mobs, pets, item frames and other players. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        var en = e.getAttacked();
        Land.Flag flag = en instanceof Player ? Land.Flag.PVP : Land.Flag.MOB;
        Block at = en.getLocation().getBlock();
        if (blocked(p, at, flag, en instanceof Player ? "fight players here" : "hit things here")) e.setCancelled(true);
    }

    // ------------------------------------------------------------ pushing and blowing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonCrosses(e.getBlock(), e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (pistonCrosses(e.getBlock(), e.getBlocks())) e.setCancelled(true);
    }

    /** A piston may not move blocks into, out of or through a gouw border it has no part in. */
    private boolean pistonCrosses(Block head, List<Block> moved) {
        Land here = store.land(head.getWorld(), head.getX(), head.getZ());
        for (Block b : moved) {
            Land bl = store.land(b.getWorld(), b.getX(), b.getZ());
            if (bl != null && bl != here) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> store.land(b.getWorld(), b.getX(), b.getZ()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> store.land(b.getWorld(), b.getX(), b.getZ()) != null);
    }

    // ------------------------------------------------------------ walking in and out

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.enterMessages()) return;
        var from = e.getFrom();
        var to = e.getTo();
        if (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4) return;
        Player p = e.getPlayer();
        World w = to.getWorld();
        Land before = store.land(w, from.getBlockX(), from.getBlockZ());
        Land now = store.land(w, to.getBlockX(), to.getBlockZ());
        if (before == now) return;
        if (now != null) {
            boolean member = now.isMember(p.getUniqueId());
            p.sendActionBar(Component.text("You enter the gouw of " + now.name + (member ? " (you are a member)." : "."),
                    member ? NamedTextColor.GREEN : NamedTextColor.GOLD));
            Border.chunk(w, to.getBlockX() >> 4, to.getBlockZ() >> 4);
        } else if (before != null) {
            p.sendActionBar(Component.text("Out in the open again.", NamedTextColor.YELLOW));
        }
    }
}
