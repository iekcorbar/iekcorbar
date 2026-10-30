package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * IekQuests 0.2 - de eigen quest-engine van iekcorbar (30-10).
 *
 * Alles in quests.yml:
 *   quests.<id>:  title, subtitle, icon, npc-id, order, startcondities,
 *                 stages (talk/craft/deliver/walk/kill/repeat, met condities +
 *                 keys van de repeat-map), een vertakking "goto" op een stage,
 *                 rewards bij finish, repeats (herhaalcontracten met cooldown +
 *                 random-tag), journal-regels, sequences (timede actie-reeksen).
 *   stages.<key>: type, material, amount, location, radius, label, voice, lines,
 *                 condities, rewards (extra), next of goto, choices: [ {label, say,
 *                 goto/do, condities} ] - choices maken een gesprek vertakkend.
 *   rewards:  money N | eco N | title A|B | sound ns:key | say tekst | tag +/-:naam |
 *             point cat +N | effect TYPE secs amp | teleport x;y;z;w | give mat N |
 *             sequence <id> | commando | shop <commando> | compass x;y;z;w
 *   condities: "tag:naam" | "!tag:naam" | "point:cat>=N" | "item:MAT>=N" |
 *              "perm:node" | "night" | "day"  ( komma = alles moet )
 *
 * Progressie per speler in data.yml: state, tags, points, cooldowns, sequences.
 * BQ blijft buiten dit alles - alleen CMI/Vault + EliteMobs worden via console
 * aangeroerd.
 */
public final class IekQuestsPlugin extends JavaPlugin implements Listener {

    // ---------------------------------------------------------------- model

    static final class Cond {
        String raw;
        Cond(String s) { raw = s; }
    }

    static final class Choice {
        String label, say, gotoKey = "";
        boolean close;                       // menu-sluiten na deze keuze (gids-loop)
        List<String> doList = new ArrayList<>();
        List<Cond> conds = new ArrayList<>();
        String voice = "";
    }

    static final class Stage {
        String key = "", kind = "talk";      // talk|craft|deliver|deliverall|walk|kill|menu
        String material = "PAPER";
        String mobType = "";                 // kill: entity type naam of "monster"
        String nameFilter = "";              // deliver/craft: display-naam moet dit bevatten (Techcore-staal)
        int amount = 1;
        boolean flat;                        // walk: alleen XZ-afstand (regios met volle hoogte)
        List<String[]> items = new ArrayList<>();   // deliverall: [material, amount, naam?]
        Location loc;
        double radius = 6;
        String label = "&aContinue";
        String voice = "";
        List<String> lines = new ArrayList<>();
        List<Cond> conds = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
        String next = "";                    // key van volgende stage; "" = sequentieel
        String gotoKey = "";                 // directe sprong na voltooien van deze stage
        List<String> rewards = new ArrayList<>();
    }

    static final class Repeat {
        String id;
        String kind = "deliver";             // deliver|walk|deliverall
        String material = "PUMPKIN";
        String name = "";                    // display-naam filter (MMOItems-vissen)
        int amount = 2;
        List<String[]> items = new ArrayList<>();   // deliverall-contract: [material, amount, naam?]
        Location loc;
        double radius = 6;
        long cooldown = 600;
        int money = 100;
        int eco = 2;
        boolean daily;                       // max 1x per gamemiddag (24u)
        String label = "&aHand in";
        String voice = "";
        List<String> rewards = new ArrayList<>();
    }

    static final class Quest {
        String id = "", title = "&fQuest", subtitle = "", icon = "BOOK";
        int npcId = -1;
        List<Integer> npcIds = new ArrayList<>();   // meerdere NPC's delven dezelfde quest (Backpack)
        int order = 0;
        boolean fromWizard;                    // komt uit wizard-quests.yml (GUI-wizard)
        boolean randomStart;                 // bij start: willekeurige eerste stage uit 'random-stages'
        List<String> randomStages = new ArrayList<>();
        List<Cond> startConds = new ArrayList<>();
        LinkedHashMap<String, Stage> stages = new LinkedHashMap<>();
        List<Repeat> repeats = new ArrayList<>();
        List<String> rewards = new ArrayList<>();
        List<String> journal = new ArrayList<>();
    }

    static final class Seq {
        List<String> steps = new ArrayList<>();
        long[] delays;
    }

    static final class Prog {
        String stage = "";                   // key van actieve stage; "" = klaar/niet gestart
        boolean started, done;
        List<String> tags = new ArrayList<>();
        Map<String, Integer> points = new HashMap<>();
        Map<String, Long> cd = new HashMap<>();
        long startedAt;
    }

    static final class Holder implements InventoryHolder {
        final UUID who;
        final String quest;
        String sig = "";                   // tekenvinger van de vorige render - geen flicker
        int page;                          // questboek-paginering
        int npc;                           // NPC-ID van de klik bron (0 = via boek); voor npc:keuzes
        Holder(UUID who, String quest) { this.who = who; this.quest = quest == null ? "" : quest; }
        Holder(UUID who, String quest, int page) { this(who, quest); this.page = page; }
        Holder(UUID who, String quest, int page, int npc) { this(who, quest, page); this.npc = npc; }
        @Override public Inventory getInventory() { return null; }
    }

    private final List<Quest> quests = new ArrayList<>();
    private final Map<String, Seq> sequences = new HashMap<>();
    private final Map<UUID, Map<String, Prog>> prog = new HashMap<>();
    private YamlConfiguration data;
    private NamespacedKey tagKey;
    private final Random rnd = new Random();
    private QuestWizard wizard;

    // ---------------------------------------------------------------- wizard-toegang

    /** Lijst voor de GUI-wizard (niet muteren buiten de plugin). */
    List<Quest> questsView() { return quests; }

    Quest questById(String id) { return byId(id); }

    List<Cond> condsOf(String s) { return conds(s); }

    /** Wizard-quest toevoegen/vervangen, wegenschrijven en direct inladen. */
    void storeWizardQuest(Quest q) {
        q.fromWizard = true;
        quests.removeIf(x -> x.id.equals(q.id));
        quests.add(q);
        writeWizardFile();
        saveData();
        load();
    }

    boolean removeWizardQuest(String id) {
        var q = byId(id);
        if (q == null || !q.fromWizard) return false;
        quests.remove(q);
        writeWizardFile();
        saveData();
        load();
        return true;
    }

