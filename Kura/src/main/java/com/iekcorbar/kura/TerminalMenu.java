package com.iekcorbar.kura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One player's view of a terminal: a 6-row chest GUI listing every item in the
 * network (amount in the tooltip), with search, sort and pages on the bottom row.
 * The Crafting Terminal shows fewer items and a 3x3 grid + result on the right.
 */
final class TerminalMenu implements InventoryHolder {
    static final int[] GRID = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    static final int RESULT = 34, CLEAR_GRID = 33, FILL_INFO = 35;
    static final int PREV = 45, SEARCH = 46, SORT = 47, CLEAR_SEARCH = 48, INFO = 49, DEPOSIT = 50, RECIPES = 51, RETRIEVE = 52, NEXT = 53;
    static final String[] SORTS = {"Amount", "Name", "Plugin / mod"};

    final KuraPlugin plugin;
    final Player player;
    final Part part;
    final boolean crafting, remote;
    final Inventory inv;
    final Set<Long> tickets = new HashSet<>();
    String search = "";
    int sort, page;
    /** Item kind shown in each item slot (null = empty). */
    final ItemStack[] keys = new ItemStack[54];
    /** Every kind matching the current search, in display order (all pages) - the Take-shown button works on this. */
    final List<ItemStack> shownKeys = new ArrayList<>();
    /** Going to the search box: don't treat the close as leaving. */
    boolean switching;
    Network network;

