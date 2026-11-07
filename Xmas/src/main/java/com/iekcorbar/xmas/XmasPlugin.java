package com.iekcorbar.xmas;

import org.bukkit.plugin.java.JavaPlugin;

public class XmasPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Xmas plugin enabled (kerst building blocks).");
        getCommand("xmas").setExecutor(new XmasCommand(this));
    }

    @Override
    public void onDisable() {
        getLogger().info("Xmas plugin disabled.");
    }
}