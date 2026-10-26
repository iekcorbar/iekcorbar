package com.iekcorbar.zukan;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** The browser GUI: item list, recipes, uses; bookmarks, search and cheat mode. */
final class Gui implements Listener {
    enum Mode { LIST, RECIPES, USES }

    /** One player's open browser. */
    static final class View implements InventoryHolder {
        final Player player;
        Inventory inv;
        Mode mode = Mode.LIST;
        String query = "", ns = "";
        boolean bookmarksOnly, cheat, fromKura, switching;
        int page, catIndex, recipeIndex, tabOffset, cycle;
        List<Catalog.Entry> filtered = new ArrayList<>();
        ItemStack focus;
        List<Recipe.Category> cats = new ArrayList<>();
        Map<Recipe.Category, List<Recipe>> byCat = new LinkedHashMap<>();
        final Deque<State> history = new ArrayDeque<>();
        final ItemStack[] slotItems = new ItemStack[54];
        final int[] tabAt = new int[54];

        View(Player p) {
            this.player = p;
            java.util.Arrays.fill(tabAt, -1);
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private record State(Mode mode, ItemStack focus, int catIndex, int recipeIndex, int page) { }

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] FREE_IN = {10, 11, 12, 13, 19, 20, 21, 22, 28, 29, 30, 31};
    private static final int[] FREE_OUT = {24, 25, 26, 33, 34, 35};
    private static final int PREV = 45, SEARCH = 46, CLEAR = 47, NS = 48, INFO = 49, MARKS = 50, CHEAT = 51, TERMINAL = 52, NEXT = 53;
    private static final int BACK = 45, HOME = 46, PREV_R = 47, COUNT = 48, NEXT_R = 49, MARK = 50, FILL = 52;

    private final ZukanPlugin plugin;
    private final Map<UUID, View> open = new HashMap<>();
    private final Map<UUID, View> searching = new HashMap<>();
    private final Map<UUID, AnvilView> searchViews = new HashMap<>();
    private final NamespacedKey marksKey;

    Gui(ZukanPlugin plugin) {
        this.plugin = plugin;
        marksKey = new NamespacedKey(plugin, "bookmarks");
    }

    // ------------------------------------------------------------ opening

    void openList(Player p, String query, boolean fromKura) {
        View v = new View(p);
        v.query = query == null ? "" : query;
        v.fromKura = fromKura;
        v.cheat = false;
        open.put(p.getUniqueId(), v);
        show(v);
        plugin.adv().grant(p, "opened");
        if (fromKura || !v.query.isEmpty()) plugin.adv().grant(p, "search");
    }

    void openRecipes(Player p, ItemStack item, boolean uses) {
        View v = new View(p);
        open.put(p.getUniqueId(), v);
        focus(v, item, uses);
        show(v);
        plugin.adv().grant(p, uses ? "uses" : "recipe");
    }

    private void show(View v) {
        v.switching = true;
        v.inv = Bukkit.createInventory(v, 54, steampunk() ? art(v) : Component.text(title(v)));
        draw(v);
        v.player.openInventory(v.inv);
        v.switching = false;
    }

    /** The steampunk look is a full-screen picture (resource pack font glyph) drawn through the window title. */
    private boolean steampunk() {
        return "steampunk".equalsIgnoreCase(plugin.getConfig().getString("gui-style", "steampunk"));
    }

    private static final net.kyori.adventure.key.Key ART_FONT = net.kyori.adventure.key.Key.key("zukan", "gui");

    private Component art(View v) {
        String glyph = v.mode == Mode.LIST ? "\ue000" : "\ue001";
        return Component.text("\uf800" + glyph).font(ART_FONT).color(NamedTextColor.WHITE)
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
    }

    private String title(View v) {
        if (v.mode == Mode.LIST) return "Zukan";
        String name = v.cats.isEmpty() ? "" : v.cats.get(Math.min(v.catIndex, v.cats.size() - 1)).name;
        return (v.mode == Mode.USES ? "Uses: " : "") + (name.isEmpty() ? "Zukan" : name);
    }