    TerminalMenu(KuraPlugin plugin, Player player, Part part, boolean remote) {
        this.plugin = plugin;
        this.player = player;
        this.part = part;
        this.crafting = part.type == PartType.CRAFTING_TERMINAL;
        this.remote = remote;
        String title = part.type == PartType.FILING_CABINET ? "Filing Cabinet" : crafting ? "Crafting Terminal" : "Storage Terminal";
        this.inv = Bukkit.createInventory(this, 54, Component.text(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    int[] itemSlots() {
        if (!crafting) {
            int[] s = new int[45];
            for (int i = 0; i < 45; i++) s[i] = i;
            return s;
        }
        int[] s = new int[30];
        int k = 0;
        for (int row = 0; row < 5; row++) for (int col = 0; col < 6; col++) s[k++] = row * 9 + col;
        return s;
    }

    boolean isItemSlot(int raw) {
        if (raw < 0 || raw >= 45) return false;
        return !crafting || raw % 9 < 6;
    }

    static boolean isGrid(int raw) {
        for (int g : GRID) if (g == raw) return true;
        return false;
    }

    ItemStack[] grid() {
        ItemStack[] m = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(GRID[i]);
            m[i] = it == null || it.isEmpty() ? null : it.clone();
        }
        return m;
    }

    // ------------------------------------------------------------ drawing

    void refresh() {
        network = plugin.menus().resolve(this);
        int[] slots = itemSlots();
        java.util.Arrays.fill(keys, null);
        shownKeys.clear();
        Set<String> favs = plugin.favourites().read(player);
        List<Entry> list = new ArrayList<>();
        if (network != null) {
            for (Map.Entry<ItemStack, Long> e : network.countsCached(plugin.tick).entrySet()) {
                Entry en = new Entry(e.getKey(), e.getValue(), name(e.getKey()), namespace(e.getKey()));
                if (matches(en)) list.add(en);
            }
        }
        Comparator<Entry> byName = Comparator.comparing(Entry::name);
        switch (sort) {
            case 1 -> list.sort(byName);
            case 2 -> list.sort(Comparator.comparing(Entry::ns).thenComparing(byName));
            default -> list.sort(Comparator.comparingLong((Entry e) -> -e.count()).thenComparing(byName));
        }
        if (!favs.isEmpty()) list.sort(Comparator.comparing(e -> !favs.contains(Favourites.id(e.key()))));   // stable: pinned on top, sort kept
        int pages = Math.max(1, (list.size() + slots.length - 1) / slots.length);
        page = Math.min(page, pages - 1);
        for (Entry e : list) shownKeys.add(e.key());
        for (int i = 0; i < slots.length; i++) {
            int idx = page * slots.length + i;
            if (idx >= list.size()) {
                inv.setItem(slots[i], null);
                continue;
            }
            Entry e = list.get(idx);
            keys[slots[i]] = e.key();
            inv.setItem(slots[i], shown(e.key(), e.count(), favs.contains(Favourites.id(e.key()))));
        }
        // bottom row
        ItemStack pane = Menus.pane();
        for (int s = 45; s < 54; s++) inv.setItem(s, pane);
        if (page > 0) inv.setItem(PREV, Menus.button(Material.ARROW, "Previous page", "Page " + page + " of " + pages, "Shift: first page"));
        if (page < pages - 1) inv.setItem(NEXT, Menus.button(Material.ARROW, "Next page", "Page " + (page + 2) + " of " + pages, "Shift: last page"));
        inv.setItem(SEARCH, Menus.button(Material.COMPASS, search.isEmpty() ? "Search" : "Search: " + search,
                "Click to type a search.", "Words: item name. #logs: item group.", "@kitchen: items of one plugin/mod."));
        inv.setItem(SORT, Menus.button(Material.HOPPER, "Sort: " + SORTS[sort], "Click to change."));
        if (!search.isEmpty()) inv.setItem(CLEAR_SEARCH, Menus.button(Material.BARRIER, "Clear search"));
        if (network == null) {
            inv.setItem(INFO, Menus.button(Material.REDSTONE_TORCH, "Not connected",
                    "Place an Inventory Cable beside a chest,", "then the terminal against that cable."));
        } else {
            int free = network.freeSlots(), all = network.slots();
            inv.setItem(INFO, Menus.button(Material.BOOK, "Network",
                    network.stores.size() + " inventor" + (network.stores.size() == 1 ? "y" : "ies"),
                    "Slots: " + free + " free of " + all + (all > 0 ? " (" + free * 100 / all + "% free)" : ""),
                    "Different items: " + list.size() + (search.isEmpty() ? "" : " (matching)"),
                    network.truncated ? "Too big: not everything is connected." : ""));
        }
        inv.setItem(DEPOSIT, Menus.button(Material.CHEST, "Store your inventory", "Puts everything from your inventory", "(not the hotbar) into the network.", "Shift-click: hotbar too."));
        inv.setItem(RETRIEVE, Menus.button(Material.DROPPER, "Take shown items",
                search.isEmpty() ? "Takes everything the network holds" : "Takes every kind matching the search",
                "into your inventory, one stack each,", "as far as you have room.", network == null || shownKeys.isEmpty() ? "Nothing to take." : shownKeys.size() + " kinds shown."));
        if (plugin.recipeViewer() != null) inv.setItem(RECIPES, Menus.button(Material.KNOWLEDGE_BOOK, "Recipes", "Look up how to make things.", "Middle-click an item for its recipes."));
        if (crafting) {
            for (int s : new int[]{CLEAR_GRID, FILL_INFO, 42, 43, 44}) inv.setItem(s, pane);
            inv.setItem(CLEAR_GRID, Menus.button(Material.BUCKET, "Clear grid", "Puts the crafting grid back into the network."));
            inv.setItem(FILL_INFO, Menus.button(Material.CRAFTING_TABLE, "Crafting", "Put ingredients in the grid. After crafting,",
                    "the grid refills itself from the network.", "Shift-click the result to craft a stack."));
        }
    }

    private ItemStack shown(ItemStack key, long count, boolean fav) {
        ItemStack it = key.clone();
        it.setAmount((int) Math.max(1, Math.min(count, it.getMaxStackSize())));
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        if (fav) lore.add(Items.line("★ Favourite - double-click to un-pin", NamedTextColor.AQUA));
        lore.add(Items.line("Stored: " + String.format(Locale.ROOT, "%,d", count), NamedTextColor.YELLOW));
        lore.add(Items.line((plugin.recipeViewer() != null
                ? "Left: stack  Right: half  Shift: to inventory  Middle: recipes"
                : "Left: stack  Right: half  Shift: to inventory") + (fav ? "" : "  Dbl: pin"), NamedTextColor.DARK_GRAY));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ search

    private record Entry(ItemStack key, long count, String name, String ns) { }

    private boolean matches(Entry e) {
        if (search.isEmpty()) return true;
        for (String word : search.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (word.startsWith("#")) {
                if (!inTag(e.key().getType(), word.substring(1))) return false;
            } else if (word.startsWith("@")) {
                if (!e.ns().startsWith(word.substring(1))) return false;
            } else if (!e.name().contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean inTag(Material m, String part) {
        for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
            if (tag.getKey().getKey().contains(part) && tag.isTagged(m)) return true;
        }
        return false;
    }

    static String name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName() && m.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(m.displayName()).toLowerCase(Locale.ROOT);
        }
        if (m != null && m.hasItemName()) return PlainTextComponentSerializer.plainText().serialize(m.itemName()).toLowerCase(Locale.ROOT);
        return it.getType().getKey().getKey().replace('_', ' ');
    }

    /** "minecraft", or the namespace of a custom item model (kitchen, steamworks, ...). */
    static String namespace(ItemStack it) {
        Key model = it.getData(DataComponentTypes.ITEM_MODEL);
        return model == null ? "minecraft" : model.namespace();
    }
}
