package com.iekcorbar.xmas;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;

public class XmasCommand implements CommandExecutor {
    private final XmasPlugin plugin;
    public XmasCommand(XmasPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            p.sendMessage("Usage: /xmas give <block>");
            return true;
        }
        if (args.length < 2) { p.sendMessage("Specify a block."); return true; }
        String id = args[1].toLowerCase();
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Xmas " + id);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "xmas:block"), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
        p.sendMessage("Gave xmas:" + id);
        return true;
    }
}