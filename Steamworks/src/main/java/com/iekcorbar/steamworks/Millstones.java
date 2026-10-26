package com.iekcorbar.steamworks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Millstone: one input stack, up to 9 result stacks. Grinds one item at a time;
 * work per tick = speed / speed-divisor (clamped). Fed by hand or by a hopper
 * pointing down into it; results are taken by hand or pushed into a container
 * next to it.
 */
final class Millstones {
    private static final int MAX_OUTPUT_STACKS = 9;

    private final SteamworksPlugin plugin;
    private final MachineRecipes recipes;

    Millstones(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.recipes = new MachineRecipes(plugin, "millstone");
    }

    void load(File file) {
        recipes.load(file);
    }

    MachineRecipes recipes() {
        return recipes;
    }

    MachineRecipes.Recipe find(ItemStack item) {
        return recipes.find(item);
    }

    // ------------------------------------------------------------ ticking (every 5 ticks)

    void tick(long tick) {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().mills) {
            if (tick % 20 == 0) pushOut(n);
            if (tick % 10 == 0) pullFromHopper(n);
            double speed = Math.abs(n.speed());
            if (speed <= 0 || n.millInput == null) continue;
            MachineRecipes.Recipe r = find(n.millInput);
            if (r == null || n.millOutput.size() >= MAX_OUTPUT_STACKS) continue;
            n.millProgress += 5 * Math.max(cfg.millMinRate, Math.min(cfg.millMaxRate, speed / cfg.millDivisor));
            Location top = n.center().add(0, 0.55, 0);
            n.world.spawnParticle(Particle.ITEM, top, 3, 0.2, 0.05, 0.2, 0.02, n.millInput.asOne());
            if (tick % 20 == 0) n.world.playSound(top, "steamworks:millstone.grind", 0.45f, 1f);
            while (n.millProgress >= r.time() && n.millInput != null) {
                n.millProgress -= r.time();
                n.millInput.setAmount(n.millInput.getAmount() - 1);
                if (n.millInput.getAmount() <= 0) {
                    n.millInput = null;
                    n.millProgress = 0;
                }
                for (MachineRecipes.Output o : r.outputs()) {
                    if (o.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= o.chance()) continue;
                    addOutput(n, o.create(plugin.items()));
                }
            }
        }
    }

    private static void addOutput(KNode n, ItemStack item) {
        for (ItemStack s : n.millOutput) {
            if (!s.isSimilar(item)) continue;
            int room = s.getMaxStackSize() - s.getAmount();
            int move = Math.min(room, item.getAmount());
            s.setAmount(s.getAmount() + move);
            item.setAmount(item.getAmount() - move);
            if (item.getAmount() <= 0) return;
        }
        n.millOutput.add(item);
    }

    /** Results go into a chest/barrel/hopper on one of the four sides. */
    private void pushOut(KNode n) {
        if (n.millOutput.isEmpty()) return;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block b = n.block().getRelative(f);
            if (!(b.getState(false) instanceof Container c)) continue;
            Inventory inv = c.getInventory();
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack s : n.millOutput) left.addAll(inv.addItem(s).values());
            n.millOutput.clear();
            n.millOutput.addAll(left);
            if (n.millOutput.isEmpty()) return;
        }
    }

    /** A hopper above that points down feeds one item at a time. */
    private void pullFromHopper(KNode n) {
        Block above = n.block().getRelative(BlockFace.UP);
        if (above.getType() != Material.HOPPER) return;
        if (!(above.getBlockData() instanceof Directional d) || d.getFacing() != BlockFace.DOWN) return;
        if (!(above.getState(false) instanceof Hopper h)) return;
        Inventory inv = h.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.isEmpty() || find(s) == null) continue;
            if (n.millInput != null && (!n.millInput.isSimilar(s) || n.millInput.getAmount() >= n.millInput.getMaxStackSize())) continue;
            if (n.millInput == null) n.millInput = s.asOne();
            else n.millInput.setAmount(n.millInput.getAmount() + 1);
            s.setAmount(s.getAmount() - 1);
            inv.setItem(slot, s.getAmount() <= 0 ? null : s);
            return;
        }
    }

    // ------------------------------------------------------------ by hand

    /** Right-click: put the held item in, or take the results (sneak: also the input). Returns true if something happened. */
    boolean interact(KNode n, Player p, ItemStack hand) {
        if (hand != null && !hand.isEmpty() && find(hand) != null) {
            int room;
            if (n.millInput == null) room = hand.getMaxStackSize();
            else if (n.millInput.isSimilar(hand)) room = n.millInput.getMaxStackSize() - n.millInput.getAmount();
            else room = 0;
            int move = Math.min(room, hand.getAmount());
            if (move <= 0) return false;
            if (n.millInput == null) n.millInput = hand.asQuantity(move);
            else n.millInput.setAmount(n.millInput.getAmount() + move);
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - move);
            p.playSound(n.center(), Sound.BLOCK_GRAVEL_PLACE, 0.6f, 1.2f);
            return true;
        }
        if (!n.millOutput.isEmpty()) {
            give(p, n.millOutput);
            n.millOutput.clear();
            return true;
        }
        if (p.isSneaking() && n.millInput != null) {
            give(p, List.of(n.millInput));
            n.millInput = null;
            n.millProgress = 0;
            return true;
        }
        return false;
    }

    private static void give(Player p, List<ItemStack> items) {
        for (ItemStack s : items) {
            for (ItemStack left : p.getInventory().addItem(s.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
    }

    static List<ItemStack> contents(KNode n) {
        List<ItemStack> out = new ArrayList<>(n.millOutput);
        if (n.millInput != null) out.add(n.millInput);
        return out;
    }
}
