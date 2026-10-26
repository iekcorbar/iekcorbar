package com.iekcorbar.satchel;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Satchel: backpacks in six tiers (canvas to netherite) with upgrade slots - pickup, magnet, feeding, everlasting.
 * The contents live in the backpack item itself. The idea of what a backpack with upgrades can do comes from the
 * Sophisticated Backpacks mod; code, models and textures are our own.
 */
public final class SatchelPlugin extends JavaPlugin {
    private Items items;
    private Backpacks backpacks;
    private Adv adv;
    private long tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        adv = Achievements.create(this, items);
        backpacks = new Backpacks(this, items, adv);
        items.registerRecipes();
        adv.start();
        var pm = getServer().getPluginManager();
        pm.registerEvents(items, this);
        pm.registerEvents(backpacks, this);
        var cmd = new SatchelCommand(this);
        getCommand("satchel").setExecutor(cmd);
        getCommand("satchel").setTabCompleter(cmd);
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, ServicePriority.Normal);
        getServer().getScheduler().runTaskTimer(this, () -> backpacks.tick(tick += 10), 10, 10);
    }

    @Override
    public void onDisable() {
        if (backpacks != null) backpacks.closeAll();
        if (items != null && !getServer().isStopping()) items.unregisterRecipes();
    }

    Items items() {
        return items;
    }

    String docsUrl() {
        return getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=satchel");
    }

    void reload() {
        reloadConfig();
    }
}
