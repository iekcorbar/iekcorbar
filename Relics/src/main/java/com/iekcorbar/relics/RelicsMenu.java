package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Browse window with every relic (Zukan style). Rows 0-1: tabs (all, one per collection, favourites), rows 2-4: the grid
 * (27 per page), row 5: paging, filters, search and progress. Everybody can browse, only relics.give can take.
 * Q on a relic = favourite, shift-right-click = preview. Collection progress and favourites live in the player's PDC.
 */
final class RelicsMenu implements Listener {
    private static final int GRID = 18, PAGE = 27, FAV_SLOT = 17;
    private final RelicsPlugin plugin;
    private final Relics relics;
    private final NamespacedKey ownedKey, favKey;
    private final Map<UUID, Object[]> searching = new HashMap<>();   // player -> {tab, favOnly, craftOnly}
    private final Map<UUID, Set<String>> ownedCache = new HashMap<>();
    private Map<String, List<String>> crateInfo = new HashMap<>();
    private long crateStamp = -1;

    RelicsMenu(RelicsPlugin plugin, Relics relics) {
        this.plugin = plugin;
        this.relics = relics;
        this.ownedKey = new NamespacedKey(plugin, "owned");
        this.favKey = new NamespacedKey(plugin, "favs");
        // every 10 s: remember which relics each online player has ever carried (the collection progress in this menu)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) for (ItemStack it : p.getInventory().getContents()) {
                RelicType t = relics.typeOf(it);
                if (t != null) mark(p, t);
            }
        }, 200L, 200L);
    }

    // ------------------------------------------------------------ player data

    private Set<String> read(Player p, NamespacedKey key, Map<UUID, Set<String>> cache) {
        if (cache != null && cache.containsKey(p.getUniqueId())) return cache.get(p.getUniqueId());
        String s = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<>();
        if (s != null && !s.isEmpty()) out.addAll(List.of(s.split(",")));
        if (cache != null) cache.put(p.getUniqueId(), out);
        return out;
    }

    Set<String> owned(Player p) { return read(p, ownedKey, ownedCache); }

    private Set<String> favs(Player p) { return read(p, favKey, null); }

    void mark(Player p, RelicType t) {
        Set<String> o = owned(p);
        if (!o.add(t.id())) return;
        p.getPersistentDataContainer().set(ownedKey, PersistentDataType.STRING, String.join(",", o));
        for (RelicType x : RelicType.values()) if (x.collection == t.collection && !o.contains(x.id())) return;
        plugin.adv().grant(p, "full_" + t.collection.id());
        p.sendMessage(Component.text("Collection complete: " + t.collection.title + "!", NamedTextColor.GOLD));
    }

    private void toggleFav(Player p, RelicType t) {
        Set<String> f = favs(p);
        if (!f.remove(t.id())) f.add(t.id());
        p.getPersistentDataContainer().set(favKey, PersistentDataType.STRING, String.join(",", f));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ownedCache.remove(e.getPlayer().getUniqueId());
        searching.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ crates: "in which shop kit does this relic sit"

    private Map<String, List<String>> crates() {
        File f = new File(plugin.getDataFolder(), "crates.yml");
        long stamp = f.exists() ? f.lastModified() : 0;
        if (stamp == crateStamp) return crateInfo;
        crateStamp = stamp;
        Map<String, List<String>> out = new HashMap<>();
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String crate : y.getKeys(false)) {
                String title = y.getString(crate + ".title", crate);
                for (String entry : y.getStringList(crate + ".items")) {
                    String[] p = entry.split("#")[0].trim().split(":");
                    for (int i = 0; i < p.length; i++) {
                        if (!p[i].equals("relic") && !p[i].equals("random")) continue;
                        if (p[i].equals("relic") && i + 1 < p.length) out.computeIfAbsent(p[i + 1], k -> new ArrayList<>()).add(title);
                        else if (p[i].equals("random") && i + 1 < p.length && p[i + 1].equals("relic")) {
                            RelicType.Collection c = i + 2 < p.length ? RelicType.Collection.byId(p[i + 2]) : null;
                            for (RelicType t : RelicType.values()) if (!plugin.craftable(t) && (c == null || t.collection == c))
                                out.computeIfAbsent(t.id(), k -> new ArrayList<>()).add(title + " (random)");
                        }
                    }
                }
            }
        }
        crateInfo = out;
        return out;
    }

    // ------------------------------------------------------------ window

    private static final class Holder implements InventoryHolder {
        RelicType.Collection tab;   // null = all
        boolean favOnly, craftOnly;
        String search = "";
        int page;
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void open(Player p) { open(p, null, false, 0, "", false); }

    private void open(Player p, RelicType.Collection tab, boolean favOnly, int page, String search, boolean craftOnly) {
        Holder h = new Holder();
        h.tab = tab;
        h.favOnly = favOnly;
        h.search = search;
        h.craftOnly = craftOnly;
        Set<String> have = owned(p), fav = favs(p);
        Map<String, List<String>> crate = crates();
        List<RelicType> list = new ArrayList<>();
        for (RelicType t : RelicType.values()) {
            if (tab != null && t.collection != tab) continue;
            if (favOnly && !fav.contains(t.id())) continue;
            if (craftOnly && !plugin.craftable(t)) continue;
            if (!search.isEmpty() && !t.displayName.toLowerCase(Locale.ROOT).contains(search) && !t.id().contains(search)) continue;
            list.add(t);
        }
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        String title = favOnly ? "Favourites" : tab == null ? "Relics" : "Relics - " + tab.title;
        h.inv = Bukkit.createInventory(h, 54, Component.text(title + (pages > 1 ? "  (" + (h.page + 1) + "/" + pages + ")" : ""), NamedTextColor.DARK_GRAY));
        Inventory inv = h.inv;
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(0, tabItem(Material.BOOKSHELF, "All relics", RelicType.values().length + " relics", tab == null && !favOnly));
        RelicType.Collection[] cols = RelicType.Collection.values();
        for (int i = 0; i < cols.length && i < 16; i++) {
            RelicType first = null;
            int got = 0, total = 0;
            for (RelicType t : RelicType.values()) if (t.collection == cols[i]) {
                if (first == null) first = t;
                total++;
                if (have.contains(t.id())) got++;
            }
            ItemStack icon = first != null ? relics.item(first, 1) : new ItemStack(Material.PAPER);
            ItemMeta m = icon.getItemMeta();
            boolean sel = tab == cols[i] && !favOnly;
            m.displayName(Component.text(cols[i].title, sel ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text(got + " / " + total + " collected", got == total ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            if (sel) m.setEnchantmentGlintOverride(true);
            icon.setItemMeta(m);
            inv.setItem(i + 1, icon);
        }
        inv.setItem(FAV_SLOT, tabItem(Material.AMETHYST_SHARD, "Favourites", fav.size() + " marked", favOnly));

        boolean give = p.hasPermission("relics.give");
        for (int i = 0; i < PAGE; i++) {
            int idx = h.page * PAGE + i;
            if (idx >= list.size()) { inv.setItem(GRID + i, null); continue; }
            RelicType t = list.get(idx);
            ItemStack it = relics.item(t, 1);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.<Component>of() : m.lore());
            lore.add(Component.empty());
            lore.add(line(have.contains(t.id()) ? "Collected" : "Not collected yet", have.contains(t.id()) ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            lore.add(line(plugin.craftable(t) ? "Craftable (recipe book)" : "Shop / event / staff only", plugin.craftable(t) ? NamedTextColor.GREEN : NamedTextColor.RED));
            List<String> in = crate.get(t.id());
            if (in != null) lore.add(line("In crate: " + String.join(", ", new LinkedHashSet<>(in)), NamedTextColor.AQUA));
            if (fav.contains(t.id())) {
                lore.add(line("Favourite", NamedTextColor.LIGHT_PURPLE));
                m.setEnchantmentGlintOverride(true);
            }
            if (give) lore.add(line("Click: take 1  -  Shift-click: take 16", NamedTextColor.DARK_GRAY));
            lore.add(line("Q: favourite  -  Shift-right-click: preview", NamedTextColor.DARK_GRAY));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(GRID + i, it);
        }

        inv.setItem(45, h.page > 0 ? named(Material.ARROW, "Previous page") : pane);
        inv.setItem(46, named(craftOnly ? Material.CRAFTING_TABLE : Material.BARRIER, craftOnly ? "Showing: craftable only" : "Showing: everything"));
        inv.setItem(47, named(Material.PAPER, give ? "Browse and take relics" : "Browse the relics (staff hands them out)"));
        ItemStack find = named(Material.COMPASS, search.isEmpty() ? "Search by name" : "Search: " + search);
        ItemMeta fm = find.getItemMeta();
        fm.lore(List.of(line(search.isEmpty() ? "Click, then type in chat" : "Right-click to clear", NamedTextColor.GRAY)));
        find.setItemMeta(fm);
        inv.setItem(48, find);
        inv.setItem(49, named(Material.BARRIER, "Close"));
        int allGot = 0;
        for (RelicType x : RelicType.values()) if (have.contains(x.id())) allGot++;
        inv.setItem(50, named(Material.NETHER_STAR, "Collected " + allGot + " / " + RelicType.values().length));
        inv.setItem(53, h.page < pages - 1 ? named(Material.ARROW, "Next page") : pane);
        p.openInventory(inv);
    }

    private static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack tabItem(Material mat, String name, String sub, boolean sel) {
        ItemStack it = named(mat, name);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(line(sub, NamedTextColor.GRAY)));
        if (sel) {
            m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            m.setEnchantmentGlintOverride(true);
        }
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

    // ------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getRawSlot();
        RelicType.Collection[] cols = RelicType.Collection.values();
        if (s == 0) { click(p); open(p, null, false, 0, h.search, h.craftOnly); }
        else if (s >= 1 && s <= 16 && s - 1 < cols.length) { click(p); open(p, cols[s - 1], false, 0, h.search, h.craftOnly); }
        else if (s == FAV_SLOT) { click(p); open(p, null, true, 0, h.search, h.craftOnly); }
        else if (s == 45 && h.page > 0) { click(p); open(p, h.tab, h.favOnly, h.page - 1, h.search, h.craftOnly); }
        else if (s == 53 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { click(p); open(p, h.tab, h.favOnly, h.page + 1, h.search, h.craftOnly); }
        else if (s == 46) { click(p); open(p, h.tab, h.favOnly, 0, h.search, !h.craftOnly); }
        else if (s == 48) {
            click(p);
            if (e.isRightClick() && !h.search.isEmpty()) { open(p, h.tab, h.favOnly, 0, "", h.craftOnly); return; }
            searching.put(p.getUniqueId(), new Object[]{h.tab, h.favOnly, h.craftOnly});
            p.closeInventory();
            p.sendMessage(Component.text("Type part of a relic name in chat (cancel to go back).", NamedTextColor.GOLD));
        }
        else if (s == 49) p.closeInventory();
        else if (s >= GRID && s < GRID + PAGE) {
            RelicType t = relics.typeOf(e.getCurrentItem());
            if (t == null) return;
            if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
                toggleFav(p, t);
                click(p);
                open(p, h.tab, h.favOnly, h.page, h.search, h.craftOnly);
                return;
            }
            if (e.isShiftClick() && e.isRightClick()) {
                p.closeInventory();
                relics.preview(p, t);
                return;
            }
            if (!p.hasPermission("relics.give")) return;
            p.getInventory().addItem(relics.item(t, e.isShiftClick() ? 16 : 1)).values()
                    .forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Object[] st = searching.remove(e.getPlayer().getUniqueId());
        if (st == null) return;
        e.setCancelled(true);
        String q = e.getMessage().trim().toLowerCase(Locale.ROOT);
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> open(p, (RelicType.Collection) st[0], (Boolean) st[1], 0, q.equals("cancel") ? "" : q, (Boolean) st[2]));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f); }
}
