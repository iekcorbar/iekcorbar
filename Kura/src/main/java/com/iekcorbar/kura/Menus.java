package com.iekcorbar.kura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Every Kura GUI: terminals (+ search box), settings menus, filter editors; also wireless terminals. */
final class Menus implements Listener {
    /** Settings / filter GUIs. */
    static final class Config implements InventoryHolder {
        enum Kind { CONTAINER, HOPPER, EMITTER, INTERFACE, COURIER, ITEM_FILTER, TAG_FILTER }

        final Kind kind;
        final Part part;
        final Block block;
        /** COURIER: the bound copper golem whose carry filter is being set. */
        UUID golem;
        final Inventory inv;
        int[] settings;
        ItemStack filter;
        /** Filter editors: the hotbar slot holding the filter being edited. */
        int handSlot;

        Config(Kind kind, Part part, Block block, String title) {
            this.kind = kind;
            this.part = part;
            this.block = block;
            this.inv = Bukkit.createInventory(this, 27, Component.text(title));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final KuraPlugin plugin;
    private final Map<UUID, TerminalMenu> open = new HashMap<>();
    /** Open search boxes: player -> the terminal to go back to. */
    private final Map<UUID, TerminalMenu> searching = new HashMap<>();
    private final Map<UUID, AnvilView> searchViews = new HashMap<>();
    /** Chunk tickets held for remote terminals: world:chunk -> users. */
    private final Map<String, Integer> tickets = new HashMap<>();

    Menus(KuraPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ small items

    static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        it.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return it;
    }

    static ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Items.line(name, NamedTextColor.GOLD));
        List<Component> l = new ArrayList<>();
        for (String s : lore) if (!s.isEmpty()) l.add(Items.line(s, NamedTextColor.GRAY));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------ terminals

    void openTerminal(Player p, Part part, boolean remote) {
        TerminalMenu m = new TerminalMenu(plugin, p, part, remote);
        m.refresh();
        open.put(p.getUniqueId(), m);
        p.openInventory(m.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.4f, 1.6f);
        plugin.adv().grant(p, "opened");
    }

    void openCabinet(Player p, Part part) {
        openTerminal(p, part, false);
    }

    /** The network a terminal shows right now. */
    Network resolve(TerminalMenu m) {
        if (m.part.type == PartType.FILING_CABINET) return plugin.networks().single(m.part.block());
        Networks.Loader loader = m.remote ? (w, cx, cz) -> ticket(m, w, cx, cz) : null;
        return plugin.parts().networkOf(m.part, loader);
    }

    private boolean ticket(TerminalMenu m, World w, int cx, int cz) {
        long key = Chunk.getChunkKey(cx, cz);
        if (m.tickets.contains(key)) return true;
        if (m.tickets.size() >= plugin.getConfig().getInt("wireless.max-loaded-chunks", 25)) return false;
        m.tickets.add(key);
        acquire(w, cx, cz);
        return true;
    }

    /** Shared chunk ticket (counted: several remote terminals may hold the same chunk). */
    private void acquire(World w, int cx, int cz) {
        String k = w.getUID() + ":" + Chunk.getChunkKey(cx, cz);
        if (tickets.merge(k, 1, Integer::sum) == 1) w.addPluginChunkTicket(cx, cz, plugin);
    }

    private void release(World w, int cx, int cz) {
        String k = w.getUID() + ":" + Chunk.getChunkKey(cx, cz);
        Integer n = tickets.get(k);
        if (n == null) return;
        if (n <= 1) {
            tickets.remove(k);
            w.removePluginChunkTicket(cx, cz, plugin);
        } else tickets.put(k, n - 1);
    }

    private void releaseTickets(TerminalMenu m) {
        for (long key : m.tickets) release(m.part.world, (int) key, (int) (key >> 32));
        m.tickets.clear();
    }

    /** Every second: keep open terminals up to date. */
    void tick() {
        for (TerminalMenu m : new ArrayList<>(open.values())) {
            if (m.player.getOpenInventory().getTopInventory() == m.inv) {
                m.refresh();
            } else if (m.player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                    && !searching.containsKey(m.player.getUniqueId())) {
                leave(m); // went to the recipe viewer and never came back
            }
        }
    }

