package com.iekcorbar.sitesync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Config-driven custom advancements (plugins/IekcorbarSiteSync/advancements.yml).
 *
 * Every tab + advancement in the file is registered as a real advancement at
 * startup (and on /iadv reload, and again after a /minecraft:reload, which
 * would otherwise wipe them), so it shows up in the advancement screen with
 * its own tab, toast and chat announcement. Progress is stored by Minecraft
 * itself (world/advancements/*.json), keyed by the advancement id — so ids
 * must stay stable.
 *
 * Each advancement has one "impossible" criterion that this class awards
 * when its requirements are met: automatic requirements (statistics,
 * placeholders, permissions, location, other advancements) are checked for
 * online players every check-interval-seconds and on join; 'website' ones
 * when the website awards the matching achievement (MySQLSync); 'manual' only
 * via /iadv grant. Rewards run from PlayerAdvancementDoneEvent, so they also
 * fire for a vanilla /advancement grant.
 *
 * Registration uses Bukkit.getUnsafe().loadAdvancement — deprecated but not
 * scheduled for removal, and Paper 26.2 still has no stable alternative
 * (same as ToastNotifier). Loading an advancement makes the server reload
 * every online player's advancement progress from disk, so player data is
 * saved first (Bukkit.savePlayers()) to never lose freshly earned progress.
 */
public class AdvancementManager implements Listener, TabExecutor {

    private static final String ROOT = "root";
    private static final String TOAST_PREFIX = "shop_toast_"; // ToastNotifier's throwaway keys — never touch

    private final IekcorbarSiteSync plugin;
    private final File file;
    private final Map<String, TabDef> tabs = new LinkedHashMap<>();
    private final Map<String, AdvDef> advancements = new LinkedHashMap<>();
    /** key -> the JSON we last successfully loaded for it; lets registerAll() skip untouched work. */
    private final Map<NamespacedKey, String> registeredJson = new LinkedHashMap<>();
    /** Changed advancements that are parked on disk and only go live after a restart. */
    private int pendingRestart;
    private BukkitTask checkTask;
    private Method papiSetPlaceholders;
    private boolean papiResolved;

    record TabDef(String id, String title, String description, String icon, String background) {}

    static final class AdvDef {
        String id, tab, parent, title, description, icon, frame, rewardMessage;
        boolean hidden, announce, toast;
        int rewardXp;
        List<Map<?, ?>> requirements = new ArrayList<>();
        List<String> rewardCommands = new ArrayList<>();
    }

    public AdvancementManager(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "advancements.yml");
    }

    // ------------------------------------------------------------------ load

    public void enable() {
        if (!file.exists()) plugin.saveResource("advancements.yml", false);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("iadv") != null) {
            plugin.getCommand("iadv").setExecutor(this);
            plugin.getCommand("iadv").setTabCompleter(this);
        }
        reload();
    }

    public void disable() {
        if (checkTask != null) checkTask.cancel();
        registeredJson.clear();
    }

    /** Re-reads advancements.yml and (re)registers everything. Main thread only. */
    public String reload() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        tabs.clear();
        advancements.clear();

        ConfigurationSection tabSec = cfg.getConfigurationSection("tabs");
        if (tabSec != null) {
            for (String rawId : tabSec.getKeys(false)) {
                String id = cleanId(rawId);
                ConfigurationSection t = tabSec.getConfigurationSection(rawId);
                if (t == null || id == null) { warn("Skipping tab '" + rawId + "': invalid id (use a-z 0-9 _ -)"); continue; }
                tabs.put(id, new TabDef(id,
                        t.getString("title", id),
                        t.getString("description", ""),
                        t.getString("icon", "minecraft:book"),
                        t.getString("background", "minecraft:block/cherry_planks")));
            }
        }

        ConfigurationSection advSec = cfg.getConfigurationSection("advancements");
        if (advSec != null) {
            for (String rawId : advSec.getKeys(false)) {
                String id = cleanId(rawId);
                ConfigurationSection a = advSec.getConfigurationSection(rawId);
                if (a == null || id == null || ROOT.equals(id)) { warn("Skipping advancement '" + rawId + "': invalid id"); continue; }
                AdvDef d = new AdvDef();
                d.id = id;
                d.tab = cleanId(a.getString("tab", tabs.isEmpty() ? "iekcorbar" : tabs.keySet().iterator().next()));
                d.parent = a.getString("parent") == null ? null : cleanId(a.getString("parent"));
                d.title = a.getString("title", id);
                d.description = a.getString("description", "");
                d.icon = a.getString("icon", "minecraft:paper");
                d.frame = a.getString("frame", "task").toLowerCase(Locale.ROOT);
                if (!Set.of("task", "goal", "challenge").contains(d.frame)) d.frame = "task";
                d.hidden = a.getBoolean("hidden", false);
                d.announce = a.getBoolean("announce", true);
                d.toast = a.getBoolean("toast", true);
                d.requirements = a.getMapList("requirements");
                d.rewardCommands = a.getStringList("rewards.commands");
                d.rewardXp = a.getInt("rewards.xp", 0);
                d.rewardMessage = a.getString("rewards.message");
                if (!tabs.containsKey(d.tab)) { warn("Advancement '" + id + "' uses unknown tab '" + d.tab + "' — skipped"); continue; }
                advancements.put(id, d);
            }
        }

        int registered = registerAll();

        if (checkTask != null) checkTask.cancel();
        long interval = Math.max(5, cfg.getInt("check-interval-seconds", 20)) * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAllOnline, interval, interval);

        String msg = "Loaded " + tabs.size() + " tab(s) and " + registered + " custom advancement(s)."
                + (pendingRestart > 0 ? " " + pendingRestart + " changed one(s) apply after the next server restart." : "");
        plugin.getLogger().info("[Advancements] " + msg);
        return msg;
    }

    @SuppressWarnings("deprecation")
    private int registerAll() {
        // Build everything first in a want-map: tab roots, then advancements (parent before child).
        Map<NamespacedKey, JsonObject> want = new LinkedHashMap<>();
        for (TabDef t : tabs.values()) {
            JsonObject root = new JsonObject();
            JsonObject display = display(t.icon(), t.title(), t.description(), "task", false, false, false);
            display.addProperty("background", t.background());
            root.add("display", display);
            root.add("criteria", JsonParser.parseString("{\"tick\":{\"trigger\":\"minecraft:tick\"}}"));
            want.put(rootKey(t.id()), root);
        }

        Set<String> done = new HashSet<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (AdvDef d : advancements.values()) {
                if (done.contains(d.id)) continue;
                if (d.parent != null && !done.contains(d.parent)) {
                    if (!advancements.containsKey(d.parent)) {
                        warn("Advancement '" + d.id + "' has unknown parent '" + d.parent + "' — placed directly under its tab");
                        d.parent = null;
                    } else {
                        continue;
                    }
                }
                JsonObject json = new JsonObject();
                json.addProperty("parent", (d.parent == null ? rootKey(d.tab) : advKey(advancements.get(d.parent))).toString());
                json.add("display", display(d.icon, d.title, d.description, d.frame, d.toast, d.announce, d.hidden));
                json.add("criteria", JsonParser.parseString("{\"done\":{\"trigger\":\"minecraft:impossible\"}}"));
                want.put(advKey(d), json);
                done.add(d.id);
                progress = true;
            }
        }

        // Idempotent core: a key that is still registered with exactly the JSON we loaded before is
        // LEFT ALONE. Paper's removeAdvancement silently refuses on some builds, which used to turn
        // the startup double-load (onEnable + ServerResourcesReloadedEvent) into a wall of
        // "already exists" warnings. Nothing changed -> no registry touch, no savePlayers() either
        // (that save is where the "manual (plugin-induced) save" warning came from).
        // Fresh boot: registeredJson is empty, but Paper has already read the copies that
        // loadAdvancement persisted in <world>/datapacks/bukkit/. If such a copy is identical
        // to what we want it is ADOPTED instead of removed + reloaded - the old route deleted
        // those files while the in-memory copy stayed, so the reload failed with
        // "already exists" and the files were gone again on the next start.
        Set<NamespacedKey> keep = new HashSet<>();
        for (Map.Entry<NamespacedKey, JsonObject> e : want.entrySet()) {
            String json = e.getValue().toString();
            String last = registeredJson.get(e.getKey());
            if (Bukkit.getAdvancement(e.getKey()) == null) continue;
            if ((last != null && last.equals(json)) || (last == null && matchesPersisted(e.getKey(), e.getValue()))) {
                keep.add(e.getKey());
                registeredJson.put(e.getKey(), json);
            }
        }

        if (keep.size() < want.size()) {
            if (!Bukkit.getOnlinePlayers().isEmpty()) Bukkit.savePlayers(); // loading reloads progress from disk
            List<NamespacedKey> old = new ArrayList<>();
            for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
                NamespacedKey k = it.next().getKey();
                if (k.getNamespace().equals(ns()) && !k.getKey().startsWith(TOAST_PREFIX) && !keep.contains(k)) old.add(k);
            }
            // Children first, so no parent disappears under a child.
            old.sort((x, y) -> Integer.compare(y.getKey().length(), x.getKey().length()));
            for (NamespacedKey k : old) {
                try { Bukkit.getUnsafe().removeAdvancement(k); } catch (Exception ignored) { }
                registeredJson.remove(k);
            }
        }

        int count = 0;
        pendingRestart = 0;
        for (Map.Entry<NamespacedKey, JsonObject> e : want.entrySet()) {
            if (keep.contains(e.getKey())) {
                count++;
                continue;
            }
            // Still registered although we just tried to remove it: removeAdvancement cannot drop
            // a live advancement (it only deletes the persisted file). Park the NEW definition in
            // that file so the next start picks it up, and keep running on the old one meanwhile.
            if (Bukkit.getAdvancement(e.getKey()) != null) {
                writePersisted(e.getKey(), e.getValue());
                pendingRestart++;
                count++;
                continue;
            }
            if (load(e.getKey(), e.getValue(), "advancement '" + e.getKey().getKey() + "'")) {
                registeredJson.put(e.getKey(), e.getValue().toString());
                count++;
            }
        }
        return count;
    }

    private File persistedFile(NamespacedKey key) {
        return new File(new File(Bukkit.getWorldContainer(), Bukkit.getWorlds().get(0).getName()),
                "datapacks/bukkit/data/" + key.getNamespace() + "/advancement/" + key.getKey() + ".json");
    }

    private void writePersisted(NamespacedKey key, JsonObject json) {
        try {
            File f = persistedFile(key);
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            java.nio.file.Files.writeString(f.toPath(), json.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            warn("Could not park the new definition of '" + key.getKey() + "': " + ex.getMessage());
        }
    }

    /** True when the copy Paper persisted for this key in the world's bukkit datapack equals {@code want}. */
    private boolean matchesPersisted(NamespacedKey key, JsonObject want) {
        try {
            if (Bukkit.getWorlds().isEmpty()) return false;
            File f = persistedFile(key);
            if (!f.isFile()) return false;
            String text = java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            return JsonParser.parseString(text).equals(want);
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean load(NamespacedKey key, JsonObject json, String what) {
        try {
            Advancement adv = Bukkit.getUnsafe().loadAdvancement(key, json.toString());
            if (adv == null) { warn("Minecraft rejected " + what + " — check its icon/background ids"); return false; }
            return true;
        } catch (Exception e) {
            warn("Could not load " + what + ": " + e.getMessage() + " (check icon/background ids)");
            return false;
        }
    }

    private JsonObject display(String icon, String title, String description, String frame, boolean toast, boolean announce, boolean hidden) {
        JsonObject display = new JsonObject();
        JsonObject iconObj = new JsonObject();
        iconObj.addProperty("id", icon.contains(":") ? icon : "minecraft:" + icon);
        display.add("icon", iconObj);
        display.add("title", JsonParser.parseString(GsonComponentSerializer.gson().serialize(text(title))));
        display.add("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(text(description))));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", announce);
        display.addProperty("hidden", hidden);
        return display;
    }

    static Component text(String s) {
        if (s == null) return Component.empty();
        if (s.contains("<") && s.contains(">")) return MiniMessage.miniMessage().deserialize(s);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private NamespacedKey rootKey(String tab) { return new NamespacedKey(plugin, tab + "/" + ROOT); }
    private NamespacedKey advKey(AdvDef d) { return new NamespacedKey(plugin, d.tab + "/" + d.id); }

    private static String cleanId(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return id.matches("[a-z0-9_\\-]+") ? id : null;
    }

    private String ns() { return new NamespacedKey(plugin, "x").getNamespace(); }

    private void warn(String msg) { plugin.getLogger().warning("[Advancements] " + msg); }

    // ------------------------------------------------------------ progress

    private boolean isDone(Player p, AdvDef d) {
        Advancement adv = Bukkit.getAdvancement(advKey(d));
        return adv != null && p.getAdvancementProgress(adv).isDone();
    }

    /** Grants the advancement (rewards follow via PlayerAdvancementDoneEvent). */
    public boolean grant(Player p, AdvDef d) {
        Advancement adv = Bukkit.getAdvancement(advKey(d));
        if (adv == null) return false;
        AdvancementProgress progress = p.getAdvancementProgress(adv);
        if (progress.isDone()) return false;
        for (String c : new ArrayList<>(progress.getRemainingCriteria())) progress.awardCriteria(c);
        return true;
    }

    private boolean revoke(Player p, AdvDef d) {
        Advancement adv = Bukkit.getAdvancement(advKey(d));
        if (adv == null) return false;
        AdvancementProgress progress = p.getAdvancementProgress(adv);
        boolean any = false;
        for (String c : new ArrayList<>(progress.getAwardedCriteria())) { progress.revokeCriteria(c); any = true; }
        return any;
    }

    private void checkAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) checkPlayer(p);
    }

    /** Grants every not-yet-earned advancement whose automatic requirements are all met. */
    public int checkPlayer(Player p) {
        int granted = 0;
        // Loop a few times so chains ("advancements" requirements) resolve in one go.
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            for (AdvDef d : advancements.values()) {
                if (d.requirements.isEmpty() || isDone(p, d)) continue;
                if (!isAutomatic(d)) continue;
                if (allMet(p, d, null) && grant(p, d)) { granted++; changed = true; }
            }
            if (!changed) break;
        }
        return granted;
    }

    /** Called by MySQLSync when the website awarded an achievement to this player. */
    public void onWebsiteAchievement(Player p, String slug) {
        if (slug == null) return;
        for (AdvDef d : advancements.values()) {
            boolean usesSlug = d.requirements.stream().anyMatch(r ->
                    "website".equalsIgnoreCase(str(r, "type")) && slug.equalsIgnoreCase(str(r, "achievement")));
            if (usesSlug && !isDone(p, d) && allMet(p, d, slug)) grant(p, d);
        }
    }

    private boolean isAutomatic(AdvDef d) {
        for (Map<?, ?> r : d.requirements) {
            String type = str(r, "type");
            if ("manual".equalsIgnoreCase(type) || "website".equalsIgnoreCase(type)) return false;
        }
        return true;
    }

    private boolean allMet(Player p, AdvDef d, String websiteSlug) {
        for (Map<?, ?> r : d.requirements) {
            try {
                if (!met(p, r, websiteSlug)) return false;
            } catch (Exception e) {
                warn("Requirement of '" + d.id + "' is invalid (" + e.getMessage() + ") — fix advancements.yml");
                return false;
            }
        }
        return true;
    }

    private boolean met(Player p, Map<?, ?> r, String websiteSlug) {
        String type = str(r, "type").toLowerCase(Locale.ROOT);
        switch (type) {
            case "manual":
                return false;
            case "website":
                return websiteSlug != null && websiteSlug.equalsIgnoreCase(str(r, "achievement"));
            case "playtime":
                return p.getStatistic(Statistic.PLAY_ONE_MINUTE) >= num(r, "hours", 0) * 72000L; // ticks
            case "break_block":
                return sumMaterials(p, Statistic.MINE_BLOCK, r, "block", "blocks") >= num(r, "amount", 1);
            case "place_block":
                return sumMaterials(p, Statistic.USE_ITEM, r, "block", "blocks") >= num(r, "amount", 1);
            case "craft_item":
                return sumMaterials(p, Statistic.CRAFT_ITEM, r, "item", "items") >= num(r, "amount", 1);
            case "kill_entity": {
                long total = 0;
                for (String e : names(r, "entity", "entities")) total += p.getStatistic(Statistic.KILL_ENTITY, entity(e));
                return total >= num(r, "amount", 1);
            }
            case "statistic": {
                Statistic stat = Statistic.valueOf(str(r, "statistic").toUpperCase(Locale.ROOT));
                long value = switch (stat.getType()) {
                    case UNTYPED -> p.getStatistic(stat);
                    case BLOCK, ITEM -> p.getStatistic(stat, material(str(r, stat.getType() == Statistic.Type.BLOCK ? "block" : "item")));
                    case ENTITY -> p.getStatistic(stat, entity(str(r, "entity")));
                };
                return value >= num(r, "amount", 1);
            }
            case "permission":
                return p.hasPermission(str(r, "permission"));
            case "location": {
                Location loc = p.getLocation();
                if (loc.getWorld() == null) return false;
                // environment: normal | nether | the_end - by dimension type, so it does not
                // depend on how the worlds are named (this server's nether is not "world_nether").
                if (r.get("environment") != null) {
                    String env = str(r, "environment").toLowerCase(Locale.ROOT).replace("minecraft:", "");
                    String actual = switch (loc.getWorld().getEnvironment()) {
                        case NORMAL -> "normal";
                        case NETHER -> "nether";
                        case THE_END -> "the_end";
                        default -> "custom";
                    };
                    return env.equals(actual);
                }
                if (!loc.getWorld().getName().equalsIgnoreCase(str(r, "world"))) return false;
                if (r.get("x") == null || r.get("z") == null) return true;
                double radius = num(r, "radius", 10);
                double dx = loc.getX() - num(r, "x", 0), dz = loc.getZ() - num(r, "z", 0);
                return dx * dx + dz * dz <= radius * radius;
            }
            case "advancements": {
                Object list = r.get("list");
                if (!(list instanceof List<?> ids)) return false;
                for (Object id : ids) {
                    AdvDef other = advancements.get(cleanId(String.valueOf(id)));
                    if (other == null || !isDone(p, other)) return false;
                }
                return true;
            }
            // Full namespaced keys from OTHER plugins' advancement systems.
            case "external_advancements": {
                Object list = r.get("keys");
                if (!(list instanceof List<?> keys) || keys.isEmpty()) return false;
                for (Object k : keys) {
                    NamespacedKey nk = NamespacedKey.fromString(String.valueOf(k).trim().toLowerCase(Locale.ROOT));
                    if (nk == null) return false;
                    org.bukkit.advancement.Advancement ext = Bukkit.getAdvancement(nk);
                    if (ext == null) return false;                    // plugin not loaded (yet)
                    if (!p.getAdvancementProgress(ext).isDone()) return false;
                }
                return true;
            }
            // Custom lore books (BookManager) — "read" = opened at least once.
            case "book_read": {
                BookManager books = plugin.getBookManager();
                if (books == null) return false;
                Set<String> read = books.readBooks(p);
                for (String id : names(r, "book", "books")) {
                    if (!read.contains(id.trim().toLowerCase(Locale.ROOT))) return false;
                }
                return true;
            }
            case "books_read": {
                BookManager books = plugin.getBookManager();
                if (books == null) return false;
                return books.readBooks(p).stream().filter(books::hasBook).count() >= num(r, "amount", 1);
            }
            case "book_series": {
                BookManager books = plugin.getBookManager();
                if (books == null) return false;
                List<String> ids = books.seriesBookIds(str(r, "series"));
                if (ids.isEmpty()) return false; // unknown series (or no books loaded yet)
                Set<String> read = books.readBooks(p);
                long have = ids.stream().filter(read::contains).count();
                return r.get("amount") == null ? have == ids.size() : have >= num(r, "amount", 1);
            }
            case "placeholder": {
                String value = placeholder(p, str(r, "placeholder"));
                if (value == null) return false;
                if (r.get("equals") != null) return value.equalsIgnoreCase(String.valueOf(r.get("equals")));
                try {
                    return Double.parseDouble(value.replaceAll("[^0-9.\\-]", "")) >= num(r, "min", 0);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            default:
                throw new IllegalArgumentException("unknown type '" + type + "'");
        }
    }

    private long sumMaterials(Player p, Statistic stat, Map<?, ?> r, String single, String plural) {
        long total = 0;
        for (String m : names(r, single, plural)) total += p.getStatistic(stat, material(m));
        return total;
    }

    private static List<String> names(Map<?, ?> r, String single, String plural) {
        Object many = r.get(plural);
        if (many instanceof List<?> l) return l.stream().map(String::valueOf).collect(Collectors.toList());
        Object one = r.get(single);
        if (one == null) throw new IllegalArgumentException("missing '" + single + "'");
        return Collections.singletonList(String.valueOf(one));
    }

    private static Material material(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null) throw new IllegalArgumentException("unknown block/item '" + name + "'");
        return m;
    }

    private static EntityType entity(String name) {
        try {
            return EntityType.valueOf(name.replace("minecraft:", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown entity '" + name + "'");
        }
    }

    private static String str(Map<?, ?> r, String key) {
        Object v = r.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static double num(Map<?, ?> r, String key, double def) {
        Object v = r.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? def : Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return def; }
    }

    // PlaceholderAPI via reflection (same pattern as PlaceholderSync) — optional.
    private String placeholder(Player p, String raw) {
        if (!papiResolved) {
            papiResolved = true;
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                try {
                    papiSetPlaceholders = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                            .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                } catch (Exception e) {
                    warn("PlaceholderAPI found but not usable: " + e.getMessage());
                }
            }
        }
        if (papiSetPlaceholders == null) return null;
        try {
            String value = (String) papiSetPlaceholders.invoke(null, p, raw);
            return value == null || value.equals(raw) ? null : value.trim();
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) checkPlayer(p); }, 60L);
    }

    @EventHandler
    public void onDone(PlayerAdvancementDoneEvent e) {
        NamespacedKey key = e.getAdvancement().getKey();
        if (!key.getNamespace().equals(ns())) return;
        String path = key.getKey();
        int slash = path.indexOf('/');
        if (slash < 0) return; // not one of ours (toasts have no slash)
        AdvDef d = advancements.get(path.substring(slash + 1));
        if (d == null) return;
        Player p = e.getPlayer();
        if (d.rewardXp > 0) p.giveExp(d.rewardXp);
        for (String cmd : d.rewardCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        if (d.rewardMessage != null && !d.rewardMessage.isBlank()) p.sendMessage(text(d.rewardMessage));
        // de meta-grap: wie álles verzamelt wordt publiekelijk onbetrouwbaar verklaard
        if (d.id.equals("untrustworthy")) {
            Bukkit.getServer().broadcast(text("&8[&6the Guild&8] &e" + p.getName()
                    + " &7is officially untrustworthy. Every single joke, found."));
        }
    }

    // /minecraft:reload re-reads datapacks and drops advancements registered at
    // runtime — put ours back afterwards.
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent e) {
        Bukkit.getScheduler().runTask(plugin, this::reload);
    }

    // -------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(text("&6/iadv reload &7| &6list &7| &6grant <player> <id> &7| &6revoke <player> <id> &7| &6check <player>"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> sender.sendMessage(text("&a" + reload()));
            case "list" -> {
                sender.sendMessage(text("&6Custom advancements (" + advancements.size() + "):"));
                for (AdvDef d : advancements.values()) {
                    String reqs = d.requirements.stream().map(r -> str(r, "type")).collect(Collectors.joining(", "));
                    sender.sendMessage(text("&e" + d.id + " &7[" + d.tab + "] &f" + stripColors(d.title) + " &8(" + reqs + ")"));
                }
            }
            case "grant", "revoke" -> {
                if (args.length < 3) { sender.sendMessage(text("&cUsage: /iadv " + args[0] + " <player> <id>")); return true; }
                Player p = Bukkit.getPlayerExact(args[1]);
                AdvDef d = advancements.get(cleanId(args[2]));
                if (p == null) { sender.sendMessage(text("&cPlayer '" + args[1] + "' is not online.")); return true; }
                if (d == null) { sender.sendMessage(text("&cUnknown advancement '" + args[2] + "'. See /iadv list")); return true; }
                boolean changed = args[0].equalsIgnoreCase("grant") ? grant(p, d) : revoke(p, d);
                sender.sendMessage(text(changed ? "&aDone." : "&7Nothing changed (already " + (args[0].equalsIgnoreCase("grant") ? "earned" : "not earned") + ")."));
            }
            case "check" -> {
                if (args.length < 2) { sender.sendMessage(text("&cUsage: /iadv check <player>")); return true; }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) { sender.sendMessage(text("&cPlayer '" + args[1] + "' is not online.")); return true; }
                sender.sendMessage(text("&aGranted " + checkPlayer(p) + " advancement(s)."));
            }
            default -> sender.sendMessage(text("&cUnknown subcommand. Use /iadv"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("reload", "list", "grant", "revoke", "check"), args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("list")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            return filter(new ArrayList<>(advancements.keySet()), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    private static String stripColors(String s) {
        return s == null ? "" : s.replaceAll("&[0-9a-fk-orA-FK-OR]", "").replaceAll("<[^>]+>", "");
    }
}
