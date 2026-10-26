package com.iekcorbar.additions;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Steamworks Additions - the atmosphere add-on. First set: the Steam Clock (strikes every
 * in-game hour, with a floating time plate), the Brass Railing and the Vent Grille.
 * Props run on the shared propkit engine; models come from the additions resource pack.
 */
public final class AdditionsPlugin extends JavaPlugin {
    Additions additions;
    private int decorTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        additions = new Additions(this);
        getServer().getPluginManager().registerEvents(additions.decor, this);
        additions.decor.registerRecipes();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(additions), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        for (org.bukkit.World w : getServer().getWorlds())
            for (org.bukkit.Chunk c : w.getLoadedChunks()) additions.decor.loadChunk(c);
        AdditionsCommand cmd = new AdditionsCommand(this);
        getCommand("additions").setExecutor(cmd);
        getCommand("additions").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> additions.decor.tick(decorTick += 10), 20L, 10L);
        getLogger().info("Steamworks Additions loaded - the Steam Clock is ready to strike.");
    }

    @Override
    public void onDisable() {
        if (additions != null && !getServer().isStopping()) additions.decor.removeAllDisplays();
    }
}