    /** Alle wizard-quests terugschrijven naar wizard-quests.yml (handgemaakte blijft heel). */
    private void writeWizardFile() {
        var l = new ArrayList<Quest>();
        for (var q : quests) if (q.fromWizard) l.add(q);
        QuestWizard.writeWizardFile(new File(getDataFolder(), "wizard-quests.yml"), l);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        tagKey = new NamespacedKey(this, "tag");
        if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = Bukkit.getPluginCommand("questbook");
        if (cmd != null) cmd.setExecutor((sender, command, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("iekquests.admin")) {
                load();
                sender.sendMessage(Component.text("IekQuests: " + quests.size() + " quest(s) reloaded."));
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("iekquests.admin")) {
                var pl = Bukkit.getPlayerExact(args[1]);
                UUID u = pl != null ? pl.getUniqueId() : UUID.fromString(args[1]);
                var mm = prog.get(u);
                if (mm != null) {
                    if (args.length >= 3) mm.remove(args[2]); else mm.clear();
                    save();
                    sender.sendMessage(Component.text("Progress reset for " + args[1]));
                }
                return true;
            }
            if (sender instanceof Player p) openMenu(p);
            else sender.sendMessage(Component.text("questbook: from console use reload/reset only"));
            return true;
        });
        wizard = new QuestWizard(this);
        Bukkit.getPluginManager().registerEvents(wizard, this);
        var wcmd = Bukkit.getPluginCommand("questwizard");
        if (wcmd != null) wcmd.setExecutor((sender, command, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("delete") && sender.hasPermission("iekquests.admin")
                    && args.length >= 2) {
                if (removeWizardQuest(args[1])) sender.sendMessage(Component.text("Wizard-quest " + args[1] + " deleted."));
                else sender.sendMessage(Component.text("Only wizard-made quests can be deleted here (not quests.yml ones)."));
                return true;
            }
            if (sender instanceof Player p && p.hasPermission("iekquests.admin")) wizard.open(p);
            else sender.sendMessage(Component.text("questwizard: iekquests.admin required."));
            return true;
        });
        var rcmd = Bukkit.getPluginCommand("iekrestart");
        if (rcmd != null) rcmd.setExecutor(this::onRestartCommand);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshOpenGuis, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 1200L, 1200L);
        load();
        getLogger().info("IekQuests " + getDescription().getVersion() + " klaar - " + quests.size() + " quest(s).");
    }

    @Override
    public void onDisable() {
        if (restartTask != null) restartTask.cancel();
        saveData();
    }

    // ---------------------------------------------------------------- /iekrestart

    private BukkitTask restartTask;
    private int restartLeft;

    /**
     * In-game restart met aankondiging (eigenaar, 30-10): titel + afteller in de chat.
     * De container draait java als PID 1 zonder restart-loop, dus Paper's eigen /restart
     * zou alleen stoppen. Daarom: markerfile .restart-request + /stop; een cron op de VPS
     * (/usr/local/sbin/iekcorbar-restart-watcher.sh) start de container dan opnieuw op.
     */
    private boolean onRestartCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.hasPermission("iekquests.admin")) {
            sender.sendMessage(Component.text("Only staff can schedule restarts."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /" + label + " <minutes 1-60 | seconds 61-3600 | now | cancel>"));
            return true;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("cancel")) {
            if (restartTask != null) {
                restartTask.cancel();
                restartTask = null;
                Bukkit.broadcast(legacy("&aRestart cancelled - carry on!"));
                for (Player p : Bukkit.getOnlinePlayers())
                    p.showTitle(Title.title(legacy("&aCancelled"), legacy("&7No restart after all.")));
            } else sender.sendMessage(Component.text("No restart is scheduled."));
            return true;
        }
        int secs;
        if (a.equals("now")) secs = 10;
        else {
            int n;
            try { n = Integer.parseInt(a); } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Give a number (minutes up to 60, seconds above 60), now or cancel."));
                return true;
            }
            secs = n <= 60 ? n * 60 : Math.min(n, 3600);
            if (secs < 10) secs = 10;
        }
        startRestart(sender, secs);
        return true;
    }

    private void startRestart(CommandSender by, int secs) {
        if (restartTask != null) restartTask.cancel();
        restartLeft = secs;
        Bukkit.broadcast(legacy("&6&#9888 Server restart in " + fmt(secs) + " &7- announced by " + by.getName() + "."));
        showRestartTitle(secs);
        restartTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            restartLeft--;
            if (restartLeft > 0) {
                showRestartTitle(restartLeft);
                int r = restartLeft;
                if (r == 300 || r == 120 || r == 60) Bukkit.broadcast(legacy("&eRestart in " + fmt(r) + "."));
                if (r == 30 || (r <= 10 && r >= 1)) Bukkit.broadcast(legacy("&cRestart in " + r + "s - log out safely!"));
                return;
            }
            restartTask = null;
            try {
                new File(Bukkit.getWorldContainer(), ".restart-request").createNewFile();
            } catch (Exception e) {
                getLogger().warning("Could not write the restart marker: " + e.getMessage());
            }
            Bukkit.broadcast(legacy("&cRestarting now. Back in about a minute - see you then!"));
            Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"), 40L);
        }, 20L, 20L);
    }

    private void showRestartTitle(int left) {
        var times = Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(100));
        for (Player p : Bukkit.getOnlinePlayers())
            p.showTitle(Title.title(legacy("&6Server restart"), legacy("&e" + fmt(left) + " left"), times));
    }

    private static String fmt(int s) {
        if (s >= 60) {
            int m = s / 60, r = s % 60;
            return r == 0 ? m + " min" : m + "m " + r + "s";
        }
        return s + "s";
    }

    // ---------------------------------------------------------------- load + save

    private void load() {
        quests.clear();
        sequences.clear();
        var qc = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "quests.yml"));

        var sq = qc.getConfigurationSection("sequences");
        if (sq != null) for (String k : sq.getKeys(false)) {
            Seq s = new Seq();
            for (var st : sq.getStringList(k)) {
                String del = "0";
                String body = st;
                if (st.contains("|")) {
                    del = st.substring(0, st.indexOf('|')).trim();
                    body = st.substring(st.indexOf('|') + 1).trim();
                }
                s.steps.add(body);
                s.delays = grow(s.delays, (long) (num(del, 0) * 20));
            }
            sequences.put(k, s);
        }

        parseQuests(qc.getConfigurationSection("quests"), false);
        var wf = new File(getDataFolder(), "wizard-quests.yml");
        if (wf.exists()) parseQuests(
                YamlConfiguration.loadConfiguration(wf).getConfigurationSection("quests"), true);
        quests.sort((a, b) -> Integer.compare(a.order, b.order));

        data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        prog.clear();
        loadData();
    }

    /** Eén quests:-sectie inlezen; fromWizard markeert de GUI-wizard-bestanden. */
    private void parseQuests(ConfigurationSection sec, boolean fromWizard) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Quest q = new Quest();
            q.fromWizard = fromWizard;
            q.id = id;
            q.title = s.getString("title", id);
            q.subtitle = s.getString("subtitle", "");
            q.icon = s.getString("icon", "BOOK");
            q.npcId = s.getInt("npc-id", -1);
            for (var x : s.getString("npc-ids", "").split(",")) {
                try { if (!x.isBlank()) q.npcIds.add(Integer.parseInt(x.trim())); } catch (NumberFormatException ignored) { }
            }
            if (q.npcId >= 0 && !q.npcIds.contains(q.npcId)) q.npcIds.add(q.npcId);
            q.order = s.getInt("order", 0);
            q.randomStart = s.getBoolean("random", false);
            q.startConds = conds(s.getString("start", ""));
            for (var r : s.getStringList("rewards")) q.rewards.add(r);
            for (var j : s.getStringList("journal")) q.journal.add(j);
            var st = s.getConfigurationSection("stages");
            if (st != null) for (String k : st.getKeys(false)) {
                var v = st.getConfigurationSection(k);
                if (v == null) continue;
                Stage g = new Stage();
                g.key = k;
                g.kind = v.getString("type", "talk");
                g.material = v.getString("material", "PAPER").toUpperCase();
                g.nameFilter = v.getString("name", "");
                g.mobType = v.getString("mob", "").toUpperCase();
                g.amount = v.getInt("amount", 1);
                g.radius = v.getDouble("radius", 6);
                g.flat = v.getBoolean("flat", false);
                g.items.addAll(parseItemList(v.getStringList("items")));
                g.label = v.getString("label", "&aContinue");
                g.voice = v.getString("voice", "");
                g.lines.addAll(v.getStringList("lines"));
                g.conds = conds(v.getString("cond", ""));
                g.next = v.getString("next", "");
                g.gotoKey = v.getString("goto", "");
                for (var r : v.getStringList("rewards")) g.rewards.add(r);
                String locStr = v.getString("location", "");
                if (!locStr.isEmpty()) g.loc = parseLoc(locStr);
                var ch = v.getConfigurationSection("choices");
                if (ch != null) for (String ck : ch.getKeys(false)) {
                    var cv = ch.getConfigurationSection(ck);
                    if (cv == null) continue;
                    Choice c = new Choice();
                    c.label = cv.getString("label", ck);
                    c.say = cv.getString("say", "");
                    c.gotoKey = cv.getString("goto", "");
                    c.close = cv.getBoolean("close", false);
                    c.voice = cv.getString("voice", "");
                    c.conds = conds(cv.getString("cond", ""));
                    for (var d : cv.getStringList("do")) c.doList.add(d);
                    g.choices.add(c);
                } else for (var cm : v.getMapList("choices")) {
                    // lijstnotatie - zoals het hele quests.yml die schrijft (vorkan,
                    // backpack). Vóór deze tak verdwenen die keuzes spoorloos: de map-
                    // parser gaf null en de menu-knoppen bleven weg. Nooit meer.
                    Choice c = new Choice();
                    c.label = mm(cm, "label");
                    if (c.label.isEmpty()) c.label = "…";
                    c.say = mm(cm, "say");
                    c.gotoKey = mm(cm, "goto");
                    c.voice = mm(cm, "voice");
                    c.conds = conds(mm(cm, "cond"));
                    Object closev = cm.get("close");
                    c.close = closev instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(closev));
                    Object dl = cm.get("do");
                    if (dl instanceof List<?> dd) for (var d : dd) if (d != null) c.doList.add(String.valueOf(d));
                    g.choices.add(c);
                }
                q.stages.put(k, g);
            }
            var rp = s.getConfigurationSection("repeat");
            if (rp != null) for (String k : rp.getKeys(false)) {
                var v = rp.getConfigurationSection(k);
                if (v == null) continue;
                Repeat r = new Repeat();
                r.id = k;
                r.kind = v.getString("type", "deliver");
                r.material = v.getString("material", "PUMPKIN").toUpperCase();
                r.name = v.getString("name", "");
                r.amount = v.getInt("amount", 2);
                r.items.addAll(parseItemList(v.getStringList("items")));
                r.radius = v.getDouble("radius", 6);
                r.cooldown = v.getLong("cooldown", 600);
                r.daily = v.getBoolean("daily", false);
                r.money = v.getInt("money", 0);
                r.eco = v.getInt("eco", 0);
                r.label = v.getString("label", "&aHand in");
                r.voice = v.getString("voice", "");
                String locStr = v.getString("location", "");
                if (!locStr.isEmpty()) r.loc = parseLoc(locStr);
                for (var x : v.getStringList("rewards")) r.rewards.add(x);
                q.repeats.add(r);
            }
            for (var x : s.getStringList("random-stages")) q.randomStages.add(x);
            quests.add(q);
        }
    }

    /** Spelersprogressie uit data.yml in het geheugen zetten. */
    private void loadData() {
        var ps = data.getConfigurationSection("players");
        if (ps != null) for (String uuid : ps.getKeys(false)) {
            var m = new HashMap<String, Prog>();
            var qs = data.getConfigurationSection(uuid + ".quests");
            if (qs != null) for (String qid : qs.getKeys(false)) {
                Prog pr = new Prog();
                pr.stage = qs.getString(qid + ".stage", "");
                pr.started = qs.getBoolean(qid + ".started");
                pr.done = qs.getBoolean(qid + ".done");
                pr.startedAt = qs.getLong(qid + ".startedAt");
                pr.tags.addAll(qs.getStringList(qid + ".tags"));
                var pp = qs.getConfigurationSection(qid + ".points");
                if (pp != null) for (String c : pp.getKeys(false)) pr.points.put(c, pp.getInt(c));
                var cds = qs.getConfigurationSection(qid + ".cd");
                if (cds != null) for (String k : cds.getKeys(false)) pr.cd.put(k, cds.getLong(k));
                m.put(qid, pr);
            }
            try { prog.put(UUID.fromString(uuid), m); } catch (IllegalArgumentException ignored) { }
        }
    }

    private long[] grow(long[] arr, long v) {
        var out = new long[arr == null ? 1 : arr.length + 1];
        if (arr != null) System.arraycopy(arr, 0, out, 0, arr.length);
        out[out.length - 1] = v;
        return out;
    }

    private double num(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    /** Waarde uit een willekeurs-map (keuze-uit-een-lijst), altijd als string. */
    private static String mm(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : String.valueOf(o);
    }

    private List<Cond> conds(String s) {        List<Cond> l = new ArrayList<>();
        if (s == null || s.isBlank()) return l;
        for (var p : s.split(",")) {
            var t = p.trim();
            if (!t.isEmpty()) l.add(new Cond(t));
        }
        return l;
    }

    /** "MATERIAL 24" of "MATERIAL 4 Tuna" (derde+ = display-naam filter) → [material, amount, naam]. */
    private static List<String[]> parseItemList(List<String> raw) {
        List<String[]> out = new ArrayList<>();
        for (var line : raw) {
            var t = line.trim();
            if (t.isEmpty()) continue;
            var sp = t.split(" ", 3);
            try {
                out.add(new String[]{sp[0].toUpperCase(), sp.length > 1 ? sp[1] : "1", sp.length > 2 ? sp[2] : ""});
            } catch (Exception ignored) { }
        }
        return out;
    }

    private void save() { saveData(); }

    private void saveData() {
        if (data == null) return;
        data.set("players", null);
        for (var e : prog.entrySet()) {
            var base = e.getKey().toString() + ".quests.";
            for (var qe : e.getValue().entrySet()) {
                var pr = qe.getValue();
                data.set(base + qe.getKey() + ".stage", pr.stage);
                data.set(base + qe.getKey() + ".started", pr.started);
                data.set(base + qe.getKey() + ".done", pr.done);
                if (pr.startedAt > 0) data.set(base + qe.getKey() + ".startedAt", pr.startedAt);
                if (!pr.tags.isEmpty()) data.set(base + qe.getKey() + ".tags", pr.tags);
                for (var pe : pr.points.entrySet()) data.set(base + qe.getKey() + ".points." + pe.getKey(), pe.getValue());
                for (var ce : pr.cd.entrySet()) data.set(base + qe.getKey() + ".cd." + ce.getKey(), ce.getValue());
            }
        }
        try {
            data.save(new File(getDataFolder(), "data.yml"));
        } catch (Exception ex) {
            getLogger().warning("data.yml niet opgeslagen: " + ex.getMessage());
        }
    }

    private Prog pr(Player p, Quest q) {
        return prog.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(q.id, k -> new Prog());
    }

    private Quest byId(String id) {
        for (var q : quests) if (q.id.equals(id)) return q;
        return null;
    }

    // ---------------------------------------------------------------- condities

    private boolean ok(Player p, Quest q, Prog pr, List<Cond> cs) {
        for (var c : cs) if (!ok(p, q, pr, c.raw)) return false;
        return true;
    }

    private boolean ok(Player p, Quest q, Prog pr, String c) {
        try {
            if (c.startsWith("!")) return !ok(p, q, pr, c.substring(1));
            if (c.startsWith("tag:")) {
                var want = c.substring(4);
                return pr.tags.contains(want) || q.stages.keySet().contains(want) && pr.stage.equals(want);
            }
            if (c.startsWith("qtag:")) {
                // tag van ÁNDER quest: qtag:<questId>:<tag> - voor de Backpack-ketting
                var x = c.split(":", 3);
                if (x.length < 3) return false;
                var m = prog.get(p.getUniqueId());
                var pp = m == null ? null : m.get(x[1]);
                return pp != null && pp.tags.contains(x[2]);
            }
            if (c.startsWith("point:")) {
                var body = c.substring(6);
                var ge = body.indexOf(">=");
                if (ge < 0) return false;
                var cat = body.substring(0, ge);
                var need = Integer.parseInt(body.substring(ge + 2));
                return pr.points.getOrDefault(cat, 0) >= need;
            }
            if (c.startsWith("item:")) {
                var body = c.substring(5);
                var ge = body.indexOf(">=");
                if (ge < 0) return false;
                return held(p, body.substring(0, ge), null) >= Integer.parseInt(body.substring(ge + 2));
            }
            if (c.startsWith("money")) {
                boolean ge = c.startsWith("money>=");
                if (!ge && !c.startsWith("money<")) return false;
                var want = Double.parseDouble(c.substring(ge ? 7 : 6));
                var bal = balance(p);
                return bal < 0 ? false : (ge ? bal >= want : bal < want);
            }
            if (c.startsWith("perm:")) return p.hasPermission(c.substring(5));
            if (c.equals("night")) { var t = p.getWorld().getTime(); return t > 13000 && t < 23000; }
            if (c.equals("day")) { var t = p.getWorld().getTime(); return t <= 13000 || t >= 23000; }
            return true;   // onbekende string: niet blokkeren (log het niet, te veel ruis)
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        int id = citizensId(e.getRightClicked());
        if (id < 0) return;
        for (var q : quests) {
            if (q.npcIds.contains(id)) {
                e.setCancelled(true);
                openQuest(e.getPlayer(), q, id);
                return;
            }
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onCraft(CraftItemEvent e) {
        var result = e.getInventory().getResult();
        if (result == null) return;
        Player p = (Player) e.getWhoClicked();
        var m = prog.get(p.getUniqueId());
        if (m == null) return;
        for (var q : quests) {
            Prog pr = m.get(q.id);
            if (pr == null || pr.stage.isEmpty()) continue;
            Stage s = q.stages.get(pr.stage);
            if (s == null || !s.kind.equals("craft") || !s.material.equals(result.getType().name())) continue;
            Bukkit.getScheduler().runTask(this, () -> {
                if (held(p, s.material) >= s.amount) advance(p, q, pr, s);
            });
            return;
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        var k = e.getEntity().getKiller();
        if (k == null) return;
        var m = prog.get(k.getUniqueId());
        if (m == null) return;
        for (var q : quests) {
            Prog pr = m.get(q.id);
            if (pr == null || pr.stage.isEmpty()) continue;
            Stage s = q.stages.get(pr.stage);
            if (s == null || !s.kind.equals("kill")) continue;
            if (!s.mobType.isEmpty() && !s.mobType.equals("MONSTER")
                    && !s.mobType.equalsIgnoreCase(e.getEntityType().name())) continue;
            if (s.mobType.equals("MONSTER") && !(e.getEntity() instanceof Monster)) continue;
            Bukkit.getScheduler().runTask(this, () -> {
                int have = pr.points.getOrDefault("kill:" + q.id + ":" + s.key, 0) + 1;
                pr.points.put("kill:" + q.id + ":" + s.key, have);
                if (have >= s.amount) advance(k, q, pr, s);
                else k.playSound(k.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.5f, 1.6f);
                save();
            });
            return;
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        var to = e.getTo();
        if (to == null) return;
        if (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY()
                && e.getFrom().getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        var m = prog.get(p.getUniqueId());
        if (m == null) return;
        for (var q : quests) {
            Prog pr = m.get(q.id);
            if (pr == null || pr.stage.isEmpty()) continue;
            Stage s = q.stages.get(pr.stage);
            if (s == null || !s.kind.equals("walk") || s.loc == null || !s.loc.getWorld().equals(p.getWorld())) continue;
            double d2 = s.flat
                    ? Math.pow(p.getLocation().getBlockX() - s.loc.getBlockX(), 2)
                    + Math.pow(p.getLocation().getBlockZ() - s.loc.getBlockZ(), 2)
                    : p.getLocation().distanceSquared(s.loc);
            if (d2 <= s.radius * s.radius) advance(p, q, pr, s);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prog.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        var holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Holder h) || !h.who.equals(p.getUniqueId())) return;
        event.setCancelled(true);
        var cur = event.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        String tag = loreTag(cur);
        if (tag == null) return;
        if (h.quest.isEmpty()) {
            if (tag.startsWith("open:")) {
                var q = byId(tag.substring(5));
                if (q != null) openQuest(p, q);
            }
            if (tag.equals("pg:-")) { openMenu(p, h.page - 1); return; }
            if (tag.equals("pg:+")) { openMenu(p, h.page + 1); return; }
            if (tag.equals("wizard") && wizard != null) wizard.open(p);
            return;
        }
        var q = byId(h.quest);
        if (q == null) return;
        Prog pr = pr(p, q);
        switch (tag) {
            case "back" -> { openMenu(p); return; }
            case "start" -> { startQuest(p, q, pr); return; }
            case "newtry" -> { resetQuest(pr); refresh(p, q); save(); return; }
        }
        if (tag.startsWith("choice:") || tag.startsWith("menu:")) {
            Stage s = pr.stage.isEmpty()
                    ? (q.stages.isEmpty() ? null : q.stages.values().iterator().next())
                    : q.stages.get(pr.stage);
            if (s == null) return;
            int i = Integer.parseInt(tag.substring(tag.indexOf(':') + 1));
            if (i < 0 || i >= s.choices.size()) return;
            Choice c = s.choices.get(i);
            // npc:<id> conditions kijken naar WELKE NPC de GUI opende (baan-meesters)
            List<Cond> rest = new ArrayList<>();
            boolean npcOk = true;
            for (var cc : c.conds) {
                if (cc.raw.startsWith("npc:")) {
                    try { if (h.npc != Integer.parseInt(cc.raw.substring(4))) npcOk = false; }
                    catch (NumberFormatException ignored) { npcOk = false; }
                } else rest.add(cc);
            }
            if (!npcOk || !ok(p, q, pr, rest)) { deny(p); return; }
            if (!c.say.isEmpty()) p.sendMessage(legacy(c.say));
            speak(p, c.voice);
            for (var d : c.doList) run(p, q, pr, d);
            if (c.close) { save(); p.closeInventory(); return; }
            if (!pr.stage.isEmpty() && !c.gotoKey.isEmpty() && q.stages.containsKey(c.gotoKey)) {
                pr.stage = c.gotoKey;
                speakFirst(p, q, pr);
            } else if (pr.done && !c.gotoKey.isEmpty() && q.stages.containsKey(c.gotoKey)) {
                // dienst-NPC semantics: een voltooide quest heropent op de gekozen stage
                pr.done = false;
                pr.started = true;
                pr.stage = c.gotoKey;
                speakFirst(p, q, pr);
            }
            refresh(p, q);
            save();
            return;
        }
        if (tag.startsWith("repeat:")) { doRepeat(p, q, pr, tag.substring(7)); return; }
        if (tag.startsWith("compass:")) {
            String[] a = tag.substring(8).split(";");
            var w = Bukkit.getWorld(a[0]);
            if (w != null) setCompass(p, new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])));
            return;
        }
        if (tag.equals("act")) {
            var s = q.stages.get(pr.stage);
            if (s == null) return;
            if (!ok(p, q, pr, s.conds)) { deny(p); return; }
            boolean done = switch (s.kind) {
                case "menu" -> false;
                case "talk" -> true;
                case "walk" -> s.loc != null && p.getWorld().equals(s.loc.getWorld())
                        && walkReached(s, p);
                case "kill" -> pr.points.getOrDefault("kill:" + q.id + ":" + s.key, 0) >= s.amount;
                case "deliverall" -> allHeld(p, s.items);
                default -> held(p, s.material, s.nameFilter.isEmpty() ? null : s.nameFilter) >= s.amount;
            };
            if (done) {
                if (s.kind.equals("deliver")) take(p, s.material, s.amount, s.nameFilter.isEmpty() ? null : s.nameFilter);
                if (s.kind.equals("deliverall")) takeAll(p, s.items);
                advance(p, q, pr, s);
            } else deny(p);
        }
    }

    /** walk-vereiste: vlak (alleen XZ) bij flat:true, anders 3D. */
    private boolean walkReached(Stage s, Player p) {
        if (s.flat) {
            double dx = p.getLocation().getBlockX() - s.loc.getBlockX();
            double dz = p.getLocation().getBlockZ() - s.loc.getBlockZ();
            return dx * dx + dz * dz <= s.radius * s.radius;
        }
        return p.getLocation().distanceSquared(s.loc) <= s.radius * s.radius;
    }

    private boolean allHeld(Player p, List<String[]> items) {
        for (var it : items)
            if (held(p, it[0], it[2].isEmpty() ? null : it[2]) < Integer.parseInt(it[1])) return false;
        return true;
    }

    private void takeAll(Player p, List<String[]> items) {
        for (var it : items)
            take(p, it[0], Integer.parseInt(it[1]), it[2].isEmpty() ? null : it[2]);
    }

    private void deny(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.6f, 0.7f);
    }

    private void speakFirst(Player p, Quest q, Prog pr) {
        var s = q.stages.get(pr.stage);
        if (s != null) speak(p, s.voice);
    }

    // ---------------------------------------------------------------- flow

    private void startQuest(Player p, Quest q, Prog pr) {
        if (q.stages.isEmpty()) { p.sendMessage(legacy("&7This quest is not ready yet.")); return; }
        if (!ok(p, q, pr, q.startConds)) { deny(p); return; }
        pr.started = true;
        pr.done = false;
        pr.startedAt = System.currentTimeMillis();
        String first;
        if (q.randomStart && !q.randomStages.isEmpty() && q.stages.containsKey(q.randomStages.get(0))) {
            first = q.randomStages.get(rnd.nextInt(q.randomStages.size()));
        } else {
            first = q.stages.keySet().iterator().next();
        }
        pr.stage = first;
        speakFirst(p, q, pr);
        refresh(p, q);
        save();
    }

    private void resetQuest(Prog pr) {
        pr.stage = "";
        pr.started = false;
        pr.done = false;
    }

    /** Stage voltooid: extra rewards, volgende stage (next/goto) of klaar. */
    private void advance(Player p, Quest q, Prog pr, Stage s) {
        for (var r : s.rewards) run(p, q, pr, r);
        String nxt = !s.gotoKey.isEmpty() ? s.gotoKey : (!s.next.isEmpty() ? s.next : nextKey(q, s));
        if (nxt == null || !q.stages.containsKey(nxt)) {
            finish(p, q, pr);
            return;
        }
        pr.stage = nxt;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.3f);
        speakFirst(p, q, pr);
        refresh(p, q);
        save();
    }

    private String nextKey(Quest q, Stage cur) {
        var it = q.stages.keySet().iterator();
        String prev = null;
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals(cur.key)) return it.hasNext() ? it.next() : null;
            prev = k;
        }
        return null;
    }

    private void finish(Player p, Quest q, Prog pr) {
        pr.done = true;
        pr.stage = "";
        if (!q.rewards.isEmpty()) runRewards(p, q, pr, q.rewards);
        refresh(p, q);
        save();
    }

    private void doRepeat(Player p, Quest q, Prog pr, String id) {
        Repeat r = null;
        for (var x : q.repeats) if (x.id.equals(id)) r = x;
        if (r == null) return;
        Long cd = pr.cd.get(r.id);
        if (cd != null && cd > now()) {
            p.sendMessage(legacy("&7Not yet - the queue clears in " + fmt(cd - now()) + "."));
            return;
        }
        if (r.kind.equals("walk")) {
            if (r.loc == null || !p.getWorld().equals(r.loc.getWorld())
                    || p.getLocation().distanceSquared(r.loc) > r.radius * r.radius) { deny(p); return; }
        } else if (!r.items.isEmpty()) {
            if (!allHeld(p, r.items)) { deny(p); return; }
            takeAll(p, r.items);
        } else {
            if (held(p, r.material, r.name.isEmpty() ? null : r.name) < r.amount) { deny(p); return; }
            take(p, r.material, r.amount, r.name.isEmpty() ? null : r.name);
        }
        if (r.money > 0) dispatch("cmi money give " + p.getName() + " -s " + r.money);
        if (r.eco > 0) dispatch("em money add " + p.getName() + " " + r.eco);
        runRewards(p, q, pr, r.rewards);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.9f, 1.4f);
        speak(p, r.voice);
        pr.cd.put(r.id, now() + (r.daily ? 86400 : r.cooldown));
        refresh(p, q);
        save();
    }

    // ---------------------------------------------------------------- acties

    /** Eén actie-regel uit de config (rewards/do/sequence-stap). */
    private void run(Player p, Quest q, Prog pr, String raw) {
        var a = raw.trim();
        if (a.isEmpty()) return;
        a = a.replace("%player%", p.getName());
        int sp = a.indexOf(' ');
        String key = sp < 0 ? a : a.substring(0, sp);
        String val = sp < 0 ? "" : a.substring(sp + 1);
        switch (key) {
            case "money" -> dispatch("cmi money give " + p.getName() + " -s " + val);
            case "pay" -> dispatch("cmi money take " + p.getName() + " -s " + val);
            case "eco" -> dispatch("em money add " + p.getName() + " " + val);
            case "title" -> {
                String[] ab = val.split("\\|");
                p.showTitle(Title.title(legacy(ab[0]), ab.length > 1 ? legacy(ab[1]) : Component.empty()));
            }
            case "sound" -> {
                var snd = sound(val);
                if (snd != null) {
                    p.playSound(p.getLocation(), snd, SoundCategory.PLAYERS, 0.8f, 1f);
                } else {
                    // Pakcet-only events (steamworks:quest.complete) staan NIET in de
                    // server-registry: speel op naam, de client resolvet via het pack.
                    p.playSound(p.getLocation(), namespaced(val), SoundCategory.PLAYERS, 0.8f, 1f);
                }
            }
            case "say" -> p.sendMessage(legacy(val));
            case "tag+" -> { if (!pr.tags.contains(val)) pr.tags.add(val); save(); }
            case "tag-" -> { pr.tags.remove(val); save(); }
            case "perm+" -> dispatch("lp user " + p.getName() + " permission set " + val + true);
            case "perm-" -> dispatch("lp user " + p.getName() + " permission unset " + val);
            case "point" -> {
                // vorm: point <cat> +N  (optellen) | point <cat> =N (zetten)
                String[] x = val.split(" ");
                if (x.length >= 2) {
                    String cat = x[0];
                    String op = x[1];
                    int n = Integer.parseInt(op.replace("+", "").replace("=", "").replace("-", ""));
                    if (op.startsWith("+")) pr.points.merge(cat, n, Integer::sum);
                    else if (op.startsWith("-")) pr.points.merge(cat, -n, Integer::sum);
                    else pr.points.put(cat, n);
                    save();
                }
            }
            case "effect" -> {
                String[] x = val.split(" ");
                var type = PotionEffectType.getByName(x[0]);
                if (type != null) p.addPotionEffect(new org.bukkit.potion.PotionEffect(type,
                        (int) (num(x.length > 1 ? x[1] : "5", 5) * 20), x.length > 2 ? Integer.parseInt(x[2]) : 0, true, false));
            }
            case "teleport" -> {
                var l = parseLoc(val);
                if (l != null) {
                    // EIGENAAR-REGEL 30-10: spelers worden NOOIT_remote gestuurd.
                    // Teleport is een staff-hulpmiddel (iekquests.admin); een
                    // speler krijgt het kompas en loopt/zoeft zelf.
                    if (p.hasPermission("iekquests.admin")) p.teleport(l);
                    else {
                        setCompass(p, l);
                        p.sendMessage(legacy("&7No ride for you - the compass points the way. Make the journey yourself!"));
                    }
                }
            }
            case "give" -> {
                String[] x = val.split(" ");
                var mt = Material.matchMaterial(x[0]);
                if (mt != null) p.getInventory().addItem(new ItemStack(mt, x.length > 1 ? Integer.parseInt(x[1]) : 1));
            }
            case "compass" -> {
                var l = parseLoc(val);
                if (l != null) setCompass(p, l);
            }
            case "shop" -> {
                // mi stations vereist console-rechten: %player% is al ingevuld
                p.closeInventory();
                dispatch(val);
            }
            case "joinjob" -> {
                // de Jobs-plugin (NuJobs NG) praten alsof de speler typt: /jobs join <job>
                final String j = val;
                Bukkit.getScheduler().runTask(this, () -> p.performCommand("jobs join " + j));
            }
            case "leavejob" -> {
                final String j = val;
                Bukkit.getScheduler().runTask(this, () -> p.performCommand("jobs leave " + j));
            }
            case "chat" -> {
                // als speler een chatbericht laten zeggen (Orion-shops: "say hi 1")
                final String msg = val;
                p.closeInventory();
                Bukkit.getScheduler().runTask(this, () -> p.chat(msg));
            }
            case "sequence" -> playSeq(p, q, pr, val);
            default -> dispatch(a);
        }
    }

    private void runRewards(Player p, Quest q, Prog pr, List<String> rewards) {
        for (var r : rewards) run(p, q, pr, r);
    }

    /** Tijdlijn: steps met eigen vertraging (notify/cmd), als folder uit BQ-land. */
    private void playSeq(Player p, Quest q, Prog pr, String seqId) {
        var s = sequences.get(seqId);
        if (s == null) return;
        long acc = 0;
        for (int i = 0; i < s.steps.size(); i++) {
            acc += s.delays[i];
            final String step = s.steps.get(i);
            Bukkit.getScheduler().runTaskLater(this, () -> run(p, q, pr, step), acc);
        }
    }

    /** Openstaande GUI's (cooldown-tellers) periodisch verversen. */
    private void refreshOpenGuis() {
        for (var p : Bukkit.getOnlinePlayers()) {
            var holder = p.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof Holder h)) continue;
            var m = prog.get(p.getUniqueId());
            if (m == null) continue;
            if (h.quest.isEmpty()) {
                String sig = "";
                for (var q : quests) {
                    var pr = m.get(q.id);
                    sig += q.id + ":" + (pr == null ? "-" : (pr.done ? "d" : pr.stage + "/" +
                            pr.cd.entrySet().stream().map(e -> e.getKey() + (e.getValue() > now() ? 1 : 0)).reduce("", (a, b) -> a + b)));
                }
                if (!sig.equals(h.sig)) { h.sig = sig; openMenu(p, h.page); }
            } else {
                var q = byId(h.quest);
                if (q == null) continue;
                var pr = m.getOrDefault(q.id, new Prog());
                Stage s = q.stages.get(pr.stage);
                String sig = (pr.started ? "s" : "-") + (pr.done ? "d" : "") + pr.stage
                        + (s == null ? "" : "/" + (s.kind.equals("kill")
                                ? pr.points.getOrDefault("kill:" + q.id + ":" + s.key, 0)
                                : held(p, s.material)))
                        + pr.cd.entrySet().stream().map(e -> e.getKey() + (e.getValue() > now() ? 1 : 0))
                                .reduce("", (a, b) -> a + b);
                if (!sig.equals(h.sig)) { h.sig = sig; openQuest(p, q, h.npc); }
            }
        }
    }

    private void dispatch(String consoleCmd) {
        Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd));
    }

    private void speak(Player p, String key) {
        if (key == null || key.isEmpty()) return;
        // Stem-events (steamworks:igor.lineXX) leven alleen IN het resourcepack:
        // Registry.SOUNDS kent ze niet en gaf null -> er speelde niets af.
        // Altijd op naam sturen; de client resolvet via pack of vanilla.
        p.playSound(p.getLocation(), namespaced(key), SoundCategory.RECORDS, 0.9f, 1f);
    }

    private String namespaced(String key) {
        return key.contains(":") ? key : "steamworks:" + key;
    }

    private Sound sound(String key) {
        try {
            NamespacedKey k = key.contains(":") ? NamespacedKey.fromString(key) : new NamespacedKey("steamworks", key);
            return k == null ? null : Registry.SOUNDS.get(k);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- inventaris

    private int held(Player p, String material) { return held(p, material, null); }

    private int held(Player p, String material, String nameContains) {
        var mt = Material.matchMaterial(material);
        if (mt == null) return 0;
        int n = 0;
        for (var st : p.getInventory().getStorageContents()) {
            if (st == null || st.getType() != mt) continue;
            if (nameContains != null && !nameContains.isEmpty() && !displayNameIs(st, nameContains)) continue;
            n += st.getAmount();
        }
        return n;
    }

    private static boolean displayNameIs(ItemStack st, String needle) {
        try {
            var im = st.getItemMeta();
            if (im == null || im.displayName() == null) return false;
            String dn = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(im.displayName()).toLowerCase();
            if (needle.startsWith("=")) return dn.equals(needle.substring(1).toLowerCase());
            return dn.contains(needle.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private void take(Player p, String material, int amount) { take(p, material, amount, null); }

    private void take(Player p, String material, int amount, String nameContains) {
        var mt = Material.matchMaterial(material);
        if (mt == null) return;
        int left = amount;
        var content = p.getInventory().getStorageContents();
        for (int i = 0; i < content.length && left > 0; i++) {
            var it = content[i];
            if (it == null || it.getType() != mt) continue;
            if (nameContains != null && !nameContains.isEmpty() && !displayNameIs(it, nameContains)) continue;
            int use = Math.min(it.getAmount(), left);
            left -= use;
            if (use >= it.getAmount()) p.getInventory().setItem(i, null);
            else { it.setAmount(it.getAmount() - use); p.getInventory().setItem(i, it); }
        }
    }

    // ---------------------------------------------------------------- GUI's

    private void openMenu(Player p) { openMenu(p, 0); }

    private void openMenu(Player p, int page) {
        List<Quest> vis = new ArrayList<>();
        for (var q : quests) {
            var pr = pr(p, q);
            if (ok(p, q, pr, q.startConds) || (pr.started && !pr.done)) vis.add(q);
        }
        boolean admin = p.hasPermission("iekquests.admin");
        int per = 35;                                   // slots 10-44, onder-rij is navigatie
        int pages = Math.max(1, (vis.size() + per - 1) / per);
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        var inv = Bukkit.createInventory(new Holder(p.getUniqueId(), "", page), 54, legacy("&6&l⚒ Quest Book"));
        int slot = 10;
        for (int i = page * per; i < vis.size() && i < (page + 1) * per; i++) {
            var q = vis.get(i);
            if (q.stages.isEmpty()) continue;
            List<String> lo = new ArrayList<>();
            if (!q.subtitle.isEmpty()) lo.add("&8" + q.subtitle);
            lo.add("   " + statusLine(q, pr(p, q)));
            var it = item(q.icon, q.title, lo);
            setTag(it, "open:" + q.id);
            inv.setItem(slot++, it);
        }
        if (admin) inv.setItem(0, tagged(Material.NETHER_STAR, "&d✚ Quest wizard &8(new quest + NPC)", "wizard"));
        if (pages > 1) {
            inv.setItem(45, tagged(Material.ARROW, "&7◀ Page " + (page == 0 ? "-" : page + 1) + "/" + pages, "pg:-"));
            inv.setItem(47, tagged(Material.ARROW, "&7Page " + (page + 1) + "/" + pages + " ▶", "pg:+"));
        }
        p.openInventory(inv);
    }

    private void openQuest(Player p, Quest q) { openQuest(p, q, 0); }

    private void openQuest(Player p, Quest q, int npcCtx) {
        Prog pr = pr(p, q);
        Stage s = pr.stage.isEmpty() ? null : q.stages.get(pr.stage);
        Stage first = q.stages.isEmpty() ? null : q.stages.values().iterator().next();
        int nChoices = pr.done || pr.stage.isEmpty()
                ? (first == null ? 0 : first.choices.size())
                : s.choices.size();
        int size = (nChoices > 7 || q.repeats.size() > 17) ? 54 : 45;
        var inv = Bukkit.createInventory(new Holder(p.getUniqueId(), q.id, 0, npcCtx), size, legacy(q.title));

        List<String> lines = new ArrayList<>();
        if (!q.subtitle.isEmpty()) lines.add("&8" + q.subtitle);
        if (!pr.started) lines.add("&eClick below to begin.");
        else if (pr.done) {
            lines.add("&aThe quest is complete!");
            for (var j : q.journal) lines.add("&7" + j);
        } else if (s != null) {
            lines.addAll(s.lines);
            if (!q.journal.isEmpty()) lines.add("&8" + q.journal.get(Math.min(q.journal.size() - 1, Math.max(0, indexOfStage(q, s)))));
        }
        var book = item(q.icon, q.title, lines);
        inv.setItem(13, book);

        if (!pr.started) {
            inv.setItem(31, tagged(Material.LIME_DYE, "&a✔ Start quest", "start"));
        } else if (pr.done) {
            if (!q.repeats.isEmpty()) {
                // contracten-rij: slots 18-34 (max 17 contracts naast elkaar)
                int slot = 18;
                for (var r : q.repeats) {
                    Long cd = pr.cd.get(r.id);
                    boolean ready = cd == null || cd <= now();
                    String cnt;
                    if (!r.items.isEmpty()) {
                        int got = 0;
                        for (var it : r.items) if (held(p, it[0], it[2].isEmpty() ? null : it[2]) >= Integer.parseInt(it[1])) got++;
                        cnt = " (" + got + "/" + r.items.size() + ")";
                    } else if (r.kind.equals("deliver")) {
                        cnt = " (" + held(p, r.material, r.name.isEmpty() ? null : r.name) + "/" + r.amount + ")";
                    } else cnt = "";
                    String lab = (ready ? "&a" : "&7") + r.label + cnt
                            + (ready ? "" : " &7still " + fmt(cd - now()));
                    if (slot < 35) { inv.setItem(slot, tagged(Material.LIME_DYE, lab, "repeat:" + r.id)); slot++; }
                }
            } else inv.setItem(31, tagged(Material.PAPER, "&aStart the quest again", "newtry"));
            // dienstknoppen blijven beschikbaar na voltooiing (keuzes zonder goto van de eerste stage)
            if (first != null) {
                int[] slots = menuDoneSlots(size);
                int n = 0;
                for (int i = 0; i < first.choices.size() && n < slots.length; i++) {
                    var c = first.choices.get(i);
                    inv.setItem(slots[n++], tagged(Material.OAK_SIGN, c.label, "menu:" + i));
                }
            }
        } else if (s != null) {
            if (!s.kind.equals("menu")) {
                inv.setItem(31, tagged(Material.PAPER, labelFor(p, q, pr, s), "act"));
                if (s.kind.equals("walk") && s.loc != null)
                    inv.setItem(size == 45 ? 33 : 15, tagged(Material.COMPASS, "&6Point the way",
                            "compass:" + s.loc.getWorld().getName() + ";" + s.loc.getBlockX()
                                    + ";" + s.loc.getBlockY() + ";" + s.loc.getBlockZ()));
            }
            // keuzes: klein veld 19-25, groot veld verbreed (18-35 + 36-44, gaten voor act/back)
            int[] slots = choiceSlots(size);
            int n = 0;
            for (var c : s.choices) {
                if (n >= slots.length) break;
                inv.setItem(slots[n++], tagged(Material.OAK_SIGN, c.label, "choice:" + indexOfChoice(s, c)));
            }
        }
        inv.setItem(40, tagged(Material.BARRIER, "&cBack", "back"));
        p.openInventory(inv);
    }

    /** Keuzeslots tijdens een stage: 7 gewoon, 25 in het brede 54-veld. */
    private static int[] choiceSlots(int size) {
        if (size <= 45) return new int[]{19, 20, 21, 22, 23, 24, 25};
        int[] t = new int[26]; int n = 0;
        for (int i = 18; i <= 35; i++) if (i != 31) t[n++] = i;
        for (int i = 36; i <= 44; i++) if (i != 40) t[n++] = i;
        return java.util.Arrays.copyOf(t, n);
    }

    /** Dienstknoppen op het voltooide paneel: 8 gewoon, extra bovenrij in het brede veld. */
    private static int[] menuDoneSlots(int size) {
        List<Integer> l = new ArrayList<>();
        for (int i = 36; i <= 44; i++) if (i != 40) l.add(i);
        if (size > 45) {
            for (int i = 0; i <= 17; i++) if (i != 13) l.add(i);   // overflow-rij bovenaan
        }
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    private int indexOfChoice(Stage s, Choice c) {
        return s.choices.indexOf(c);
    }

    private int indexOfStage(Quest q, Stage s) {
        int i = 0;
        for (var k : q.stages.keySet()) {
            if (k.equals(s.key)) return i;
            i++;
        }
        return 0;
    }

    private void refresh(Player p, Quest q) {
        var holder = p.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof Holder h) openQuest(p, q, h.npc);
    }

    private String labelFor(Player p, Quest q, Prog pr, Stage s) {
        var flt = s.nameFilter.isEmpty() ? null : s.nameFilter;
        return switch (s.kind) {
            case "craft" -> "&e" + s.label + " &8(" + held(p, s.material, flt) + "/" + s.amount + ")";
            case "deliver" -> "&a" + s.label + " &8(" + held(p, s.material, flt) + "/" + s.amount + ")";
            case "walk" -> "&e" + s.label + " &8[" + (s.loc == null ? "?" : s.loc.getBlockX() + "," + s.loc.getBlockZ()) + "]";
            case "kill" -> "&c" + s.label + " &8(" + pr.points.getOrDefault("kill:" + q.id + ":" + s.key, 0) + "/" + s.amount + ")";
            case "deliverall" -> {
                int got = 0;
                for (var it : s.items) if (held(p, it[0], it[2].isEmpty() ? null : it[2]) >= Integer.parseInt(it[1])) got++;
                yield "&a" + s.label + " &8(" + got + "/" + s.items.size() + " parts)";
            }
            default -> "&a" + s.label;
        };
    }

    private String statusLine(Quest q, Prog pr) {
        if (!pr.started) return "&7Not started yet";
        if (pr.done) return "&a✔ Completed";
        var s = q.stages.get(pr.stage);
        int i = s == null ? 0 : indexOfStage(q, s) + 1;
        return "&eStage " + i + "/" + q.stages.size() + (s == null ? "" : " - " + plain(s.label));
    }

    // ---------------------------------------------------------------- items

    private ItemStack item(String material, String name, List<String> lore) {
        var mt = Material.matchMaterial(material);
        var it = new ItemStack(mt == null ? Material.BOOK : mt);
        var im = it.getItemMeta();
        im.displayName(legacy(name));
        List<Component> lo = new ArrayList<>();
        for (var l : lore) lo.add(legacy(l));
        im.lore(lo);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack tagged(Material m, String name, String tag) {
        var it = new ItemStack(m);
        var im = it.getItemMeta();
        im.displayName(legacy(name));
        im.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    private void setTag(ItemStack it, String tag) {
        var im = it.getItemMeta();
        if (im == null) return;
        im.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
    }

    private String loreTag(ItemStack it) {
        var im = it.getItemMeta();
        if (im == null) return null;
        return im.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- misc

    private Method mRegistry, mGetNpc, mId;
    private boolean citizensBroken;

    private int citizensId(Entity ent) {
        if (citizensBroken) return -1;
        try {
            if (mRegistry == null) {
                Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
                mRegistry = api.getMethod("getNPCRegistry");
            }
            Object registry = mRegistry.invoke(null);
            if (mGetNpc == null) mGetNpc = registry.getClass().getMethod("getNPC", Entity.class);
            Object npc = mGetNpc.invoke(registry, ent);
            if (npc == null) return -1;
            if (mId == null) mId = npc.getClass().getMethod("getId");
            return (int) mId.invoke(npc);
        } catch (Exception e) {
            citizensBroken = true;
            getLogger().warning("Citizens niet bereikbaar - NPC-klikken werken alleen via het menu: " + e.getMessage());
            return -1;
        }
    }

    private void setCompass(Player p, Location loc) {
        try {
            var mc = p.getClass().getMethod("getMetaCompass").invoke(p);
            mc.getClass().getMethod("setLocation", Location.class).invoke(mc, loc);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f, 1.6f);
        } catch (Exception e) {
            p.sendMessage(legacy("&7Compass mode is not available on this server."));
        }
    }

    private Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }

    private String plain(String s) {
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s));
    }

    /** Echte geldbalans via Vault (reflectie, geen compile-afhankelijkheid). */
    private Object econProvider;
    private Method mBalance;

    private double balance(Player p) {
        try {
            if (mBalance == null) {
                Class<?> ec = Class.forName("net.milkbowl.vault.economy.Economy");
                var reg = Bukkit.getServicesManager().getRegistration(ec);
                if (reg == null) return -1;
                econProvider = reg.getProvider();
                mBalance = ec.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            }
            return (double) mBalance.invoke(econProvider, p);
        } catch (Exception e) {
            return -1;
        }
    }

    private Location parseLoc(String s) {
        String[] a = s.split(";");
        if (a.length < 4) return null;
        var w = Bukkit.getWorld(a[3].trim());
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long now() { return System.currentTimeMillis() / 1000L; }

    private String fmt(long secs) {
        if (secs <= 0) return "";
        long m = secs / 60, s = secs % 60;
        return m > 0 ? m + "m" + (s > 0 ? " " + s + "s" : "") : s + "s";
    }
}
