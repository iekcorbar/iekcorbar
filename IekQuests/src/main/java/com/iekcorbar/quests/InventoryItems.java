package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight ItemJoin replacement for IekQuests: places custom items in the
 * player's crafting grid (2x2) on inventory open and triggers actions when clicked.
 * Each item is identified by a PDC key so clicks can be matched reliably.
 */
final class InventoryItems implements Listener {
    private final IekQuestsPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, ItemDef> items = new HashMap<>();

    record ItemDef(String id, Material material, int slot, String name, String action, String quest, boolean cancel, String left, String right, String shiftRight,
                   java.util.List<String> lore, boolean skull) {}

    InventoryItems(IekQuestsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "inv_item");
        load();
    }

    void load() {
        items.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("inventory-items");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection one = sec.getConfigurationSection(key);
            if (one == null) continue;
            String matName = one.getString("material", "PAPER");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) { plugin.getLogger().warning("Unknown material for inventory item " + key + ": " + matName); continue; }
            int slot = one.getInt("slot", -1);
            String name = one.getString("name", key);
            String action = one.getString("action", "");
            String quest = one.getString("quest", "");
            boolean cancel = one.getBoolean("cancel", true);
            items.put(key, new ItemDef(key, mat, slot, name, action, quest, cancel,
                    one.getString("action-left", ""), one.getString("action-right", ""), one.getString("action-shift-right", ""),
                    one.getStringList("lore"), one.getBoolean("skull", false)));
        }
        plugin.getLogger().info("Loaded " + items.size() + " inventory items.");
    }

    private String papi(Player p, String text) {
        String t = text.replace("%player%", p.getName());
        try {
            Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) c.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class).invoke(null, p, t);
        } catch (Throwable ignored) {
            return t;
        }
    }

    private ItemStack build(ItemDef def, Player p) {
        ItemStack it = new ItemStack(def.material());
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(def.name().replace("&", "§"));
            if (!def.lore().isEmpty()) {
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String l : def.lore()) lore.add(papi(p, l).replace("&", "§"));
                m.setLore(lore);
            }
            m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            if (def.skull() && m instanceof org.bukkit.inventory.meta.SkullMeta sm) sm.setOwningPlayer(p);
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, def.id());
            it.setItemMeta(m);
        }
        return it;
    }

    private boolean isOurs(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    /** Vanilla rekent het resultaatvak (slot 0) bij elke rasterwijziging opnieuw uit: zet daar ons slot-0-item. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        if (e.getInventory().getType() != InventoryType.CRAFTING) return;
        boolean ours = false;
        for (ItemStack it : e.getInventory().getMatrix()) if (isOurs(it)) { ours = true; break; }
        if (!ours) return;
        ItemDef zero = null;
        for (ItemDef d : items.values()) if (d.slot() == 0) { zero = d; break; }
        e.getInventory().setResult(zero == null || !(e.getView().getPlayer() instanceof Player pl) ? null : build(zero, pl));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent e) {
        if (isOurs(e.getCurrentItem())) e.setCancelled(true);
    }

    /** Place all items in the crafting grid of the given inventory. */
    void placeInCrafting(Inventory inv, Player p) {
        if (inv == null || inv.getType() != InventoryType.CRAFTING) return;
        for (ItemDef def : items.values()) {
            if (def.slot() < 0) continue;
            int slot = def.slot();
            if (slot >= inv.getSize()) continue;
            ItemStack it = build(def, p);
            inv.setItem(slot, it);
        }
    }

    /** Remove all our items from an inventory. */
    void removeFromInventory(Inventory inv) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            ItemMeta m = it.getItemMeta();
            if (m == null) continue;
            if (m.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                inv.setItem(i, null);
            }
        }
    }

    /** Remove all inventory items from a player's main inventory (on quit or reset). */
    void remove(Player p) {
        removeFromInventory(p.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> placeInCrafting(top, p), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getInventory();
        if (top.getType() == InventoryType.CRAFTING) removeFromInventory(top);
        // het eigen inventory-scherm geeft server-side geen open-event, en na een menu (klik op een item)
        // sluit alleen dat menu: bij elke sluiting het eigen vak opnieuw vullen (placeFor checkt het type)
        schedulePlace(p, 2L);
    }

    /** Het eigen 2x2-vak bestaat altijd; vul het zonder open-event (client opent het zelf). */
    void placeFor(Player p) {
        if (!p.isOnline()) return;
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING) return;
        removeFromInventory(top);
        placeInCrafting(top, p);
        // de client wist het 2x2-vak zelf bij sluiten; de server-kant ziet dan geen verschil en zou niets sturen
        p.updateInventory();
        Bukkit.getScheduler().runTaskLater(plugin, p::updateInventory, 3L);
    }

    private void schedulePlace(Player p, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> placeFor(p), delay);
    }

    /** Alle spelers (na een /questbook reload of plugin-herstart). */
    void placeForAll() {
        for (Player p : Bukkit.getOnlinePlayers()) placeFor(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) { schedulePlace(e.getPlayer(), 10L); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) { schedulePlace(e.getPlayer(), 10L); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorld(PlayerChangedWorldEvent e) { schedulePlace(e.getPlayer(), 10L); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGameMode(PlayerGameModeChangeEvent e) { schedulePlace(e.getPlayer(), 5L); }

    /** Bij vertrek geen items in het vak laten (anders komen ze in de inventory terecht). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        removeFromInventory(e.getPlayer().getOpenInventory().getTopInventory());
        remove(e.getPlayer());
    }

    /** Menu-items nooit droppen bij de dood. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        removeFromInventory(e.getEntity().getOpenInventory().getTopInventory());
        e.getDrops().removeIf(it -> it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null) return;
        String id = m.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) return;
        ItemDef def = items.get(id);
        if (def == null) return;
        if (def.cancel()) e.setCancelled(true);
        // Trigger quest tag if defined
        if (!def.quest().isEmpty()) {
            String[] qparts = def.quest().split(":", 2);
            if (qparts.length == 2) {
                plugin.triggerQuestTag(p, qparts[0], qparts[1]);
            }
        }
        // Trigger the action (per click type when configured, otherwise the default action)
        String action = def.action();
        if (e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT && !def.shiftRight().isEmpty()) action = def.shiftRight();
        else if (e.isRightClick() && !def.right().isEmpty()) action = def.right();
        else if (e.isLeftClick() && !def.left().isEmpty()) action = def.left();
        runAction(p, action);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
    }

    private void runAction(Player p, String action) {
        if (action.startsWith("quest:")) {
            String[] parts = action.split(":", 3);
            if (parts.length >= 3) {
                plugin.triggerQuestTag(p, parts[1], parts[2]);
            }
        } else if (action.startsWith("player:")) {
            String cmd = action.substring("player:".length()).replace("%player%", p.getName());
            p.performCommand(cmd);
        } else if (action.startsWith("command:")) {
            String cmd = action.substring("command:".length());
            cmd = cmd.replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else if (action.startsWith("open:")) {
            String what = action.substring("open:".length());
            switch (what) {
                case "workbench" -> p.openWorkbench(null, true);
                case "profile" -> p.performCommand("profile");
                case "jobs" -> p.performCommand("jobs");
                case "gouw" -> p.performCommand("gouw");
                default -> p.sendMessage(Component.text("Unknown open target: " + what));
            }
        }
    }
}
