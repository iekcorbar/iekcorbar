package com.iekcorbar.quests;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SequencesManager implements Listener {
    private static final Pattern DURATION = Pattern.compile("(\\d+)(ms|s|m)", Pattern.CASE_INSENSITIVE);
    private final IekQuestsPlugin plugin;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, List<BukkitTask>>> active = new HashMap<>();
    private final List<String> loadErrors = new ArrayList<>();
    private YamlConfiguration config = new YamlConfiguration();

    private record Step(long delayMillis, List<String> commands, List<String> addTags, List<String> removeTags,
                        List<String> addJournal, List<String> removeJournal, List<String> rewards,
                        List<String> dialogues, boolean malformedFields) { }
    private record Definition(String id, List<String> prerequisites, List<Step> steps, boolean malformedPrerequisites) { }

    SequencesManager(IekQuestsPlugin plugin) { this.plugin = plugin; }

    void load() {
        cancelAll();
        File file = plugin.organizationFile("Sequences.yml");
        config = YamlConfiguration.loadConfiguration(file);
        definitions.clear();
        loadErrors.clear();
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) { loadErrors.add("ERROR: Sequence " + id + " must be a mapping."); continue; }
            List<Step> steps = new ArrayList<>();
            for (Map<?, ?> rawStep : section.getMapList("steps")) {
                Map<String, Object> map = new LinkedHashMap<>();
                rawStep.forEach((key, value) -> map.put(String.valueOf(key), value));
                long delay = parseDuration(String.valueOf(map.getOrDefault("after", "")));
                boolean malformed = false;
                for (String field : List.of("commands", "add-player-tags", "remove-player-tags", "addjournal", "removejournal", "rewards", "dialogues")) {
                    Object value = map.get(field);
                    if (value != null && (!(value instanceof List<?>) || ((List<?>) value).stream().anyMatch(item -> !(item instanceof String)))) malformed = true;
                }
                steps.add(new Step(delay,
                        strings(map.get("commands")), strings(map.get("add-player-tags")), strings(map.get("remove-player-tags")),
                        strings(map.get("addjournal")), strings(map.get("removejournal")), strings(map.get("rewards")),
                        strings(map.get("dialogues")), malformed));
            }
            Object prerequisites = section.get("prerequisiteTag");
            boolean malformedPrerequisites = prerequisites != null && (!(prerequisites instanceof List<?>)
                    || ((List<?>) prerequisites).stream().anyMatch(value -> !(value instanceof String)));
            definitions.put(id, new Definition(id, section.getStringList("prerequisiteTag"), List.copyOf(steps), malformedPrerequisites));
        }
        validateFindings(null).forEach(plugin.getLogger()::warning);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private long parseDuration(String text) {
        if (text == null) return -1;
        String value = text.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.matches()) return -1;
        try {
            long amount = Long.parseLong(matcher.group(1));
            long factor = switch (matcher.group(2)) { case "ms" -> 1; case "s" -> 1000; default -> 60_000; };
            long millis = Math.multiplyExact(amount, factor);
            return millis > Long.MAX_VALUE - 49 ? -1 : millis;
        } catch (ArithmeticException | NumberFormatException ex) { return -1; }
    }

    void start(Player player, List<String> ids) {
        if (player == null || !player.isOnline()) return;
        for (String id : ids) {
            Definition definition = definitions.get(id);
            if (definition == null || !plugin.dialogueManager().eventTagsPass(player, definition.prerequisites)) continue;
            if (!validateFindings(id).isEmpty()) {
                plugin.getLogger().warning("Sequence " + id + " was not started because its definition is invalid.");
                continue;
            }
            Map<String, List<BukkitTask>> byId = active.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
            if (byId.containsKey(id)) continue;
            List<BukkitTask> tasks = new ArrayList<>();
            byId.put(id, tasks);
            for (int index = 0; index < definition.steps.size(); index++) {
                Step step = definition.steps.get(index);
                boolean lastStep = index == definition.steps.size() - 1;
                long ticks = step.delayMillis == 0 ? 0 : Math.max(1, (step.delayMillis + 49) / 50);
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> runStep(player, definition, step, lastStep), ticks);
                tasks.add(task);
            }
        }
    }

    private void runStep(Player player, Definition definition, Step step, boolean lastStep) {
        if (!player.isOnline() || !plugin.dialogueManager().eventTagsPass(player, definition.prerequisites)) {
            cancel(player.getUniqueId(), definition.id);
            return;
        }
        DialogueManager dialogue = plugin.dialogueManager();
        dialogue.runSequenceCommands(player, step.commands);
        dialogue.changeSequencePlayerTags(player, step.addTags, step.removeTags);
        plugin.journalManager().add(player, step.addJournal);
        plugin.journalManager().remove(player, step.removeJournal);
        dialogue.runSequenceRewards(player, step.rewards);
        dialogue.queueEventDialogues(player, step.dialogues);
        plugin.save();
        if (lastStep) finish(player.getUniqueId(), definition.id);
    }

    private void finish(UUID playerId, String id) {
        Map<String, List<BukkitTask>> byId = active.get(playerId);
        if (byId == null) return;
        byId.remove(id);
        if (byId.isEmpty()) active.remove(playerId);
    }

    private void cancel(UUID playerId, String id) {
        Map<String, List<BukkitTask>> byId = active.get(playerId);
        if (byId == null) return;
        List<BukkitTask> tasks = byId.remove(id);
        if (tasks != null) tasks.forEach(BukkitTask::cancel);
        if (byId.isEmpty()) active.remove(playerId);
    }

    private void cancelAll() {
        for (Map<String, List<BukkitTask>> byId : active.values())
            for (List<BukkitTask> tasks : byId.values()) tasks.forEach(BukkitTask::cancel);
        active.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cancelPlayer(event.getPlayer().getUniqueId()); }

    void cancelPlayer(UUID playerId) {
        Map<String, List<BukkitTask>> byId = active.remove(playerId);
        if (byId != null) for (List<BukkitTask> tasks : byId.values()) tasks.forEach(BukkitTask::cancel);
    }

    void shutdown() { cancelAll(); }

    List<String> ids() { return List.copyOf(definitions.keySet()); }

    List<String> validateFindings(String onlyId) {
        List<String> findings = new ArrayList<>(loadErrors);
        for (Definition definition : definitions.values()) {
            if (onlyId != null && !onlyId.equals(definition.id)) continue;
            if (!definition.id.matches("[A-Za-z0-9_-]{1,64}")) findings.add("ERROR: Invalid sequence ID " + definition.id);
            if (definition.malformedPrerequisites) findings.add("ERROR: Sequence " + definition.id + " prerequisiteTag must be a YAML list of strings.");
            for (String tag : definition.prerequisites) if (!plugin.dialogueManager().isKnownPlayerTag(tag.startsWith("!") ? tag.substring(1) : tag))
                findings.add("ERROR: Sequence " + definition.id + " references unknown player tag " + tag);
            if (definition.steps.isEmpty()) findings.add("ERROR: Sequence " + definition.id + " has no steps.");
            long previous = -1;
            int index = 0;
            for (Step step : definition.steps) {
                index++;
                if (step.delayMillis < 0) findings.add("ERROR: Sequence " + definition.id + " step " + index + " has invalid after duration.");
                if (step.malformedFields) findings.add("ERROR: Sequence " + definition.id + " step " + index + " action fields must be YAML lists of strings.");
                if (step.delayMillis < previous) findings.add("ERROR: Sequence " + definition.id + " steps must be ordered by increasing after duration.");
                previous = step.delayMillis;
                for (String tag : step.addTags) if (!plugin.dialogueManager().isKnownPlayerTag(tag) || tag.startsWith("!")) findings.add("ERROR: Sequence " + definition.id + " has invalid add-player-tags value " + tag);
                for (String tag : step.removeTags) if (!plugin.dialogueManager().isKnownPlayerTag(tag) || tag.startsWith("!")) findings.add("ERROR: Sequence " + definition.id + " has invalid remove-player-tags value " + tag);
                for (String entry : step.addJournal) if (!plugin.journalManager().contains(entry)) findings.add("ERROR: Sequence " + definition.id + " references unknown Journal entry " + entry);
                for (String entry : step.removeJournal) if (!plugin.journalManager().contains(entry)) findings.add("ERROR: Sequence " + definition.id + " references unknown Journal entry " + entry);
                for (String reward : step.rewards) if (!plugin.dialogueManager().hasReward(reward)) findings.add("ERROR: Sequence " + definition.id + " references unknown reward " + reward);
                for (String dialogue : step.dialogues) if (!plugin.dialogueManager().hasDialogue(dialogue)) findings.add("ERROR: Sequence " + definition.id + " references unknown dialogue " + dialogue);
                for (String command : step.commands) if (!plugin.dialogueManager().isValidCommand(command)) findings.add("ERROR: Sequence " + definition.id + " has invalid command " + command);
            }
        }
        return findings;
    }

    void validate(CommandSender sender, String onlyId) {
        if (onlyId != null && !definitions.containsKey(onlyId)) throw new IllegalArgumentException("Unknown sequence ID: " + onlyId);
        List<String> findings = validateFindings(onlyId);
        if (findings.isEmpty()) sender.sendMessage("Sequence validation passed.");
        else findings.forEach(sender::sendMessage);
    }

    void command(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Use /iekdialogue sequences <list|show|validate> [id].");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> sender.sendMessage("Sequences: " + String.join(", ", definitions.keySet()));
            case "show" -> {
                if (args.length < 3 || !definitions.containsKey(args[2])) throw new IllegalArgumentException("Unknown sequence ID.");
                sender.sendMessage(config.getConfigurationSection(args[2]).getValues(true).toString());
            }
            case "validate" -> validate(sender, args.length > 2 ? args[2] : null);
            default -> throw new IllegalArgumentException("Use /iekdialogue sequences <list|show|validate> [id].");
        }
    }
}
