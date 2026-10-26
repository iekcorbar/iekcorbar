package com.iekcorbar.sitesync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Custom written books, defined as plain .txt files in
 * plugins/IekcorbarSiteSync/books/ (see the example files written on first
 * start for the exact format), optionally grouped into series with volumes.
 *
 * Books can be handed out with /ibook give|giveseries and can show up in
 * vanilla loot-table chests (dungeons, mineshafts, strongholds ...) through
 * LootGenerateEvent, configured per loot table in books.yml. One roll per
 * generated container: if it succeeds, one eligible book is picked by weight.
 *
 * Every book item carries its id in the PersistentDataContainer
 * (iekcorbarsitesync:book_id) so it can be told apart from a player-written
 * book.
 *
 * Reading a book (right-click with it in hand, opening it on a lectern, or
 * /ibook open) marks it as read in the player's own PersistentDataContainer
 * (iekcorbarsitesync:books_read, comma-separated ids — stored in the vanilla
 * playerdata file, no database). AdvancementManager's book_read / books_read
 * / book_series requirements read that list.
 */
public class BookManager implements Listener, TabExecutor {

    private static final Pattern HEADER_END = Pattern.compile("^={3,}\\s*$");
    private static final Pattern PAGE_BREAK = Pattern.compile("^-{3,}\\s*$");
    private static final int MAX_PAGE_CHARS = 256;   // what fits on one written-book page
    private static final int MAX_TITLE_CHARS = 32;   // vanilla limit for the book's own title

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    record BookDef(String id, String title, String author, String series, int volume, String description,
                   boolean copyable, boolean loot, int weight, Set<String> lootTables, List<String> pages,
                   String model) {}

    private final IekcorbarSiteSync plugin;
    private final File booksDir;
    private final File configFile;
    private final NamespacedKey idKey;
    private final NamespacedKey readKey;
    private final Map<String, BookDef> books = new LinkedHashMap<>();
    private final Map<String, Double> lootChances = new LinkedHashMap<>();
    private boolean enabled = true;
    private boolean lootEnabled = true;
    private boolean discoveryMessage = true;
    private String discoveryStyle = "toast"; // toast | chat | both
    private String defaultModel = "";       // FreeMinecraftModels display model for books without a model: line
    private java.lang.reflect.Method applyDisplayModel;

    public BookManager(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
        this.booksDir = new File(plugin.getDataFolder(), "books");
        this.configFile = new File(plugin.getDataFolder(), "books.yml");
        this.idKey = new NamespacedKey(plugin, "book_id");
        this.readKey = new NamespacedKey(plugin, "books_read");
    }

    // ------------------------------------------------------------------ load

