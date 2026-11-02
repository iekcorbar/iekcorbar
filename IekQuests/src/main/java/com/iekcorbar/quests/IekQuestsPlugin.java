package com.iekcorbar.quests;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.event.ClickCallback;
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
import org.bukkit.event.inventory.InventoryDragEvent;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.UUID;


public final class IekQuestsPlugin extends JavaPlugin implements Listener {

    // ---------------------------------------------------------------- model

    static final class Cond {
        String raw;
        Cond(String s) { raw = s; }
    }

    static final class Choice {
        String label, say, gotoKey = "", action = "";
        boolean close;                       
        List<String> doList = new ArrayList<>();
        List<Cond> conds = new ArrayList<>();
        String voice = "";
        int limitPerDay;                     
    }

    static final class Stage {
        String key = "", kind = "talk";      
        String material = "PAPER";
        String mobType = "";                 // kill: entity type naam of "monster"
        String nameFilter = "";              
        int amount = 1;
        boolean flat;                        
        List<String[]> items = new ArrayList<>();   // deliverall: [material, amount, naam?]
        Location loc;
        double radius = 6;
        String label = "&aContinue";
        String voice = "";
        List<String> lines = new ArrayList<>();
        List<Cond> conds = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
        String next = "";                    
        String gotoKey = "";                 
        List<String> rewards = new ArrayList<>();
    }

    static final class Repeat {
        String id;
        String kind = "deliver";             // deliver|walk|deliverall
        String material = "PUMPKIN";
        String name = "";                    // display-naam filter (MMOItems-vissen)
        int amount = 2;
        List<String[]> items = new ArrayList<>();   
        Location loc;
        double radius = 6;
        long cooldown = 600;
        int money = 100;
        int eco = 2;
        boolean daily;                       
        String label = "&aHand in";
        String voice = "";
        List<String> rewards = new ArrayList<>();
    }

    static final class Quest {
        String id = "", title = "&fQuest", subtitle = "", icon = "BOOK";
        int npcId = -1;
        List<Integer> npcIds = new ArrayList<>();   
        int order = 0;
        String group = "";                     
        String mode = "player";               
        boolean fromWizard;                    
        boolean randomStart;                 
        List<String> randomStages = new ArrayList<>();
        List<Cond> startConds = new ArrayList<>();
        LinkedHashMap<String, Stage> stages = new LinkedHashMap<>();
        List<Repeat> repeats = new ArrayList<>();
        List<String> rewards = new ArrayList<>();
        List<String> journal = new ArrayList<>();
    }

    private record DialogueOption(String label, String tag) {}

    private static final class DialogueSession {
        final UUID player;
        final String quest;
        final int npc;
        int textPage;
        int choicePage;
        final List<String> replyLines = new ArrayList<>();
        DialogueSession(UUID player, String quest, int npc) {
            this.player = player;
            this.quest = quest;
            this.npc = npc;
        }
    }

    static final class Seq {
        List<String> steps = new ArrayList<>();
        long[] delays;
    }

    static final class Prog {
        String stage = "";                   
        boolean started, done;
        List<String> tags = new ArrayList<>();
        Map<String, Integer> points = new HashMap<>();
        Map<String, Long> cd = new HashMap<>();
        long startedAt;
    }

    static final class Holder implements InventoryHolder {
        final UUID who;
        final String quest;
        String sig = "";                   
        int page;                          // questboek-paginering
        int npc;                           
        Holder(UUID who, String quest) { this.who = who; this.quest = quest == null ? "" : quest; }
        Holder(UUID who, String quest, int page) { this(who, quest); this.page = page; }
        Holder(UUID who, String quest, int page, int npc) { this(who, quest, page); this.npc = npc; }
        @Override public Inventory getInventory() { return null; }
    }

    private final List<Quest> quests = new ArrayList<>();
    private final Map<String, Seq> sequences = new HashMap<>();
    private final Map<UUID, Map<String, Prog>> prog = new HashMap<>();
    private final Map<UUID, String[]> chatActive = new HashMap<>();   
    private final Map<UUID, DialogueSession> dialogueSessions = new HashMap<>();
    private final Set<UUID> suppressDialogueRefresh = new HashSet<>();
    private DialogueManager dialogueManager;
    private EventsManager eventsManager;
    private ObjectivesManager objectivesManager;
    private JournalManager journalManager;
    private SequencesManager sequencesManager;
    private QuestPackageManager questPackageManager;
    private final Map<UUID, Long> travelCd = new HashMap<>();
    private ItemLibrary items;
    private NamespacedKey modeKey;
    ItemLibrary items() { return items; }
    private YamlConfiguration data;
    private NamespacedKey tagKey;
    private NamespacedKey hudOffKey;
    private final Random rnd = new Random();
    private QuestWizard wizard;
    private DialogueWizard dialogueWizard;

    Prog progress(Player p, Quest q) { return pr(p, q); }
    YamlConfiguration dialogueData() { return data; }
    DialogueManager dialogueManager() { return dialogueManager; }
    EventsManager eventsManager() { return eventsManager; }
    ObjectivesManager objectivesManager() { return objectivesManager; }
    JournalManager journalManager() { return journalManager; }
    SequencesManager sequencesManager() { return sequencesManager; }
    File organizationFile(String name) { return new File(new File(getDataFolder(), "organization"), name); }
    QuestPackageManager questPackageManager() { return questPackageManager; }
    String itemIdOf(ItemStack stack) { return items == null ? null : items.idOf(stack); }
    String dialogueConfig(String key, String fallback) { return getConfig().getString("dialogue." + key, fallback); }
    boolean conditionsPass(Player p, Quest q, Prog pr, List<Cond> conditions) { return ok(p, q, pr, conditions); }
    boolean questConditionsPass(Player p, Quest q, Prog pr, List<String> conditions) {
        List<Cond> parsed = new ArrayList<>();
        if (conditions != null) for (String condition : conditions) if (condition != null && !condition.isBlank()) parsed.add(new Cond(condition));
        return ok(p, q, pr, parsed);
    }
    boolean beginDialogueQuestAction(Player p) {
        UUID id = p.getUniqueId();
        boolean alreadySuppressed = suppressDialogueRefresh.contains(id);
        suppressDialogueRefresh.add(id);
        return alreadySuppressed;
    }

    void endDialogueQuestAction(Player p, boolean alreadySuppressed) {
        if (!alreadySuppressed) suppressDialogueRefresh.remove(p.getUniqueId());
    }

    
    
    public void triggerQuestTag(Player p, String questId, String actionName) {
        String tag = questId + "_" + actionName;
        var m = prog.get(p.getUniqueId());
        if (m != null) {
            
            for (var entry : m.entrySet()) {
                var pr = entry.getValue();
                if (!pr.tags.contains(tag)) {
                    pr.tags.add(tag);
                }
            }
            save();
        }
        getLogger().info("Quest tag set: " + tag + " by " + p.getName());
    }

    

    
    List<Quest> questsView() { return quests; }

    Quest questById(String id) { return byId(id); }

    List<Cond> condsOf(String s) { return conds(s); }

    
    void storeWizardQuest(Quest q) {
        q.fromWizard = true;
        quests.removeIf(x -> x.id.equals(q.id));
        quests.add(q);
        writeWizardQuest(q);
        saveData();
        load();
    }

    boolean removeWizardQuest(String id) {
        var q = byId(id);
        if (q == null || !q.fromWizard) return false;
        quests.remove(q);
        questPackageManager.removeWizardQuest(id);
        saveData();
        load();
        return true;
    }

