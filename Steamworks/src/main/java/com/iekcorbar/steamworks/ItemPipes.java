package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Items through pipes (automatic part): a pipe side that touches a vanilla container is off, input
 * (takes items out of the container) or output (puts items into it). A steam wrench on the container
 * cycles the side of the pipe next to it. A network moves ONE item type at a time, at most 4 items
 * every 10 ticks (8 per second). The axle-driven pump is not built yet.
 */
final class ItemPipes implements Listener {
    private static final char OFF = 'N', IN = 'I', OUT = 'O';
    private static final int PER_CYCLE = 4;
    private final SteamworksPlugin plugin;

    ItemPipes(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    static String modes(KNode n) {
        return n.itemModes != null && n.itemModes.length() == 6 ? n.itemModes : "NNNNNN";
    }

    private static int index(BlockFace f) {
        for (int i = 0; i < Kinetics.FACES.length; i++) if (Kinetics.FACES[i] == f) return i;
        return -1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWrench(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !Oil.containerBlock(b)) return;
        if (plugin.items().materialOf(e.getItem()) != MaterialType.WRENCH) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        for (BlockFace f : Kinetics.FACES) {
            KNode pipe = plugin.kinetics().at(b.getRelative(f));
            if (pipe == null || pipe.type != PartType.PIPE) continue;
            int side = index(f.getOppositeFace());
            char[] m = modes(pipe).toCharArray();
            m[side] = switch (m[side]) {
                case OFF -> IN;
                case IN -> OUT;
                default -> OFF;
            };
            pipe.itemModes = new String(m);
            plugin.saveSoon(pipe);
            String what = m[side] == IN ? "takes items out" : m[side] == OUT ? "puts items in" : "off";
            p.sendActionBar(Component.text("Pipe side: " + what, NamedTextColor.GOLD));
            p.swingMainHand();
            return;
        }
        p.sendActionBar(Component.text("No pipe next to this container.", NamedTextColor.GRAY));
    }

    /** Called every 10 ticks: every pipe network moves items between its input and output containers. */
    void tick() {
        Set<KNode> seen = new HashSet<>();
        for (KNode start : plugin.kinetics().all()) {
            if (start.type != PartType.PIPE || !start.alive || seen.contains(start)) continue;
            List<KNode> net = new ArrayList<>();
            ArrayDeque<KNode> q = new ArrayDeque<>();
            q.add(start);
            seen.add(start);
            while (!q.isEmpty() && net.size() < 512) {
                KNode n = q.poll();
                net.add(n);
                for (BlockFace f : Kinetics.FACES) {
                    KNode m = plugin.kinetics().rel(n, f.getModX(), f.getModY(), f.getModZ());
                    if (m != null && m.type == PartType.PIPE && seen.add(m)) q.add(m);
                }
            }
            move(net);
        }
    }

    private void move(List<KNode> net) {
        List<Container> ins = new ArrayList<>(), outs = new ArrayList<>();
        for (KNode n : net) {
            String m = modes(n);
            for (int i = 0; i < Kinetics.FACES.length; i++) {
                char mode = m.charAt(i);
                if (mode == OFF) continue;
                Block c = n.block().getRelative(Kinetics.FACES[i]);
                if (!Oil.containerBlock(c) || !(c.getState() instanceof Container box)) continue;
                if (mode == IN) ins.add(box);
                else outs.add(box);
            }
        }
        if (ins.isEmpty() || outs.isEmpty()) return;
        Material type = null;
        for (Container src : ins) {
            for (ItemStack s : src.getInventory().getStorageContents()) {
                if (s != null && !s.isEmpty()) {
                    type = s.getType();
                    break;
                }
            }
            if (type != null) break;
        }
        if (type == null) return;
        int budget = PER_CYCLE;
        for (Container src : ins) {
            if (budget <= 0) break;
            Inventory from = src.getInventory();
            int take = Math.min(budget, count(from, type));
            if (take <= 0) continue;
            Map<Integer, ItemStack> rest = from.removeItem(new ItemStack(type, take));
            int taken = take - amount(rest);
            ItemStack carry = new ItemStack(type, taken);
            for (Container dst : outs) {
                if (carry.getAmount() <= 0) break;
                if (dst.getBlock().equals(src.getBlock())) continue;
                int stuck = amount(dst.getInventory().addItem(carry));
                carry = new ItemStack(type, stuck);
            }
            if (carry.getAmount() > 0) from.addItem(carry);
            budget -= taken - carry.getAmount();
        }
    }

    private static int count(Inventory inv, Material type) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) if (s != null && s.getType() == type) n += s.getAmount();
        return n;
    }

    private static int amount(Map<Integer, ItemStack> leftovers) {
        int n = 0;
        for (ItemStack s : leftovers.values()) n += s.getAmount();
        return n;
    }
}
