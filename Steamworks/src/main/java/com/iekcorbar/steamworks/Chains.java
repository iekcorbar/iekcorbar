package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chain drive: two Chain Sprockets on the same axis, up to MAX blocks apart in
 * the plane across that axis, linked by right-clicking both with chains (one
 * chain per block of distance). Linked sprockets turn together (factor 1 in
 * Kinetics.links). Sneak + right-click with an empty hand takes the chain off;
 * breaking a sprocket drops its chains.
 */
final class Chains {
    static final int MAX = 12;

    private final SteamworksPlugin plugin;
    private final Map<UUID, KNode> pending = new HashMap<>();

    Chains(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isChain(ItemStack i) {
        return i != null && (i.getType() == Material.IRON_CHAIN || i.getType() == Material.COPPER_CHAIN);
    }

    void interact(KNode n, Player p, ItemStack hand) {
        if (!isChain(hand)) {
            unlink(n, true);
            p.sendActionBar(Component.text("Chain taken off.", NamedTextColor.YELLOW));
            return;
        }
        KNode first = pending.remove(p.getUniqueId());
        if (first == null || !first.alive || first == n) {
            if (n.chain != null) {
                p.sendActionBar(Component.text("This sprocket already has a chain - sneak + right-click with an empty hand to take it off.", NamedTextColor.YELLOW));
                return;
            }
            pending.put(p.getUniqueId(), n);
            p.sendActionBar(Component.text("Now right-click the other sprocket with chains (same axis, up to " + MAX + " blocks).", NamedTextColor.GOLD));
            p.playSound(n.center(), Sound.BLOCK_CHAIN_HIT, 0.6f, 1.2f);
            return;
        }
        String problem = check(first, n);
        if (problem != null) {
            p.sendActionBar(Component.text(problem, NamedTextColor.RED));
            return;
        }
        int dx = n.x - first.x, dy = n.y - first.y, dz = n.z - first.z;
        int need = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (count(p, hand.getType()) < need) {
                p.sendActionBar(Component.text("You need " + need + " chains for this distance.", NamedTextColor.RED));
                return;
            }
            p.getInventory().removeItem(new ItemStack(hand.getType(), need));
        }
        first.chain = new int[]{dx, dy, dz};
        n.chain = new int[]{-dx, -dy, -dz};
        changed(first);
        changed(n);
        p.sendActionBar(Component.text("Sprockets chained (" + need + " chains).", NamedTextColor.GOLD));
        p.playSound(n.center(), Sound.BLOCK_CHAIN_PLACE, 0.8f, 1f);
    }

    private static String check(KNode a, KNode b) {
        if (a.world != b.world) return "Both sprockets must be in the same world.";
        if (a.axis != b.axis) return "Both sprockets must turn on the same axis.";
        if (b.chain != null) return "The second sprocket already has a chain.";
        int along = switch (a.axis) {
            case X -> b.x - a.x;
            case Y -> b.y - a.y;
            case Z -> b.z - a.z;
        };
        if (along != 0) return "The sprockets must be side by side (in one plane), not behind each other.";
        double d = Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2) + Math.pow(b.z - a.z, 2));
        if (d < 2) return "Too close - use cogwheels for neighbours.";
        if (d > MAX) return "Too far (max " + MAX + " blocks).";
        return null;
    }

    private static int count(Player p, Material m) {
        int c = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (s != null && s.getType() == m) c += s.getAmount();
        return c;
    }

    /** Takes the chain off both ends (dropping the chains at this sprocket if asked). */
    void unlink(KNode n, boolean drop) {
        if (n.chain == null) return;
        int[] c = n.chain;
        KNode o = plugin.kinetics().rel(n, c[0], c[1], c[2]);
        int chains = (int) Math.ceil(Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]));
        n.chain = null;
        if (o != null && o.chain != null) {
            o.chain = null;
            changed(o);
        }
        changed(n);
        if (drop) n.world.dropItemNaturally(n.center(), new ItemStack(Material.IRON_CHAIN, chains));
    }

    private void changed(KNode n) {
        if (!n.alive) return;
        plugin.kinetics().relink(n);
        plugin.visuals().chainSpans(n);
        plugin.saveSoon(n);
    }
}
