package com.iekcorbar.quests;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * IekItems GUI: item-browser, item-editor en model-kiezer (items uit de resourcepacks).
 * Werkt los (/iekitem) of als kiezer voor de quest-wizard (openPicker). Alle wijzigingen
 * gaan direct naar items.yml via ItemLibrary.
 */
final class ItemEditor implements Listener {

    static final class IH implements InventoryHolder {
        final UUID who; final String page; final String arg; final int n;
        IH(UUID who, String page, String arg, int n) { this.who = who; this.page = page; this.arg = arg; this.n = n; }
        @Override public Inventory getInventory() { return null; }
    }

    /** Per speler: kiezer-callbacks + wachtende chat-invoer + filters. */
    static final class Sess {
        BiConsumer<String, Integer> onPick; Runnable onCancel; boolean askAmount;
        String ctx; String ctxId; List<String> acc = new ArrayList<>(); boolean multi, clearRequested;
        String modelFilter = ""; String itemFilter = "";
        void reset() { onPick = null; onCancel = null; askAmount = false; ctx = null; acc = new ArrayList<>(); }
    }

    private static final TextDecoration ITALIC = TextDecoration.ITALIC;

    private final IekQuestsPlugin plugin;
    private final ItemLibrary lib;
    private final NamespacedKey tagKey;
    private final Map<UUID, Sess> sessions = new HashMap<>();
    private volatile List<String> modelCache = null;
    private volatile boolean scanning = false;

    ItemEditor(IekQuestsPlugin plugin, ItemLibrary lib) {
        this.plugin = plugin;
        this.lib = lib;
        this.tagKey = new NamespacedKey(plugin, "ie");
    }

    private Sess session(Player p) { return sessions.computeIfAbsent(p.getUniqueId(), u -> new Sess()); }

    // ---------------------------------------------------------------- toegang

    void open(Player p) { session(p).reset(); browser(p, 0); }

    void edit(Player p, String id) { session(p).reset(); editor(p, id); }

    /** Voor de quest-wizard: speler kiest/maakt een item; callback krijgt id (+aantal). */
    void openPicker(Player p, boolean askAmount, BiConsumer<String, Integer> onPick, Runnable onCancel) {
        var ss = session(p);
        ss.reset();
        ss.onPick = onPick; ss.onCancel = onCancel; ss.askAmount = askAmount;
        browser(p, 0);
    }

