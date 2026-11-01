package com.iekcorbar.bouw;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bouw - building pieces for builders: doors and fences in every wood, as props on the shared propkit
 * engine. Models and textures are converted from the MIT-licensed Macaw's Doors / Macaw's Fences
 * (see LICENSE-MACAWS.txt inside the resource pack).
 */
public final class BouwPlugin extends JavaPlugin {
    Bouw bouw;
    BouwMenu menu;
    private Adv adv;
    private int decorTick;

    Adv adv() { return adv; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bouw = new Bouw(this);
        getServer().getPluginManager().registerEvents(bouw.decor, this);
        bouw.decor.registerRecipes();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(bouw), this, org.bukkit.plugin.ServicePriority.Normal);
        adv = Achievements.create(this);
        adv.start();
        for (org.bukkit.World w : getServer().getWorlds())
            for (org.bukkit.Chunk c : w.getLoadedChunks()) bouw.decor.loadChunk(c);
        menu = new BouwMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);
        BouwCommand cmd = new BouwCommand(this);
        getCommand("bouw").setExecutor(cmd);
        getCommand("bouw").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> bouw.decor.tick(decorTick += 10), 20L, 10L);
        getLogger().info("Bouw loaded - " + bouw.decor.defs.size() + " building pieces.");
    }

    @Override
    public void onDisable() {
        if (bouw != null && !getServer().isStopping()) bouw.decor.removeAllDisplays();
    }
}
