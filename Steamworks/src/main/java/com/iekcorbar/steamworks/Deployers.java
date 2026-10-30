package com.iekcorbar.steamworks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Deployer: places or breaks the block in front of it, powered from behind.
 * A filter slot sets which item to deploy (right-click to set).
 */
final class Deployers {
    private final SteamworksPlugin plugin;

    Deployers(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ ticking

    void tick(long tick) {
        for (KNode n : plugin.kinetics().deployers) {
            double speed = Math.abs(n.speed());
            if (speed <= 0) continue;
            if (tick % 20 != 0) continue;
            Block target = n.block().getRelative(n.facing);
            if (n.deployItem != null && !n.deployItem.isEmpty()) {
                // try to place the item
                if (target.getType().isAir() || target.isReplaceable()) {
                    Material mat = n.deployItem.getType();
                    if (mat.isBlock()) {
                        target.setType(mat);
                        n.world.spawnParticle(Particle.BLOCK, target.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1, mat.createBlockData());
                        n.world.playSound(target.getLocation(), Sound.BLOCK_STONE_PLACE, 0.5f, 1f);
                    }
                }
            } else {
                // break the block in front
                if (!target.getType().isAir() && target.getType() != Material.BEDROCK) {
                    List<ItemStack> drops = new ArrayList<>(target.getDrops());
                    target.setType(Material.AIR);
                    for (ItemStack d : drops) n.world.dropItemNaturally(target.getLocation().add(0.5, 0.5, 0.5), d);
                    n.world.spawnParticle(Particle.BLOCK, target.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1, target.getType().createBlockData());
                    n.world.playSound(target.getLocation(), Sound.BLOCK_STONE_BREAK, 0.5f, 1f);
                }
            }
        }
    }

    // ------------------------------------------------------------ by hand

    boolean interact(KNode n, Player p, ItemStack hand) {
        if (hand != null && !hand.isEmpty()) {
            n.deployItem = hand.asOne();
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            p.sendActionBar(net.kyori.adventure.text.Component.text("Deployer filter set to " + hand.getType().name(), net.kyori.adventure.text.format.NamedTextColor.GOLD));
            return true;
        }
        if (n.deployItem != null) {
            n.deployItem = null;
            p.sendActionBar(net.kyori.adventure.text.Component.text("Deployer filter cleared", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            return true;
        }
        return false;
    }

    static List<ItemStack> contents(KNode n) {
        List<ItemStack> out = new ArrayList<>();
        if (n.deployItem != null) out.add(n.deployItem);
        return out;
    }
}