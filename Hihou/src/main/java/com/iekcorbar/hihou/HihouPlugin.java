package com.iekcorbar.hihou;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 秘宝 Hihou - treasure/event/vote/lucky/victory chests with physical keys. v0.1: the chest side.
 * v0.2 (planned, see HIHOU-DEV-NOTES.md): the Game Island automation - finish-plate races,
 * last-one-standing rounds, quiz answers and manual awards, each paying out a key or a victory
 * chest per course configuration.
 */
public final class HihouPlugin extends JavaPlugin {
    private Keys keys;
    private Chests chests;
    private long tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keys = new Keys(this);
        chests = new Chests(this, keys);
        var pm = getServer().getPluginManager();
        pm.registerEvents(chests.decor, this);
        pm.registerEvents(chests, this);
        chests.decor.registerRecipes(); // none defined yet (chests are staff-given), harmless
        HihouCommand cmd = new HihouCommand(this);
        getCommand("hihou").setExecutor(cmd);
        getCommand("hihou").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 5;
            chests.decor.tick(tick);
        }, 5L, 5L);
        getLogger().info("Hihou enabled: " + Chests.TYPES.length + " chest types, keys ready.");
    }

    @Override
    public void onDisable() {
        if (chests != null) {
            chests.decor.removeAllDisplays();
            chests.store.flush();
        }
    }

    Keys keys() {
        return keys;
    }

    Chests chests() {
        return chests;
    }
}
