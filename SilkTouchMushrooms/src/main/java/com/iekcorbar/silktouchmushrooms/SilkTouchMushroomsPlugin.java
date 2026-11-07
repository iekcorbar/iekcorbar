package com.iekcorbar.silktouchmushrooms;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Lets red/brown mushrooms be picked up with Silk Touch (vanilla drops them as items). */
public class SilkTouchMushroomsPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SilkTouchMushrooms enabled.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.RED_MUSHROOM && b.getType() != Material.BROWN_MUSHROOM) return;
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (tool == null || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        // cancel default drop and drop the block itself
        e.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(b.getType()));
    }
}