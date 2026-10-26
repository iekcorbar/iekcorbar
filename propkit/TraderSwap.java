package __PKG__;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Shared "sold by a wandering trader" helper (propkit/TraderSwap.java, copied into each plugin by propkit/sync.py -
 * edit it only there). WanderingTrades cannot put data on an item, so a trade file hands out a plain PAPER whose name
 * is the item's name and whose LAST lore line is the plugin name (the marker). Items of ours always end with that
 * line too, so a plain one is told apart by having no persistent data of this plugin's namespace. It is swapped for
 * the real item (the lookup gets the name) when the trade is taken (merchant window clicks/closing), on a
 * right-click, and nowhere else - no timers, no inventory scans while nobody trades.
 */
final class TraderSwap implements Listener {
    private final JavaPlugin plugin;
    private final String marker;
    private final String namespace;
    private final Function<String, ItemStack> lookup;
    private final Set<UUID> pending = new HashSet<>();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    TraderSwap(JavaPlugin plugin, String marker, Function<String, ItemStack> lookup) {
        this.plugin = plugin;
        this.marker = marker;
        this.namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        this.lookup = lookup;
    }

    /** The real item for a plain trade item, or null when it is not one (already real, other plugin, other item). */
    ItemStack swapped(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        if (!m.hasDisplayName()) return null;
        List<Component> lore = m.lore();
        if (lore == null || lore.isEmpty() || !PLAIN.serialize(lore.get(lore.size() - 1)).equals(marker)) return null;
        for (var key : m.getPersistentDataContainer().getKeys()) if (key.getNamespace().equals(namespace)) return null;
        ItemStack real = lookup.apply(PLAIN.serialize(m.displayName()));
        if (real == null) return null;
        real.setAmount(Math.min(it.getAmount(), real.getMaxStackSize()));
        return real;
    }

    private void scan(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack real = swapped(inv.getItem(i));
            if (real != null) inv.setItem(i, real);
        }
    }

    private void later(Player p) {
        if (!pending.add(p.getUniqueId())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(p.getUniqueId());
            if (p.isOnline()) scan(p);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.MERCHANT && e.getWhoClicked() instanceof Player p) later(p);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getType() == InventoryType.MERCHANT && e.getPlayer() instanceof Player p) later(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack real = swapped(e.getItem());
        if (real == null) return;
        var inv = e.getPlayer().getInventory();
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) inv.setItemInOffHand(real);
        else inv.setItemInMainHand(real);
        e.setCancelled(true);
    }
}
