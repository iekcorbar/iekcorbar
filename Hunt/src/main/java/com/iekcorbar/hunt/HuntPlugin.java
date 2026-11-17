package com.iekcorbar.hunt;

import org.bukkit.plugin.java.JavaPlugin;

public class HuntPlugin extends JavaPlugin {
    private HuntManager manager;

    @Override
    public void onEnable() {
        manager = new HuntManager(this);
        getServer().getPluginManager().registerEvents(manager, this);
        getCommand("hunt").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player p)) { sender.sendMessage("Only players."); return true; }
            if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
                manager.claimRewards(p);
                return true;
            }
            sender.sendMessage(org.bukkit.ChatColor.GOLD + "=== Hunt Progress ===");
            for (String cat : new String[]{"wildlife", "kitchen", "relics", "zoo", "sakagura", "steamworks"}) {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this, "hunt_" + cat);
                int count = p.getPersistentDataContainer().getOrDefault(key, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                int[] ms = HuntManager.MILESTONES.get(cat);
                String milestones = "";
                if (ms != null) {
                    for (int m : ms) {
                        org.bukkit.NamespacedKey claimed = new org.bukkit.NamespacedKey(this, "hunt_" + cat + "_" + m);
                        boolean done = p.getPersistentDataContainer().has(claimed, org.bukkit.persistence.PersistentDataType.BYTE);
                        milestones += (done ? "§a✓" : "§7○") + m + " ";
                    }
                }
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + cat + ": " + org.bukkit.ChatColor.WHITE + count + " achievements  " + milestones);
            }
            sender.sendMessage(org.bukkit.ChatColor.GRAY + "Use /hunt claim to collect pending rewards.");
            return true;
        });
        getLogger().info("Hunt plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
        getLogger().info("Hunt plugin disabled.");
    }

    public HuntManager manager() { return manager; }
}