    private void writeWizardQuest(Quest q) {
        File target = new File(questPackageManager.questFolder(), "_wizard-" + q.id + ".yml");
        QuestWizard.writeWizardFile(target, List.of(q));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        tagKey = new NamespacedKey(this, "tag");
        modeKey = new NamespacedKey(this, "mode");
        hudOffKey = new NamespacedKey(this, "hud_off");
        saveDefaultConfig();
        if (!new File(getDataFolder(), "config.yml").exists()) saveResource("config.yml", false);
        questPackageManager = new QuestPackageManager(this);
        dialogueManager = new DialogueManager(this);
        eventsManager = new EventsManager(this);
        objectivesManager = new ObjectivesManager(this);
        journalManager = new JournalManager(this);
        sequencesManager = new SequencesManager(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(eventsManager, this);
        Bukkit.getPluginManager().registerEvents(objectivesManager, this);
        Bukkit.getPluginManager().registerEvents(sequencesManager, this);
        var dcmd = Bukkit.getPluginCommand("iekdialogue");
        if (dcmd != null) dcmd.setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("iekquests.dialogue") && !sender.hasPermission("iekquests.admin")) {
                sender.sendMessage(Component.text("Dialogue editing requires iekquests.dialogue or iekquests.admin."));
                return true;
            }
            dialogueManager.adminCommand(sender, args);
            return true;
        });
        var cmd = Bukkit.getPluginCommand("questbook");
        if (cmd != null) cmd.setExecutor((sender, command, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload") && (sender.hasPermission("iekquests.reload") || sender.hasPermission("iekquests.admin"))) {
                reloadAllData();
                sender.sendMessage(Component.text("IekQuests: " + quests.size() + " quest(s), dialogues, tags, NPC bindings, and menu items reloaded."));
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("check") && (sender.hasPermission("iekquests.admin"))) {
                adminCheck(sender, args.length >= 2 ? args[1] : null);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("track") && sender instanceof Player tp) {
                boolean off = tp.getPersistentDataContainer().has(hudOffKey, PersistentDataType.BYTE);
                if (off) tp.getPersistentDataContainer().remove(hudOffKey); else tp.getPersistentDataContainer().set(hudOffKey, PersistentDataType.BYTE, (byte) 1);
                tp.sendMessage(legacy(off ? "&aQuest tracker on." : "&7Quest tracker off."));
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("reset") && (sender.hasPermission("iekquests.reset") || sender.hasPermission("iekquests.admin"))) {
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
            if (args.length >= 4 && args[0].equalsIgnoreCase("do") && sender instanceof Player dp) { chatDo(dp, args); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("open") && sender instanceof Player op) {
                var oq = byId(args[1]);
                if (oq != null) openQuest(op, oq, 0);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("mode") && sender instanceof Player mp) {
                modeCommand(mp, args.length > 1 ? args[1] : null);
                return true;
            }
            if (sender instanceof Player p) { if (chatMode(p, null)) chatMenu(p); else openMenu(p); }
            else sender.sendMessage(Component.text("questbook: from console use reload/reset only"));
            return true;
        });
        var objectiveCommand = Bukkit.getPluginCommand("iekobjective");
        if (objectiveCommand != null) objectiveCommand.setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("iekquests.objective") && !sender.hasPermission("iekquests.admin")) {
                sender.sendMessage(Component.text("Objective administration requires iekquests.objective or iekquests.admin."));
                return true;
            }
            objectivesManager.command(sender, args);
            return true;
        });
        var iekQuestCommand = Bukkit.getPluginCommand("iekquest");
        if (iekQuestCommand != null) iekQuestCommand.setExecutor((sender, command, label, args) -> {
            if (!sender.isOp() && !sender.hasPermission("iekquests.admin")) {
                sender.sendMessage(Component.text("This IekQuests command is for operators only."));
                return true;
            }
            try {
                if (args.length == 2 && args[0].equalsIgnoreCase("reload") && args[1].equalsIgnoreCase("quest")) {
                    QuestPackageManager.ReloadReport report = questPackageManager.reload();
                    for (String warning : report.warnings()) sender.sendMessage(Component.text("Warning: " + warning));
                    for (String error : report.errors()) sender.sendMessage(Component.text("Error: " + error));
                    if (!report.success()) {
                        sender.sendMessage(Component.text("Quest reload stopped; the previous organization files were kept."));
                        return true;
                    }
                    loadAggregateData();
                    sender.sendMessage(Component.text("Quest packages reloaded: " + report.bundles() + " bundle(s), "
                            + report.quests() + " legacy quest definition(s)."));
                    dialogueManager.adminCommand(sender, new String[]{"validate"});
                    objectivesManager.command(sender, new String[]{"validate"});
                    eventsManager.command(sender, new String[]{"events", "validate"});
                    sequencesManager.validate(sender, null);
                    return true;
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start")) {
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) throw new IllegalArgumentException("Player must be online: " + args[2]);
                    int started = eventsManager.startById(target, args[3]);
                    sender.sendMessage(Component.text(started == 0
                            ? "No eligible configured event entry named '" + args[3] + "' was started for " + target.getName() + "."
                            : "Started " + started + " event entry/entries named '" + args[3] + "' for " + target.getName() + "."));
                    return true;
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("journal")
                        && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                    journalManager.changeOnlinePlayer(sender, args[2], args[3], args[1].equalsIgnoreCase("add"));
                    return true;
                }
                sender.sendMessage(Component.text("Usage: /iekquest reload quest | /iekquest event start <player> <event-id> | /iekquest journal <add|remove> <player> <entry-id>"));
            } catch (Exception ex) {
                sender.sendMessage(Component.text("IekQuests command failed: " + codeErrorLocation(ex)));
            }
            return true;
        });
        var journalCommand = Bukkit.getPluginCommand("journal");
        if (journalCommand != null) journalCommand.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the Journal."));
                return true;
            }
            journalManager.open(player);
            return true;
        });
        items = new ItemLibrary(this);
        Bukkit.getPluginManager().registerEvents(items, this);
        items.load();
        items.editor = new ItemEditor(this, items);
        Bukkit.getPluginManager().registerEvents(items.editor, this);
        var icmd = Bukkit.getPluginCommand("iekitem");
        if (icmd != null) { icmd.setExecutor(items); icmd.setTabCompleter(items); }
        wizard = new QuestWizard(this);
        Bukkit.getPluginManager().registerEvents(wizard, this);
        dialogueWizard = new DialogueWizard(this, dialogueManager);
        Bukkit.getPluginManager().registerEvents(dialogueWizard, this);
        var wcmd = Bukkit.getPluginCommand("questwizard");
        if (wcmd != null) wcmd.setExecutor((sender, command, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("delete") && (sender.hasPermission("iekquests.edit") || sender.hasPermission("iekquests.admin"))
                    && args.length >= 2) {
                if (removeWizardQuest(args[1])) sender.sendMessage(Component.text("Wizard-quest " + args[1] + " deleted."));
                else sender.sendMessage(Component.text("Only wizard-managed quests can be deleted here; edit the source bundle for uploaded quests."));
                return true;
            }
            if (sender instanceof Player p && (p.hasPermission("iekquests.wizard") || p.hasPermission("iekquests.admin"))) wizard.open(p);
            else if (sender instanceof Player p && p.hasPermission("iekquests.dialogue")) dialogueWizard.open(p);
            else sender.sendMessage(Component.text("questwizard requires iekquests.wizard, iekquests.dialogue, or iekquests.admin."));
            return true;
        });
        var rcmd = Bukkit.getPluginCommand("iekrestart");
        if (rcmd != null) rcmd.setExecutor(this::onRestartCommand);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshOpenGuis, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::tracker, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 1200L, 1200L);
        load();
        getLogger().info("IekQuests " + getDescription().getVersion() + " klaar - " + quests.size() + " quest(s).");
    }

    @Override
    public void onDisable() {
        if (restartTask != null) restartTask.cancel();
        if (objectivesManager != null) objectivesManager.shutdown();
        if (sequencesManager != null) sequencesManager.shutdown();
        if (dialogueManager != null) dialogueManager.shutdown();
        saveData();
    }

    // ---------------------------------------------------------------- /iekrestart

    private BukkitTask restartTask;
    private int restartLeft;

    





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

    private String codeErrorLocation(Exception ex) {
        StackTraceElement source = null;
        for (StackTraceElement element : ex.getStackTrace()) {
            if (element.getClassName().startsWith("com.iekcorbar.quests.")) { source = element; break; }
        }
        String location = source == null ? "" : " at " + source.getFileName() + ":" + source.getLineNumber();
        return ex.getClass().getSimpleName() + ": " + ex.getMessage() + location;
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
        if (questPackageManager != null) {
            QuestPackageManager.ReloadReport report = questPackageManager.reload();
            for (String warning : report.warnings()) getLogger().warning(warning);
            for (String error : report.errors()) getLogger().severe(error);
            if (!report.success()) getLogger().severe("Quest package reload failed; existing organization files remain active.");
        }
        loadAggregateData();
    }

    private void loadAggregateData() {
        quests.clear();
        sequences.clear();
        var qc = YamlConfiguration.loadConfiguration(organizationFile("quests.yml"));

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
        quests.sort((a, b) -> Integer.compare(a.order, b.order));

        data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        prog.clear();
        loadData();
        if (journalManager != null) journalManager.load();
        if (dialogueManager != null) dialogueManager.load();
        if (objectivesManager != null) objectivesManager.load();
        if (dialogueManager != null) dialogueManager.validateObjectiveReferences();
        if (sequencesManager != null) sequencesManager.load();
        if (eventsManager != null) eventsManager.load();
        if (dialogueManager != null) dialogueManager.validateSequenceReferences();
    }

    
    private void parseQuests(ConfigurationSection sec, boolean fromWizard) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Quest q = new Quest();
            q.fromWizard = fromWizard || questPackageManager != null && questPackageManager.isWizardQuest(id);
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
            q.group = s.getString("group", "");
            q.mode = s.getString("mode", "player").toLowerCase();
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
                    c.action = cv.getString("action", "").toLowerCase();
                    c.close = cv.getBoolean("close", false);
                    c.limitPerDay = cv.getInt("limit-per-day", 0);
                    c.voice = cv.getString("voice", "");
                    c.conds = conds(cv.getString("cond", ""));
                    for (var d : cv.getStringList("do")) c.doList.add(d);
                    g.choices.add(c);
                } else for (var cm : v.getMapList("choices")) {
                    
                    
                    
                    Choice c = new Choice();
                    c.label = mm(cm, "label");
                    if (c.label.isEmpty()) c.label = "…";
                    c.say = mm(cm, "say");
                    c.gotoKey = mm(cm, "goto");
                    c.action = mm(cm, "action").toLowerCase();
                    c.voice = mm(cm, "voice");
                    c.conds = conds(mm(cm, "cond"));
                    Object closev = cm.get("close");
                    c.close = closev instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(closev));
                    try { c.limitPerDay = Integer.parseInt(mm(cm, "limit-per-day").trim()); } catch (NumberFormatException ignored) { }
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

    void save() { saveData(); }
    void saveDialogueProgress() { saveData(); }

    void reloadAllData() {
        reloadConfig();
        eliteItemFiles = null;
        load();
        if (items != null) items.load();
    }

    void reloadDialogueConfiguration() { reloadAllData(); }
    void openDialogueWizard(Player player) { if (dialogueWizard != null) dialogueWizard.open(player); }
    QuestWizard questWizard() { return wizard; }

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

    
    void runItemActions(Player p, List<String> acts) {
        var scratch = new Prog();
        for (var a : acts) run(p, null, scratch, a);
    }

    private Quest byId(String id) {
        for (var q : quests) if (q.id.equals(id)) return q;
        return null;
    }

    

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
            if (c.startsWith("eco>=") || c.startsWith("eco<")) {
                boolean ge = c.startsWith("eco>=");
                var want = Double.parseDouble(c.substring(ge ? 5 : 4));
                var bal = ecoBalance(p);
                return bal < 0 ? false : (ge ? bal >= want : bal < want);
            }
            if (c.startsWith("money")) {
                boolean ge = c.startsWith("money>=");
                if (!ge && !c.startsWith("money<")) return false;
                var want = Double.parseDouble(c.substring(ge ? 7 : 6));
                var bal = balance(p);
                return bal < 0 ? false : (ge ? bal >= want : bal < want);
            }
            if (c.startsWith("perm:")) return p.hasPermission(c.substring(5));
            if (c.startsWith("quest:")) {      
                var m = prog.get(p.getUniqueId());
                var pp = m == null ? null : m.get(c.substring(6));
                return pp != null && pp.done;
            }
            if (c.startsWith("world:")) return p.getWorld().getName().equalsIgnoreCase(c.substring(6));
            if (c.startsWith("level>=")) return p.getLevel() >= Integer.parseInt(c.substring(7));
            if (c.startsWith("gamemode:")) return p.getGameMode().name().equalsIgnoreCase(c.substring(9));
            if (c.equals("night")) { var t = p.getWorld().getTime(); return t > 13000 && t < 23000; }
            if (c.equals("day")) { var t = p.getWorld().getTime(); return t <= 13000 || t >= 23000; }
            return true;   
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
        boolean legacyManaged = quests.stream().anyMatch(q -> q.npcIds.contains(id));
        if (dialogueManager != null && dialogueManager.hasNpc(id)) {
            e.setCancelled(true);
            if (dialogueManager.canPlayerSeeNpc(e.getPlayer(), id)) dialogueManager.openForNpc(e.getPlayer(), id);
        } else if (legacyManaged) {
            e.setCancelled(true);
            getLogger().warning("Quest NPC " + id + " has no binding in npcs.yml; legacy dialogue was not opened.");
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
    public void onJoin(PlayerJoinEvent e) {
        if (dialogueManager != null) Bukkit.getScheduler().runTask(this, () -> dialogueManager.refreshNpcVisibility(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (sequencesManager != null) sequencesManager.cancelPlayer(e.getPlayer().getUniqueId());
        if (dialogueManager != null) dialogueManager.close(e.getPlayer());
        chatActive.remove(e.getPlayer().getUniqueId());
        dialogueSessions.remove(e.getPlayer().getUniqueId());
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
        handleTag(p, q, tag, h.npc);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
    }

    
    void handleTag(Player p, Quest q, String tag, int npc) {
        Prog pr = pr(p, q);
        switch (tag) {
            case "mode:chat" -> {
                if (!setPref(p, q, "chat")) return;
                p.closeInventory();
                chatQuest(p, q, npc);
                return;
            }
            case "mode:gui" -> {
                if (!setPref(p, q, "gui")) return;
                chatActive.remove(p.getUniqueId());
                openQuestGui(p, q, npc);
                return;
            }
            case "back" -> { if (chatMode(p, null)) chatMenu(p); else openMenu(p); return; }
            case "start" -> { startQuest(p, q, pr); return; }
            case "refuse" -> { closeNpcDialogue(p); return; }
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
            
            List<Cond> rest = new ArrayList<>();
            boolean npcOk = true;
            for (var cc : c.conds) {
                if (cc.raw.startsWith("npc:")) {
                    try { if (npc != Integer.parseInt(cc.raw.substring(4))) npcOk = false; }
                    catch (NumberFormatException ignored) { npcOk = false; }
                } else rest.add(cc);
            }
            if (!npcOk || !ok(p, q, pr, rest)) { deny(p); return; }
            String action = c.action == null ? "" : c.action.toLowerCase(Locale.ROOT);
            if ((action.equals("start") || action.equals("accept")) && (pr.started || pr.done)) {
                dialogueMessage(p, dialogueLabel("errors.unavailable", "&7That option is no longer available."));
                deny(p);
                return;
            }
            if ((action.equals("start") || action.equals("accept")) && !ok(p, q, pr, q.startConds)) {
                dialogueMessage(p, dialogueLabel("errors.requirements", "&cYou do not meet the requirements for this quest."));
                deny(p);
                return;
            }
            if (action.equals("continue")) {
                Stage active = pr.started && !pr.done ? q.stages.get(pr.stage) : null;
                if (active == null) {
                    dialogueMessage(p, dialogueLabel("errors.unavailable", "&7That option is no longer available."));
                    deny(p);
                    return;
                }
                if (!stageActionReady(p, q, pr, active)) {
                    dialogueMessage(p, dialogueLabel("errors.requirements", "&cYou do not meet the requirements for this quest."));
                    deny(p);
                    return;
                }
                handleTag(p, q, "act", npc);
                if (!c.say.isEmpty()) sayDialogueOrChat(p, q, c.say);
                speak(p, c.voice);
                for (var d : c.doList) run(p, q, pr, d);
                save();
                return;
            }
            if (c.limitPerDay > 0) {
                
                String lk = "dl:" + q.id + ":" + s.key + ":" + i;
                int today = (int) java.time.LocalDate.now().toEpochDay();
                int raw = pr.points.getOrDefault(lk, 0);
                int used = raw / 1000 == today ? raw % 1000 : 0;
                if (used >= c.limitPerDay) {
                    dialogueMessage(p, "&cDaily limit reached (" + c.limitPerDay + "/day). Come back tomorrow.");
                    deny(p);
                    return;
                }
                pr.points.put(lk, today * 1000 + used + 1);
            }
            if (action.equals("start") || action.equals("accept")) {
                startQuest(p, q, pr);
                if (!pr.started) return;
                if (!c.say.isEmpty()) sayDialogueOrChat(p, q, c.say);
                speak(p, c.voice);
                for (var d : c.doList) run(p, q, pr, d);
                save();
                return;
            }
            if (!c.say.isEmpty()) sayDialogueOrChat(p, q, c.say);
            speak(p, c.voice);
            for (var d : c.doList) run(p, q, pr, d);
            if (action.equals("refuse")) {
                save();
                closeNpcDialogue(p);
                if (!dialogueSessions.containsKey(p.getUniqueId())) p.closeInventory();
                return;
            }
            if (action.equals("close") || c.close) {
                
                save();
                refresh(p, q);
                return;
            }
            if (!pr.stage.isEmpty() && !c.gotoKey.isEmpty() && q.stages.containsKey(c.gotoKey)) {
                pr.stage = c.gotoKey;
                speakFirst(p, q, pr);
            } else if (pr.done && !c.gotoKey.isEmpty() && q.stages.containsKey(c.gotoKey)) {
                
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

    boolean stageActionReady(Player p, Quest q, Prog pr, Stage s) {
        if (!ok(p, q, pr, s.conds)) return false;
        return switch (s.kind) {
            case "menu" -> false;
            case "talk" -> true;
            case "walk" -> s.loc != null && p.getWorld().equals(s.loc.getWorld()) && walkReached(s, p);
            case "kill" -> pr.points.getOrDefault("kill:" + q.id + ":" + s.key, 0) >= s.amount;
            case "deliverall" -> allHeld(p, s.items);
            default -> held(p, s.material, s.nameFilter.isEmpty() ? null : s.nameFilter) >= s.amount;
        };
    }

    
    void speakFirst(Player p, Quest q, Prog pr) {
        var s = q.stages.get(pr.stage);
        if (s == null) return;
        speak(p, s.voice);
        
        if (dialogueManager != null && dialogueManager.hasSession(p)) return;
        if (!chatMode(p, q) || dialogueSessions.containsKey(p.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) refresh(p, q); }, 2L);
    }

    // ---------------------------------------------------------------- flow

    void startQuest(Player p, Quest q, Prog pr) {
        if (q.stages.isEmpty()) { dialogueMessage(p, "&7This quest is not ready yet."); return; }
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
        if (eventsManager != null) eventsManager.questAccepted(p, q.id);
        speakFirst(p, q, pr);
        openQuest(p, q);
        save();
    }

    private void resetQuest(Prog pr) {
        pr.stage = "";
        pr.started = false;
        pr.done = false;
    }

    
    void advance(Player p, Quest q, Prog pr, Stage s) {
        for (var r : s.rewards) run(p, q, pr, r);
        String nxt = !s.gotoKey.isEmpty() ? s.gotoKey : (!s.next.isEmpty() ? s.next : nextKey(q, s));
        if (nxt == null || !q.stages.containsKey(nxt)) {
            finish(p, q, pr);
            return;
        }
        pr.stage = nxt;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.3f);
        speakFirst(p, q, pr);
        openQuest(p, q);
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

    void finish(Player p, Quest q, Prog pr) {
        pr.done = true;
        pr.stage = "";
        if (!q.rewards.isEmpty()) runRewards(p, q, pr, q.rewards);
        if (eventsManager != null) eventsManager.questCompleted(p, q.id);
        refresh(p, q);
        save();
    }

    boolean repeatActionReady(Player p, Quest q, String id) {
        Repeat repeat = null;
        for (Repeat candidate : q.repeats) if (candidate.id.equals(id)) repeat = candidate;
        if (repeat == null) return false;
        Long cooldown = pr(p, q).cd.get(repeat.id);
        if (cooldown != null && cooldown > now()) return false;
        if (repeat.kind.equals("walk")) return repeat.loc != null && p.getWorld().equals(repeat.loc.getWorld())
                && p.getLocation().distanceSquared(repeat.loc) <= repeat.radius * repeat.radius;
        if (!repeat.items.isEmpty()) return allHeld(p, repeat.items);
        return held(p, repeat.material, repeat.name.isEmpty() ? null : repeat.name) >= repeat.amount;
    }

    void doRepeat(Player p, Quest q, Prog pr, String id) {
        Repeat r = null;
        for (var x : q.repeats) if (x.id.equals(id)) r = x;
        if (r == null) return;
        Long cd = pr.cd.get(r.id);
        if (cd != null && cd > now()) {
            dialogueMessage(p, "&7Not yet - the queue clears in " + fmt(cd - now()) + ".");
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
        if (r.money > 0) { dispatch("cmi money give " + p.getName() + " -s " + r.money); logTx(p, q, "repeat " + r.id + " money " + r.money); }
        if (r.eco > 0) { dispatch("em money add " + p.getName() + " " + r.eco); logTx(p, q, "repeat " + r.id + " eco " + r.eco); }
        runRewards(p, q, pr, r.rewards);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.9f, 1.4f);
        speak(p, r.voice);
        pr.cd.put(r.id, now() + (r.daily ? 86400 : r.cooldown));
        refresh(p, q);
        save();
    }

    

    
    void run(Player p, Quest q, Prog pr, String raw) {
        var a = raw.trim().replaceFirst("(?i)\\s+-s\\s*$", "").trim();
        if (a.isEmpty()) return;
        a = a.replace("%player%", p.getName());
        int sp = a.indexOf(' ');
        String key = sp < 0 ? a : a.substring(0, sp);
        String val = sp < 0 ? "" : a.substring(sp + 1);
        switch (key) {
            case "say", "title", "sound", "tag+", "tag-", "point", "effect", "compass", "sequence", "chat", "joinjob", "leavejob" -> { }
            default -> logTx(p, q, a);
        }
        switch (key) {
            case "money" -> dispatch("cmi money give " + p.getName() + " -s " + val);
            case "pay" -> dispatch("cmi money take " + p.getName() + " -s " + val);
            case "eco" -> dispatch("em money add " + p.getName() + " " + val);
            case "ecopay" -> dispatch("em money remove " + p.getName() + " " + val);
            case "eloot" -> {
                String file = val.trim().endsWith(".yml") ? val.trim() : val.trim() + ".yml";
                if (!eliteItemExists(file)) {
                    getLogger().warning("eloot: EliteMobs-item niet gevonden: " + file + " (quest " + (q == null ? "-" : q.id) + ")");
                    dialogueMessage(p, "&cThat item is not available right now. Tell staff.");
                } else dispatch("em loot give " + p.getName() + " " + file);
            }
            case "title" -> {
                String[] ab = val.split("\\|");
                p.showTitle(Title.title(legacy(ab[0]), ab.length > 1 ? legacy(ab[1]) : Component.empty()));
            }
            case "sound" -> {
                var snd = sound(val);
                if (snd != null) {
                    p.playSound(p.getLocation(), snd, SoundCategory.PLAYERS, 0.8f, 1f);
                } else {
                    
                    
                    p.playSound(p.getLocation(), namespaced(val), SoundCategory.PLAYERS, 0.8f, 1f);
                }
            }
            case "say" -> dialogueMessage(p, val);
            case "tag+" -> {
                if (pr != null) { if (!pr.tags.contains(val)) pr.tags.add(val); save(); }
                else if (dialogueManager != null) dialogueManager.changeRewardPlayerTag(p, val, true);
            }
            case "tag-" -> {
                if (pr != null) { pr.tags.remove(val); save(); }
                else if (dialogueManager != null) dialogueManager.changeRewardPlayerTag(p, val, false);
            }
            case "perm+" -> dispatch("lp user " + p.getName() + " permission set " + val + true);
            case "perm-" -> dispatch("lp user " + p.getName() + " permission unset " + val);
            case "point" -> {
                
                String[] x = val.split(" ");
                if (x.length >= 2) {
                    String cat = x[0];
                    String op = x[1];
                    int n = Integer.parseInt(op.replace("+", "").replace("=", "").replace("-", ""));
                    if (pr != null) {
                        if (op.startsWith("+")) pr.points.merge(cat, n, Integer::sum);
                        else if (op.startsWith("-")) pr.points.merge(cat, -n, Integer::sum);
                        else pr.points.put(cat, n);
                    } else {
                        String path = "rewards.player-points." + p.getUniqueId() + "." + cat;
                        int current = data.getInt(path);
                        if (op.startsWith("+")) data.set(path, current + n);
                        else if (op.startsWith("-")) data.set(path, current - n);
                        else data.set(path, n);
                    }
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
                    
                    
                    
                    if (p.hasPermission("iekquests.admin")) p.teleport(l);
                    else {
                        setCompass(p, l);
                        dialogueMessage(p, "&7No ride for you - the compass points the way. Make the journey yourself!");
                    }
                }
            }
            case "item" -> {
                String[] x = val.trim().split(" ");
                if (!items.exists(x[0])) {
                    getLogger().warning("item reward: onbekend IekItem '" + x[0] + "' (quest " + (q == null ? "-" : q.id) + ")");
                    dialogueMessage(p, "&cThat item is not available right now. Tell staff.");
                } else items.give(p, x[0], x.length > 1 ? (int) num(x[1], 1) : 1);
            }
            case "travel" -> travel(p, val);
            case "xp" -> p.giveExp((int) num(val.trim(), 0));
            case "heal" -> {
                var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                p.setHealth(mh == null ? 20 : mh.getValue());
            }
            case "feed" -> { p.setFoodLevel(20); p.setSaturation(20f); }
            case "broadcast" -> Bukkit.broadcast(legacy(val));
            case "cmdplayer" -> {
                final String cmdp = val.startsWith("/") ? val.substring(1) : val;
                Bukkit.getScheduler().runTask(this, () -> p.performCommand(cmdp));
            }
            case "takeitem" -> {
                String[] x = val.trim().split(" ");
                take(p, x[0].toLowerCase().startsWith("item:") ? x[0] : (items.exists(x[0]) ? "item:" + x[0] : x[0]),
                        x.length > 1 ? (int) num(x[1], 1) : 1);
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
                
                final String j = val;
                Bukkit.getScheduler().runTask(this, () -> p.performCommand("jobs join " + j));
            }
            case "leavejob" -> {
                final String j = val;
                Bukkit.getScheduler().runTask(this, () -> p.performCommand("jobs leave " + j));
            }
            case "chat" -> {
                
                final String msg = val;
                p.closeInventory();
                Bukkit.getScheduler().runTask(this, () -> p.chat(msg));
            }
            case "sequence" -> playSeq(p, q, pr, val);
            default -> dispatch(a);
        }
    }

    void runRewards(Player p, Quest q, Prog pr, List<String> rewards) {
        for (var r : rewards) run(p, q, pr, r);
    }

    void runStandaloneRewards(Player player, List<String> rewards) {
        for (String reward : rewards) run(player, null, null, reward);
    }

    boolean hasSequence(String id) { return sequences.containsKey(id); }
    boolean hasIekItemReward(String id) { return items.exists(id); }
    boolean hasEliteItemReward(String file) { return eliteItemExists(file.endsWith(".yml") ? file : file + ".yml"); }
    boolean isValidRewardLocation(String value) { return parseLoc(value) != null; }

    
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

    // ---------------------------------------------------------------- tracker + staff-check

    
    private void tracker() {
        for (var p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) continue;
            if (p.getPersistentDataContainer().has(hudOffKey, PersistentDataType.BYTE)) continue;
            var m = prog.get(p.getUniqueId());
            if (m == null) continue;
            for (var q : quests) {
                Prog pr = m.get(q.id);
                if (pr == null || !pr.started || pr.done || pr.stage.isEmpty()) continue;
                Stage st = q.stages.get(pr.stage);
                if (st == null || st.kind.equals("talk") || st.kind.equals("menu")) continue;
                p.sendActionBar(legacy("&6" + plain(q.title) + " &8» " + labelFor(p, q, pr, st)));
                break;
            }
        }
    }

    
    private void adminCheck(CommandSender sender, String who) {
        var none = new ArrayList<String>();
        for (var q : quests) if (q.npcIds.isEmpty()) none.add(q.id);
        sender.sendMessage(legacy("&6IekQuests: &f" + quests.size() + " quests, " + sequences.size() + " sequences"));
        sender.sendMessage(legacy("&7Without NPC (" + none.size() + "): &f" + (none.isEmpty() ? "-" : String.join(", ", none))));
        if (who == null) { sender.sendMessage(legacy("&8Use /questbook check <player> for a player's tags and active quests.")); return; }
        var pl = Bukkit.getPlayerExact(who);
        if (pl == null) { sender.sendMessage(legacy("&cPlayer not online: " + who)); return; }
        var m = prog.get(pl.getUniqueId());
        if (m == null || m.isEmpty()) { sender.sendMessage(legacy("&7" + who + " has no progress yet.")); return; }
        var active = new ArrayList<String>();
        var tags = new java.util.TreeSet<String>();
        for (var e : m.entrySet()) {
            Prog pr = e.getValue();
            if (pr.started && !pr.done) active.add(e.getKey() + "@" + pr.stage);
            tags.addAll(pr.tags);
        }
        sender.sendMessage(legacy("&7Active: &f" + (active.isEmpty() ? "-" : String.join(", ", active))));
        sender.sendMessage(legacy("&7Tags (" + tags.size() + "): &f" + (tags.isEmpty() ? "-" : String.join(", ", tags))));
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
                if (!sig.equals(h.sig)) { h.sig = sig; openQuestGui(p, q, h.npc); }
            }
        }
    }

    private final Object txLock = new Object();

    
    private void logTx(Player p, Quest q, String what) {
        try {
            var f = new File(getDataFolder(), "transactions.log");
            String line = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + p.getName() + " | " + (q == null ? "-" : q.id) + " | " + what + System.lineSeparator();
            synchronized (txLock) {
                java.nio.file.Files.writeString(f.toPath(), line, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            getLogger().warning("transactions.log: " + e.getMessage());
        }
    }

    private void dispatch(String consoleCmd) {
        Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd));
    }

    void speak(Player p, String key) {
        if (key == null || key.isEmpty()) return;
        
        
        
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

    
    private static String itemId(String material) {
        return material != null && material.regionMatches(true, 0, "item:", 0, 5) ? material.substring(5).trim().toLowerCase() : null;
    }

    private int held(Player p, String material, String nameContains) {
        String iid = itemId(material);
        if (iid != null) {
            int n = 0;
            for (var st : p.getInventory().getStorageContents()) if (st != null && iid.equals(items.idOf(st))) n += st.getAmount();
            return n;
        }
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
        String iid = itemId(material);
        var mt = iid != null ? null : Material.matchMaterial(material);
        if (iid == null && mt == null) return;
        int left = amount;
        var content = p.getInventory().getStorageContents();
        for (int i = 0; i < content.length && left > 0; i++) {
            var it = content[i];
            if (it == null) continue;
            if (iid != null ? !iid.equals(items.idOf(it)) : it.getType() != mt) continue;
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
        
        Map<String, List<Quest>> groups = new LinkedHashMap<>();
        for (var q : vis) {
            String g = q.group.isEmpty() ? "Other" : q.group;
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(q);
        }
        List<Object> flat = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            flat.add(entry.getKey());
            flat.addAll(entry.getValue());
        }
        boolean admin = p.hasPermission("iekquests.admin");
        int per = 35;                                   
        int pages = Math.max(1, (flat.size() + per - 1) / per);
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        var inv = Bukkit.createInventory(new Holder(p.getUniqueId(), "", page), 54, legacy("&6&l⚒ Quest Book"));
        int slot = 10;
        for (int i = page * per; i < flat.size() && i < (page + 1) * per; i++) {
            Object obj = flat.get(i);
            if (obj instanceof String groupName) {
                var header = item("GRAY_STAINED_GLASS_PANE", "&6" + groupName, List.of("&7---"));
                inv.setItem(slot++, header);
            } else if (obj instanceof Quest q) {
                if (q.stages.isEmpty()) continue;
                List<String> lo = new ArrayList<>();
                if (!q.subtitle.isEmpty()) lo.add("&8" + q.subtitle);
                lo.add("   " + statusLine(q, pr(p, q)));
                var it = item(q.icon, q.title, lo);
                setTag(it, "open:" + q.id);
                inv.setItem(slot++, it);
            }
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
        if (suppressDialogueRefresh.contains(p.getUniqueId())) return;
        if (dialogueManager != null && dialogueManager.hasSession(p)) return;
        if (npcCtx > 0) {
            if (dialogueManager != null && dialogueManager.hasNpc(npcCtx)) dialogueManager.openForNpc(p, npcCtx);
            return;
        }
        DialogueSession activeDialog = dialogueSessions.get(p.getUniqueId());
        if (activeDialog != null && activeDialog.quest.equals(q.id)) {
            renderNpcDialog(p, q, activeDialog);
            return;
        }
        if (chatMode(p, q)) { p.closeInventory(); chatQuest(p, q, npcCtx); }
        else { chatActive.remove(p.getUniqueId()); openQuestGui(p, q, npcCtx); }
    }

    private void openQuestGui(Player p, Quest q, int npcCtx) {
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
            
            int[] slots = choiceSlots(size);
            int n = 0;
            for (var c : s.choices) {
                if (n >= slots.length) break;
                inv.setItem(slots[n++], tagged(Material.OAK_SIGN, c.label, "choice:" + indexOfChoice(s, c)));
            }
        }
        inv.setItem(40, tagged(Material.BARRIER, "&cBack", "back"));
        if (q.mode.equals("player")) inv.setItem(4, tagged(Material.WRITABLE_BOOK, "&bConversation mode",
                "mode:chat"));
        p.openInventory(inv);
    }

    
    private void openNpcDialog(Player p, Quest q, int npc, int textPage, int choicePage, List<String> replies) {
        DialogueSession session = dialogueSessions.get(p.getUniqueId());
        if (session == null || !session.quest.equals(q.id) || session.npc != npc) {
            session = new DialogueSession(p.getUniqueId(), q.id, npc);
            dialogueSessions.put(p.getUniqueId(), session);
        }
        session.textPage = textPage;
        session.choicePage = choicePage;
        session.replyLines.clear();
        session.replyLines.addAll(replies);
        renderNpcDialog(p, q, session);
    }

    private void closeNpcDialogue(Player p) {
        dialogueSessions.remove(p.getUniqueId());
        p.closeDialog();
    }

    private void dialogueMessage(Player p, String message) {
        if (dialogueManager != null && dialogueManager.hasSession(p)) {
            dialogueManager.appendLine(p, message);
            return;
        }
        DialogueSession session = dialogueSessions.get(p.getUniqueId());
        if (session == null) {
            p.sendMessage(legacy(message));
            return;
        }
        session.replyLines.add(message);
        Quest q = byId(session.quest);
        if (q != null) renderNpcDialog(p, q, session);
    }

    private void sayDialogueOrChat(Player p, Quest q, String message) {
        DialogueSession session = dialogueSessions.get(p.getUniqueId());
        if (session != null && session.quest.equals(q.id)) dialogueMessage(p, message);
        else p.sendMessage(legacy(message));
    }

    private Stage dialogueStage(Quest q, Prog pr) {
        if (!pr.stage.isEmpty()) return q.stages.get(pr.stage);
        return q.stages.isEmpty() ? null : q.stages.values().iterator().next();
    }

    private List<String> dialogueLines(Quest q, Prog pr) {
        List<String> lines = new ArrayList<>();
        if (!q.subtitle.isEmpty()) lines.add("&8" + q.subtitle);
        if (pr.done) {
            lines.add(dialogueLabel("complete-text", "&aThe quest is complete!"));
            for (var line : q.journal) lines.add("&7" + line);
        } else {
            Stage stage = dialogueStage(q, pr);
            if (stage != null) lines.addAll(stage.lines);
            if (!pr.started) lines.add(dialogueLabel("ready-text", "&7Ready when you are."));
        }
        if (lines.isEmpty()) lines.add(dialogueLabel("empty-text", "&7There is nothing more to say right now."));
        return lines;
    }

    private boolean dialogueChoiceAllowed(Player p, Quest q, Prog pr, Stage stage, Choice choice, int npc) {
        List<Cond> rest = new ArrayList<>();
        for (var cond : choice.conds) {
            if (cond.raw.startsWith("npc:")) {
                try { if (npc != Integer.parseInt(cond.raw.substring(4))) return false; }
                catch (NumberFormatException ignored) { return false; }
            } else rest.add(cond);
        }
        return ok(p, q, pr, rest);
    }

    private List<DialogueOption> dialogueOptions(Player p, Quest q, int npc) {
        Prog pr = pr(p, q);
        Stage stage = dialogueStage(q, pr);
        List<DialogueOption> options = new ArrayList<>();
        if (!pr.started) {
            if (stage != null) for (int i = 0; i < stage.choices.size(); i++) {
                Choice choice = stage.choices.get(i);
                if ((choice.action.equals("start") || choice.action.equals("accept")
                        || choice.action.equals("refuse") || choice.action.equals("close"))
                        && dialogueChoiceAllowed(p, q, pr, stage, choice, npc))
                    options.add(new DialogueOption(choice.label, "choice:" + i));
            }
            if (options.isEmpty()) {
                options.add(new DialogueOption(dialogueLabel("start", "&aYes, I want to begin"), "start"));
                options.add(new DialogueOption(dialogueLabel("refuse", "&cNo, not now"), "refuse"));
            }
            return options;
        }
        if (pr.done) {
            if (q.repeats.isEmpty()) options.add(new DialogueOption(dialogueLabel("restart", "&aStart the quest again"), "newtry"));
            else for (var repeat : q.repeats) {
                Long cooldown = pr.cd.get(repeat.id);
                boolean ready = cooldown == null || cooldown <= now();
                String label = (ready ? "&a" : "&7") + repeat.label
                        + (ready ? "" : " &7(" + fmt(cooldown - now()) + ")");
                options.add(new DialogueOption(label, "repeat:" + repeat.id));
            }
            Stage first = q.stages.isEmpty() ? null : q.stages.values().iterator().next();
            if (first != null) for (int i = 0; i < first.choices.size(); i++) {
                Choice choice = first.choices.get(i);
                if (dialogueChoiceAllowed(p, q, pr, first, choice, npc))
                    options.add(new DialogueOption(choice.label, "menu:" + i));
            }
            return options;
        }
        if (stage == null) return options;
        if (!stage.kind.equals("menu")) options.add(new DialogueOption(labelFor(p, q, pr, stage), "act"));
        for (int i = 0; i < stage.choices.size(); i++) {
            Choice choice = stage.choices.get(i);
            if ("start".equalsIgnoreCase(choice.action) || "accept".equalsIgnoreCase(choice.action)) continue;
            if (dialogueChoiceAllowed(p, q, pr, stage, choice, npc))
                options.add(new DialogueOption(choice.label, "choice:" + i));
        }
        if (options.isEmpty()) options.add(new DialogueOption(dialogueLabel("close", "&cGoodbye"), "refuse"));
        return options;
    }

    private String dialogueLabel(String key, String fallback) {
        return getConfig().getString("dialogue." + key, fallback);
    }

    private void renderNpcDialog(Player p, Quest q, DialogueSession session) {
        if (!p.isOnline() || !dialogueSessions.containsKey(p.getUniqueId())) return;
        Prog pr = pr(p, q);
        List<String> lines = dialogueLines(q, pr);
        lines.addAll(session.replyLines);
        int linesPerPage = Math.max(1, Math.min(32, getConfig().getInt("dialogue.lines-per-page", 8)));
        int textPages = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
        List<DialogueOption> options = dialogueOptions(p, q, session.npc);
        int optionPages = Math.max(1, (options.size() + 5) / 6);
        session.textPage = Math.max(0, Math.min(session.textPage, textPages - 1));
        session.choicePage = Math.max(0, Math.min(session.choicePage, optionPages - 1));

        List<DialogBody> body = new ArrayList<>();
        int from = session.textPage * linesPerPage;
        int to = Math.min(lines.size(), from + linesPerPage);
        for (int i = from; i < to; i++) body.add(DialogBody.plainMessage(legacy(lines.get(i)), 200));
        body.add(DialogBody.plainMessage(legacy(dialogueLabel("text-page", "&8Text") + " " + (session.textPage + 1) + "/" + textPages
                + " &7| " + dialogueLabel("answers-page", "&8Answers") + " " + (session.choicePage + 1) + "/" + optionPages), 200));

        List<ActionButton> buttons = new ArrayList<>();
        if (textPages > 1) {
            buttons.add(dialogueButton(session.textPage > 0 ? dialogueLabel("previous-text", "&e↑ Previous text") : "&8↑ Previous text",
                    "text:-", session));
            buttons.add(dialogueButton(session.textPage + 1 < textPages ? dialogueLabel("next-text", "&e↓ More text") : "&8↓ More text",
                    "text:+", session));
        }
        if (optionPages > 1) {
            buttons.add(dialogueButton(session.choicePage > 0 ? dialogueLabel("previous-options", "&7◀ Earlier answers") : "&8◀ Earlier answers",
                    "choices:-", session));
            buttons.add(dialogueButton(session.choicePage + 1 < optionPages ? dialogueLabel("next-options", "&7Later answers ▶") : "&8Later answers ▶",
                    "choices:+", session));
        }
        int optionStart = session.choicePage * 6;
        int optionEnd = Math.min(options.size(), optionStart + 6);
        for (int i = optionStart; i < optionEnd; i++) {
            DialogueOption option = options.get(i);
            buttons.add(dialogueButton("&f" + (i - optionStart + 1) + ": " + option.label(), "answer:" + option.tag(), session));
        }
        if (buttons.isEmpty()) buttons.add(dialogueButton(dialogueLabel("empty-options", "&7There are no available responses."), "noop", session));
        ActionButton close = ActionButton.builder(legacy(dialogueLabel("close", "&cClose conversation")))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) closeNpcDialogue(player);
                }, ClickCallback.Options.builder().uses(1).build())).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(legacy(q.title)).body(body).canCloseWithEscape(false).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE).build())
                .type(DialogType.multiAction(buttons).exitAction(close).columns(2).build()));
        p.showDialog(dialog);
    }

    private ActionButton dialogueButton(String label, String action, DialogueSession session) {
        UUID playerId = session.player;
        String questId = session.quest;
        int npcId = session.npc;
        return ActionButton.builder(legacy(label)).action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player) || !player.getUniqueId().equals(playerId)) return;
            DialogueSession current = dialogueSessions.get(playerId);
            if (current == null || !current.quest.equals(questId) || current.npc != npcId) return;
            Quest quest = byId(questId);
            if (quest == null) { closeNpcDialogue(player); return; }
            switch (action) {
                case "text:-" -> current.textPage--;
                case "text:+" -> current.textPage++;
                case "choices:-" -> current.choicePage--;
                case "choices:+" -> current.choicePage++;
                case "noop" -> { }
                default -> {
                    if (!action.startsWith("answer:")) return;
                    String tag = action.substring("answer:".length());
                    if (dialogueOptions(player, quest, npcId).stream().noneMatch(option -> option.tag().equals(tag))) return;
                    handleTag(player, quest, tag, npcId);
                }
            }
            DialogueSession updated = dialogueSessions.get(playerId);
            if (updated != null && updated.quest.equals(questId)) renderNpcDialog(player, quest, updated);
        }, ClickCallback.Options.builder().uses(1).build())).build();
    }

    
    private static int[] choiceSlots(int size) {
        if (size <= 45) return new int[]{19, 20, 21, 22, 23, 24, 25};
        int[] t = new int[26]; int n = 0;
        for (int i = 18; i <= 35; i++) if (i != 31) t[n++] = i;
        for (int i = 36; i <= 44; i++) if (i != 40) t[n++] = i;
        return java.util.Arrays.copyOf(t, n);
    }

    
    private static int[] menuDoneSlots(int size) {
        List<Integer> l = new ArrayList<>();
        for (int i = 36; i <= 44; i++) if (i != 40) l.add(i);
        if (size > 45) {
            for (int i = 0; i <= 17; i++) if (i != 13 && i != 4) l.add(i);   // overflow-rij bovenaan
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
        DialogueSession session = dialogueSessions.get(p.getUniqueId());
        if (session != null && session.quest.equals(q.id)) {
            renderNpcDialog(p, q, session);
            return;
        }
        var holder = p.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof Holder h) openQuestGui(p, q, h.npc);
        else {
            var ca = chatActive.get(p.getUniqueId());
            if (ca != null && ca[0].equals(q.id)) chatQuest(p, q, Integer.parseInt(ca[1]));
        }
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

    

    

    
    private String prefMode(Player p) {
        String v = p.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        return v != null ? v : getConfig().getString("default-mode", "gui");
    }

    
    boolean chatMode(Player p, Quest q) {
        if (q != null && q.mode.equals("gui")) return false;
        if (q != null && q.mode.equals("chat")) return true;
        return prefMode(p).equals("chat");
    }

    private boolean setPref(Player p, Quest q, String mode) {
        if (q != null && !q.mode.equals("player")) {
            p.sendMessage(legacy("&7This quest always uses &f" + q.mode + "&7 mode."));
            return false;
        }
        p.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
        p.sendMessage(legacy("&7Quest mode: &f" + (mode.equals("chat") ? "conversation (clickable chat)" : "GUI windows")
                + "&7. Switch any time with &f/questbook mode&7."));
        return true;
    }

    private void modeCommand(Player p, String arg) {
        String cur = prefMode(p);
        String want = arg == null ? (cur.equals("chat") ? "gui" : "chat") : arg.toLowerCase();
        if (!want.equals("gui") && !want.equals("chat")) { p.sendMessage(legacy("&cUsage: /questbook mode [gui|chat]")); return; }
        if (!setPref(p, null, want)) return;
        var ca = chatActive.get(p.getUniqueId());
        if (want.equals("gui")) {
            chatActive.remove(p.getUniqueId());
            if (ca != null && byId(ca[0]) != null) openQuestGui(p, byId(ca[0]), Integer.parseInt(ca[1]));
            else openMenu(p);
        } else chatMenu(p);
    }

    private Component button(String label, String command, String hover) {
        return legacy("&8[ " + label + " &8]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(legacy(hover)));
    }

    private String doCmd(Quest q, Prog pr, String tag, int npc) {
        return "/questbook do " + q.id + " " + (pr.stage.isEmpty() ? "-" : pr.stage) + " " + tag + " " + npc;
    }

    
    private void chatDo(Player p, String[] a) {
        var q = byId(a[1]);
        if (q == null) return;
        Prog pr = pr(p, q);
        String tag = a[3];
        int npc = 0;
        try { npc = a.length > 4 ? Integer.parseInt(a[4]) : 0; } catch (NumberFormatException ignored) { }
        boolean stateful = tag.equals("start") || tag.equals("newtry") || tag.equals("act")
                || tag.startsWith("choice:") || tag.startsWith("menu:");
        String cur = pr.stage.isEmpty() ? "-" : pr.stage;
        if (stateful && !cur.equals(a[2])) {
            p.sendMessage(legacy(dialogueLabel("errors.unavailable", "&7That option is no longer available.")));
            chatQuest(p, q, npc);
            return;
        }
        handleTag(p, q, tag, npc);
    }

    private void chatMenu(Player p) {
        chatActive.remove(p.getUniqueId());
        p.sendMessage(legacy("&6&l━━ Quests ━━"));
        int n = 0;
        for (var q : quests) {
            var pr = pr(p, q);
            if (!(ok(p, q, pr, q.startConds) || (pr.started && !pr.done))) continue;
            p.sendMessage(button("&e" + plain(q.title), "/questbook open " + q.id, "Open this quest")
                    .append(legacy(" " + statusLine(q, pr))));
            n++;
        }
        if (n == 0) p.sendMessage(legacy("&7No quests available right now."));
        p.sendMessage(button("&bSwitch to GUI", "/questbook mode gui", "Use windows instead of chat"));
    }

    private void chatQuest(Player p, Quest q, int npc) {
        Prog pr = pr(p, q);
        Stage s = pr.stage.isEmpty() ? null : q.stages.get(pr.stage);
        Stage first = q.stages.isEmpty() ? null : q.stages.values().iterator().next();
        chatActive.put(p.getUniqueId(), new String[]{q.id, String.valueOf(npc)});

        p.sendMessage(Component.empty());
        p.sendMessage(legacy("&6&l━━ ").append(legacy(q.title)).append(legacy(" &6&l━━")));
        if (!q.subtitle.isEmpty()) p.sendMessage(legacy("&8" + q.subtitle));
        if (!pr.started) {
            p.sendMessage(legacy("&7Ready when you are."));
            p.sendMessage(button("&a✔ Start quest", doCmd(q, pr, "start", npc), "Begin this quest"));
        } else if (pr.done) {
            p.sendMessage(legacy("&aThe quest is complete!"));
            for (var j : q.journal) p.sendMessage(legacy("&7" + j));
            for (var r : q.repeats) {
                Long cd = pr.cd.get(r.id);
                boolean ready = cd == null || cd <= now();
                String cnt = r.kind.equals("deliver") && r.items.isEmpty()
                        ? " (" + held(p, r.material, r.name.isEmpty() ? null : r.name) + "/" + r.amount + ")" : "";
                p.sendMessage(button((ready ? "&a" : "&7") + r.label + cnt + (ready ? "" : " &7still " + fmt(cd - now())),
                        doCmd(q, pr, "repeat:" + r.id, npc), ready ? "Hand in again" : "On cooldown"));
            }
            if (q.repeats.isEmpty())
                p.sendMessage(button("&aStart the quest again", doCmd(q, pr, "newtry", npc), "Reset and begin again"));
            if (first != null) {
                for (int i = 0; i < first.choices.size(); i++)
                    p.sendMessage(button("&f" + first.choices.get(i).label, doCmd(q, pr, "menu:" + i, npc), "Choose"));
            }
        } else if (s != null) {
            for (var l : s.lines) p.sendMessage(legacy(l));
            if (!s.kind.equals("menu"))
                p.sendMessage(button(labelFor(p, q, pr, s), doCmd(q, pr, "act", npc), "Do this step"));
            if (s.kind.equals("walk") && s.loc != null)
                p.sendMessage(button("&6Point the way", doCmd(q, pr, "compass:" + s.loc.getWorld().getName() + ";"
                        + s.loc.getBlockX() + ";" + s.loc.getBlockY() + ";" + s.loc.getBlockZ(), npc), "Aim your compass"));
            for (int i = 0; i < s.choices.size(); i++)
                p.sendMessage(button("&f▸ " + s.choices.get(i).label, doCmd(q, pr, "choice:" + i, npc), "Choose"));
        }
        Component foot = Component.empty();
        if (q.mode.equals("player"))
            foot = foot.append(button("&bGUI mode", "/questbook mode gui", "Use windows instead of chat")).append(Component.text(" "));
        foot = foot.append(button("&7Quests", "/questbook", "All quests"));
        p.sendMessage(foot);
    }

    

    



    private void travel(Player p, String val) {
        var cfg = getConfig();
        if (!cfg.getBoolean("travel.enabled", true)) { dialogueMessage(p, "&7Travel is switched off right now."); return; }
        String[] t = val.trim().split("\\s+");
        if (t.length == 0 || t[0].isEmpty()) return;
        double cost = 0;
        int warm = cfg.getInt("travel.warmup-seconds", 3);
        for (int i = 1; i < t.length; i++) {
            String[] kv = t[i].split("=", 2);
            if (kv.length < 2) continue;
            if (kv[0].equalsIgnoreCase("cost")) cost = num(kv[1], 0);
            else if (kv[0].equalsIgnoreCase("warmup")) warm = (int) num(kv[1], warm);
        }
        String rawLoc = cfg.getString("travel.destinations." + t[0], t[0]);
        Location dest = parseLoc(rawLoc);
        if (dest == null) {
            dialogueMessage(p, "&cUnknown destination. Tell staff.");
            getLogger().warning("travel: onbekende bestemming '" + t[0] + "'");
            return;
        }
        long cdSec = cfg.getLong("travel.cooldown-seconds", 0);
        Long until = travelCd.get(p.getUniqueId());
        if (cdSec > 0 && until != null && until > System.currentTimeMillis()) {
            dialogueMessage(p, "&cYou can travel again in " + fmt((until - System.currentTimeMillis()) / 1000) + ".");
            deny(p);
            return;
        }
        final double fcost = cost;
        if (fcost > 0 && balance(p) < fcost) {
            dialogueMessage(p, "&cTravel costs &f" + (long) fcost + "&c - you can't afford it.");
            deny(p);
            return;
        }
        p.closeInventory();
        final Location from = p.getLocation().clone();
        final int[] left = {Math.max(0, warm) * 2};      // halve seconden
        final String destName = t[0];
        Runnable go = () -> {
            if (fcost > 0) {
                if (balance(p) < fcost) { dialogueMessage(p, "&cTravel cancelled - not enough money."); return; }
                dispatch("cmi money take " + p.getName() + " -s " + (long) fcost);
                logTx(p, null, "travel " + destName + " cost " + (long) fcost);
            }
            if (cdSec > 0) travelCd.put(p.getUniqueId(), System.currentTimeMillis() + cdSec * 1000);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1f);
            p.teleportAsync(dest).thenAccept(ok -> {
                if (ok) p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.2f);
            });
        };
        if (left[0] == 0) { go.run(); return; }
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!p.isOnline()) { task.cancel(); return; }
            if (!p.getWorld().equals(from.getWorld()) || p.getLocation().distanceSquared(from) > 2.25) {
                p.sendActionBar(legacy("&cTravel cancelled - you moved."));
                task.cancel();
                return;
            }
            if (left[0] <= 0) { task.cancel(); go.run(); return; }
            p.sendActionBar(legacy("&bTravelling in &f" + ((left[0] + 1) / 2) + "s &7- stand still"));
            left[0]--;
        }, 0L, 10L);
    }

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

    Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }

    private String plain(String s) {
        return org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', s == null ? "" : s));
    }

    
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

    
    private Method mEcoCheck;

    private double ecoBalance(Player p) {
        try {
            if (mEcoCheck == null) {
                Class<?> c = Class.forName("com.magmaguy.elitemobs.economy.EconomyHandler");
                mEcoCheck = c.getMethod("checkCurrency", UUID.class);
            }
            return ((Number) mEcoCheck.invoke(null, p.getUniqueId())).doubleValue();
        } catch (Throwable e) {
            return -1;
        }
    }

    
    private java.util.Set<String> eliteItemFiles;

    private boolean eliteItemExists(String file) {
        if (eliteItemFiles == null) {
            eliteItemFiles = new java.util.HashSet<>();
            var root = new File(getDataFolder().getParentFile(), "EliteMobs/customitems");
            if (root.isDirectory()) {
                try (var w = java.nio.file.Files.walk(root.toPath())) {
                    w.filter(x -> x.toString().endsWith(".yml")).forEach(x -> eliteItemFiles.add(x.getFileName().toString()));
                } catch (Exception ignored) { }
            }
        }
        return eliteItemFiles.contains(file);
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