    public void enable() {
        if (!configFile.exists()) plugin.saveResource("books.yml", false);
        if (!booksDir.exists()) {
            // First start only: drop the example files so the format is visible.
            plugin.saveResource("books/example_volume_1.txt", false);
            plugin.saveResource("books/example_volume_2.txt", false);
        }
        // Shipped books are added once if missing (never overwritten, so edits on the server stay)
        if (!new File(booksDir, "engineers_manual.txt").exists()) plugin.saveResource("books/engineers_manual.txt", false);
        if (!new File(booksDir, "brewers_manual.txt").exists()) plugin.saveResource("books/brewers_manual.txt", false);
        if (!new File(booksDir, "not_supposed_to.txt").exists()) plugin.saveResource("books/not_supposed_to.txt", false);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("ibook") != null) {
            plugin.getCommand("ibook").setExecutor(this);
            plugin.getCommand("ibook").setTabCompleter(this);
        }
        reload();
    }

    /** Re-reads books.yml and every books/*.txt. Returns a one-line summary. */
    public String reload() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        enabled = cfg.getBoolean("enabled", true);
        lootEnabled = cfg.getBoolean("loot.enabled", true);
        discoveryMessage = cfg.getBoolean("discovery-message", true);
        discoveryStyle = cfg.getString("discovery-style", "toast").trim().toLowerCase(Locale.ROOT);
        defaultModel = cfg.getString("default-model", "").trim();

        lootChances.clear();
        ConfigurationSection tables = cfg.getConfigurationSection("loot.tables");
        if (tables != null) {
            for (String key : tables.getKeys(false)) {
                lootChances.put(key.toLowerCase(Locale.ROOT), Math.max(0, Math.min(1, tables.getDouble(key))));
            }
        }

        books.clear();
        File[] files = booksDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                try {
                    BookDef def = parse(f);
                    if (def == null) continue;
                    if (books.containsKey(def.id())) { warn(f.getName() + ": duplicate id '" + def.id() + "' - skipped"); continue; }
                    books.put(def.id(), def);
                } catch (Exception e) {
                    warn(f.getName() + ": " + e.getMessage());
                }
            }
        }
        String msg = "Loaded " + books.size() + " book(s) in " + seriesNames().size() + " series; loot "
                + (lootEnabled ? "on (" + lootChances.size() + " loot table rule(s))" : "off") + ".";
        plugin.getLogger().info("[Books] " + msg);
        return msg;
    }

    private BookDef parse(File f) throws IOException {
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        Map<String, String> header = new LinkedHashMap<>();
        int i = 0;
        boolean sawEnd = false;
        for (; i < lines.size(); i++) {
            String line = stripBom(lines.get(i)).stripTrailing();
            if (HEADER_END.matcher(line).matches()) { sawEnd = true; i++; break; }
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int c = t.indexOf(':');
            if (c <= 0) { warn(f.getName() + ": header line without 'key: value' ignored: " + t); continue; }
            header.put(t.substring(0, c).strip().toLowerCase(Locale.ROOT), t.substring(c + 1).strip());
        }
        if (!sawEnd) { warn(f.getName() + ": no '===' line ending the header - skipped"); return null; }

        String baseName = f.getName().substring(0, f.getName().length() - 4);
        String id = cleanId(header.getOrDefault("id", baseName));
        if (id == null) { warn(f.getName() + ": invalid id (use a-z 0-9 _ -) - skipped"); return null; }

        String title = header.get("title");
        if (title == null || title.isBlank()) { warn(f.getName() + ": missing 'title' - skipped"); return null; }

        // Body: pages separated by a line of '---'. Anything too long for one page is split on word boundaries.
        List<String> pages = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (; i < lines.size(); i++) {
            String line = lines.get(i).stripTrailing();
            if (PAGE_BREAK.matcher(line).matches()) { addPage(pages, cur, f.getName()); cur.setLength(0); continue; }
            if (cur.length() > 0) cur.append('\n');
            cur.append(line);
        }
        addPage(pages, cur, f.getName());
        if (pages.isEmpty()) { warn(f.getName() + ": no pages after the header - skipped"); return null; }

        int volume = 0;
        try { volume = Integer.parseInt(header.getOrDefault("volume", "0")); } catch (NumberFormatException ignored) { }
        String series = header.get("series");
        if (series != null && series.isBlank()) series = null;

        Set<String> lootTables = new HashSet<>();
        String lt = header.get("loot-tables");
        if (lt != null) {
            for (String s : lt.split("[,;]")) if (!s.isBlank()) lootTables.add(s.strip().toLowerCase(Locale.ROOT));
        }

        int weight = 10;
        try { weight = Math.max(1, Integer.parseInt(header.getOrDefault("loot-weight", "10"))); } catch (NumberFormatException ignored) { }

        return new BookDef(id, title, header.getOrDefault("author", "Unknown"), series, volume,
                header.getOrDefault("description", ""),
                bool(header.get("copyable"), false), bool(header.get("loot"), true), weight, lootTables, pages,
                header.getOrDefault("model", "").trim());
    }

    private void addPage(List<String> pages, StringBuilder cur, String fileName) {
        String text = cur.toString().strip();
        if (text.isEmpty()) return;
        if (visible(text) <= MAX_PAGE_CHARS) { pages.add(text); return; }
        // Too long for one page: pack whole lines (keeps poems/line breaks intact),
        // only splitting a single line on words when that line alone is too long.
        StringBuilder page = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (visible(page.toString()) + visible(line) + 1 > MAX_PAGE_CHARS && page.length() > 0) {
                pages.add(page.toString().strip());
                page.setLength(0);
            }
            if (visible(line) <= MAX_PAGE_CHARS) {
                if (page.length() > 0) page.append('\n');
                page.append(line);
                continue;
            }
            for (String word : line.split(" +")) {
                if (page.length() > 0 && visible(page.toString()) + 1 + visible(word) > MAX_PAGE_CHARS) {
                    pages.add(page.toString().strip());
                    page.setLength(0);
                }
                if (page.length() > 0) page.append(' ');
                page.append(word);
            }
        }
        if (!page.toString().isBlank()) pages.add(page.toString().strip());
    }

    /** Length as shown in the book — colour/format codes take no space. */
    private static int visible(String s) {
        return s.replaceAll("&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])", "").length();
    }

    // ------------------------------------------------------------------ items

    public ItemStack createItem(BookDef def) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();

        String stripped = def.title().replaceAll("&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])", "");
        if (stripped.length() > MAX_TITLE_CHARS) stripped = stripped.substring(0, MAX_TITLE_CHARS);
        meta.setTitle(stripped);
        meta.setAuthor(def.author());

        // The full (coloured, any length) title is the item's name; the plain
        // book title above is only what vanilla shows when the book is open.
        String named = def.title().matches("(?s)^&.*") ? def.title() : "&6" + def.title();
        meta.displayName(LEGACY.deserialize(named).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (def.series() != null) {
            String line = def.volume() > 0
                    ? "&7Volume &e" + def.volume() + " &7of &e" + def.series()
                    : "&7Part of &e" + def.series();
            lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        if (!def.description().isBlank()) {
            lore.add(LEGACY.deserialize("&8" + def.description()).decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) meta.lore(lore);

        for (String page : def.pages()) meta.addPages(LEGACY.deserialize(page));
        if (!def.copyable()) meta.setGeneration(BookMeta.Generation.TATTERED); // vanilla: cannot be copied
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        applyModel(item, def);
        return item;
    }

    /** The book's own look: its model: line, else books.yml default-model; "none" = vanilla book. */
    private String modelFor(BookDef def) {
        String m = def.model().isEmpty() ? defaultModel : def.model();
        return m.equalsIgnoreCase("none") ? "" : m;
    }

    /**
     * Gives the item the FreeMinecraftModels display model (item_model) of its
     * book - the same mechanism the prop items use. It stays a real written
     * book; only the look in hand/inventory changes. No FMM or unknown model =
     * the vanilla look.
     */
    private void applyModel(ItemStack item, BookDef def) {
        String model = modelFor(def);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (model.isEmpty()) {
            if (meta.hasItemModel()) {
                meta.setItemModel(null);
                item.setItemMeta(meta);
            }
            return;
        }
        try {
            if (applyDisplayModel == null) {
                applyDisplayModel = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.api.ModelItemAPI")
                        .getMethod("applyDisplayModel", ItemStack.class, String.class);
            }
            if (!(boolean) applyDisplayModel.invoke(null, item, model)) {
                plugin.getLogger().warning("[Books] Unknown book model '" + model + "' for " + def.id()
                        + " (needs <model>.bbmodel + <model>.json in FreeMinecraftModels' models folder).");
            }
        } catch (Exception e) {
            // FreeMinecraftModels missing - vanilla look
        }
    }

    /** Books handed out before a model change get the current look the next time they're read. */
    private void refreshModel(Player p, String id) {
        BookDef def = books.get(id);
        if (def == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!id.equals(bookIdOf(hand))) return;
        String want = modelFor(def);
        NamespacedKey have = hand.getItemMeta().getItemModel();
        String haveId = have == null ? "" : have.getKey().replaceFirst("^display/", "");
        if (!want.equals(haveId)) applyModel(hand, def);
    }

    /** The custom book id of this item, or null if it isn't one of ours. */
    public String bookIdOf(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ reading

    /** Ids of every custom book this player has read (a fresh, mutable copy). */
    public Set<String> readBooks(Player p) {
        Set<String> out = new LinkedHashSet<>();
        String raw = p.getPersistentDataContainer().get(readKey, PersistentDataType.STRING);
        if (raw != null) for (String s : raw.split(",")) if (!s.isBlank()) out.add(s);
        return out;
    }

    private void saveRead(Player p, Set<String> read) {
        if (read.isEmpty()) p.getPersistentDataContainer().remove(readKey);
        else p.getPersistentDataContainer().set(readKey, PersistentDataType.STRING, String.join(",", read));
    }

    /** Marks a book as read; true if it was new. Re-checks advancements right away. */
    public boolean markRead(Player p, String id) {
        BookDef b = id == null ? null : books.get(id);
        if (b == null) return false;
        Set<String> read = readBooks(p);
        if (!read.add(id)) return false;
        saveRead(p, read);

        if (discoveryMessage) {
            String progress = "";
            if (b.series() != null) {
                List<String> ids = seriesBookIds(b.series());
                long have = ids.stream().filter(read::contains).count();
                progress = " &8(&e" + have + "&7/&e" + ids.size() + "&8)";
            }
            if (!discoveryStyle.equals("toast")) {
                send(p, "&8[&6Lore&8] &7New lore discovered: &f" + b.title()
                        + (b.series() == null ? "" : progress.replace("&8)", " &7of " + b.series() + "&8)")));
            }
            if (!discoveryStyle.equals("chat")) {
                // Popup top-right; the toast only shows its title, so the series progress goes in there.
                // Next tick, outside the interact event (loading the toast reloads advancement
                // progress); scheduled before the advancement check below, so it shows first.
                Component title = AdvancementManager.text("&6New lore: &f" + b.title() + progress);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) ToastNotifier.showToast(plugin, p, title, "minecraft:written_book", "task");
                });
            }
        }
        AdvancementManager adv = plugin.getAdvancementManager();
        if (adv != null) Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) adv.checkPlayer(p); });
        return true;
    }

    /** Ids of every volume of a series (case-insensitive name), sorted by volume. Empty if unknown. */
    public List<String> seriesBookIds(String series) {
        return booksOfSeries(series).stream().map(BookDef::id).collect(Collectors.toList());
    }

    public boolean hasBook(String id) {
        return id != null && books.containsKey(id.toLowerCase(Locale.ROOT));
    }

    // Right-click with the book in hand opens it; right-clicking a lectern that
    // holds one opens it there. Placing a book ON a lectern also right-clicks
    // the lectern, so that case (empty lectern) is not counted as reading.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRead(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        Block block = e.getClickedBlock();

        if (block != null && block.getType() == Material.LECTERN) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (block.getState() instanceof Lectern lectern) {
                String id = bookIdOf(lectern.getInventory().getItem(0));
                if (id != null) markRead(p, id);
            }
            return;
        }
        if (e.getHand() == null) return;
        if (swapTraderBook(p, e)) return;
        String id = bookIdOf(e.getItem());
        if (id != null) {
            markRead(p, id);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> refreshModel(p, id));
        }
    }

    /**
     * Wandering Trades cannot set the book's data, so the trader sells a plain BOOK named "Engineer's Manual" whose
     * LAST lore line is "Steamworks engineer's manual"; the first right-click swaps it for the real book and opens it.
     */
    private boolean swapTraderBook(Player p, PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) return false;
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) return false;
        String last = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(lore.get(lore.size() - 1));
        if (!last.equals("Steamworks engineer's manual")) return false;
        BookDef def = books.get("engineers_manual");
        if (def == null) return false;
        ItemStack real = createItem(def);
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            for (ItemStack left : p.getInventory().addItem(real).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        } else {
            p.getInventory().setItem(e.getHand(), real);
        }
        e.setCancelled(true);
        p.openBook(real);
        markRead(p, "engineers_manual");
        return true;
    }

    // ------------------------------------------------------------------ loot

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!enabled || !lootEnabled || books.isEmpty() || event.getLootTable() == null) return;
        String table = event.getLootTable().getKey().asString().toLowerCase(Locale.ROOT);
        double chance = chanceFor(table);
        if (chance <= 0) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= chance) return;

        List<BookDef> eligible = books.values().stream()
                .filter(b -> b.loot() && (b.lootTables().isEmpty() || b.lootTables().contains(table)))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) return;

        int total = eligible.stream().mapToInt(BookDef::weight).sum();
        int pick = rnd.nextInt(total);
        for (BookDef b : eligible) {
            pick -= b.weight();
            if (pick < 0) { event.getLoot().add(createItem(b)); return; }
        }
    }

    /** Exact table match first, then the longest wildcard ("minecraft:chests/village/*"). */
    private double chanceFor(String table) {
        Double exact = lootChances.get(table);
        if (exact != null) return exact;
        int bestLen = -1;
        double best = 0;
        for (Map.Entry<String, Double> e : lootChances.entrySet()) {
            String k = e.getKey();
            if (!k.endsWith("*")) continue;
            String prefix = k.substring(0, k.length() - 1);
            if (table.startsWith(prefix) && prefix.length() > bestLen) { bestLen = prefix.length(); best = e.getValue(); }
        }
        return best;
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(sender, label); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> send(sender, "&a" + reload());
            case "list" -> list(sender, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            case "info" -> {
                BookDef b = args.length > 1 ? books.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (b == null) { send(sender, "&cUsage: /" + label + " info <id>"); return true; }
                send(sender, "&6" + b.title() + " &7(id &f" + b.id() + "&7) by &f" + b.author());
                send(sender, "&7Series: &f" + (b.series() == null ? "-" : b.series() + (b.volume() > 0 ? " vol. " + b.volume() : "")));
                send(sender, "&7Pages: &f" + b.pages().size() + " &7| copyable: &f" + b.copyable());
                send(sender, "&7In loot: &f" + b.loot() + " &7(weight " + b.weight() + ")"
                        + (b.lootTables().isEmpty() ? "" : " &7only in: &f" + String.join(", ", b.lootTables())));
            }
            case "give" -> {
                if (args.length < 3) { send(sender, "&cUsage: /" + label + " give <player> <id> [amount]"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                BookDef b = books.get(args[2].toLowerCase(Locale.ROOT));
                if (target == null) { send(sender, "&cPlayer '" + args[1] + "' is not online."); return true; }
                if (b == null) { send(sender, "&cUnknown book '" + args[2] + "'. Try /" + label + " list"); return true; }
                int amount = 1;
                if (args.length > 3) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                    catch (NumberFormatException e) { send(sender, "&cAmount must be a number."); return true; }
                }
                for (int n = 0; n < amount; n++) give(target, createItem(b));
                send(sender, "&aGave &f" + amount + "x " + b.title() + " &ato &f" + target.getName() + "&a.");
            }
            case "giveseries" -> {
                if (args.length < 3) { send(sender, "&cUsage: /" + label + " giveseries <player> <series>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { send(sender, "&cPlayer '" + args[1] + "' is not online."); return true; }
                String series = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                List<BookDef> list = booksOfSeries(series);
                if (list.isEmpty()) { send(sender, "&cNo series called '" + series + "'. Try /" + label + " list"); return true; }
                for (BookDef b : list) give(target, createItem(b));
                send(sender, "&aGave &f" + list.size() + " &avolume(s) of &f" + list.get(0).series() + " &ato &f" + target.getName() + "&a.");
            }
            case "open" -> {
                BookDef b = args.length > 1 ? books.get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (b == null) { send(sender, "&cUsage: /" + label + " open <id> [player]"); return true; }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) { send(sender, "&cSpecify an online player."); return true; }
                target.openBook(createItem(b));
                markRead(target, b.id());
            }
            case "progress" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) { send(sender, "&cUsage: /" + label + " progress <player>"); return true; }
                Set<String> read = readBooks(target);
                send(sender, "&6" + target.getName() + " &7has read &f" + read.stream().filter(books::containsKey).count()
                        + "&7/&f" + books.size() + " &7book(s).");
                for (String s : seriesNames()) {
                    List<String> ids = seriesBookIds(s);
                    long have = ids.stream().filter(read::contains).count();
                    send(sender, " &7" + s + ": &f" + have + "/" + ids.size() + (have == ids.size() ? " &a(complete)" : ""));
                }
            }
            case "forget" -> {
                if (args.length < 2) { send(sender, "&cUsage: /" + label + " forget <player> [id|all]"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { send(sender, "&cPlayer '" + args[1] + "' is not online."); return true; }
                Set<String> read = readBooks(target);
                String what = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all";
                boolean changed = what.equals("all") ? !read.isEmpty() : read.remove(what);
                if (what.equals("all")) read.clear();
                saveRead(target, read);
                send(sender, changed ? "&aForgot " + (what.equals("all") ? "every read book" : "'" + what + "'") + " for " + target.getName()
                        + ". &7(Advancements already earned stay; use /iadv revoke for those.)" : "&7Nothing to forget.");
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        send(s, "&6/" + label + " list [series] &7- all books / the volumes of a series");
        send(s, "&6/" + label + " info <id> &7- details of one book");
        send(s, "&6/" + label + " give <player> <id> [amount] &7- give a book");
        send(s, "&6/" + label + " giveseries <player> <series> &7- give every volume of a series");
        send(s, "&6/" + label + " open <id> [player] &7- open a book to read (no item; counts as read)");
        send(s, "&6/" + label + " progress <player> &7- which books / series a player has read");
        send(s, "&6/" + label + " forget <player> [id|all] &7- reset read status (testing)");
        send(s, "&6/" + label + " reload &7- re-read books.yml and books/*.txt");
    }

    private void list(CommandSender sender, String series) {
        if (books.isEmpty()) { send(sender, "&7No books loaded. Put .txt files in plugins/IekcorbarSiteSync/books/"); return; }
        if (series != null) {
            List<BookDef> list = booksOfSeries(series);
            if (list.isEmpty()) { send(sender, "&cNo series called '" + series + "'."); return; }
            send(sender, "&6" + list.get(0).series() + " &7(" + list.size() + " volume(s))");
            for (BookDef b : list) send(sender, " &7" + (b.volume() > 0 ? "Vol. " + b.volume() : "-") + " &f" + b.id() + " &8- " + b.title());
            return;
        }
        for (String s : seriesNames()) send(sender, "&6[Series] &f" + s + " &7(" + booksOfSeries(s).size() + " volume(s))");
        for (BookDef b : books.values()) if (b.series() == null) send(sender, "&7- &f" + b.id() + " &8- " + b.title());
    }

    private void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private List<String> seriesNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (BookDef b : books.values()) if (b.series() != null) names.putIfAbsent(b.series().toLowerCase(Locale.ROOT), b.series());
        return new ArrayList<>(names.values());
    }

    private List<BookDef> booksOfSeries(String series) {
        return books.values().stream()
                .filter(b -> b.series() != null && b.series().equalsIgnoreCase(series.strip()))
                .sorted(Comparator.comparingInt(BookDef::volume).thenComparing(BookDef::id))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(List.of("list", "info", "give", "giveseries", "open", "progress", "forget", "reload"));
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && Set.of("give", "giveseries", "progress", "forget").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 3 && sub.equals("forget")) {
                out.add("all");
                out.addAll(books.keySet());
            } else if ((args.length == 2 && (sub.equals("info") || sub.equals("open"))) || (args.length == 3 && sub.equals("give"))) {
                out.addAll(books.keySet());
            } else if (sub.equals("list") || (args.length >= 3 && sub.equals("giveseries"))) {
                out.addAll(seriesNames());
            } else if (args.length == 3 && sub.equals("open")) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ helpers

    private static void send(CommandSender to, String legacy) {
        to.sendMessage(LEGACY.deserialize(legacy));
    }

    private void warn(String msg) {
        plugin.getLogger().warning("[Books] " + msg);
    }

    private static boolean bool(String v, boolean def) {
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1");
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }

    private static String cleanId(String raw) {
        if (raw == null) return null;
        String id = raw.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        return id.matches("[a-z0-9_-]+") ? id : null;
    }
}
