package com.iekcorbar.wildlife;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wildlife: new animals with FreeMinecraftModels models, added next to the
 * vanilla ones (never replacing them), spawning per biome and scarce on
 * purpose; a Field Journal filled by watching them through a spyglass, and a
 * Wildlife advancement tab. Inspired by the MIT-licensed Naturalist mod; own
 * models (tools/make_animals.py) and code.
 */
public final class WildlifePlugin extends JavaPlugin {
    long tick;
    private Fmm fmm;
    private Animals animals;
    private DragonAI dragon;
    private Spawner spawner;
    private Journal journal;
    private Adv adv;
    private Adv zooAdv;
    private WildItems items;
    private Ambience ambience;
    private Zoo zoo;
    private Molehills molehills;
    private Rescue rescue;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fmm = new Fmm(getLogger());
        molehills = new Molehills(this);
        rescue = new Rescue(this);
        animals = new Animals(this);
        dragon = new DragonAI(this);
        spawner = new Spawner(this);
        journal = new Journal(this);
        items = new WildItems(this);
        ambience = new Ambience(this);
        zoo = new Zoo(this);
        adv = Achievements.create(this);
        zooAdv = ZooAchievements.create(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(animals, this);
        pm.registerEvents(dragon, this);
        pm.registerEvents(journal, this);
        pm.registerEvents(items, this);
        pm.registerEvents(molehills, this);
        pm.registerEvents(rescue, this);
        items.registerRecipes();
        pm.registerEvents(zoo.decor, this);
        pm.registerEvents(zoo, this);
        zoo.decor.registerRecipes();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        WildlifeCommand cmd = new WildlifeCommand(this);
        getCommand("wildlife").setExecutor(cmd);
        getCommand("wildlife").setTabCompleter(cmd);
        ZooCommand zc = new ZooCommand(this);
        getCommand("zoo").setExecutor(zc);
        getCommand("zoo").setTabCompleter(zc);

        getServer().getScheduler().runTask(this, () -> {
            journal.registerAdvancements();
            adv.start();
            zooAdv.start();
            for (World w : getServer().getWorlds()) {
                for (Entity e : w.getEntities())
                    if (e instanceof LivingEntity le && e.getPersistentDataContainer().has(animals.speciesKey, PersistentDataType.STRING)) animals.register(le);
                for (org.bukkit.Chunk c : w.getLoadedChunks()) molehills.loadChunk(c);   // existing molehills in already-loaded chunks
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 5;
            journal.tick();
            if (tick % 10 == 0) {
                animals.tick(tick);
                zoo.decor.tick(tick);
            }
            if (tick % 40 == 0) ambience.tick();
            if (tick % (20L * Math.max(1, getConfig().getInt("spawning.interval-seconds", 10))) == 0) spawner.tick();
            if (tick % 20 == 0) for (Player p : getServer().getOnlinePlayers()) rescue.trySpawn(p, java.util.concurrent.ThreadLocalRandom.current());
        }, 5L, 5L);
        getServer().getScheduler().runTaskTimer(this, dragon::tick, 20L, 1L);   // riding and staying want every tick
        getLogger().info("Wildlife enabled with " + Species.values().length + " species.");
    }

    @Override
    public void onDisable() {
        if (animals != null) animals.removeAllUnnamed();
        if (molehills != null && !getServer().isStopping()) molehills.removeAll();
        if (zoo != null && !getServer().isStopping()) { // props and recipes vanish with the server anyway
            zoo.decor.removeAllDisplays();
            zoo.decor.unregisterRecipes();
        }
    }

    void reload() {
        reloadConfig();
        spawner.load();
        ambience.load();
    }

    Zoo zoo() { return zoo; }
    Adv adv() { return adv; }
    Adv zooAdv() { return zooAdv; }
    Fmm fmm() { return fmm; }
    Animals animals() { return animals; }
    DragonAI dragon() { return dragon; }
    Molehills molehills() { return molehills; }
    Spawner spawner() { return spawner; }
    Journal journal() { return journal; }
    WildItems items() { return items; }
    Rescue rescue() { return rescue; }
}
