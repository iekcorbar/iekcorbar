package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** /wildlife: a chest menu with every species (found or not), your totals and shortcuts to the journal, dragons and zoo help. */
final class WildlifeMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inv;
        final List<Species> slots = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int STATS = 45, JOURNAL = 48, DRAGONS = 50, ZOO = 52;
    private final WildlifePlugin plugin;

    WildlifeMenu(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Component.text("Wildlife"));
        Set<String> seen = plugin.journal().seen(p, false), rares = plugin.journal().seen(p, true);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 36; i < 54; i++) h.inv.setItem(i, pane);
        for (Species s : Species.values()) {
            if (!s.animal() || h.slots.size() >= 36) continue;
            int slot = h.slots.size();
            h.slots.add(s);
            if (seen.contains(s.id())) {
                Material m = Material.matchMaterial(s.icon);
                boolean rare = rares.contains(s.id());
                h.inv.setItem(slot, item(m == null ? Material.PAPER : m, s.displayName, rare ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN,
                        s.notes, "",
                        "Lives in: " + String.join(", ", s.biomes).replace('_', ' '),
                        "Active: " + (s.time == Species.Time.DAY ? "by day" : s.time == Species.Time.NIGHT ? "at night" : "day and night"),
                        rare ? "Rare look: " + s.rareName() + " ✔" : "Rare look: ???", "",
                        "Click: point me to the nearest one"));
            } else {
                h.inv.setItem(slot, item(Material.GRAY_DYE, "???", NamedTextColor.GRAY, "Not yet recorded.", "Hint: " + s.hint));
            }
        }
        h.inv.setItem(STATS, item(Material.PAPER, "Your records", NamedTextColor.GOLD,
                "Species: " + seen.size() + " / " + Species.journalCount(),
                "Rare looks: " + rares.size() + " / " + Species.journalCount()));
        h.inv.setItem(JOURNAL, item(Material.WRITABLE_BOOK, "Field Journal", NamedTextColor.GREEN, "Click: read the full journal book."));
        h.inv.setItem(DRAGONS, item(Material.COAL, "My dragons", NamedTextColor.GOLD, "Click: list your tamed Steam Dragons."));
        h.inv.setItem(ZOO, item(Material.LEAD, "Zoo", NamedTextColor.AQUA, "Click: how to build your own zoo."));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory() || !(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot();
        if (slot == JOURNAL) {
            p.closeInventory();
            plugin.journal().open(p);
        } else if (slot == DRAGONS) {
            p.closeInventory();
            p.performCommand("wildlife tamed");
        } else if (slot == ZOO) {
            p.closeInventory();
            p.performCommand("zoo");
        } else if (slot >= 0 && slot < h.slots.size()) {
            Species s = h.slots.get(slot);
            if (plugin.journal().seen(p, false).contains(s.id())) {
                p.closeInventory();
                p.performCommand("wildlife find " + s.id());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private static ItemStack item(Material m, String name, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(name, c).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Component.text(x, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }
}
