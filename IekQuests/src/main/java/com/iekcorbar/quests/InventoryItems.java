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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight ItemJoin replacement for IekQuests: places custom items in the
 * player's inventory on join/respawn and triggers actions when clicked.
 * Each item is identified by a PDC key so clicks can be matched reliably.
 */
final class InventoryItems implements Listener {
    private final IekQuestsPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, ItemDef> items = new HashMap<>();

    record ItemDef(String id, Material material, int slot, String name, String action, String quest, boolean cancel) {}

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
            items.put(key, new ItemDef(key, mat, slot, name, action, quest, cancel));
        }
        plugin.getLogger().info("Loaded " + items.size() + " inventory items.");
    }

    /** Place all items in the player's inventory. Called on join/respawn. */
    void give(Player p) {
        for (ItemDef def : items.values()) {
            if (def.slot() < 0) continue;
            ItemStack it = new ItemStack(def.material());
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(def.name().replace("&", "\u00a7"));
                m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, def.id());
                it.setItemMeta(m);
            }
            // Place in the correct slot (support CRAFTING slots via offset)
            int slot = def.slot();
            if (slot >= 0 && slot < p.getInventory().getSize()) {
                p.getInventory().setItem(slot, it);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        // Delay by a few ticks so the inventory is ready
        Bukkit.getScheduler().runTaskLater(plugin, () -> give(e.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> give(e.getPlayer()), 5L);
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
        // Trigger the action
        String action = def.action();
        if (action.startsWith("quest:")) {
            String[] parts = action.split(":", 3);
            if (parts.length >= 3) {
                plugin.triggerQuestTag(p, parts[1], parts[2]);
            }
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
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
    }

    /** Remove all inventory items from a player (on quit or reset). */
    void remove(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || it.getType().isAir()) continue;
            ItemMeta m = it.getItemMeta();
            if (m == null) continue;
            if (m.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                p.getInventory().setItem(i, null);
            }
        }
    }
}
