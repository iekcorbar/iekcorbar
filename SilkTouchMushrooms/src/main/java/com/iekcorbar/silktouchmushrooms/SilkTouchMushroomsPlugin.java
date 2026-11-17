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

import java.util.Set;

/** Lets mushrooms (small and big) be picked up as blocks with Silk Touch. */
public class SilkTouchMushroomsPlugin extends JavaPlugin implements Listener {
    private static final Set<Material> MUSHROOMS = Set.of(
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM_BLOCK, Material.BROWN_MUSHROOM_BLOCK, Material.MUSHROOM_STEM);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SilkTouchMushrooms enabled.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!MUSHROOMS.contains(b.getType())) return;
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (tool == null || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        // cancel default drop and drop the block itself
        e.setDropItems(false);
        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(b.getType()));
    }
}