    void closeFor(Part part) {
        for (TerminalMenu m : new ArrayList<>(open.values())) if (m.part == part) m.player.closeInventory();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Config c && c.part == part) p.closeInventory();
        }
    }

    void closeAll() {
        for (TerminalMenu m : new ArrayList<>(open.values())) m.player.closeInventory();
        for (UUID id : new ArrayList<>(searchViews.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Config) p.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTerminalClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof TerminalMenu m)) return;
        Player p = m.player;
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < 54;
        ClickType click = e.getClick();
        if (click == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true); // would collect the shown items
            if (top && m.isItemSlot(raw) && m.keys[raw] != null) {
                toggleFav(m, m.keys[raw]);   // double-click an item in the list: pin/unpin as favourite
                m.refresh();
            }
            return;
        }
        if (!top) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack moving = e.getCurrentItem();
                if (moving == null || moving.isEmpty() || m.network == null) return;
                int in = m.network.insert(moving, null);
                if (in > 0) e.setCurrentItem(moving.getAmount() - in <= 0 ? null : moving.asQuantity(moving.getAmount() - in));
                m.refresh();
            }
            return;
        }
        if (m.crafting && TerminalMenu.isGrid(raw)) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack it = m.inv.getItem(raw);
                if (it != null && !it.isEmpty()) {
                    int in = m.network == null ? 0 : m.network.insert(it, null);
                    ItemStack left = in >= it.getAmount() ? null : it.asQuantity(it.getAmount() - in);
                    m.inv.setItem(raw, null);
                    if (left != null) give(p, left);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> updateResult(m));
            return;
        }
        e.setCancelled(true);
        if (m.crafting && raw == TerminalMenu.RESULT) {
            craft(m, e.isShiftClick());
            return;
        }
        if (m.isItemSlot(raw)) {
            itemClick(m, raw, click, e.getHotbarButton());
            return;
        }
        switch (raw) {
            case TerminalMenu.PREV -> { m.page = e.isShiftClick() ? 0 : Math.max(0, m.page - 1); m.refresh(); }
            case TerminalMenu.NEXT -> { if (e.isShiftClick()) m.page = Integer.MAX_VALUE / 4; else m.page++; m.refresh(); }
            case TerminalMenu.SORT -> { m.sort = (m.sort + 1) % TerminalMenu.SORTS.length; m.refresh(); }
            case TerminalMenu.CLEAR_SEARCH -> { m.search = ""; m.page = 0; m.refresh(); }
            case TerminalMenu.SEARCH -> openSearch(m);
            case TerminalMenu.DEPOSIT -> deposit(m, e.isShiftClick());
            case TerminalMenu.RETRIEVE -> retrieve(m);
            case TerminalMenu.RECIPES -> {
                var viewer = plugin.recipeViewer();
                if (viewer != null) {
                    if (m.crafting) clearGrid(m); // nothing may be left in the grid while the player is away
                    m.switching = true;
                    viewer.accept(p, m.search);
                }
            }
            case TerminalMenu.CLEAR_GRID -> { if (m.crafting) { clearGrid(m); m.refresh(); } }
            default -> { }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.4f);
    }

    private void itemClick(TerminalMenu m, int raw, ClickType click, int hotbar) {
        Player p = m.player;
        Network net = m.network;
        if (net == null) return;
        ItemStack cursor = p.getItemOnCursor();
        if (!cursor.isEmpty()) {
            // put the cursor in (right-click: one item)
            ItemStack put = click.isRightClick() ? cursor.asOne() : cursor;
            int in = net.insert(put, null);
            if (in > 0) p.setItemOnCursor(cursor.getAmount() - in <= 0 ? null : cursor.asQuantity(cursor.getAmount() - in));
            m.refresh();
            return;
        }
        ItemStack key = m.keys[raw];
        if (key == null) return;
        if (click == ClickType.MIDDLE) {
            // middle-click (pick block): look this exact item up in the recipe viewer
            var viewer = plugin.recipeViewer();
            if (viewer == null) return;
            if (m.crafting) clearGrid(m);
            m.switching = true;
            viewer.accept(p, TerminalMenu.name(key));
            return;
        }
        int stack = key.getMaxStackSize();
        if (click.isShiftClick()) {
            ItemStack got = net.take(key, stack, null);
            if (got != null) for (ItemStack left : p.getInventory().addItem(got).values()) net.insert(left, null);
        } else if (click == ClickType.NUMBER_KEY) {
            // hotbar key: a stack into that hotbar slot, if it is empty
            if (hotbar < 0 || hotbar > 8) return;
            ItemStack there = p.getInventory().getItem(hotbar);
            if (there != null && !there.isEmpty()) return;
            ItemStack got = net.take(key, stack, null);
            if (got != null) p.getInventory().setItem(hotbar, got);
        } else if (click.isRightClick()) {
            long have = net.count(key);
            int n = (int) Math.max(1, (Math.min(have, stack) + 1) / 2);
            ItemStack got = net.take(key, n, null);
            if (got != null) p.setItemOnCursor(got);
        } else if (click.isLeftClick()) {
            ItemStack got = net.take(key, stack, null);
            if (got != null) p.setItemOnCursor(got);
        }
        m.refresh();
    }

    private void deposit(TerminalMenu m, boolean hotbarToo) {
        if (m.network == null) return;
        Inventory pi = m.player.getInventory();
        for (int i = hotbarToo ? 0 : 9; i < 36; i++) {
            ItemStack it = pi.getItem(i);
            if (it == null || it.isEmpty() || plugin.items().typeOf(it) == KItem.WIRELESS_TERMINAL
                    || plugin.items().typeOf(it) == KItem.ADV_WIRELESS_TERMINAL) continue;
            int in = m.network.insert(it, null);
            if (in > 0) pi.setItem(i, it.getAmount() - in <= 0 ? null : it.asQuantity(it.getAmount() - in));
        }
        m.refresh();
    }

    /** Take one stack of every kind currently shown (the search narrows it), as far as there is room. */
    private void retrieve(TerminalMenu m) {
        if (m.network == null || m.shownKeys.isEmpty()) return;
        Player p = m.player;
        int kinds = 0;
        for (ItemStack key : new ArrayList<>(m.shownKeys)) {
            ItemStack probe = key.asOne();
            if (p.getInventory().firstEmpty() < 0 && !fits(p.getInventory(), probe)) break;   // no room left at all
            if (!fits(p.getInventory(), key)) continue;                                        // this stack would not fit anywhere
            ItemStack got = m.network.take(key, key.getMaxStackSize(), null);
            if (got == null) continue;
            kinds++;
            for (ItemStack left : p.getInventory().addItem(got).values()) m.network.insert(left, null);   // race with machines
        }
        if (kinds > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.1f);
        } else {
            p.sendActionBar(Component.text("No room in your inventory (or nothing to take).", NamedTextColor.GRAY));
        }
        m.refresh();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTerminalDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof TerminalMenu m)) return;
        boolean grid = false;
        for (int raw : e.getRawSlots()) {
            if (raw >= 54) continue;
            if (m.crafting && TerminalMenu.isGrid(raw)) {
                grid = true;
                continue;
            }
            e.setCancelled(true);
            // a drag over the item list is a plain "put in"
            if (e.getRawSlots().size() == 1 && m.isItemSlot(raw) && m.network != null) {
                ItemStack cursor = e.getOldCursor();
                int in = m.network.insert(cursor, null);
                ItemStack left = in >= cursor.getAmount() ? null : cursor.asQuantity(cursor.getAmount() - in);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    m.player.setItemOnCursor(left);
                    m.refresh();
                });
            }
            return;
        }
        if (grid) Bukkit.getScheduler().runTask(plugin, () -> updateResult(m));
    }

    @EventHandler
    public void onTerminalClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof TerminalMenu m)) return;
        if (m.switching) {
            m.switching = false;
            return;
        }
        leave(m);
    }

    private void leave(TerminalMenu m) {
        if (m.crafting) clearGrid(m);
        open.remove(m.player.getUniqueId(), m);
        releaseTickets(m);
    }

    // ------------------------------------------------------------ crafting

    void updateResult(TerminalMenu m) {
        if (!m.crafting || m.inv.getViewers().isEmpty()) return;
        ItemStack[] grid = m.grid();
        boolean empty = true;
        for (ItemStack g : grid) if (g != null) empty = false;
        if (empty) {
            m.inv.setItem(TerminalMenu.RESULT, null);
            return;
        }
        ItemCraftResult r = Bukkit.craftItemResult(grid, m.player.getWorld(), m.player);
        ItemStack res = r.getResult();
        m.inv.setItem(TerminalMenu.RESULT, res == null || res.isEmpty() ? null : res);
    }

    private void craft(TerminalMenu m, boolean shift) {
        Player p = m.player;
        ItemStack first = null;
        for (int n = 0; n < (shift ? 64 : 1); n++) {
            ItemStack[] grid = m.grid();
            ItemStack[] copy = new ItemStack[9];
            for (int i = 0; i < 9; i++) copy[i] = grid[i] == null ? null : grid[i].clone();
            ItemCraftResult r = Bukkit.craftItemResult(copy, p.getWorld(), p);
            ItemStack res = r.getResult();
            if (res == null || res.isEmpty()) break;
            if (first == null) first = res.clone();
            else if (!first.isSimilar(res)) break;
            if (shift) {
                if (!fits(p.getInventory(), res)) break;
            } else {
                ItemStack cursor = p.getItemOnCursor();
                if (!cursor.isEmpty() && (!cursor.isSimilar(res) || cursor.getAmount() + res.getAmount() > cursor.getMaxStackSize())) break;
            }
            ItemStack[] after = r.getResultingMatrix();
            for (int i = 0; i < 9; i++) {
                ItemStack a = i < after.length ? after[i] : null;
                m.inv.setItem(TerminalMenu.GRID[i], a == null || a.isEmpty() ? null : a);
            }
            for (ItemStack over : r.getOverflowItems()) give(p, over);
            // keep the recipe going: refill emptied slots from the network
            if (m.network != null) {
                for (int i = 0; i < 9; i++) {
                    if (grid[i] == null) continue;
                    ItemStack now = m.inv.getItem(TerminalMenu.GRID[i]);
                    if (now != null && !now.isEmpty()) continue;
                    ItemStack got = m.network.take(grid[i].asOne(), 1, null);
                    if (got != null) m.inv.setItem(TerminalMenu.GRID[i], got);
                }
            }
            if (shift) {
                for (ItemStack left : p.getInventory().addItem(res).values()) give(p, left);
            } else {
                ItemStack cursor = p.getItemOnCursor();
                p.setItemOnCursor(cursor.isEmpty() ? res : cursor.asQuantity(cursor.getAmount() + res.getAmount()));
            }
        }
        if (first != null) p.playSound(p.getLocation(), Sound.BLOCK_CRAFTER_CRAFT, 0.4f, 1.2f);
        updateResult(m);
        m.refresh();
    }

    private static boolean fits(Inventory inv, ItemStack it) {
        int left = it.getAmount();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty()) return true;
            if (s.isSimilar(it)) left -= s.getMaxStackSize() - s.getAmount();
            if (left <= 0) return true;
        }
        return false;
    }

    private void clearGrid(TerminalMenu m) {
        for (int g : TerminalMenu.GRID) {
            ItemStack it = m.inv.getItem(g);
            if (it == null || it.isEmpty()) continue;
            m.inv.setItem(g, null);
            int in = m.network == null ? 0 : m.network.insert(it, null);
            if (in < it.getAmount()) give(m.player, it.asQuantity(it.getAmount() - in));
        }
        m.inv.setItem(TerminalMenu.RESULT, null);
    }

    /**
     * Fills the crafting grid of the player's open Crafting Terminal with a recipe
     * (from the recipe viewer): each cell is a list of accepted items; takes them
     * from the network. Returns false when no Crafting Terminal is open.
     */
    boolean fillGrid(Player p, List<List<ItemStack>> cells) {
        TerminalMenu m = open.get(p.getUniqueId());
        if (m == null || !m.crafting) return false;
        m.network = resolve(m);
        clearGrid(m);
        Set<ItemStack> kinds = m.network == null ? Set.of() : m.network.countsCached(plugin.tick).keySet();
        for (int i = 0; i < 9 && i < cells.size(); i++) {
            List<ItemStack> options = cells.get(i);
            if (options == null || options.isEmpty() || m.network == null) continue;
            for (ItemStack option : options) {
                ItemStack got = m.network.take(option.asOne(), 1, null);
                if (got == null) {
                    // same kind with other data (e.g. damaged tools) also fits most recipes
                    for (ItemStack k : kinds) {
                        if (k.getType() == option.getType() && Filter.sameKind(k, option)) {
                            got = m.network.take(k, 1, null);
                            break;
                        }
                    }
                }
                if (got != null) {
                    m.inv.setItem(TerminalMenu.GRID[i], got);
                    break;
                }
            }
        }
        m.switching = false;
        p.openInventory(m.inv);
        updateResult(m);
        m.refresh();
        return true;
    }

    /** Reopens the player's terminal (after the recipe viewer). */
    boolean reopen(Player p) {
        TerminalMenu m = open.get(p.getUniqueId());
        if (m == null) return false;
        m.switching = false;
        p.openInventory(m.inv);
        m.refresh();
        return true;
    }

    // ------------------------------------------------------------ search box (anvil)

    private void openSearch(TerminalMenu m) {
        Player p = m.player;
        m.switching = true;
        AnvilView view = MenuType.ANVIL.builder().checkReachable(false).title(Component.text("Search")).build(p);
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(m.search.isEmpty() ? " " : m.search));
        paper.setItemMeta(meta);
        searching.put(p.getUniqueId(), m);
        searchViews.put(p.getUniqueId(), view);
        p.openInventory(view);
        view.getTopInventory().setItem(0, paper);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilView view = searchViews.get(e.getView().getPlayer().getUniqueId());
        if (view == null || e.getView() != view) return;
        String text = view.getRenameText() == null ? "" : view.getRenameText();
        ItemStack out = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = out.getItemMeta();
        meta.displayName(Items.line(text.isBlank() ? "Show everything" : "Search: " + text.trim(), NamedTextColor.GOLD));
        meta.lore(List.of(Items.line("Click here, or press Esc.", NamedTextColor.GRAY)));
        out.setItemMeta(meta);
        e.setResult(out);
        view.setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent e) {
        AnvilView view = searchViews.get(e.getWhoClicked().getUniqueId());
        if (view == null || e.getView() != view) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 2) finishSearch((Player) e.getWhoClicked());
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent e) {
        AnvilView view = searchViews.get(e.getPlayer().getUniqueId());
        if (view == null || e.getView() != view) return;
        String text = view.getRenameText();
        view.getTopInventory().clear(); // our paper must not end up in the player's inventory
        searchViews.remove(e.getPlayer().getUniqueId());
        TerminalMenu m = searching.remove(e.getPlayer().getUniqueId());
        if (m == null) return;
        m.search = text == null ? "" : text.trim();
        m.page = 0;
        if (m.search.equalsIgnoreCase("cookie") && e.getPlayer() instanceof Player cp) {
            cp.sendActionBar(net.kyori.adventure.text.Component.text("Cookies are not a storage emergency.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            plugin.adv().grant(cp, "cookie");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!m.player.isOnline()) {
                leave(m);
                return;
            }
            m.refresh();
            m.player.openInventory(m.inv);
        });
    }

    private void finishSearch(Player p) {
        p.closeInventory(); // onAnvilClose applies the text and reopens the terminal
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TerminalMenu m = open.get(e.getPlayer().getUniqueId());
        searching.remove(e.getPlayer().getUniqueId());
        searchViews.remove(e.getPlayer().getUniqueId());
        if (m != null) leave(m);
    }

    // ------------------------------------------------------------ settings menus

    void openContainerSettings(Player p, Block b) {
        Config c = new Config(Config.Kind.CONTAINER, null, b, "Inventory Configurator");
        c.settings = plugin.networks().settings(b);
        c.filter = plugin.networks().settingsFilter(b);
        drawConfig(c);
        p.openInventory(c.inv);
    }

    void openPartSettings(Player p, Part part) {
        Config.Kind kind = switch (part.type) {
            case INVENTORY_HOPPER -> Config.Kind.HOPPER;
            case LEVEL_EMITTER -> Config.Kind.EMITTER;
            case INTERFACE -> Config.Kind.INTERFACE;
            default -> null;
        };
        if (kind == null) {
            p.sendActionBar(Component.text("A cable joins whatever it touches - nothing to set here.", NamedTextColor.GRAY));
            return;
        }
        Config c = new Config(kind, part, part.block(), part.type.item.displayName);
        c.filter = part.filter;
        drawConfig(c);
        p.openInventory(c.inv);
    }

    private static final String[] PRIORITY = {"Lowest", "Low", "Normal", "High", "Highest"};

    /** A bound copper golem courier: choose what it may carry (Inventory Configurator, sneak + right-click). */
    void openCourierSettings(Player p, UUID golem, ItemStack filter) {
        Config c = new Config(Config.Kind.COURIER, null, null, "Courier filter");
        c.golem = golem;
        c.filter = filter;
        drawConfig(c);
        p.openInventory(c.inv);
    }

    private void drawConfig(Config c) {
        Inventory inv = c.inv;
        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        switch (c.kind) {
            case CONTAINER -> {
                inv.setItem(10, button(Material.COMPARATOR, "Priority: " + PRIORITY[c.settings[0] + 2],
                        "Higher priority fills first and empties last.", "Click to change."));
                inv.setItem(12, filterSlot(c.filter, "Only items passing this filter go in."));
                inv.setItem(14, button(c.settings[1] == 1 ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                        c.settings[1] == 1 ? "Left out of networks" : "Part of networks", "Click to change."));
                inv.setItem(16, button(c.settings[2] == 1 ? Material.ITEM_FRAME : Material.GLASS,
                        c.settings[2] == 1 ? "Keeps the last item in each slot" : "Can be emptied completely",
                        "Keeping one item per slot reserves the slot", "for that item. Click to change."));
                inv.setItem(22, button(Material.SPYGLASS, "Show network", "Highlights every block of this network for 10 s."));
            }
            case HOPPER -> {
                inv.setItem(11, button(Material.HOPPER, "Inventory Hopper", "Moves up to " + plugin.getConfig().getInt("inventory-hopper.items-per-transfer", 8)
                        + " items at a time", "from the block behind it to the block in front.", "Either side may be a whole network,",
                        "a chest, or an Open Crate (front only)."));
                inv.setItem(13, filterSlot(c.filter, "Only these items are moved."));
            }
            case EMITTER -> {
                Part p = c.part;
                inv.setItem(4, button(Material.REDSTONE, p.powered ? "Signal: ON" : "Signal: off"));
                inv.setItem(11, button(p.mode == 0 ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                        p.mode == 0 ? "On when MORE than the amount" : "On when LESS than the amount", "Click to change."));
                ItemStack shown = c.filter == null ? button(Material.ITEM_FRAME, "Item: none", "Click with an item to choose it.") : c.filter.asOne();
                inv.setItem(13, shown);
                inv.setItem(15, button(Material.PAPER, "Amount: " + p.amount));
                int[] steps = {-64, -10, -1, 1, 10, 64};
                int[] slots = {19, 20, 21, 23, 24, 25};
                for (int i = 0; i < 6; i++) {
                    inv.setItem(slots[i], button(steps[i] < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                            (steps[i] > 0 ? "+" : "") + steps[i]));
                }
            }
            case INTERFACE -> {
                Part p = c.part;
                inv.setItem(11, button(p.mode == 0 ? Material.HOPPER : Material.DROPPER, p.mode == 0 ? "Mode: Import" : "Mode: Export",
                        p.mode == 0 ? "Everything put into the barrel goes into the network." : "The barrel is kept stocked with",
                        p.mode == 0 ? "" : "the filter's items, N stacks per item.", "Click to change."));
                inv.setItem(13, filterSlot(c.filter, p.mode == 0 ? "Import only these items." : "Items to keep in stock."));
                if (p.mode == 1) {
                    int stacks = Math.max(1, Math.min(27, p.amount));
                    inv.setItem(15, button(p.amount <= 1 ? Material.CHEST_MINECART : Material.MINECART, "Stock: " + stacks + " stack" + (stacks == 1 ? "" : "s") + " of each",
                            "Click: 1 - 2 - 3 - 4 - 6 - 8 - 12 - 16 - 24.", "The barrel fills to this much, per item."));
                    inv.setItem(24, button(Material.GRAY_STAINED_GLASS_PANE, "Hint", "Export runs every " + plugin.getConfig().getInt("inventory-interface.interval-ticks", 20) + " ticks.", "With an Item Filter in the middle,", "every listed item is kept stocked."));
                }
            }
            case ITEM_FILTER, TAG_FILTER -> { }
            case COURIER -> {
                inv.setItem(11, button(Material.COPPER_BLOCK, "Copper golem courier",
                        "Carries items from copper chests and machine outputs", "to the bound Inventory Interface."));
                inv.setItem(13, filterSlot(c.filter, "Only these items are carried (any item or a filter). Empty: everything."));
                inv.setItem(22, button(Material.IRON_DOOR, "Close", "Freed golem: right-click it with the", "Configurator without sneaking."));
            }
        }
    }

    private static ItemStack filterSlot(ItemStack filter, String what) {
        if (filter != null) {
            ItemStack it = filter.clone();
            ItemMeta m = it.getItemMeta();
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(Items.line("Filter. Click with an empty hand to remove.", NamedTextColor.YELLOW));
            m.lore(lore);
            it.setItemMeta(m);
            return it;
        }
        return button(Material.ITEM_FRAME, "Filter: none", what, "Click with a filter (or any item) to set it.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConfigClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Config c)) return;
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        if (raw >= 27) {
            if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (c.kind == Config.Kind.ITEM_FILTER || c.kind == Config.Kind.TAG_FILTER) {
            filterClick(c, p, raw);
            return;
        }
        ItemStack cursor = p.getItemOnCursor();
        if (c.kind == Config.Kind.COURIER && raw == 22) { p.closeInventory(); return; }
        int filterSlot = c.kind == Config.Kind.CONTAINER ? 12 : 13;
        if (raw == filterSlot && c.kind != Config.Kind.EMITTER) {
            setFilter(c, p, cursor);
        } else if (c.kind == Config.Kind.CONTAINER) {
            switch (raw) {
                case 10 -> c.settings[0] = c.settings[0] >= 2 ? -2 : c.settings[0] + 1;
                case 14 -> c.settings[1] ^= 1;
                case 16 -> c.settings[2] ^= 1;
                case 22 -> {
                    Network n = plugin.networks().at(c.block, null);
                    if (n == null) p.sendActionBar(Component.text("This container is not in a network.", NamedTextColor.GRAY));
                    else highlight(p, n);
                    p.closeInventory();
                    return;
                }
                default -> { return; }
            }
            plugin.networks().saveSettings(c.block, c.settings, c.filter);
        } else if (c.kind == Config.Kind.EMITTER) {
            Part part = c.part;
            switch (raw) {
                case 11 -> part.mode ^= 1;
                case 13 -> {
                    part.filter = cursor.isEmpty() ? null : cursor.asOne();
                    c.filter = part.filter;
                }
                case 19 -> part.amount = Math.max(0, part.amount - 64);
                case 20 -> part.amount = Math.max(0, part.amount - 10);
                case 21 -> part.amount = Math.max(0, part.amount - 1);
                case 23 -> part.amount += 1;
                case 24 -> part.amount += 10;
                case 25 -> part.amount += 64;
                default -> { return; }
            }
            plugin.parts().markDirty(part);
        } else if (c.kind == Config.Kind.INTERFACE && raw == 11) {
            c.part.mode ^= 1;
            plugin.parts().markDirty(c.part);
        } else if (c.kind == Config.Kind.INTERFACE && raw == 15) {
            int[] steps = {1, 2, 3, 4, 6, 8, 12, 16, 24};
            int cur = Math.max(1, Math.min(27, c.part.amount));
            int i = 0;
            while (i < steps.length - 1 && steps[i] < cur) i++;
            c.part.amount = steps[(i + 1) % steps.length];
            plugin.parts().markDirty(c.part);
        } else {
            return;
        }
        drawConfig(c);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.4f);
    }

    /** Filter slot: a Kura filter item is put in (and given back later); any other item is only copied. */
    private void setFilter(Config c, Player p, ItemStack cursor) {
        ItemStack old = c.filter;
        boolean oldReal = old != null && plugin.items().typeOf(old) != null && plugin.items().typeOf(old).filter();
        ItemStack next;
        if (cursor.isEmpty()) {
            next = null;
            if (oldReal) p.setItemOnCursor(old);
        } else if (plugin.items().typeOf(cursor) != null && plugin.items().typeOf(cursor).filter()) {
            next = cursor.asOne();
            ItemStack rest = cursor.getAmount() > 1 ? cursor.asQuantity(cursor.getAmount() - 1) : null;
            p.setItemOnCursor(rest);
            if (oldReal) give(p, old);
        } else {
            next = cursor.asOne();
            if (oldReal) give(p, old);
        }
        c.filter = next;
        if (c.kind == Config.Kind.COURIER) {
            plugin.couriers().setFilter(c.golem, next);
        } else if (c.part != null) {
            c.part.filter = next;
            plugin.parts().markDirty(c.part);
        } else {
            plugin.networks().saveSettings(c.block, c.settings, next);
        }
        drawConfig(c);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConfigDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Config)) return;
        for (int raw : e.getRawSlots()) if (raw < 27) {
            e.setCancelled(true);
            return;
        }
    }

    // ------------------------------------------------------------ filter editors (right-click the filter item)

    void openFilterEditor(Player p, KItem type) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Config c = new Config(type == KItem.ITEM_FILTER ? Config.Kind.ITEM_FILTER : Config.Kind.TAG_FILTER, null, null, type.displayName);
        c.handSlot = p.getInventory().getHeldItemSlot();
        c.filter = hand;
        drawFilter(c, p);
        p.openInventory(c.inv);
    }

    private ItemStack editing(Config c, Player p) {
        ItemStack it = p.getInventory().getItem(c.handSlot);
        KItem t = plugin.items().typeOf(it);
        return t == KItem.ITEM_FILTER || t == KItem.TAG_FILTER ? it : null;
    }

    private void drawFilter(Config c, Player p) {
        ItemStack f = editing(c, p);
        if (f == null) return;
        Inventory inv = c.inv;
        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        boolean deny = plugin.items().flag(f, "filter_deny");
        if (c.kind == Config.Kind.ITEM_FILTER) {
            List<ItemStack> items = plugin.items().filterItems(f);
            for (int i = 0; i < 18; i++) inv.setItem(i, i < items.size() ? items.get(i) : null);
            inv.setItem(21, button(deny ? Material.RED_CONCRETE : Material.LIME_CONCRETE, deny ? "Blocks these items" : "Allows only these items", "Click to change."));
            boolean nbt = plugin.items().flag(f, "filter_nbt");
            inv.setItem(23, button(nbt ? Material.NAME_TAG : Material.PAPER, nbt ? "Exact match (names, enchantments...)" : "Any item of the same kind", "Click to change."));
            inv.setItem(26, button(Material.BOOK, "How to use", "Click a slot with an item to add it,", "click it with an empty hand to remove it."));
        } else {
            List<ItemStack> samples = plugin.items().filterItems(f);
            List<String> sel = tagSelection(f, samples.size());
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, i < samples.size() ? samples.get(i) : null);
                if (i < samples.size()) {
                    String t = sel.get(i);
                    inv.setItem(9 + i, button(t.equals("-") ? Material.GRAY_DYE : Material.LIME_DYE, t.equals("-") ? "No group chosen" : "#" + t.replace("minecraft:", ""),
                            "Click to pick the next group", "this item belongs to."));
                }
            }
            inv.setItem(22, button(deny ? Material.RED_CONCRETE : Material.LIME_CONCRETE, deny ? "Blocks these groups" : "Allows only these groups", "Click to change."));
            inv.setItem(26, button(Material.BOOK, "How to use", "Put an item in the top row,", "then pick one of its groups below it."));
        }
    }

    private List<String> tagSelection(ItemStack f, int n) {
        String s = plugin.items().string(f, "filter_tagsel");
        List<String> out = new ArrayList<>();
        if (s != null) for (String t : s.split(",", -1)) out.add(t.isEmpty() ? "-" : t);
        while (out.size() < n) out.add("-");
        while (out.size() > n) out.remove(out.size() - 1);
        return out;
    }

    private void saveTags(ItemStack f, List<String> sel) {
        plugin.items().setString(f, "filter_tagsel", String.join(",", sel));
        List<String> tags = new ArrayList<>();
        for (String t : sel) if (!t.equals("-") && !tags.contains(t)) tags.add(t);
        plugin.items().setFilterTags(f, tags);
    }

    private static List<String> tagsOf(Material m) {
        List<String> out = new ArrayList<>();
        for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) if (tag.isTagged(m)) out.add(tag.getKey().toString());
        out.sort(null);
        return out;
    }

    private void filterClick(Config c, Player p, int raw) {
        ItemStack f = editing(c, p);
        if (f == null) {
            p.closeInventory();
            return;
        }
        ItemStack cursor = p.getItemOnCursor();
        if (c.kind == Config.Kind.ITEM_FILTER) {
            List<ItemStack> items = plugin.items().filterItems(f);
            if (raw < 18) {
                if (cursor.isEmpty()) {
                    if (raw < items.size()) items.remove(raw);
                } else if (raw < items.size()) {
                    items.set(raw, cursor.asOne());
                } else {
                    items.add(cursor.asOne());
                }
                plugin.items().setFilterItems(f, items);
            } else if (raw == 21) {
                plugin.items().setFlag(f, "filter_deny", !plugin.items().flag(f, "filter_deny"));
            } else if (raw == 23) {
                plugin.items().setFlag(f, "filter_nbt", !plugin.items().flag(f, "filter_nbt"));
            } else return;
        } else {
            List<ItemStack> samples = plugin.items().filterItems(f);
            List<String> sel = tagSelection(f, samples.size());
            if (raw < 9) {
                if (cursor.isEmpty()) {
                    if (raw < samples.size()) {
                        samples.remove(raw);
                        sel.remove(raw);
                    }
                } else if (raw < samples.size()) {
                    samples.set(raw, cursor.asOne());
                    sel.set(raw, "-");
                } else {
                    samples.add(cursor.asOne());
                    sel.add("-");
                }
                plugin.items().setFilterItems(f, samples);
                saveTags(f, sel);
            } else if (raw < 18 && raw - 9 < samples.size()) {
                int i = raw - 9;
                List<String> options = tagsOf(samples.get(i).getType());
                if (options.isEmpty()) {
                    p.sendActionBar(Component.text("This item is in no group.", NamedTextColor.GRAY));
                    return;
                }
                int at = options.indexOf(sel.get(i));
                sel.set(i, at + 1 >= options.size() ? "-" : options.get(at + 1));
                saveTags(f, sel);
            } else if (raw == 22) {
                plugin.items().setFlag(f, "filter_deny", !plugin.items().flag(f, "filter_deny"));
            } else return;
        }
        drawFilter(c, p);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.4f);
    }

    // ------------------------------------------------------------ network browser (wireless terminals)

    /** Chest listing every network with a terminal in range; click opens it. */
    static final class Browser implements InventoryHolder {
        final Inventory inv;
        final boolean remote;
        final List<Part> targets = new ArrayList<>();

        Browser(String title, boolean remote) {
            inv = Bukkit.createInventory(this, 54, Component.text(title));
            this.remote = remote;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void openBrowser(Player p, int range, boolean remote) {
        if (!plugin.getConfig().getBoolean("wireless.browser", true)) {
            p.sendActionBar(Component.text("Look at a Storage or Crafting Terminal (up to " + range + " blocks).", NamedTextColor.GRAY));
            return;
        }
        List<Part> near = plugin.parts().terminalsNear(p.getLocation(), range);
        if (near.isEmpty()) {
            p.sendActionBar(Component.text("No terminals within " + range + " blocks.", NamedTextColor.GRAY));
            return;
        }
        Browser b = new Browser("Networks nearby", remote);
        Set<Network> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        ItemStack pane = pane();
        for (int i = 0; i < 54; i++) b.inv.setItem(i, pane);
        for (Part part : near) {
            if (b.targets.size() >= 45) break;
            Network net = plugin.parts().networkOf(part, null);   // loaded view only: opening it later uses tickets
            if (net == null || !seen.add(net)) continue;
            ItemStack icon = plugin.items().create(part.type.item, 1);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(part.type.item.displayName + "  " + part.x + " " + part.y + " " + part.z, NamedTextColor.GOLD));
            int free = net.freeSlots(), all = net.slots();
            List<Component> lore = new ArrayList<>();
            lore.add(Items.line(net.stores.size() + " container" + (net.stores.size() == 1 ? "" : "s")
                    + (all > 0 ? " · " + free + " of " + all + " slots free" : ""), NamedTextColor.GRAY));
            if (remote) lore.add(Items.line("Opening loads the chunks it needs (max "
                    + plugin.getConfig().getInt("wireless.max-loaded-chunks", 25) + ").", NamedTextColor.DARK_GRAY));
            lore.add(Items.line("Click to open.", NamedTextColor.YELLOW));
            meta.lore(lore);
            icon.setItemMeta(meta);
            b.targets.add(part);
            b.inv.setItem(b.targets.size() - 1, icon);
        }
        if (b.targets.isEmpty()) {
            p.sendActionBar(Component.text("Those terminals are not connected to a network.", NamedTextColor.GRAY));
            return;
        }
        p.openInventory(b.inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBrowserClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Browser b)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= b.targets.size()) return;
        openTerminal((Player) e.getWhoClicked(), b.targets.get(raw), b.remote);
    }

    // ------------------------------------------------------------ item use: filters, wireless terminals

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getItem();
        KItem t = plugin.items().typeOf(hand);
        if (t == null) return;
        Block clicked = e.getClickedBlock();
        if (clicked != null && plugin.parts().at(clicked) != null) return; // handled by Parts
        if (t == KItem.CONFIGURATOR || t == KItem.PAINT_KIT) return; // handled by Parts (on blocks)
        if (clicked != null && clicked.getType().isInteractable() && !e.getPlayer().isSneaking() && t.filter()) return;
        Player p = e.getPlayer();
        switch (t) {
            case ITEM_FILTER, TAG_FILTER -> {
                e.setCancelled(true);
                openFilterEditor(p, t);
            }
            case POLY_FILTER -> {
                e.setCancelled(true);
                p.sendActionBar(Component.text("Nothing to set: put it in a filter slot.", NamedTextColor.GRAY));
            }
            case WIRELESS_TERMINAL -> {
                e.setCancelled(true);
                wireless(p);
            }
            case ADV_WIRELESS_TERMINAL -> {
                e.setCancelled(true);
                advancedWireless(p, hand);
            }
            default -> { }
        }
    }

    private void wireless(Player p) {
        int range = plugin.getConfig().getInt("wireless.range", 16);
        RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        Part part = hit == null || hit.getHitBlock() == null ? null : plugin.parts().at(hit.getHitBlock());
        if (part == null || !part.type.terminal()) {
            openBrowser(p, range, false);   // looking at nothing: choose a nearby network instead
            return;
        }
        openTerminal(p, part, false);
    }

    private void advancedWireless(Player p, ItemStack hand) {
        String bound = plugin.items().string(hand, "bound");
        if (bound == null) {
            openBrowser(p, plugin.getConfig().getInt("wireless.advanced-range", 64), true);
            return;
        }
        String[] f = bound.split(",");
        World w = Bukkit.getWorld(f[0]);
        if (w == null) {
            p.sendActionBar(Component.text("That terminal's world is not loaded.", NamedTextColor.RED));
            return;
        }
        int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]);
        Location at = new Location(w, x + 0.5, y + 0.5, z + 0.5);
        int range = plugin.getConfig().getInt("wireless.advanced-range", 64);
        boolean near = p.getWorld() == w && p.getLocation().distanceSquared(at) <= (double) range * range;
        // hold the terminal's chunk (loads it if needed); kept while the terminal is open
        acquire(w, x >> 4, z >> 4);
        boolean opened = false;
        try {
            Part part = plugin.parts().at(w.getBlockAt(x, y, z));
            if (part == null || !part.type.terminal()) {
                p.sendActionBar(Component.text("The bound terminal is gone.", NamedTextColor.RED));
                return;
            }
            if (!near) {
                int tier = beaconTier(part.block());
                boolean ok = tier >= 4 || (tier >= 1 && p.getWorld() == w);
                if (!ok) {
                    p.sendActionBar(Component.text(p.getWorld() == w ? "Too far away (" + range + " blocks). A beacon next to the terminal extends the range."
                            : "In another world: needs a level 4 beacon next to the terminal.", NamedTextColor.GRAY));
                    return;
                }
            }
            TerminalMenu m = new TerminalMenu(plugin, p, part, true);
            m.tickets.add(Chunk.getChunkKey(x >> 4, z >> 4)); // the ticket taken above now belongs to the menu
            opened = true;
            m.refresh();
            open.put(p.getUniqueId(), m);
            p.openInventory(m.inv);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.8f);
        } finally {
            if (!opened) release(w, x >> 4, z >> 4);
        }
    }

    /** Highest beacon level within 8 blocks of a terminal. */
    private static int beaconTier(Block terminal) {
        int best = 0;
        World w = terminal.getWorld();
        for (int cx = (terminal.getX() - 8) >> 4; cx <= (terminal.getX() + 8) >> 4; cx++) {
            for (int cz = (terminal.getZ() - 8) >> 4; cz <= (terminal.getZ() + 8) >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                for (BlockState st : w.getChunkAt(cx, cz).getTileEntities(b -> b.getType() == Material.BEACON, false)) {
                    if (st.getLocation().distanceSquared(terminal.getLocation()) > 64 + 64 + 64) continue;
                    if (st instanceof Beacon b) best = Math.max(best, b.getTier());
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ helpers

    /** Shows the blocks of a network with particles for 10 seconds (only to this player). */
    void highlight(Player p, Network n) {
        List<Networks.Pos> list = new ArrayList<>(n.members);
        if (list.size() > 400) list = list.subList(0, 400);
        final List<Networks.Pos> shown = list;
        for (int i = 0; i < 10; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                for (Networks.Pos pos : shown) {
                    if (!pos.world().equals(p.getWorld().getUID())) continue;
                    p.spawnParticle(Particle.HAPPY_VILLAGER, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, 2, 0.3, 0.3, 0.3, 0);
                }
            }, i * 20L);
        }
    }

    static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    /** Double-click in a terminal: pin or unpin this kind as favourite (always shown first). */
    private void toggleFav(TerminalMenu m, ItemStack key) {
        boolean on = plugin.favourites().toggle(m.player, Favourites.id(key));
        m.player.sendActionBar(Component.text((on ? "★ Pinned to the top of your terminals." : "☆ Unpinned."),
                on ? NamedTextColor.AQUA : NamedTextColor.GRAY));
        m.player.playSound(m.player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, on ? 1.7f : 0.9f);
    }
}
