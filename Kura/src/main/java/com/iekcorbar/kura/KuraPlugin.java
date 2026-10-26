package com.iekcorbar.kura;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Kura: a storage network. An Inventory Connector joins the chests touching it,
 * cables carry the network further, and a Storage/Crafting Terminal shows every
 * item in it. Inventory Hoppers, Interfaces and Open Crates move items in and out
 * (also for Steamworks belts and arms, which see the Interface as a normal barrel),
 * Level Emitters give redstone signals, and wireless terminals open it from afar.
 * Behaviour modelled on the MIT-licensed Tom's Simple Storage mod; own code and models.
 */
public final class KuraPlugin extends JavaPlugin implements Listener {
    private ZukanData zukanData;
    /** Our own protection probe - ignored by our break handlers. */
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    long tick;
    private Items items;
    private Parts parts;
    private Networks networks;
    private Menus menus;
    private Couriers couriers;
    private Adv adv;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        parts = new Parts(this);
        networks = new Networks(this);
        menus = new Menus(this);
        items.registerRecipes();
        adv = Achievements.create(this, items);
        adv.start();
        zukanData = new ZukanData(this);
        getServer().getServicesManager().register(java.util.function.Supplier.class, zukanData, this, ServicePriority.Normal); // recipe viewer (Zukan)
        var pm = getServer().getPluginManager();
        pm.registerEvents(parts, this);
        // items sold by wandering traders (WanderingTrades hands out a plain paper, see TraderSwap)
        pm.registerEvents(new TraderSwap(this, "Kura", name -> {
            for (KItem t : KItem.values()) if (t.displayName.equals(name)) return items.create(t, 1);
            return null;
        }), this);
        pm.registerEvents(networks, this);
        pm.registerEvents(menus, this);
        couriers = new Couriers(this);
        pm.registerEvents(couriers, this);
        pm.registerEvents(this, this);
        KuraCommand cmd = new KuraCommand(this);
        getCommand("kura").setExecutor(cmd);
        getCommand("kura").setTabCompleter(cmd);

        // for the recipe viewer (plain JDK types, looked up by plugin name - no shared API jar):
        // fill the open Crafting Terminal's grid with a recipe, and go back to the open terminal
        BiPredicate<Player, List<List<ItemStack>>> fill = (p, cells) -> menus.fillGrid(p, cells);
        Predicate<Player> reopen = p -> menus.reopen(p);
        getServer().getServicesManager().register(BiPredicate.class, fill, this, ServicePriority.Normal);
        getServer().getServicesManager().register(Predicate.class, reopen, this, ServicePriority.Normal);

        getServer().getScheduler().runTask(this, () -> {
            couriers.loadAll();
            for (World w : getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getPersistentDataContainer().has(parts.displayTag(), PersistentDataType.BYTE)) e.remove();
                }
                for (Chunk c : w.getLoadedChunks()) parts.loadChunk(c);
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 10;
            parts.tick();
            if (tick % 20 == 0) {
                menus.tick();
                couriers.tick(tick);
            }
        }, 10L, 10L);
        getLogger().info("Kura enabled. Guide: " + docsUrl());
    }

    @Override
    public void onDisable() {
        if (parts == null) return;
        menus.closeAll();
        parts.saveAll(true);
        if (!getServer().isStopping()) {
            parts.removeAllDisplays();
            items.unregisterRecipes();
        }
        for (World w : getServer().getWorlds()) w.removePluginChunkTickets(this);
    }

    void reload() {
        reloadConfig();
        parts.load();
        networks.load();
        items.registerRecipes();
        getServer().getServicesManager().unregister(java.util.function.Supplier.class, zukanData);
        zukanData = new ZukanData(this);
        getServer().getServicesManager().register(java.util.function.Supplier.class, zukanData, this, ServicePriority.Normal); // recipe viewer (Zukan)
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        parts.unloadChunk(e.getChunk());
    }

    /** Whether the player may break here (asks protection plugins with a probe event). */
    boolean canBreak(Player p, Block b) {
        ProbeBreakEvent ev = new ProbeBreakEvent(b, p);
        getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** The recipe viewer's "open recipes for this search" hook (Zukan plugin), or null. */
    @SuppressWarnings("unchecked")
    BiConsumer<Player, String> recipeViewer() {
        for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(BiConsumer.class)) {
            if (r.getPlugin().getName().equals("Zukan")) return (BiConsumer<Player, String>) r.getProvider();
        }
        return null;
    }

    String docsUrl() {
        return getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=kura");
    }

    Items items() { return items; }

    Adv adv() { return adv; }
    Parts parts() { return parts; }
    Networks networks() { return networks; }
    Menus menus() { return menus; }
}
