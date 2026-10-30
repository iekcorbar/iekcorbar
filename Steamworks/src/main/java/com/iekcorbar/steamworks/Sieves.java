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
 * Sieve: items dropped on top get sifted. Gravel gives flint, sand gives stones, etc.
 * Powered from the side. Results are taken by hand or pushed into a container below.
 */
final class Sieves {
    private final SteamworksPlugin plugin;
    private final MachineRecipes recipes;

    Sieves(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.recipes = new MachineRecipes(plugin, "sieve");
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
        for (KNode n : plugin.kinetics().sieves) {
            double speed = Math.abs(n.speed());
            if (speed <= 0) continue;
            if (n.sieveInput == null) continue;
            MachineRecipes.Recipe r = find(n.sieveInput);
            if (r == null) {
                n.world.dropItemNaturally(n.center().add(0, 0.3, 0), n.sieveInput);
                n.sieveInput = null;
                n.sieveProgress = 0;
                plugin.saveSoon(n);
                continue;
            }
            n.sieveProgress += 5 * Math.max(cfg.millMinRate, Math.min(cfg.millMaxRate, speed / cfg.millDivisor));
            Location top = n.center().add(0, 0.55, 0);
            n.world.spawnParticle(Particle.ITEM, top, 3, 0.2, 0.05, 0.2, 0.02, n.sieveInput.asOne());
            if (tick % 20 == 0 && cfg.machineVolume > 0) n.world.playSound(top, "steamworks:millstone.grind", 0.45f * cfg.machineVolume, 1f);
            while (n.sieveProgress >= r.time() && n.sieveInput != null) {
                n.sieveProgress -= r.time();
                n.sieveInput.setAmount(n.sieveInput.getAmount() - 1);
                if (n.sieveInput.getAmount() <= 0) {
                    n.sieveInput = null;
                    n.sieveProgress = 0;
                }
                for (MachineRecipes.Output o : r.outputs()) {
                    if (o.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= o.chance()) continue;
                    addOutput(n, o.create(plugin.items()));
                }
            }
        }
    }

    private static void addOutput(KNode n, ItemStack item) {
        for (ItemStack s : n.sieveOutput) {
            if (!s.isSimilar(item)) continue;
            int room = s.getMaxStackSize() - s.getAmount();
            int move = Math.min(room, item.getAmount());
            s.setAmount(s.getAmount() + move);
            item.setAmount(item.getAmount() - move);
            if (item.getAmount() <= 0) return;
        }
        n.sieveOutput.add(item);
    }

    /** Results go into a container below; what is left falls out. */
    void pushOut() {
        for (KNode n : plugin.kinetics().sieves) {
            if (n.sieveOutput.isEmpty()) continue;
            Block below = n.block().getRelative(BlockFace.DOWN);
            if (below.getState(false) instanceof Container c) {
                Inventory inv = c.getInventory();
                List<ItemStack> left = new ArrayList<>();
                for (ItemStack s : n.sieveOutput) left.addAll(inv.addItem(s).values());
                n.sieveOutput.clear();
                n.sieveOutput.addAll(left);
            }
            if (!n.sieveOutput.isEmpty()) {
                Location beside = n.center().add(0, 0.2, 0);
                for (ItemStack s : new ArrayList<>(n.sieveOutput)) n.world.dropItemNaturally(beside, s);
                n.sieveOutput.clear();
                plugin.saveSoon(n);
            }
        }
    }

    /** Right-click: put the held item in, or take the results. */
    boolean interact(KNode n, Player p, ItemStack hand) {
        if (hand != null && !hand.isEmpty() && find(hand) != null) {
            int room;
            if (n.sieveInput == null) room = hand.getMaxStackSize();
            else if (n.sieveInput.isSimilar(hand)) room = n.sieveInput.getMaxStackSize() - n.sieveInput.getAmount();
            else room = 0;
            int move = Math.min(room, hand.getAmount());
            if (move <= 0) return false;
            if (n.sieveInput == null) n.sieveInput = hand.asQuantity(move);
            else n.sieveInput.setAmount(n.sieveInput.getAmount() + move);
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - move);
            p.playSound(n.center(), Sound.BLOCK_GRAVEL_PLACE, 0.6f, 1.2f);
            return true;
        }
        if (!n.sieveOutput.isEmpty()) {
            for (ItemStack s : n.sieveOutput) {
                for (ItemStack left : p.getInventory().addItem(s.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            n.sieveOutput.clear();
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
            return true;
        }
        return false;
    }

    static List<ItemStack> contents(KNode n) {
        List<ItemStack> out = new ArrayList<>(n.sieveOutput);
        if (n.sieveInput != null) out.add(n.sieveInput);
        return out;
    }
}