    void closeAll() {
        for (View v : new ArrayList<>(open.values())) v.player.closeInventory();
    }

    /** Every second: ingredient slots with several options cycle through them. */
    void tick() {
        for (View v : new ArrayList<>(open.values())) {
            if (v.mode == Mode.LIST || v.inv == null || v.player.getOpenInventory().getTopInventory() != v.inv) continue;
            v.cycle++;
            drawRecipe(v);
        }
    }

    // ------------------------------------------------------------ small items

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        it.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return it;
    }

    private static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(name, NamedTextColor.GOLD));
        List<Component> l = new ArrayList<>();
        for (String s : lore) if (!s.isEmpty()) l.add(line(s, NamedTextColor.GRAY));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    /** An item for display, with a hint line. */
    private ItemStack shown(ItemStack base, String... hints) {
        ItemStack it = base.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String h : hints) if (!h.isEmpty()) lore.add(line(h, NamedTextColor.DARK_GRAY));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String counts(ItemStack it) {
        String k = Catalog.keyOf(it);
        int r = plugin.catalog().recipesFor(k).size(), u = plugin.catalog().usesOf(k).size();
        return r + " recipe" + (r == 1 ? "" : "s") + ", " + u + " use" + (u == 1 ? "" : "s");
    }

    // ------------------------------------------------------------ bookmarks

    private List<ItemStack> marks(Player p) {
        String s = p.getPersistentDataContainer().get(marksKey, PersistentDataType.STRING);
        List<ItemStack> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        try {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s))) if (it != null && !it.isEmpty()) out.add(it);
        } catch (Exception ignored) {
            // unreadable list: start again
        }
        return out;
    }

    private void saveMarks(Player p, List<ItemStack> list) {
        if (list.isEmpty()) p.getPersistentDataContainer().remove(marksKey);
        else p.getPersistentDataContainer().set(marksKey, PersistentDataType.STRING, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(list)));
    }

    private boolean marked(Player p, ItemStack it) {
        String k = Catalog.keyOf(it);
        for (ItemStack m : marks(p)) if (Catalog.keyOf(m).equals(k)) return true;
        return false;
    }

    private void toggleMark(Player p, ItemStack it) {
        List<ItemStack> list = marks(p);
        String k = Catalog.keyOf(it);
        boolean removed = list.removeIf(m -> Catalog.keyOf(m).equals(k));
        if (!removed) {
            if (list.size() >= 45) {
                p.sendActionBar(Component.text("Bookmark list is full (45).", NamedTextColor.RED));
                return;
            }
            list.add(it.asOne());
        }
        saveMarks(p, list);
        p.sendActionBar(Component.text(removed ? "Bookmark removed." : "Bookmarked.", NamedTextColor.GREEN));
    }

    // ------------------------------------------------------------ drawing

    private void draw(View v) {
        java.util.Arrays.fill(v.slotItems, null);
        java.util.Arrays.fill(v.tabAt, -1);
        for (int i = 0; i < 54; i++) v.inv.setItem(i, null);
        if (v.mode == Mode.LIST) drawList(v);
        else drawRecipe(v);
    }

    private void refilter(View v) {
        v.filtered = new ArrayList<>();
        List<String> markKeys = new ArrayList<>();
        if (v.bookmarksOnly) for (ItemStack m : marks(v.player)) markKeys.add(Catalog.keyOf(m));
        String[] words = v.query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        for (Catalog.Entry e : plugin.catalog().entries) {
            if (v.bookmarksOnly && !markKeys.contains(e.key())) continue;
            if (!v.ns.isEmpty() && !e.ns().equals(v.ns)) continue;
            boolean ok = true;
            for (String w : words) {
                if (w.isEmpty()) continue;
                if (w.startsWith("#")) ok = inTag(e.stack().getType(), w.substring(1));
                else if (w.startsWith("@")) ok = e.ns().startsWith(w.substring(1));
                else ok = e.name().contains(w);
                if (!ok) break;
            }
            if (ok) v.filtered.add(e);
        }
    }

    private static boolean inTag(Material m, String part) {
        for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
            if (tag.getKey().getKey().contains(part) && tag.isTagged(m)) return true;
        }
        return false;
    }

    private void drawList(View v) {
        refilter(v);
        int pages = Math.max(1, (v.filtered.size() + 44) / 45);
        v.page = Math.max(0, Math.min(v.page, pages - 1));
        for (int i = 0; i < 45; i++) {
            int idx = v.page * 45 + i;
            if (idx >= v.filtered.size()) break;
            Catalog.Entry e = v.filtered.get(idx);
            v.slotItems[i] = e.stack();
            String hint = v.cheat ? "Click: take stack  Right: take one  Shift: recipes / uses" : "Click: recipes  Right: uses  Shift: bookmark";
            v.inv.setItem(i, shown(e.stack(), counts(e.stack()), hint));
        }
        if (!steampunk()) {
            ItemStack pane = pane();
            for (int s = 45; s < 54; s++) v.inv.setItem(s, pane);
        }
        if (v.page > 0) v.inv.setItem(PREV, button(Material.ARROW, "Previous page", "Page " + v.page + " of " + pages));
        if (v.page < pages - 1) v.inv.setItem(NEXT, button(Material.ARROW, "Next page", "Page " + (v.page + 2) + " of " + pages));
        v.inv.setItem(SEARCH, button(Material.COMPASS, v.query.isEmpty() ? "Search" : "Search: " + v.query,
                "Click to type.", "Words: item name.  #logs: item group.", "@steamworks: items of one plugin."));
        if (!v.query.isEmpty()) v.inv.setItem(CLEAR, button(Material.BARRIER, "Clear search"));
        v.inv.setItem(NS, button(Material.NAME_TAG, "Show: " + (v.ns.isEmpty() ? "everything" : v.ns), "Click to switch between", "vanilla and each plugin."));
        v.inv.setItem(INFO, button(Material.BOOK, "Zukan", v.filtered.size() + " items", plugin.catalog().recipeCount + " recipes known"));
        v.inv.setItem(MARKS, button(v.bookmarksOnly ? Material.LIME_DYE : Material.GRAY_DYE, v.bookmarksOnly ? "Showing bookmarks" : "Bookmarks",
                "Click to show only your bookmarked items.", "Shift-click an item to add or remove it."));
        if (v.player.hasPermission("zukan.cheat")) {
            v.inv.setItem(CHEAT, button(v.cheat ? Material.LIME_CONCRETE : Material.RED_CONCRETE, v.cheat ? "Cheat mode: ON" : "Cheat mode: off",
                    "When on, clicking an item gives it to you."));
        }
        if (v.fromKura && plugin.kuraReopen() != null) v.inv.setItem(TERMINAL, button(Material.CHEST, "Back to the terminal"));
    }

    private void focus(View v, ItemStack item, boolean uses) {
        v.focus = item.asOne();
        v.mode = uses ? Mode.USES : Mode.RECIPES;
        String key = Catalog.keyOf(item);
        List<Recipe> list = uses ? plugin.catalog().usesOf(key) : plugin.catalog().recipesFor(key);
        v.byCat = new LinkedHashMap<>();
        List<Recipe> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(a.category.order, b.category.order));
        for (Recipe r : sorted) v.byCat.computeIfAbsent(r.category, c -> new ArrayList<>()).add(r);
        v.cats = new ArrayList<>(v.byCat.keySet());
        v.catIndex = 0;
        v.recipeIndex = 0;
        v.tabOffset = 0;
        v.cycle = 0;
    }

    private void push(View v) {
        v.history.push(new State(v.mode, v.focus, v.catIndex, v.recipeIndex, v.page));
        while (v.history.size() > 30) v.history.removeLast();
    }

    private void drawRecipe(View v) {
        for (int i = 0; i < 54; i++) {
            v.inv.setItem(i, null);
            v.slotItems[i] = null;
            v.tabAt[i] = -1;
        }
        if (!steampunk()) {
            ItemStack pane = pane();
            for (int s = 0; s < 54; s++) v.inv.setItem(s, pane);
        }
        boolean uses = v.mode == Mode.USES;
        // tabs
        int shownTabs = v.cats.size() > 9 ? 7 : 9, first = v.cats.size() > 9 ? 1 : 0;
        v.tabOffset = Math.max(0, Math.min(v.tabOffset, Math.max(0, v.cats.size() - shownTabs)));
        if (v.cats.size() > 9) {
            v.inv.setItem(0, button(Material.ARROW, "Previous types"));
            v.inv.setItem(8, button(Material.ARROW, "More types"));
        }
        for (int i = 0; i < shownTabs && v.tabOffset + i < v.cats.size(); i++) {
            Recipe.Category c = v.cats.get(v.tabOffset + i);
            boolean on = v.tabOffset + i == v.catIndex;
            ItemStack icon = c.icon.clone();
            ItemMeta m = icon.getItemMeta();
            m.displayName(line((on ? "> " : "") + c.name + " (" + v.byCat.get(c).size() + ")", on ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            m.lore(List.of());
            if (on) m.setEnchantmentGlintOverride(true);
            icon.setItemMeta(m);
            v.inv.setItem(first + i, icon);
            v.tabAt[first + i] = v.tabOffset + i;
        }
        ItemStack nothing = button(Material.STRUCTURE_VOID, uses ? "Not used in any recipe" : "No recipe known",
                "This item can't be made with a known recipe.", "It may be found in the world or dropped by mobs.");
        Recipe r = null;
        List<Recipe> list = v.cats.isEmpty() ? List.of() : v.byCat.get(v.cats.get(Math.min(v.catIndex, v.cats.size() - 1)));
        if (list.isEmpty()) {
            v.inv.setItem(22, nothing);
            if (v.focus != null) v.inv.setItem(20, shown(v.focus));
        } else {
            v.recipeIndex = Math.max(0, Math.min(v.recipeIndex, list.size() - 1));
            r = list.get(v.recipeIndex);
            drawOneRecipe(v, r);
        }
        // bottom bar
        if (!v.history.isEmpty()) v.inv.setItem(BACK, button(Material.ARROW, "Back"));
        v.inv.setItem(HOME, button(Material.BOOK, "All items"));
        if (list.size() > 1) {
            v.inv.setItem(PREV_R, button(Material.SPECTRAL_ARROW, "Previous recipe"));
            v.inv.setItem(NEXT_R, button(Material.SPECTRAL_ARROW, "Next recipe"));
            v.inv.setItem(COUNT, button(Material.PAPER, "Recipe " + (v.recipeIndex + 1) + " of " + list.size()));
        }
        if (v.focus != null) {
            v.inv.setItem(MARK, button(marked(v.player, v.focus) ? Material.LIME_DYE : Material.GRAY_DYE, marked(v.player, v.focus) ? "Bookmarked" : "Bookmark this item", "Click to change."));
        }
        if (v.fromKura && r != null && r.grid && plugin.kuraFill() != null) {
            v.inv.setItem(FILL, button(Material.CRAFTING_TABLE, "Move ingredients to the Crafting Terminal",
                    "Takes what you need from the network", "and fills the grid."));
        } else if (v.fromKura && plugin.kuraReopen() != null) {
            v.inv.setItem(FILL, button(Material.CHEST, "Back to the terminal"));
        }
    }

    private void drawOneRecipe(View v, Recipe r) {
        boolean cycle = v.cycle > 0;
        int[] inSlots = r.grid ? GRID : FREE_IN;
        for (int i = 0; i < r.inputs.size() && i < inSlots.length; i++) {
            List<ItemStack> opts = r.inputs.get(i);
            if (opts.isEmpty()) {
                v.inv.setItem(inSlots[i], null);
                continue;
            }
            ItemStack it = opts.get(cycle ? v.cycle % opts.size() : 0);
            v.slotItems[inSlots[i]] = it;
            v.inv.setItem(inSlots[i], shown(it, opts.size() > 1 ? "One of " + opts.size() + " items (cycles)" : "", counts(it), "Click: recipes  Right: uses"));
        }
        v.inv.setItem(23, button(Material.ARROW, r.shapeless ? "Shapeless: any arrangement" : "Result"));
        int[] outSlots = r.grid ? new int[]{25} : FREE_OUT;
        for (int i = 0; i < r.outputs.size() && i < outSlots.length; i++) {
            ItemStack o = r.outputs.get(i);
            v.slotItems[outSlots[i]] = o;
            v.inv.setItem(outSlots[i], shown(o, counts(o), "Click: recipes  Right: uses"));
        }
        if (r.station != null) v.inv.setItem(37, shown(r.station, "Made with this"));
        if (!r.info.isEmpty()) {
            ItemStack info = button(Material.PAPER, r.category.name);
            ItemMeta m = info.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String s : r.info) lore.add(line(s, NamedTextColor.GRAY));
            m.lore(lore);
            info.setItemMeta(m);
            v.inv.setItem(39, info);
        }
    }

    // ------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof View v)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 54) return;
        ClickType click = e.getClick();
        Player p = v.player;
        if (v.mode == Mode.LIST) listClick(v, raw, click);
        else recipeClick(v, raw, click);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.5f);
    }

    private void listClick(View v, int raw, ClickType click) {
        if (raw < 45) {
            ItemStack it = v.slotItems[raw];
            if (it == null) return;
            if (v.cheat && v.player.hasPermission("zukan.cheat") && !click.isShiftClick()) {
                ItemStack give = it.asQuantity(click.isRightClick() ? 1 : it.getMaxStackSize());
                for (ItemStack left : v.player.getInventory().addItem(give).values()) v.player.getWorld().dropItemNaturally(v.player.getLocation(), left);
                return;
            }
            if (!v.cheat && click.isShiftClick()) {
                toggleMark(v.player, it);
                plugin.adv().grant(v.player, "bookmark");
                if (marks(v.player).size() >= 50) plugin.adv().grant(v.player, "hoarder");
                if (v.bookmarksOnly) draw(v);
                return;
            }
            boolean uses = click.isRightClick();
            focus(v, it, uses);
            show(v);
            return;
        }
        switch (raw) {
            case PREV -> { if (v.page > 0) v.page--; draw(v); }
            case NEXT -> { v.page++; draw(v); }
            case SEARCH -> openSearch(v);
            case CLEAR -> { v.query = ""; v.page = 0; draw(v); }
            case NS -> {
                TreeSet<String> all = new TreeSet<>();
                for (Catalog.Entry en : plugin.catalog().entries) all.add(en.ns());
                List<String> order = new ArrayList<>();
                order.add("");
                order.add("minecraft");
                for (String s : all) if (!s.equals("minecraft")) order.add(s);
                v.ns = order.get((order.indexOf(v.ns) + 1) % order.size());
                v.page = 0;
                draw(v);
            }
            case MARKS -> { v.bookmarksOnly = !v.bookmarksOnly; v.page = 0; draw(v); }
            case CHEAT -> {
                if (v.player.hasPermission("zukan.cheat")) {
                    v.cheat = !v.cheat;
                    if (v.cheat) plugin.adv().grant(v.player, "cheat");
                    draw(v);
                }
            }
            case TERMINAL -> terminal(v);
            default -> { }
        }
    }

    private void terminal(View v) {
        Predicate<Player> back = plugin.kuraReopen();
        if (back == null) return;
        open.remove(v.player.getUniqueId(), v);
        v.switching = true;
        if (!back.test(v.player)) v.player.closeInventory();
    }

    private void recipeClick(View v, int raw, ClickType click) {
        if (v.tabAt[raw] >= 0) {
            v.catIndex = v.tabAt[raw];
            v.recipeIndex = 0;
            v.cycle = 0;
            show(v);
            return;
        }
        ItemStack it = v.slotItems[raw];
        if (it != null) {
            if (click.isShiftClick()) {
                toggleMark(v.player, it);
                return;
            }
            push(v);
            focus(v, it, click.isRightClick());
            show(v);
            return;
        }
        List<Recipe> list = v.cats.isEmpty() ? List.of() : v.byCat.get(v.cats.get(Math.min(v.catIndex, v.cats.size() - 1)));
        switch (raw) {
            case 0 -> {
                if (v.cats.size() > 9) { v.tabOffset = Math.max(0, v.tabOffset - 7); drawRecipe(v); }
            }
            case 8 -> {
                if (v.cats.size() > 9) { v.tabOffset += 7; drawRecipe(v); }
            }
            case BACK -> {
                if (v.history.isEmpty()) return;
                State s = v.history.pop();
                focus(v, s.focus(), s.mode() == Mode.USES);
                v.catIndex = Math.min(s.catIndex(), Math.max(0, v.cats.size() - 1));
                v.recipeIndex = s.recipeIndex();
                show(v);
            }
            case HOME -> {
                v.mode = Mode.LIST;
                v.history.clear();
                show(v);
            }
            case PREV_R -> {
                if (list.size() > 1) { v.recipeIndex = (v.recipeIndex + list.size() - 1) % list.size(); v.cycle = 0; drawRecipe(v); }
            }
            case NEXT_R -> {
                if (list.size() > 1) { v.recipeIndex = (v.recipeIndex + 1) % list.size(); v.cycle = 0; drawRecipe(v); }
            }
            case MARK -> {
                if (v.focus != null) { toggleMark(v.player, v.focus); drawRecipe(v); }
            }
            case FILL -> {
                if (!v.fromKura) return;
                Recipe r = list.isEmpty() ? null : list.get(Math.min(v.recipeIndex, list.size() - 1));
                BiPredicate<Player, List<List<ItemStack>>> fill = plugin.kuraFill();
                if (r != null && r.grid && fill != null) {
                    open.remove(v.player.getUniqueId(), v);
                    v.switching = true;
                    if (!fill.test(v.player, r.inputs)) {
                        v.player.closeInventory();
                        v.player.sendActionBar(Component.text("Open a Crafting Terminal first.", NamedTextColor.RED));
                    } else plugin.adv().grant(v.player, "filled");
                } else terminal(v);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof View v)) return;
        if (v.switching) return;
        if (searching.containsKey(v.player.getUniqueId())) return;
        open.remove(v.player.getUniqueId(), v);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
        searching.remove(e.getPlayer().getUniqueId());
        searchViews.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ search box (anvil)

    private void openSearch(View v) {
        Player p = v.player;
        v.switching = true;
        AnvilView view = MenuType.ANVIL.builder().checkReachable(false).title(Component.text("Search")).build(p);
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(v.query.isEmpty() ? " " : v.query));
        paper.setItemMeta(meta);
        searching.put(p.getUniqueId(), v);
        searchViews.put(p.getUniqueId(), view);
        p.openInventory(view);
        view.getTopInventory().setItem(0, paper);
        v.switching = false;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilView view = searchViews.get(e.getView().getPlayer().getUniqueId());
        if (view == null || e.getView() != view) return;
        String text = view.getRenameText() == null ? "" : view.getRenameText();
        ItemStack out = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = out.getItemMeta();
        meta.displayName(line(text.isBlank() ? "Show everything" : "Search: " + text.trim(), NamedTextColor.GOLD));
        meta.lore(List.of(line("Click here, or press Esc.", NamedTextColor.GRAY)));
        out.setItemMeta(meta);
        e.setResult(out);
        view.setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent e) {
        AnvilView view = searchViews.get(e.getWhoClicked().getUniqueId());
        if (view == null || e.getView() != view) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 2) e.getWhoClicked().closeInventory();
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent e) {
        AnvilView view = searchViews.get(e.getPlayer().getUniqueId());
        if (view == null || e.getView() != view) return;
        String text = view.getRenameText();
        view.getTopInventory().clear();
        searchViews.remove(e.getPlayer().getUniqueId());
        View v = searching.remove(e.getPlayer().getUniqueId());
        if (v == null) return;
        v.query = text == null ? "" : text.trim();
        v.page = 0;
        if (v.query.equalsIgnoreCase("recipe for disaster")) { // joke: it shows the TNT recipes
            v.query = "tnt";
            v.player.sendActionBar(net.kyori.adventure.text.Component.text("Recipe for disaster, coming up.", net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.adv().grant(v.player, "disaster");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (v.player.isOnline()) show(v);
        });
    }
}
