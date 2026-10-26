package com.iekcorbar.zukan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Zukan - recipe and item browser (JEI-style) for vanilla and all our plugins. One chest GUI:
 * item list with search, recipes and uses per item, bookmarks, cheat mode for staff, and a
 * link with Kura's Crafting Terminal. The catalog is built once, shortly after the server
 * has started (every plugin has registered its recipes by then) and on /zukan reload.
 */
public final class ZukanPlugin extends JavaPlugin implements Listener {
    private Catalog catalog;
    private Gui gui;
    private Adv adv;
    private NamespacedKey bookKey;
    private boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bookKey = new NamespacedKey(this, "book");
        catalog = new Catalog(this);
        adv = Achievements.create(this);
        adv.start();
        gui = new Gui(this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(this, this);
        ZukanCommand cmd = new ZukanCommand(this);
        getCommand("zukan").setExecutor(cmd);
        getCommand("zukan").setTabCompleter(cmd);
        // Kura's Recipes button: open the list with a search text, coming from the terminal
        BiConsumer<Player, String> fromKura = (p, query) -> {
            if (ready) gui.openList(p, query, true);
        };
        getServer().getServicesManager().register(BiConsumer.class, fromKura, this, ServicePriority.Normal);
        if (getConfig().getBoolean("book-recipe", true)) registerBookRecipe();
        // a short while after startup: all plugins have registered their recipes and services
        Bukkit.getScheduler().runTaskLater(this, this::rebuild, 40L);
        // again after a minute and a half: other plugins' item models (FreeMinecraftModels) are ready by then
        Bukkit.getScheduler().runTaskLater(this, this::rebuild, 1800L);
        Bukkit.getScheduler().runTaskTimer(this, () -> gui.tick(), 20L, 20L);
        getLogger().info("Zukan enabled. Guide: " + getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=zukan"));
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.closeAll();
        Bukkit.removeRecipe(new NamespacedKey(this, "zukan_book"), false);
    }

    // ------------------------------------------------------------ catalog

    Catalog catalog() {
        return catalog;
    }

    boolean ready() {
        return ready;
    }

    /** (Re)builds the catalog from vanilla and the provider plugins; closes open browsers first. */
    void rebuild() {
        gui.closeAll();
        long t = System.currentTimeMillis();
        try {
            catalog.build();
            ready = true;
            getLogger().info("Catalog built in " + (System.currentTimeMillis() - t) + " ms.");
        } catch (Throwable e) {
            getLogger().severe("Could not build the catalog: " + e);
        }
    }

    void reloadAll() {
        reloadConfig();
        rebuild();
    }

    Adv adv() {
        return adv;
    }

    boolean isBookItem(ItemStack it) {
        return isBook(it);
    }

    Gui gui() {
        return gui;
    }

    // ------------------------------------------------------------ Kura hooks

    @SuppressWarnings("unchecked")
    BiPredicate<Player, List<List<ItemStack>>> kuraFill() {
        for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(BiPredicate.class)) {
            if (r.getPlugin().getName().equals("Kura")) return (BiPredicate<Player, List<List<ItemStack>>>) r.getProvider();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Predicate<Player> kuraReopen() {
        for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(Predicate.class)) {
            if (r.getPlugin().getName().equals("Kura")) return (Predicate<Player>) r.getProvider();
        }
        return null;
    }

    // ------------------------------------------------------------ the book

    ItemStack book() {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Zukan", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Right-click: every item and recipe.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private void registerBookRecipe() {
        ShapelessRecipe r = new ShapelessRecipe(new NamespacedKey(this, "zukan_book"), book());
        r.addIngredient(Material.BOOK);
        r.addIngredient(Material.COMPASS);
        Bukkit.addRecipe(r);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.BOOK || !it.hasItemMeta()) return;
        if (!it.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE)) {
            if (!fromTrader(it)) return;
            // sold by a wandering trader (WanderingTrades cannot set PDC): swap it for the real book on first use
            ItemStack real = book();
            real.setAmount(it.getAmount());
            e.getPlayer().getInventory().setItemInMainHand(real);
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("zukan.use")) return;
        open(p, "");
    }

    /** F (swap hands) with the Zukan book in a hand opens the browser instead. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (!getConfig().getBoolean("swap-key", true)) return;
        if (!isBook(e.getMainHandItem()) && !isBook(e.getOffHandItem())) return;
        e.setCancelled(true);
        if (e.getPlayer().hasPermission("zukan.use")) open(e.getPlayer(), "");
    }

    /** The plain book of wanderingtrades/trades/zukan.yml: named "Zukan", last lore line "Zukan recipe browser". */
    private boolean fromTrader(ItemStack it) {
        var m = it.getItemMeta();
        var lore = m.lore();
        if (lore == null || lore.isEmpty() || m.displayName() == null) return false;
        var ser = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        return ser.serialize(m.displayName()).equals("Zukan") && ser.serialize(lore.get(lore.size() - 1)).equals("Zukan recipe browser");
    }

    private boolean isBook(ItemStack it) {
        return it != null && it.getType() == Material.BOOK && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }

    void open(Player p, String query) {
        if (!ready) {
            p.sendMessage(Component.text("Zukan is still loading, try again in a moment.", NamedTextColor.RED));
            return;
        }
        gui.openList(p, query, false);
    }
}
