package com.iekcorbar.kitchen;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.function.BiFunction;

/**
 * Kitchen: Japanese farming and cooking. Crops as structure-void markers with their type and
 * stage in chunk data (ItemDisplay shows the plant), cutting board + knives, cooking pot,
 * kamado stove, storage and decorative props, and meals you put down and eat bite by bite.
 * Works alone; with Steamworks its flour counts as wheat, and Steamworks recipes can ask for
 * Kitchen items through the item bridge registered here.
 */
public final class KitchenPlugin extends JavaPlugin implements Listener {
    /** Our own protection probe - ignored by our break handlers. */
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    long tick;
    private Items items;
    private Adv adv;
    private Crops crops;
    private Props props;
    private Meals meals;
    private Recipes recipes;
    private KitchenDecor kitchenDecor;
    private Competition competition;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "kitchen.yml").exists()) saveResource("kitchen.yml", false);
        items = new Items(this);
        recipes = new Recipes(this);
        crops = new Crops(this);
        props = new Props(this);
        meals = new Meals(this);
        kitchenDecor = new KitchenDecor(this);
        competition = new Competition(this);
        items.registerRecipes();
        adv = Achievements.create(this, items);
        adv.start();
        kitchenDecor.decor.registerRecipes();
        var pm = getServer().getPluginManager();
        pm.registerEvents(items, this);
        // items sold by wandering traders (WanderingTrades hands out a plain paper, see TraderSwap)
        pm.registerEvents(new TraderSwap(this, "Kitchen", name -> {
            for (KItem t : KItem.values()) if (t.displayName.equals(name)) return items.create(t, 1);
            return null;
        }), this);
        pm.registerEvents(crops, this);
        pm.registerEvents(props, this);
        pm.registerEvents(meals, this);
        pm.registerEvents(kitchenDecor.decor, this);
        pm.registerEvents(this, this);
        KitchenCommand cmd = new KitchenCommand(this);
        getCommand("kitchen").setExecutor(cmd);
        getCommand("kitchen").setTabCompleter(cmd);

        // item bridge for other plugins (Steamworks recipes: kitchen:<id>) - a plain JDK type, so no shared API jar
        BiFunction<String, Integer, ItemStack> bridge = (id, n) -> {
            KItem t = KItem.byId(id);
            return t == null ? null : items.create(t, n == null ? 1 : n);
        };
        getServer().getServicesManager().register(BiFunction.class, bridge, this, ServicePriority.Normal);
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, ServicePriority.Normal); // recipe viewer (Zukan)

        // recipes of other plugins are registered by now: read vanilla campfire recipes etc. one tick later
        getServer().getScheduler().runTask(this, () -> {
            recipes.load();
            for (World w : getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    var pdc = e.getPersistentDataContainer();
                    if (pdc.has(new org.bukkit.NamespacedKey(this, "display"), PersistentDataType.BYTE)
                            || pdc.has(new org.bukkit.NamespacedKey(this, "meal"), PersistentDataType.BYTE)) e.remove();
                }
                for (Chunk c : w.getLoadedChunks()) {
                    crops.loadChunk(c);
                    props.loadChunk(c);
                    meals.loadChunk(c);
                    kitchenDecor.decor.loadChunk(c);
                }
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 10;
            props.tick();
            kitchenDecor.decor.tick(tick);
            if (tick % 20 == 0) crops.tick();
            if (tick % 600 == 0) meals.saveAll();
        }, 10L, 10L);
        getLogger().info("Kitchen enabled. Guide: " + docsUrl());
    }

    @Override
    public void onDisable() {
        if (props == null) return;
        crops.saveAll();
        props.saveAll(true);
        meals.saveAll();
        if (competition != null) competition.stop();
        if (getServer().isStopping()) return; // displays are not persistent and vanish with the server
        for (World w : getServer().getWorlds()) for (Chunk c : w.getLoadedChunks()) meals.unloadChunk(c);
        crops.removeAllDisplays();
        props.removeAllDisplays();
        meals.removeAllDisplays();
        kitchenDecor.decor.removeAllDisplays();
        kitchenDecor.decor.unregisterRecipes();
        items.unregisterRecipes();
    }

    void reload() {
        reloadConfig();
        crops.load();
        props.load();
        recipes.load();
        items.registerRecipes();
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        crops.unloadChunk(e.getChunk());
        props.unloadChunk(e.getChunk());
        meals.unloadChunk(e.getChunk());
    }

    /** Whether the player may break here (asks protection plugins with a probe event). */
    boolean canBreak(Player p, Block b) {
        ProbeBreakEvent ev = new ProbeBreakEvent(b, p);
        getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** Whether the player may build at {@code target} (probe place event, nothing changes). */
    boolean canBuild(Player p, Block target, Block against, ItemStack hand) {
        BlockState state = target.getState();
        BlockPlaceEvent ev = new BlockPlaceEvent(target, state, against, hand, p, true, EquipmentSlot.HAND);
        getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled() && ev.canBuild();
    }

    String docsUrl() {
        return getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=kitchen");
    }

    Adv adv() {
        return adv;
    }

    Items items() { return items; }
    KitchenDecor kitchenDecor() { return kitchenDecor; }
    Crops crops() { return crops; }
    Props props() { return props; }
    Meals meals() { return meals; }
    Recipes recipes() { return recipes; }
Competition competition() { return competition; }
}