    // ---------------------------------------------------------------- bouwstenen

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s).decoration(ITALIC, false);
    }

    private ItemStack btn(Material m, String name, String tag, String... lore) {
        var it = new ItemStack(m);
        var im = it.getItemMeta();
        im.displayName(legacy(name));
        List<Component> lo = new ArrayList<>();
        for (String l : lore) lo.add(legacy(l));
        im.lore(lo);
        im.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack tagItem(ItemStack it, String tag, String... extra) {
        var im = it.getItemMeta();
        List<Component> lo = im.lore() == null ? new ArrayList<>() : new ArrayList<>(im.lore());
        for (String l : extra) lo.add(legacy(l));
        im.lore(lo);
        im.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    private static String yn(boolean b) { return b ? "&2ON" : "&7off"; }

    // ---------------------------------------------------------------- browser

    private void browser(Player p, int page) {
        var ss = session(p);
        List<String> ids = new ArrayList<>();
        for (String id : lib.ids()) if (ss.itemFilter.isEmpty() || id.contains(ss.itemFilter)) ids.add(id);
        int pages = Math.max(1, (ids.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        var inv = Bukkit.createInventory(new IH(p.getUniqueId(), "browse", "", page), 54,
                legacy(ss.onPick != null ? "&8Pick an item" : "&8IekItems"));
        for (int i = 0; i < 45 && page * 45 + i < ids.size(); i++) {
            String id = ids.get(page * 45 + i);
            var it = lib.build(id, 1);
            if (it == null) continue;
            inv.setItem(i, tagItem(it, "open:" + id, "", "&8id: " + id,
                    ss.onPick != null ? "&aClick: select  &7Right-click: edit" : "&aClick: edit  &7Shift-click: get a copy"));
        }
        if (page > 0) inv.setItem(45, btn(Material.ARROW, "&ePrevious", "page:" + (page - 1)));
        if (page < pages - 1) inv.setItem(46, btn(Material.ARROW, "&eNext", "page:" + (page + 1)));
        inv.setItem(48, btn(Material.COMPASS, "&bSearch", "search",
                "&7filter: &f" + (ss.itemFilter.isEmpty() ? "-" : ss.itemFilter), "&7Click, then type part of an id."));
        inv.setItem(49, btn(Material.LIME_DYE, "&a+ New item", "new", "&7Type an id in chat, then edit it here."));
        inv.setItem(50, btn(Material.HOPPER, "&bFrom my hand", "fromhand", "&7Save the item in your hand as a new item."));
        inv.setItem(53, btn(Material.BARRIER, ss.onPick != null ? "&cCancel" : "&cClose", "close"));
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- editor

    private void editor(Player p, String id) {
        var s = lib.def(id);
        if (s == null) { browser(p, 0); return; }
        var ss = session(p);
        var inv = Bukkit.createInventory(new IH(p.getUniqueId(), "edit", id, 0), 54, legacy("&8Item: " + id));
        var pv = lib.build(id, 1);
        if (pv != null) inv.setItem(4, pv);
        var ench = s.getConfigurationSection("enchants");
        inv.setItem(10, btn(Material.IRON_INGOT, "&6Material", "f:material",
                "&7current: &f" + s.getString("material", "PAPER"), "&7Type a material, or 'hand'."));
        inv.setItem(11, btn(Material.NAME_TAG, "&6Name", "f:name", "&7current: &f" + s.getString("name", "-"), "&7& colors work."));
        inv.setItem(12, btn(Material.WRITABLE_BOOK, "&6Lore", "f:lore", "&7lines: &f" + s.getStringList("lore").size(),
                "&7One line per message, 'done' ends, 'clear' empties."));
        inv.setItem(13, btn(Material.ENCHANTED_BOOK, "&6Enchants", "f:enchants",
                "&7set: &f" + (ench == null ? 0 : ench.getKeys(false).size()), "&7Lines like: &fsharpness 3"));
        inv.setItem(14, btn(Material.GLOWSTONE_DUST, "&6Glow", "t:glow", yn(s.getBoolean("glow"))));
        inv.setItem(15, btn(Material.ANVIL, "&6Unbreakable", "t:unbreakable", yn(s.getBoolean("unbreakable"))));
        inv.setItem(16, btn(Material.APPLE, "&6Consume on use", "t:consume", yn(s.getBoolean("consume"))));
        inv.setItem(19, btn(Material.PAINTING, "&d&lModel", "models",
                "&7current: &f" + s.getString("item-model", "vanilla look"), "&7Click to browse models from the resourcepack."));
        inv.setItem(20, btn(Material.IRON_SWORD, "&6Attributes", "f:attributes", "&7lines: &f" + s.getStringList("attributes").size(),
                "&7e.g. &fattack_damage 7 add_number mainhand"));
        inv.setItem(21, btn(Material.BLACK_BANNER, "&6Flags", "f:flags", "&7" + s.getStringList("flags"),
                "&7e.g. HIDE_ATTRIBUTES, HIDE_ENCHANTS"));
        inv.setItem(22, btn(Material.COMMAND_BLOCK, "&dRight-click actions", "f:use", "&7lines: &f" + s.getStringList("use").size(),
                "&7Same syntax as quest rewards."));
        inv.setItem(23, btn(Material.CLOCK, "&6Use cooldown (s)", "f:use-cooldown", "&7current: &f" + s.getDouble("use-cooldown", 1.0)));
        inv.setItem(24, btn(Material.TRIPWIRE_HOOK, "&6Use permission", "f:use-permission", "&7current: &f" + s.getString("use-permission", "-")));
        inv.setItem(31, btn(Material.CHEST, "&aGet a copy", "get"));
        inv.setItem(33, btn(Material.LAVA_BUCKET, "&cDelete (shift-click)", "delete"));
        if (ss.onPick != null) inv.setItem(40, btn(Material.EMERALD_BLOCK, "&a&lSelect this item", "select"));
        inv.setItem(45, btn(Material.ARROW, "&cBack", "back"));
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- modellen uit de resourcepacks

    private File packDir(String path) {
        var f = new File(path);
        return f.isAbsolute() ? f : new File(plugin.getDataFolder().getParentFile().getParentFile(), path);
    }

    private void scanModels(Runnable done) {
        if (scanning) return;
        scanning = true;
        List<String> dirs = new ArrayList<>(plugin.getConfig().getStringList("pack-dirs"));
        if (dirs.isEmpty()) dirs = List.of("plugins/ResourcePackManager/mixer", "plugins/IekQuests/packs");
        final List<String> scanDirs = dirs;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TreeSet<String> out = new TreeSet<>();
            var pat = java.util.regex.Pattern.compile("assets/([^/]+)/items/(.+)\\.json");
            for (String d : scanDirs) {
                var zips = packDir(d).listFiles((x, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip"));
                if (zips == null) continue;
                for (var z : zips) {
                    try (var zf = new java.util.zip.ZipFile(z)) {
                        var en = zf.entries();
                        while (en.hasMoreElements()) {
                            var m = pat.matcher(en.nextElement().getName());
                            if (m.matches() && !m.group(1).equals("minecraft")) out.add(m.group(1) + ":" + m.group(2));
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("IekItems: pack " + z.getName() + ": " + ex.getMessage());
                    }
                }
            }
            modelCache = new ArrayList<>(out);
            scanning = false;
            plugin.getLogger().info("IekItems: " + out.size() + " item model(s) found in resourcepacks.");
            Bukkit.getScheduler().runTask(plugin, done);
        });
    }

    private void models(Player p, String id, int page) {
        if (modelCache == null) {
            p.sendMessage(legacy("&7Scanning resourcepacks for models..."));
            scanModels(() -> { if (p.isOnline()) models(p, id, 0); });
            return;
        }
        var ss = session(p);
        List<String> list = new ArrayList<>();
        for (String m : modelCache) if (ss.modelFilter.isEmpty() || m.contains(ss.modelFilter)) list.add(m);
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        var s = lib.def(id);
        Material base = Material.PAPER;
        if (s != null) { var m = Material.matchMaterial(s.getString("material", "PAPER")); if (m != null && !m.isAir()) base = m; }
        var inv = Bukkit.createInventory(new IH(p.getUniqueId(), "models", id, page), 54,
                legacy("&8Models (" + list.size() + ") " + (page + 1) + "/" + pages));
        for (int i = 0; i < 45 && page * 45 + i < list.size(); i++) {
            String m = list.get(page * 45 + i);
            var it = new ItemStack(base);
            var im = it.getItemMeta();
            var nk = NamespacedKey.fromString(m);
            if (nk != null) im.setItemModel(nk);
            im.displayName(legacy("&f" + m));
            im.lore(List.of(legacy("&aClick: use this model")));
            im.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, "model:" + m);
            it.setItemMeta(im);
            inv.setItem(i, it);
        }
        if (page > 0) inv.setItem(45, btn(Material.ARROW, "&ePrevious", "mpage:" + (page - 1)));
        if (page < pages - 1) inv.setItem(46, btn(Material.ARROW, "&eNext", "mpage:" + (page + 1)));
        inv.setItem(47, btn(Material.COMPASS, "&bSearch", "msearch", "&7filter: &f" + (ss.modelFilter.isEmpty() ? "-" : ss.modelFilter)));
        inv.setItem(48, btn(Material.PAPER, "&eType a model id", "mtype", "&7e.g. mmoitems:sword/long_sword"));
        inv.setItem(49, btn(Material.REDSTONE, "&cRemove model (vanilla look)", "mclear"));
        inv.setItem(50, btn(Material.SPYGLASS, "&bRescan packs", "mrescan"));
        inv.setItem(53, btn(Material.ARROW, "&cBack", "mback"));
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof IH h) || !h.who.equals(p.getUniqueId())) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        var cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        String tag = cur.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if (tag == null) return;
        var ss = session(p);
        boolean right = e.isRightClick(), shift = e.isShiftClick();

        switch (h.page) {
            case "browse" -> {
                if (tag.startsWith("page:")) { browser(p, Integer.parseInt(tag.substring(5))); return; }
                if (tag.startsWith("open:")) {
                    String id = tag.substring(5);
                    if (ss.onPick != null && !right) { finish(p, id); return; }
                    if (ss.onPick == null && shift) { lib.give(p, id, 1); return; }
                    editor(p, id);
                    return;
                }
                switch (tag) {
                    case "close" -> { p.closeInventory(); var c = ss.onCancel; ss.reset(); if (c != null) c.run(); }
                    case "search" -> ask(p, "search", null, false, "Type part of an item id (or 'clear')");
                    case "new" -> ask(p, "newid", null, false, "Type an id for the new item (a-z, 0-9, _)");
                    case "fromhand" -> ask(p, "handid", null, false, "Type an id; the item in your hand is saved under it");
                    default -> { }
                }
            }
            case "edit" -> {
                String id = h.arg;
                if (tag.startsWith("f:")) {
                    String f = tag.substring(2);
                    boolean multi = List.of("lore", "enchants", "attributes", "flags", "use").contains(f);
                    String prompt = switch (f) {
                        case "material" -> "Type a material (e.g. IRON_SWORD) or 'hand'";
                        case "name" -> "Type the display name (& colors)";
                        case "lore" -> "Type lore lines, one per message; 'done' to finish, 'clear' to empty";
                        case "enchants" -> "Type enchants as 'sharpness 3', one per message; 'done' to finish, 'clear' to empty";
                        case "attributes" -> "Type attribute lines like 'attack_damage 7 add_number mainhand'; 'done' / 'clear'";
                        case "flags" -> "Type ItemFlags (HIDE_ATTRIBUTES, HIDE_ENCHANTS...), one per message; 'done' / 'clear'";
                        case "use" -> "Type right-click actions (quest-reward syntax); 'done' / 'clear'";
                        case "use-cooldown" -> "Type the cooldown in seconds";
                        default -> "Type the value (or 'clear')";
                    };
                    ask(p, f, id, multi, prompt);
                    return;
                }
                if (tag.startsWith("t:")) {
                    String f = tag.substring(2);
                    lib.put(id, f, !lib.def(id).getBoolean(f));
                    editor(p, id);
                    return;
                }
                switch (tag) {
                    case "models" -> models(p, id, 0);
                    case "get" -> lib.give(p, id, 1);
                    case "select" -> finish(p, id);
                    case "delete" -> {
                        if (!shift) { p.sendMessage(legacy("&cShift-click to really delete it.")); return; }
                        lib.remove(id);
                        browser(p, 0);
                    }
                    case "back" -> browser(p, 0);
                    default -> { }
                }
            }
            case "models" -> {
                String id = h.arg;
                if (tag.startsWith("model:")) { lib.put(id, "item-model", tag.substring(6)); editor(p, id); return; }
                if (tag.startsWith("mpage:")) { models(p, id, Integer.parseInt(tag.substring(6))); return; }
                switch (tag) {
                    case "msearch" -> ask(p, "msearch", id, false, "Type part of a model name (or 'clear')");
                    case "mtype" -> ask(p, "mtype", id, false, "Type the full model id (namespace:path)");
                    case "mclear" -> { lib.put(id, "item-model", null); editor(p, id); }
                    case "mrescan" -> { modelCache = null; models(p, id, 0); }
                    case "mback" -> editor(p, id);
                    default -> { }
                }
            }
            default -> { }
        }
    }

    private void finish(Player p, String id) {
        var ss = session(p);
        var cb = ss.onPick;
        if (cb == null) return;
        if (ss.askAmount) { ask(p, "amount", id, false, "How many? (number)"); return; }
        ss.reset();
        p.closeInventory();
        cb.accept(id, 1);
    }

    // ---------------------------------------------------------------- chat-invoer

    private void ask(Player p, String ctx, String id, boolean multi, String prompt) {
        var ss = session(p);
        ss.ctx = ctx; ss.ctxId = id; ss.multi = multi; ss.acc = new ArrayList<>(); ss.clearRequested = false;
        p.closeInventory();
        p.sendMessage(legacy("&6IekItems: &f" + prompt + " &7('cancel' aborts)"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        var ss = sessions.get(e.getPlayer().getUniqueId());
        if (ss == null || ss.ctx == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        var p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> input(p, ss, text));
    }

    private void input(Player p, Sess ss, String text) {
        String ctx = ss.ctx, id = ss.ctxId;
        if (ctx == null) return;
        if (text.equalsIgnoreCase("cancel")) { ss.ctx = null; back(p, id, ctx); return; }
        boolean clear = text.equalsIgnoreCase("clear");

        if (ss.multi) {
            if (clear) { ss.acc.clear(); ss.clearRequested = true; text = "done"; }
            if (!text.equalsIgnoreCase("done")) { ss.acc.add(text); p.sendMessage(legacy("&7+ " + text)); return; }
            List<String> lines = new ArrayList<>(ss.acc);
            ss.ctx = null;
            switch (ctx) {
                case "enchants" -> {
                    lib.setEnchants(id, lines);
                }
                case "flags" -> {
                    List<String> fl = new ArrayList<>();
                    for (String l : lines) fl.add(l.toUpperCase(Locale.ROOT));
                    lib.put(id, "flags", fl.isEmpty() ? null : fl);
                }
                default -> lib.put(id, ctx, lines.isEmpty() ? null : lines);
            }
            back(p, id, ctx);
            return;
        }

        ss.ctx = null;
        switch (ctx) {
            case "search" -> { ss.itemFilter = clear ? "" : text.toLowerCase(Locale.ROOT); browser(p, 0); }
            case "msearch" -> { ss.modelFilter = clear ? "" : text.toLowerCase(Locale.ROOT); models(p, id, 0); }
            case "mtype" -> {
                if (NamespacedKey.fromString(text.toLowerCase(Locale.ROOT)) == null) p.sendMessage(legacy("&cNot a valid model id."));
                else lib.put(id, "item-model", text.toLowerCase(Locale.ROOT));
                editor(p, id);
            }
            case "newid", "handid" -> {
                String nid = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
                if (nid.isEmpty() || lib.exists(nid)) { p.sendMessage(legacy("&cInvalid or already used id.")); browser(p, 0); return; }
                if (ctx.equals("handid")) {
                    var hand = p.getInventory().getItemInMainHand();
                    if (hand.getType() == Material.AIR) { p.sendMessage(legacy("&cYour hand is empty.")); browser(p, 0); return; }
                    lib.saveFromStack(nid, hand);
                } else {
                    lib.put(nid, "material", "PAPER");
                    lib.put(nid, "name", "&f" + nid);
                }
                editor(p, nid);
            }
            case "amount" -> {
                int n;
                try { n = Math.max(1, Integer.parseInt(text)); } catch (Exception ex) { n = 1; }
                var cb = ss.onPick;
                ss.reset();
                if (cb != null) cb.accept(id, n);
            }
            case "material" -> {
                Material m = text.equalsIgnoreCase("hand") ? p.getInventory().getItemInMainHand().getType() : Material.matchMaterial(text);
                if (m == null || m.isAir()) p.sendMessage(legacy("&cUnknown material."));
                else lib.put(id, "material", m.name());
                editor(p, id);
            }
            case "use-cooldown" -> {
                try { lib.put(id, "use-cooldown", Double.parseDouble(text)); } catch (Exception ex) { p.sendMessage(legacy("&cNot a number.")); }
                editor(p, id);
            }
            default -> { lib.put(id, ctx, clear ? null : text); editor(p, id); }
        }
    }

    private void back(Player p, String id, String ctx) {
        switch (ctx) {
            case "search", "newid", "handid" -> browser(p, 0);
            case "msearch", "mtype" -> models(p, id, 0);
            case "amount" -> { var ss = session(p); if (ss.onPick != null) browser(p, 0); else editor(p, id); }
            default -> editor(p, id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
}
