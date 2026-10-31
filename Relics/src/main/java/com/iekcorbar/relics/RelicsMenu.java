package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
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

/** Blader-venster met alle relics (Zukan-achtig): tabbladen per collectie, raster, pagina's. Staff pakt er items uit. */
final class RelicsMenu implements Listener {
    private static final int PAGE = 36, GRID = 9;
    private final RelicsPlugin plugin;
    private final Relics relics;

    RelicsMenu(RelicsPlugin plugin, Relics relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    private static final class Holder implements InventoryHolder {
        RelicType.Collection tab; // null = alles
        int page;
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void open(Player p) { open(p, null, 0); }

    private void open(Player p, RelicType.Collection tab, int page) {
        Holder h = new Holder();
        h.tab = tab;
        List<RelicType> list = new ArrayList<>();
        for (RelicType t : RelicType.values()) if (tab == null || t.collection == tab) list.add(t);
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        h.inv = org.bukkit.Bukkit.createInventory(h, 54, Component.text("Relics" + (tab == null ? "" : " - " + tab.title)
                + (pages > 1 ? "  (" + (h.page + 1) + "/" + pages + ")" : ""), NamedTextColor.DARK_GRAY));
        Inventory inv = h.inv;
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(0, tabItem(Material.BOOKSHELF, "All relics", RelicType.values().length, tab == null));
        RelicType.Collection[] cols = RelicType.Collection.values();
        for (int i = 0; i < cols.length && i < 8; i++) {
            RelicType first = null;
            for (RelicType t : RelicType.values()) if (t.collection == cols[i]) { first = t; break; }
            ItemStack icon = first != null ? relics.item(first, 1) : new ItemStack(Material.PAPER);
            ItemMeta m = icon.getItemMeta();
            m.displayName(Component.text(cols[i].title, tab == cols[i] ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text(cols[i].size() + " relics", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            if (tab == cols[i]) m.setEnchantmentGlintOverride(true);
            icon.setItemMeta(m);
            inv.setItem(i + 1, icon);
        }

        boolean give = p.hasPermission("relics.give");
        for (int i = 0; i < PAGE; i++) {
            int idx = h.page * PAGE + i;
            if (idx >= list.size()) { inv.setItem(GRID + i, null); continue; }
            RelicType t = list.get(idx);
            ItemStack it = relics.item(t, 1);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.<Component>of() : m.lore());
            lore.add(Component.empty());
            lore.add(Component.text(plugin.craftable(t) ? "Craftable (recipe book)" : "Shop / event / staff only",
                    plugin.craftable(t) ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            if (give) lore.add(Component.text("Click: take 1  -  Shift-click: take 16", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(GRID + i, it);
        }

        inv.setItem(45, h.page > 0 ? named(Material.ARROW, "Previous page") : pane);
        inv.setItem(49, named(Material.BARRIER, "Close"));
        inv.setItem(53, h.page < pages - 1 ? named(Material.ARROW, "Next page") : pane);
        inv.setItem(47, named(Material.PAPER, give ? "Browse and take relics" : "Browse the relics (staff hands them out)"));
        p.openInventory(inv);
    }

    private ItemStack tabItem(Material mat, String name, int count, boolean sel) {
        ItemStack it = named(mat, name);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(Component.text(count + " relics", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (sel) { m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)); m.setEnchantmentGlintOverride(true); }
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getRawSlot();
        RelicType.Collection[] cols = RelicType.Collection.values();
        if (s == 0) { click(p); open(p, null, 0); }
        else if (s >= 1 && s <= 8 && s - 1 < cols.length) { click(p); open(p, cols[s - 1], 0); }
        else if (s == 45 && h.page > 0) { click(p); open(p, h.tab, h.page - 1); }
        else if (s == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { click(p); open(p, h.tab, h.page + 1); }
        else if (s == 49) p.closeInventory();
        else if (s >= GRID && s < GRID + PAGE) {
            RelicType t = relics.typeOf(e.getCurrentItem());
            if (t == null || !p.hasPermission("relics.give")) return;
            p.getInventory().addItem(relics.item(t, e.isShiftClick() ? 16 : 1)).values()
                    .forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f); }
}
