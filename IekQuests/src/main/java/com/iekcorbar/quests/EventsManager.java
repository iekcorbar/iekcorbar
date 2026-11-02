package com.iekcorbar.quests;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class EventsManager implements Listener {
    static final List<String> TYPES = List.of("on-join", "on-first-join", "on-leave", "on-respawn",
            "on-world-change", "on-advancement", "on-quest-accept", "on-quest-complete", "on-level-up",
            "on-region-enter", "on-region-leave", "on-objective-complete");
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private final IekQuestsPlugin plugin;
    private YamlConfiguration config = new YamlConfiguration();
    private boolean worldGuardEventsRegistered;
    private final Set<String> registeredRegionEventTypes = new HashSet<>();

    EventsManager(IekQuestsPlugin plugin) { this.plugin = plugin; }

    void load() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) plugin.saveResource("events.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        for (String type : TYPES) {
            if (config.get(type) == null) config.createSection(type);
            else if (!(config.get(type) instanceof ConfigurationSection) && !(config.get(type) instanceof Map<?, ?>))
                plugin.getLogger().warning("events.yml section '" + type + "' must be a mapping.");
        }
        registerWorldGuardEvents();
        validateAll(null, false);
    }

    YamlConfiguration configuration() { return config; }

    List<String> objectiveCompletionDialogues(String objectiveId) {
        List<String> result = new ArrayList<>();
        ConfigurationSection entries = config.getConfigurationSection("on-objective-complete");
        if (entries == null) return result;
        for (String id : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(id);
            if (entry != null && objectiveId.equals(entry.getString("objective-id", ""))) result.addAll(entry.getStringList("dialogues"));
        }
        return result;
    }

    void fire(String type, Player player, Map<String, String> context) {
        if (player == null || !type.equals("on-leave") && !player.isOnline()) return;
        ConfigurationSection entries = config.getConfigurationSection(type);
        if (entries == null) return;
        for (String id : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(id);
            if (entry != null) executeEntry(type, id, entry, player, context, false);
        }
        plugin.save();
    }

    int startById(Player player, String eventId) {
        if (!ID.matcher(eventId).matches()) throw new IllegalArgumentException("Invalid event ID: " + eventId);
        if (!player.isOnline()) throw new IllegalArgumentException("Player is not online: " + player.getName());
        int started = 0;
        for (String type : TYPES) {
            ConfigurationSection entries = config.getConfigurationSection(type);
            ConfigurationSection entry = entries == null ? null : entries.getConfigurationSection(eventId);
            if (entry != null && executeEntry(type, eventId, entry, player, Map.of(), true)) started++;
        }
        plugin.save();
        return started;
    }

    private boolean executeEntry(String type, String id, ConfigurationSection entry, Player player,
                                 Map<String, String> context, boolean manualStart) {
        if ((!manualStart && !matches(type, entry, context))
                || !plugin.dialogueManager().eventTagsPass(player, entry.getStringList("prerequisiteTag"))) return false;
        if (type.equals("on-leave") && !entry.getStringList("dialogues").isEmpty()) {
            plugin.getLogger().warning("Ignored dialogues in on-leave event '" + id + "'; dialogues are not supported on leave.");
        }
        plugin.dialogueManager().assignEventPlayerTags(player, entry.getStringList("setplayertag"));
        plugin.journalManager().add(player, entry.getStringList("addjournal"));
        plugin.journalManager().remove(player, entry.getStringList("removejournal"));
        plugin.dialogueManager().runEventCommands(player, entry.getStringList("commands"));
        plugin.sequencesManager().start(player, entry.getStringList("sequences"));
        if (!type.equals("on-leave")) plugin.dialogueManager().queueEventDialogues(player, entry.getStringList("dialogues"));
        return true;
    }

    private boolean matches(String type, ConfigurationSection entry, Map<String, String> context) {
        return switch (type) {
            case "on-world-change" -> equalFilter(entry, "world", context.get("world"))
                    && equalOptionalFilter(entry, "from-world", context.get("from-world"));
            case "on-advancement" -> equalFilter(entry, "advancement", context.get("advancement"));
            case "on-quest-accept", "on-quest-complete" -> equalFilter(entry, "quest-id", context.get("quest-id"));
            case "on-level-up" -> {
                int level = parseInt(context.get("level"), -1);
                int exact = entry.getInt("level", -1), min = entry.getInt("min-level", -1), max = entry.getInt("max-level", -1);
                yield level >= 0 && (exact < 0 || level == exact) && (min < 0 || level >= min) && (max < 0 || level <= max);
            }
            case "on-region-enter", "on-region-leave" -> equalFilter(entry, "region", context.get("region"));
            case "on-objective-complete" -> entry.getString("objective-id", "").equals(context.get("objective-id"));
            default -> true;
        };
    }

    private boolean equalFilter(ConfigurationSection entry, String field, String actual) {
        String expected = entry.getString(field, "").trim();
        return !expected.isEmpty() && actual != null && expected.equalsIgnoreCase(actual);
    }

    private boolean equalOptionalFilter(ConfigurationSection entry, String field, String actual) {
        String expected = entry.getString(field, "").trim();
        return expected.isEmpty() || actual != null && expected.equalsIgnoreCase(actual);
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            fire("on-join", player, Map.of());
            if (firstJoin) fire("on-first-join", player, Map.of());
        });
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST)
    public void onLeave(PlayerQuitEvent event) { fire("on-leave", event.getPlayer(), Map.of()); }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) fire("on-respawn", player, Map.of()); });
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        fire("on-world-change", event.getPlayer(), Map.of("world", event.getPlayer().getWorld().getName(),
                "from-world", event.getFrom().getName()));
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        fire("on-advancement", event.getPlayer(), Map.of("advancement", event.getAdvancement().getKey().toString()));
    }

    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) fire("on-level-up", event.getPlayer(), Map.of("level", String.valueOf(event.getNewLevel())));
    }

    void questAccepted(Player player, String questId) {
        fire("on-quest-accept", player, Map.of("quest-id", questId));
    }

    void questCompleted(Player player, String questId) {
        fire("on-quest-complete", player, Map.of("quest-id", questId));
    }

    private void registerWorldGuardEvents() {
        if (worldGuardEventsRegistered || Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;
        registerRegionEvent("com.sk89q.worldguard.bukkit.event.RegionEnterEvent", "on-region-enter");
        registerRegionEvent("com.sk89q.worldguard.bukkit.event.RegionLeaveEvent", "on-region-leave");
        worldGuardEventsRegistered = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerRegionEvent(String className, String type) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className);
            Listener listener = new Listener() {};
            EventExecutor executor = (ignored, event) -> {
                try {
                    Object actor = event.getClass().getMethod("getPlayer").invoke(event);
                    Object region = event.getClass().getMethod("getRegion").invoke(event);
                    String regionId = String.valueOf(region.getClass().getMethod("getId").invoke(region));
                    if (actor instanceof Player player) fire(type, player, Map.of("region", regionId));
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Could not read WorldGuard region event: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
            registeredRegionEventTypes.add(type);
        } catch (ClassNotFoundException ignored) {
            plugin.getLogger().warning("WorldGuard is installed but " + type + " event API is unavailable; region events are disabled.");
        }
    }

    void command(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Use /iekdialogue events <list|show|create|set|delete|validate|reload>.");
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("reload")) { plugin.reloadAllData(); sender.sendMessage("IekQuests events reloaded."); return; }
        if (operation.equals("list")) {
            if (args.length == 2) for (String type : TYPES) sender.sendMessage(type + ": " + entryIds(type));
            else sender.sendMessage(args[2] + ": " + entryIds(args[2]));
            return;
        }
        if (operation.equals("validate")) {
            String type = args.length > 2 ? checkedType(args[2]) : null;
            String id = args.length > 3 ? checkedId(args[3]) : null;
            if (type != null && id != null && !section(type).contains(id)) throw new IllegalArgumentException("Unknown event entry: " + type + "/" + id);
            validate(sender, type, id);
            return;
        }
        if (operation.equals("show")) {
            requireArgs(args, 4, "show <event-type> <id>");
            ConfigurationSection section = section(args[2]);
            ConfigurationSection entry = section.getConfigurationSection(args[3]);
            if (entry == null) throw new IllegalArgumentException("Unknown event entry.");
            sender.sendMessage(YamlConfiguration.loadConfiguration(new java.io.StringReader(entryToYaml(args[2], args[3]))).saveToString());
            return;
        }
        if (operation.equals("create")) {
            requireArgs(args, 4, "create <event-type> <id>");
            String type = checkedType(args[2]), id = checkedId(args[3]);
            if (section(type).contains(id)) throw new IllegalArgumentException("Event ID already exists: " + id);
            config.createSection(type + "." + id);
            config.set(type + "." + id + ".prerequisiteTag", List.of());
            config.set(type + "." + id + ".commands", List.of());
            config.set(type + "." + id + ".setplayertag", List.of());
            config.set(type + "." + id + ".addjournal", List.of());
            config.set(type + "." + id + ".removejournal", List.of());
            config.set(type + "." + id + ".sequences", List.of());
            if (type.equals("on-objective-complete")) config.set(type + "." + id + ".objective-id", "");
            if (!type.equals("on-leave")) config.set(type + "." + id + ".dialogues", List.of());
            save();
            sender.sendMessage("Created event " + type + "/" + id + ".");
            return;
        }
        if (operation.equals("set")) {
            requireArgs(args, 6, "set <event-type> <id> <field> <value>");
            String type = checkedType(args[2]), id = checkedId(args[3]), field = args[4];
            ConfigurationSection entry = section(type).getConfigurationSection(id);
            if (entry == null) throw new IllegalArgumentException("Unknown event entry.");
            if (type.equals("on-leave") && field.equalsIgnoreCase("dialogues")) throw new IllegalArgumentException("on-leave does not support dialogues.");
            String value = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));
            String path = type + "." + id + "." + field;
            if (Set.of("prerequisiteTag", "commands", "setplayertag", "addjournal", "removejournal", "dialogues", "sequences").contains(field))
                config.set(path, value.isBlank() ? List.of() : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
            else if (Set.of("world", "from-world", "advancement", "quest-id", "region", "objective-id").contains(field)) config.set(path, value);
            else if (Set.of("level", "min-level", "max-level").contains(field)) config.set(path, Integer.parseInt(value));
            else throw new IllegalArgumentException("Unsupported event field: " + field);
            save();
            sender.sendMessage("Updated event " + type + "/" + id + ".");
            return;
        }
        if (operation.equals("delete")) {
            requireArgs(args, 4, "delete <event-type> <id>");
            String type = checkedType(args[2]), id = checkedId(args[3]);
            if (!section(type).contains(id)) throw new IllegalArgumentException("Unknown event entry.");
            config.set(type + "." + id, null);
            save();
            sender.sendMessage("Deleted event " + type + "/" + id + ".");
            return;
        }
        throw new IllegalArgumentException("Unknown events operation.");
    }

    private List<String> entryIds(String type) { return new ArrayList<>(section(type).getKeys(false)); }
    private ConfigurationSection section(String type) {
        checkedType(type);
        ConfigurationSection section = config.getConfigurationSection(type);
        if (section == null) throw new IllegalArgumentException("Event section must be a mapping: " + type);
        return section;
    }

    private void validateAll(CommandSender sender, boolean report) {
        validateInternal(sender, report, null, null);
    }

    private void validate(CommandSender sender, String typeFilter, String idFilter) {
        validateInternal(sender, true, typeFilter, idFilter);
    }

    private void validateInternal(CommandSender sender, boolean report, String typeFilter, String idFilter) {
        List<String> findings = collectValidationFindings(typeFilter, idFilter);
        if (report || !findings.isEmpty()) {
            if (sender != null) {
                if (findings.isEmpty()) sender.sendMessage("Events validation passed.");
                else findings.forEach(sender::sendMessage);
            } else findings.forEach(plugin.getLogger()::warning);
        }
    }

    List<String> validationFindings() { return collectValidationFindings(null, null); }
    List<String> objectiveCompletionValidationFindings() { return collectValidationFindings("on-objective-complete", null); }

    private List<String> collectValidationFindings(String typeFilter, String idFilter) {
        List<String> findings = new ArrayList<>();
        Set<String> knownPlayerTags = Set.copyOf(plugin.dialogueManager().playerTagIds());
        Set<String> knownDialogues = Set.copyOf(plugin.dialogueManager().dialogueIds());
        Set<String> knownJournalEntries = Set.copyOf(plugin.journalManager().entryIds());
        for (String type : TYPES) {
            if (typeFilter != null && !typeFilter.equals(type)) continue;
            ConfigurationSection entries = config.getConfigurationSection(type);
            if (entries == null) { findings.add("ERROR: " + type + " must be a mapping (use {})."); continue; }
            for (String id : entries.getKeys(false)) {
                if (idFilter != null && !idFilter.equals(id)) continue;
                ConfigurationSection entry = entries.getConfigurationSection(id);
                if (!ID.matcher(id).matches()) findings.add("ERROR: malformed event ID " + type + "/" + id);
                if (entry == null) { findings.add("ERROR: event entry is not a mapping: " + type + "/" + id); continue; }
                Object dialogueValues = entry.get("dialogues");
                if (type.equals("on-leave") && dialogueValues != null
                        && (!(dialogueValues instanceof List<?>) || !((List<?>) dialogueValues).isEmpty()))
                    findings.add("ERROR: on-leave entry " + id + " cannot define dialogues.");
                if (type.equals("on-leave") && !entry.getStringList("sequences").isEmpty()) findings.add("WARNING: on-leave entry " + id + " starts sequences that are canceled when the player disconnects.");
                for (String field : List.of("prerequisiteTag", "commands", "setplayertag", "addjournal", "removejournal", "dialogues", "sequences")) {
                    Object rawList = entry.get(field);
                    if (rawList != null && (!(rawList instanceof List<?>) || ((List<?>) rawList).stream().anyMatch(value -> !(value instanceof String))))
                        findings.add("ERROR: " + type + "/" + id + " field " + field + " must be a YAML list of strings.");
                }
                for (String tagExpression : entry.getStringList("prerequisiteTag")) {
                    String tag = tagExpression.startsWith("!") ? tagExpression.substring(1).trim() : tagExpression.trim();
                    if (!knownPlayerTags.contains(tag)) findings.add("ERROR: " + type + "/" + id + " references unknown player tag " + tag);
                }
                for (String tag : entry.getStringList("setplayertag")) if (!knownPlayerTags.contains(tag)) findings.add("ERROR: " + type + "/" + id + " assigns unknown player tag " + tag);
                for (String journalId : entry.getStringList("addjournal")) if (!knownJournalEntries.contains(journalId)) findings.add("ERROR: " + type + "/" + id + " references unknown Journal entry " + journalId + " in addjournal.");
                for (String journalId : entry.getStringList("removejournal")) if (!knownJournalEntries.contains(journalId)) findings.add("ERROR: " + type + "/" + id + " references unknown Journal entry " + journalId + " in removejournal.");
                for (String dialogue : entry.getStringList("dialogues")) if (!knownDialogues.contains(dialogue)) findings.add("ERROR: " + type + "/" + id + " references unknown dialogue " + dialogue);
                for (String sequence : entry.getStringList("sequences")) if (!plugin.sequencesManager().ids().contains(sequence)) findings.add("ERROR: " + type + "/" + id + " references unknown sequence " + sequence);
                if (type.equals("on-objective-complete") && !plugin.objectivesManager().hasObjective(entry.getString("objective-id", "")))
                    findings.add("ERROR: " + type + "/" + id + " references an unknown or missing objective-id.");
                if (type.equals("on-world-change") && entry.getString("world", "").isBlank()) findings.add("ERROR: " + type + "/" + id + " needs world.");
                if (type.equals("on-advancement")) {
                    String key = entry.getString("advancement", "");
                    if (key.isBlank() || NamespacedKey.fromString(key) == null) findings.add("ERROR: invalid advancement key in " + id);
                }
                if ((type.equals("on-quest-accept") || type.equals("on-quest-complete")) && plugin.questById(entry.getString("quest-id", "")) == null)
                    findings.add("ERROR: " + type + "/" + id + " references an unknown quest.");
                if (type.equals("on-level-up") && !entry.contains("level") && !entry.contains("min-level") && !entry.contains("max-level"))
                    findings.add("ERROR: " + type + "/" + id + " needs level or a level range.");
                if (type.equals("on-level-up") && entry.contains("level") && (entry.contains("min-level") || entry.contains("max-level")))
                    findings.add("ERROR: " + type + "/" + id + " cannot combine exact level with a level range.");
                if ((type.equals("on-region-enter") || type.equals("on-region-leave")) && !registeredRegionEventTypes.contains(type))
                    findings.add("ERROR: compatible WorldGuard region event API is unavailable; " + type + "/" + id + " cannot run.");
                if ((type.equals("on-region-enter") || type.equals("on-region-leave")) && entry.getString("region", "").isBlank())
                    findings.add("ERROR: " + type + "/" + id + " needs region.");
                for (String command : entry.getStringList("commands")) if (!command.matches("(?i)^(server|ser|player|op):\\s*.+$"))
                    findings.add("ERROR: unsupported command syntax in " + type + "/" + id + ": " + command);
            }
        }
        findings.addAll(plugin.sequencesManager().validateFindings(null));
        return findings;
    }

    private String entryToYaml(String type, String id) {
        YamlConfiguration wrapper = new YamlConfiguration();
        wrapper.set(type + "." + id, config.get(type + "." + id));
        return wrapper.saveToString();
    }

    private void save() {
        try { config.save(new File(plugin.getDataFolder(), "events.yml")); }
        catch (Exception ex) { plugin.getLogger().severe("Could not save events.yml: " + ex.getMessage()); }
    }

    private String checkedType(String type) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown event type: " + type);
        return type;
    }
    private String checkedId(String id) {
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid event ID: " + id);
        return id;
    }
    private void requireArgs(String[] args, int length, String syntax) {
        if (args.length < length) throw new IllegalArgumentException("Usage: /iekdialogue events " + syntax);
    }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
}
