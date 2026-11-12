package com.iekcorbar.xmas;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Xmas Town: the Christmas building blocks of the server (see {@link XmasDecor}) and, from 0.2.0,
 * the Kerstman himself.
 *
 * Prop plumbing is the shared propkit engine (Decor.java / DecorDef.java are copied in by
 * propkit/sync.py - edit them there, never here).
 */
public final class XmasPlugin extends JavaPlugin {

    private XmasDecor decor;
    private long tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        decor = new XmasDecor(this);
        decor.decor.registerRecipes();
        var pm = getServer().getPluginManager();
        pm.registerEvents(decor.decor, this);
        var xmasCmd = getCommand("xmas");
        XmasCommand exec = new XmasCommand(this);
        xmasCmd.setExecutor(exec);
        xmasCmd.setTabCompleter(exec);
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this),
                this, ServicePriority.Normal);                                    // recipe viewer (Zukan)

        // re-draw every prop that is already stored in the world (displays are not persistent)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (World w : getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(this, "decor_display"),
                            PersistentDataType.BYTE)) e.remove();
                }
                for (Chunk c : w.getLoadedChunks()) decor.decor.loadChunk(c);
            }
        }, 40L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 10;
            decor.decor.tick(tick);
        }, 10L, 10L);
        getLogger().info("Xmas enabled: " + decor.decor.defs.size() + " building blocks"
                + (decor.decor.count() > 0 ? ", " + decor.decor.count() + " already placed." : "."));
    }

    @Override
    public void onDisable() {
        if (decor == null) return;
        if (getServer().isStopping()) return;   // displays vanish with the server anyway
        decor.decor.removeAllDisplays();
        decor.decor.unregisterRecipes();
    }

    XmasDecor decor() {
        return decor;
    }

    void reload() {
        reloadConfig();
        decor.decor.unregisterRecipes();
        decor.decor.registerRecipes();
    }
}
