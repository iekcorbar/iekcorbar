package com.iekcorbar.atelier;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Atelier - building pieces for builders: doors and fences in every wood, as props on the shared propkit
 * engine. Models and textures are converted from the MIT-licensed Macaw's Doors / Macaw's Fences
 * (see LICENSE-MACAWS.txt inside the resource pack).
 */
public final class AtelierPlugin extends JavaPlugin {
    Atelier atelier;
    private Adv adv;
    private int decorTick;

    Adv adv() { return adv; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        atelier = new Atelier(this);
        getServer().getPluginManager().registerEvents(atelier.decor, this);
        atelier.decor.registerRecipes();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(atelier), this, org.bukkit.plugin.ServicePriority.Normal);
        adv = Achievements.create(this);
        adv.start();
        for (org.bukkit.World w : getServer().getWorlds())
            for (org.bukkit.Chunk c : w.getLoadedChunks()) atelier.decor.loadChunk(c);
        AtelierCommand cmd = new AtelierCommand(this);
        getCommand("atelier").setExecutor(cmd);
        getCommand("atelier").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> atelier.decor.tick(decorTick += 10), 20L, 10L);
        getLogger().info("Atelier loaded - " + atelier.decor.defs.size() + " building pieces.");
    }

    @Override
    public void onDisable() {
        if (atelier != null && !getServer().isStopping()) atelier.decor.removeAllDisplays();
    }
}
