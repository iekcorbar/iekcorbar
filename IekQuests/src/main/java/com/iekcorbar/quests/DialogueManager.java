package com.iekcorbar.quests;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


final class DialogueManager {
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([hms])", Pattern.CASE_INSENSITIVE);
    private static final String DIALOGUE_HEADER = "IekQuests dialogue configuration.\nVoice plays a namespaced sound event; the client must already have the resource pack that provides it.\nDialogue effects, Reward entries, and configured sequences run once when an eligible dialogue is selected, not when its pages are redrawn.\nReward entries reference IDs in Reward.yml; use prerequisites, tags, or cooldowns to limit repeat claims.\n";
    private static final String RESPONSE_HEADER = "IekQuests response configuration.\ntext and reply define the response label and optional follow-up text. prerequisites and server-prerequisites gate visibility and selection; prefix a tag with ! to require absence.\nnext-dialogue accepts one ID, an ordered comma-separated fallback, or a quoted [id; id] randomized candidate group.\ncommands run in order; remove-player-tags are applied before add-player-tags, then add-server-tags and Journal effects. executeobjective runs one Manual objective before any response effects; sequences starts named Sequences.yml definitions after the response succeeds.\nper-player-cooldown and limit-per-day restrict response use. quest-id and quest-state/quest-stage provide quest context and eligibility checks.\nquest-actions perform quest operations; target-stage is for goto-stage and repeat-id is for repeat-contract. Empty irrelevant fields should be omitted.\nvoice plays a namespaced sound event; the client must already have the resource pack that provides it.\nsequences starts reusable definitions from Sequences.yml after this dialogue is selected.\n";
    private static final String NPC_HEADER = "IekQuests Citizens NPC bindings.\nvisibility-prerequisites are optional player-tag conditions evaluated separately for each viewer; prefix a tag with ! to require absence.\nCitizens must be installed for per-player NPC visibility. An empty or missing list keeps the NPC visible to all players.\nExample: npc_tutorial_dialogue: {npc-id: 123, display-name: Tutorial, visibility-prerequisites: [tutorial_started], dialogues: [tutorial_intro]}.\n";
    private static final String PLAYER_TAGS_HEADER = "IekQuests player tag registry.\nRegister valid IDs under tags; registration does not assign a tag to any player.\nAssign tags through supported dialogue, event, objective, or administrative operations.\nPositive prerequisites require the tag to be assigned. Prefix a prerequisite with ! to require absence.\nTag IDs are case-sensitive. Unknown or unregistered tags fail prerequisites and are reported by validation.\nExample: tags: [has_seen_intro, quest_active].\n";
    private final IekQuestsPlugin plugin;
    private final Map<String, DialogueDef> dialogues = new LinkedHashMap<>();
    private final Map<String, ResponseDef> responses = new LinkedHashMap<>();
    private final Map<Integer, NpcDef> npcs = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, ArrayDeque<String>> eventDialogueQueues = new HashMap<>();
    private final Set<String> playerTagDefinitions = new HashSet<>();
    private final Set<String> serverTagDefinitions = new HashSet<>();
    private YamlConfiguration dialogueFile;
    private YamlConfiguration responseFile;
    private YamlConfiguration npcFile;
    private YamlConfiguration playerTagFile;
    private YamlConfiguration serverTagFile;
    private YamlConfiguration rewardFile;
    private final Map<String, List<String>> rewards = new LinkedHashMap<>();
    private final Map<String, String> invalidRewardDefinitions = new LinkedHashMap<>();
    private long nextSessionId = 1;
    private final Set<Integer> visibilityControlledNpcIds = new HashSet<>();
    private boolean citizensSpawnListenerRegistered;

    DialogueManager(IekQuestsPlugin plugin) { this.plugin = plugin; }

    private static final class DialogueDef {
        String id;
        String title = "";
        String voice = "";
        List<String> lines = new ArrayList<>();
        List<String> prerequisites = new ArrayList<>();
        List<String> serverPrerequisites = new ArrayList<>();
        List<String> questPrerequisites = new ArrayList<>();
        String questId = "";
        String questState = "";
        String questStage = "";
        List<String> responseIds = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> addPlayerTags = new ArrayList<>();
        List<String> addServerTags = new ArrayList<>();
        List<String> addJournal = new ArrayList<>();
        List<String> removeJournal = new ArrayList<>();
        List<String> rewardIds = new ArrayList<>();
        List<String> sequenceIds = new ArrayList<>();
        boolean malformedSequenceIds;
    }

    private static final class QuestAction {
        String questId = "";
        String action = "";
        String targetStage = "";
        String repeatId = "";
    }

    private static final class ResponseDef {
        String id;
        String text = "";
        String reply = "";
        String voice = "";
        String nextDialogue = "";
        List<String> prerequisites = new ArrayList<>();
        List<String> serverPrerequisites = new ArrayList<>();
        List<String> questPrerequisites = new ArrayList<>();
        String questId = "";
        String questState = "";
        String questStage = "";
        List<String> commands = new ArrayList<>();
        List<String> addPlayerTags = new ArrayList<>();
        List<String> addServerTags = new ArrayList<>();
        List<String> addJournal = new ArrayList<>();
        List<String> removeJournal = new ArrayList<>();
        List<String> removePlayerTags = new ArrayList<>();
        List<String> sequenceIds = new ArrayList<>();
        boolean malformedSequenceIds;
        List<String> legacyActions = new ArrayList<>();
        List<QuestAction> questActions = new ArrayList<>();
        String executeObjective = "";
        boolean malformedRemovePlayerTags;
        long cooldownMillis = -1;
        int limitPerDay;
        String limitKey = "";
    }

    private static final class NpcDef {
        String key;
        int id;
        String name;
        List<String> dialogueIds = new ArrayList<>();
        List<String> visibilityPrerequisites = new ArrayList<>();
        boolean malformedVisibilityPrerequisites;
    }

    private static final class Session {
        final UUID player;
        final int npcId;
        final String npcName;
        final long token;
        String dialogueId = "";
        int textPage;
        int responsePage;
        boolean ended;
        boolean preview;
        final List<String> history = new ArrayList<>();

        Session(UUID player, int npcId, String npcName, long token) {
            this.player = player;
            this.npcId = npcId;
            this.npcName = npcName;
            this.token = token;
        }
    }

    private record VisibleResponse(ResponseDef response, String label) {}
    private record NextDialoguePlan(List<String> ids, boolean randomized, String error) {}

    void load() {
        eventDialogueQueues.clear();
        File folder = plugin.getDataFolder();
        File dialoguePath = plugin.organizationFile("dialogues.yml");
        File responsePath = plugin.organizationFile("responses.yml");
        File npcPath = plugin.organizationFile("npcs.yml");
        File playerTagsPath = plugin.organizationFile("player-tags.yml");
        File serverTagsPath = plugin.organizationFile("server-tags.yml");
        File rewardPath = plugin.organizationFile("Reward.yml");

        boolean noGraphFiles = !dialoguePath.exists() && !responsePath.exists() && !npcPath.exists();
        boolean allGraphFiles = dialoguePath.exists() && responsePath.exists() && npcPath.exists();
        if (noGraphFiles) {
            migrateLegacyQuests(dialoguePath, responsePath, npcPath);
        } else if (!allGraphFiles) {
            writePartialMigrationReport(folder, dialoguePath, responsePath, npcPath);
        }
        ensureYamlHeader(dialoguePath, DIALOGUE_HEADER);
        ensureYamlHeader(responsePath, RESPONSE_HEADER);
        ensureYamlHeader(npcPath, NPC_HEADER);
        ensureYamlHeader(playerTagsPath, PLAYER_TAGS_HEADER);
        dialogueFile = YamlConfiguration.loadConfiguration(dialoguePath);
        responseFile = YamlConfiguration.loadConfiguration(responsePath);
        npcFile = YamlConfiguration.loadConfiguration(npcPath);
        playerTagFile = YamlConfiguration.loadConfiguration(playerTagsPath);
        serverTagFile = YamlConfiguration.loadConfiguration(serverTagsPath);
        if (!serverTagsPath.exists()) save(serverTagFile, serverTagsPath, "server-tags.yml");
        rewardFile = YamlConfiguration.loadConfiguration(rewardPath);

        Set<Integer> previousVisibilityIds = new HashSet<>(visibilityControlledNpcIds);
        visibilityControlledNpcIds.clear();
        dialogues.clear(); responses.clear(); npcs.clear();
        rewards.clear(); invalidRewardDefinitions.clear();
        playerTagDefinitions.clear(); serverTagDefinitions.clear();
        loadTagDefinitions(playerTagFile, playerTagDefinitions, "player-tags.yml");
        loadTagDefinitions(serverTagFile, serverTagDefinitions, "server-tags.yml");
        loadDialogues();
        loadResponses();
        loadNpcs();
        loadRewards();
        validateReferences();
        refreshAllNpcVisibility(previousVisibilityIds);
        registerCitizensSpawnListener();
    }

    private void ensureYamlHeader(File target, String header) {
        try {
            String text = target.exists() ? java.nio.file.Files.readString(target.toPath()) : "";
            String marker = header.lines().findFirst().orElse("");
            if (text.startsWith("# " + marker) || text.startsWith(marker)) return;
            StringBuilder comments = new StringBuilder();
            for (String line : header.split("\\R")) comments.append("# ").append(line).append('\n');
            java.nio.file.Path path = target.toPath();
            java.nio.file.Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temporary, comments + text);
            try {
                java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                java.nio.file.Files.deleteIfExists(temporary);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not add documentation header to " + target.getName() + ": " + ex.getMessage());
        }
    }

    private void loadRewards() {
        for (String id : rewardFile.getKeys(false)) {
            if (!validId(id)) {
                String problem = "Invalid Reward.yml ID.";
                invalidRewardDefinitions.put(id, problem);
                plugin.getLogger().warning(problem + " " + id);
                continue;
            }
            Object value = rewardFile.get(id);
            if (!(value instanceof List<?> list)) {
                invalidRewardDefinitions.put(id, "Reward definition must be a YAML list of reward actions.");
                continue;
            }
            List<String> actions = new ArrayList<>();
            boolean malformed = false;
            for (Object action : list) {
                if (!(action instanceof String actionText) || actionText.isBlank()) {
                    invalidRewardDefinitions.putIfAbsent(id, "Reward actions must be non-empty strings.");
                    malformed = true;
                    continue;
                }
                actions.add(actionText.trim());
            }
            if (malformed) continue;
            rewards.put(id, List.copyOf(actions));
        }
    }

    private String rewardActionProblem(String raw) {
        if (raw == null || raw.isBlank()) return "Action is empty.";
        String action = raw.trim();
        int split = action.indexOf(' ');
        String key = split < 0 ? action : action.substring(0, split);
        String value = split < 0 ? "" : action.substring(split + 1).trim();
        Set<String> builtIn = Set.of("money", "pay", "eco", "ecopay", "eloot", "title", "sound", "say", "tag+", "tag-", "point", "effect", "perm+", "perm-", "teleport", "item", "travel", "xp", "heal", "feed", "broadcast", "cmdplayer", "takeitem", "give", "compass", "shop", "joinjob", "leavejob", "chat", "sequence");
        if (!builtIn.contains(key)) return action.startsWith("/") ? "Server commands must not start with '/'." : null;
        if (Set.of("heal", "feed").contains(key) && value.isEmpty()) return null;
        if (value.isEmpty()) return "Action '" + key + "' requires a value.";
        try {
            switch (key) {
                case "money", "pay", "eco", "ecopay", "xp" -> {
                    if (!value.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) return "Expected one numeric amount.";
                    if (key.equals("xp")) Integer.parseInt(value); else Double.parseDouble(value);
                }
                case "give" -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length > 2) return "Use give <material> [amount].";
                    String material = parts[0];
                    if (org.bukkit.Material.matchMaterial(material) == null) return "Unknown Bukkit material: " + material;
                    if (parts.length > 1 && Integer.parseInt(parts[1]) < 1) return "Item amount must be positive.";
                }
                case "item" -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length > 2) return "Use item <items.yml-id> [amount].";
                    if (!plugin.hasIekItemReward(parts[0])) return "Unknown IekItem ID: " + parts[0];
                    if (parts.length > 1 && Integer.parseInt(parts[1]) < 1) return "Item amount must be positive.";
                }
                case "takeitem" -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length > 2) return "Use takeitem <material-or-item-id> [amount].";
                    String itemId = parts[0];
                    if (itemId.toLowerCase(Locale.ROOT).startsWith("item:")) itemId = itemId.substring(5);
                    if (org.bukkit.Material.matchMaterial(itemId) == null && !plugin.hasIekItemReward(itemId))
                        return "Unknown Bukkit material or IekItem ID: " + itemId;
                    if (parts.length > 1 && Integer.parseInt(parts[1]) < 1) return "Item amount must be positive.";
                }
                case "eloot" -> {
                    if (!plugin.hasEliteItemReward(value)) return "Unknown EliteMobs item file: " + value;
                }
                case "teleport", "compass" -> {
                    if (!plugin.isValidRewardLocation(value)) return "Invalid reward location; use world,x,y,z.";
                }
                case "point" -> {
                    String[] parts = value.split("\\s+");
                    if (parts.length != 2 || !parts[1].matches("[+=-]?\\d+")) return "Use point <category> <+N|-N|N>.";
                }
                case "sound" -> {
                    String soundKey = value.contains(":") ? value : "steamworks:" + value;
                    if (org.bukkit.NamespacedKey.fromString(soundKey) == null) return "Invalid namespaced sound key.";
                }
                case "title" -> {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length > 2 || parts[0].isBlank()) return "Use title <title>|<subtitle> with an optional subtitle.";
                }
                case "effect" -> {
                    String[] parts = value.split("\\s+");
                    if (org.bukkit.potion.PotionEffectType.getByName(parts[0]) == null) return "Unknown potion effect: " + parts[0];
                    if (parts.length > 1 && Integer.parseInt(parts[1]) <= 0) return "Effect duration must be positive.";
                    if (parts.length > 2 && Integer.parseInt(parts[2]) < 0) return "Effect amplifier must not be negative.";
                    if (parts.length > 3) return "Use effect <type> [duration-seconds] [amplifier].";
                }
                case "tag+", "tag-" -> {
                    if (!playerTagDefinitions.contains(value)) return "Unknown registered player tag: " + value;
                }
                case "sequence" -> {
                    if (!plugin.hasSequence(value)) return "Unknown quest sequence: " + value;
                }
            }
        } catch (NumberFormatException ex) {
            return "Invalid numeric value for action '" + key + "'.";
        }
        return null;
    }

    private void writePartialMigrationReport(File folder, File dialoguePath, File responsePath, File npcPath) {
        List<String> affected = plugin.questsView().stream()
                .filter(quest -> quest.npcId >= 0 || !quest.npcIds.isEmpty())
                .map(quest -> quest.id)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        StringBuilder report = new StringBuilder("IekQuests partial dialogue graph migration report\n\n")
                .append("Automatic legacy migration was skipped because only some graph files exist. Existing graph files were preserved.\n")
                .append("Present files: ");
        List<String> present = new ArrayList<>();
        if (dialoguePath.exists()) present.add("dialogues.yml");
        if (responsePath.exists()) present.add("responses.yml");
        if (npcPath.exists()) present.add("npcs.yml");
        report.append(present.isEmpty() ? "none" : String.join(", ", present)).append("\nMissing files: ");
        List<String> missing = new ArrayList<>();
        if (!dialoguePath.exists()) missing.add("dialogues.yml");
        if (!responsePath.exists()) missing.add("responses.yml");
        if (!npcPath.exists()) missing.add("npcs.yml");
        report.append(String.join(", ", missing)).append("\n\nLegacy NPC-bound quest IDs requiring review/migration:\n");
        if (affected.isEmpty()) report.append("- none detected\n");
        else for (String id : affected) report.append("- ").append(id).append('\n');
        report.append("\nManual action: back up the existing graph files, then migrate each listed quest's NPC lines, choices, conditions, voice, rewards, branches, and quest actions into dialogues.yml, responses.yml, and npcs.yml. Validate with /iekdialogue validate before enabling player access.\n");
        File reportFile = new File(folder, "dialogue-migration-report.txt");
        try {
            if (!reportFile.exists()) java.nio.file.Files.writeString(reportFile.toPath(), report.toString());
            plugin.getLogger().warning("Partial dialogue graph detected; automatic migration was skipped. Review dialogue-migration-report.txt.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not write dialogue-migration-report.txt: " + ex.getMessage());
        }
    }

    boolean hasNpc(int npcId) { return npcs.containsKey(npcId); }
    boolean hasSession(Player player) { return sessions.containsKey(player.getUniqueId()); }
    List<String> dialogueIds() { return List.copyOf(dialogues.keySet()); }
    List<String> responseIds() { return List.copyOf(responses.keySet()); }
    List<String> playerTagIds() { return List.copyOf(playerTagDefinitions); }
    List<String> serverTagIds() { return List.copyOf(serverTagDefinitions); }
    boolean isKnownPlayerTag(String tag) { return playerTagDefinitions.contains(tag); }
    boolean hasDialogue(String id) { return dialogues.containsKey(id); }
    boolean hasReward(String id) { return rewards.containsKey(id); }
    boolean isValidCommand(String command) { return command != null && command.trim().matches("(?i)^(server|ser|player|op):\\s*.+$"); }
    void refreshPlayerDialogue(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && player.isOnline()) render(player, session);
    }
    void wizardAction(Player player, String... args) { adminCommand(player, args); }

    void shutdown() { eventDialogueQueues.clear(); sessions.clear(); }

    boolean eventTagsPass(Player player, List<String> required) { return tagsPass(player, required, true); }

    void assignEventPlayerTags(Player player, List<String> tags) {
        applyTags(player, tags, List.of());
        plugin.save();
    }

    void runSequenceCommands(Player player, List<String> commands) {
        runCommands(player, new Session(player.getUniqueId(), -1, "Sequence", nextSessionId++), commands);
    }

    void changeSequencePlayerTags(Player player, List<String> add, List<String> remove) {
        applyPlayerTagChanges(player, add, remove);
        plugin.save();
    }

    void runSequenceRewards(Player player, List<String> ids) {
        for (String id : ids) {
            List<String> actions = rewards.get(id);
            if (actions == null) { plugin.getLogger().warning("Sequence references unknown Reward.yml ID " + id); continue; }
            plugin.runStandaloneRewards(player, actions);
        }
    }

    void startSequences(Player player, List<String> ids) {
        if (plugin.sequencesManager() != null) plugin.sequencesManager().start(player, ids);
    }

    void validateSequenceReferences() {
        if (plugin.sequencesManager() == null) return;
        for (DialogueDef dialogue : dialogues.values()) for (String id : dialogue.sequenceIds)
            if (!plugin.sequencesManager().ids().contains(id)) plugin.getLogger().warning("Dialogue " + dialogue.id + " references unknown sequence " + id);
        for (DialogueDef dialogue : dialogues.values()) if (dialogue.malformedSequenceIds)
            plugin.getLogger().warning("Dialogue " + dialogue.id + " sequences must be a YAML list of strings.");
        for (ResponseDef response : responses.values()) for (String id : response.sequenceIds)
            if (!plugin.sequencesManager().ids().contains(id)) plugin.getLogger().warning("Response " + response.id + " references unknown sequence " + id);
        for (ResponseDef response : responses.values()) if (response.malformedSequenceIds)
            plugin.getLogger().warning("Response " + response.id + " sequences must be a YAML list of strings.");
    }

    void changeRewardPlayerTag(Player player, String tag, boolean assign) {
        if (!playerTagDefinitions.contains(tag)) {
            plugin.getLogger().warning("Reward references undefined player tag " + tag + ".");
            return;
        }
        List<String> assigned = new ArrayList<>(runtimeData().getStringList("dialogue.player-tags." + player.getUniqueId()));
        if (assign && !assigned.contains(tag)) assigned.add(tag);
        if (!assign) assigned.remove(tag);
        runtimeData().set("dialogue.player-tags." + player.getUniqueId(), assigned);
        if (player.isOnline()) refreshNpcVisibility(player);
        plugin.save();
    }

    void runEventCommands(Player player, List<String> commands) {
        runCommands(player, new Session(player.getUniqueId(), -1, "Events", nextSessionId++), commands);
    }

    void queueEventDialogues(Player player, List<String> dialogueIds) {
        if (dialogueIds == null || dialogueIds.isEmpty()) return;
        Session activeSession = sessions.get(player.getUniqueId());
        ArrayDeque<String> queue = eventDialogueQueues.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        for (String dialogueId : dialogueIds) {
            if (activeSession != null && dialogueId.equals(activeSession.dialogueId)) {
                plugin.getLogger().warning("Coalesced duplicate follow-up dialogue '" + dialogueId + "' for " + player.getName() + ".");
                continue;
            }
            queue.addLast(dialogueId);
        }
        if (queue.isEmpty()) eventDialogueQueues.remove(player.getUniqueId());
        startNextEventDialogue(player);
    }

    private void startNextEventDialogue(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || sessions.containsKey(playerId)) return;
        ArrayDeque<String> queue = eventDialogueQueues.get(playerId);
        if (queue == null || queue.isEmpty()) { eventDialogueQueues.remove(playerId); return; }
        String id = queue.removeFirst();
        DialogueDef dialogue = dialogues.get(id);
        if (dialogue == null || !eligibleDialogue(player, dialogue, -1)) {
            plugin.getLogger().warning("Skipped unavailable event dialogue '" + id + "' for " + player.getName() + ".");
            Bukkit.getScheduler().runTask(plugin, () -> startNextEventDialogue(player));
            return;
        }
        Session session = new Session(playerId, -1, "Events", nextSessionId++);
        sessions.put(playerId, session);
        selectDialogue(player, session, dialogue);
    }

    void openForNpc(Player player, int npcId) {
        NpcDef npc = npcs.get(npcId);
        if (npc == null || !canPlayerSeeNpc(player, npcId)) return;
        Session session = new Session(player.getUniqueId(), npcId, npc.name, nextSessionId++);
        sessions.put(player.getUniqueId(), session);
        DialogueDef selected = firstEligible(npc, player);
        if (selected == null) {
            session.ended = true;
            session.history.add(plugin.dialogueConfig("no-eligible-dialogue", "&7There is nothing to discuss right now."));
            render(player, session);
            return;
        }
        selectDialogue(player, session, selected);
    }

    void close(Player player) {
        UUID id = player.getUniqueId();
        sessions.remove(id);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) { eventDialogueQueues.remove(id); return; }
            startNextEventDialogue(player);
        });
    }

    void appendLine(Player player, String text) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.history.add(text);
        render(player, session);
    }

    void refreshForQuest(Player player, IekQuestsPlugin.Quest quest) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.ended) return;
        DialogueDef current = dialogues.get(session.dialogueId);
        if (current == null || !current.questId.equals(quest.id)) return;
        NpcDef npc = npcs.get(session.npcId);
        DialogueDef selected = npc == null ? null : firstEligible(npc, player);
        if (selected == null) {
            session.ended = true;
            session.dialogueId = "";
            session.history.add(plugin.dialogueConfig("no-eligible-dialogue", "&7There is nothing to discuss right now."));
            render(player, session);
        } else if (!selected.id.equals(current.id)) {
            selectDialogue(player, session, selected);
        } else render(player, session);
    }

    private DialogueDef firstEligible(NpcDef npc, Player player) {
        for (String id : npc.dialogueIds) {
            DialogueDef d = dialogues.get(id);
            if (d != null && eligibleDialogue(player, d, npc.id)) return d;
        }
        return null;
    }

    private boolean eligibleDialogue(Player player, DialogueDef d, int npcId) {
        if (!tagsPass(player, d.prerequisites, true) || !tagsPass(player, d.serverPrerequisites, false)) return false;
        IekQuestsPlugin.Quest quest = d.questId.isEmpty() ? null : plugin.questById(d.questId);
        if (!d.questId.isEmpty() && quest == null) return false;
        if (quest == null) return d.questPrerequisites.isEmpty();
        IekQuestsPlugin.Prog p = plugin.progress(player, quest);
        if (!statePass(d.questState, d.questStage, quest, p)) return false;
        if (d.questState.equalsIgnoreCase("not_started") && !questConditionsPass(player, quest, p,
                quest.startConds.stream().map(condition -> condition.raw).toList(), npcId)) return false;
        return questConditionsPass(player, quest, p, d.questPrerequisites, npcId);
    }

    private boolean statePass(String state, String stage, IekQuestsPlugin.Quest quest, IekQuestsPlugin.Prog p) {
        if (!state.isBlank()) {
            switch (state.toLowerCase(Locale.ROOT)) {
                case "not_started", "not-started" -> { if (p.started || p.done) return false; }
                case "active", "started" -> { if (!p.started || p.done) return false; }
                case "completed", "done" -> { if (!p.done) return false; }
                default -> { return false; }
            }
        }
        return stage.isBlank() || p.stage.equalsIgnoreCase(stage);
    }

    private void selectDialogue(Player player, Session session, DialogueDef dialogue) {
        if (!eligibleDialogue(player, dialogue, session.npcId)) {
            session.ended = true;
            session.dialogueId = "";
            session.history.add(plugin.dialogueConfig("dialogue-unavailable", "&7That dialogue is no longer available."));
            render(player, session);
            return;
        }
        ArrayDeque<String> queued = eventDialogueQueues.get(player.getUniqueId());
        if (queued != null) {
            queued.removeFirstOccurrence(dialogue.id);
            if (queued.isEmpty()) eventDialogueQueues.remove(player.getUniqueId());
        }
        session.dialogueId = dialogue.id;
        session.textPage = 0;
        session.responsePage = 0;

        // These effects belong to selecting/opening the dialogue, not selecting a response.
        applyTags(player, dialogue.addPlayerTags, dialogue.addServerTags);
        if (!session.preview) startSequences(player, dialogue.sequenceIds);
        if (!session.preview) applyJournal(player, dialogue.addJournal, dialogue.removeJournal);
        runCommands(player, session, dialogue.commands);
        if (!session.preview) runDialogueRewards(player, dialogue);
        if (!dialogue.voice.isBlank()) plugin.speak(player, dialogue.voice);
        plugin.save();
        render(player, session);
    }

    private List<VisibleResponse> visibleResponses(Player player, Session session, DialogueDef dialogue) {
        List<VisibleResponse> visible = new ArrayList<>();
        for (String responseId : dialogue.responseIds) {
            ResponseDef response = responses.get(responseId);
            if (response == null || (!session.preview && !eligibleResponse(player, session, dialogue, response))) continue;
            visible.add(new VisibleResponse(response, response.text));
        }
        return visible;
    }

    private boolean eligibleResponse(Player player, Session session, DialogueDef dialogue, ResponseDef response) {
        if (!responseTagEffectsValid(response)) return false;
        if (!response.executeObjective.isBlank()) plugin.objectivesManager().refreshAvailability(player);
        if (!tagsPass(player, response.prerequisites, true) || !tagsPass(player, response.serverPrerequisites, false)) return false;
        if (!response.executeObjective.isBlank()
                && !plugin.objectivesManager().canExecuteObjective(player, response.executeObjective)) return false;
        IekQuestsPlugin.Quest quest = questFor(dialogue, response);
        if (!response.questId.isBlank() && quest == null) return false;
        if (quest == null) return response.questPrerequisites.isEmpty() && questActionReady(player, response, null, session.npcId)
                && !onCooldown(player, response);
        IekQuestsPlugin.Prog p = plugin.progress(player, quest);
        if (!statePass(response.questState, response.questStage, quest, p)) return false;
        return questConditionsPass(player, quest, p, response.questPrerequisites, session.npcId)
                && questActionReady(player, response, quest, session.npcId)
                && !onCooldown(player, response)
                && !dailyLimitReached(player, response, quest);
    }

    private boolean responseTagEffectsValid(ResponseDef response) {
        Set<String> overlap = new HashSet<>(response.addPlayerTags);
        overlap.retainAll(response.removePlayerTags);
        if (!overlap.isEmpty()) return false;
        for (String tag : response.removePlayerTags)
            if (tag.startsWith("!") || !playerTagDefinitions.contains(tag)) return false;
        for (String tag : response.addPlayerTags)
            if (tag.startsWith("!") || !playerTagDefinitions.contains(tag)) return false;
        return true;
    }

    private boolean questConditionsPass(Player player, IekQuestsPlugin.Quest quest, IekQuestsPlugin.Prog progress,
                                        List<String> conditions, int npcId) {
        List<String> ordinary = new ArrayList<>();
        for (String condition : conditions) {
            if (condition == null || condition.isBlank()) continue;
            if (condition.startsWith("npc:")) {
                try { if (npcId != Integer.parseInt(condition.substring(4).trim())) return false; }
                catch (NumberFormatException ex) { return false; }
            } else ordinary.add(condition);
        }
        return plugin.questConditionsPass(player, quest, progress, ordinary);
    }

    private IekQuestsPlugin.Quest questFor(DialogueDef dialogue, ResponseDef response) {
        String id = response.questId.isBlank() ? dialogue.questId : response.questId;
        return id.isBlank() ? null : plugin.questById(id);
    }

    private boolean questActionReady(Player player, ResponseDef response, IekQuestsPlugin.Quest fallbackQuest, int npcId) {
        for (QuestAction action : response.questActions) {
            IekQuestsPlugin.Quest quest = action.questId.isBlank() ? fallbackQuest : plugin.questById(action.questId);
            if (quest == null) return false;
            IekQuestsPlugin.Prog p = plugin.progress(player, quest);
            switch (action.action.toLowerCase(Locale.ROOT)) {
                case "accept", "start" -> {
                    if (p.started || p.done || quest.stages.isEmpty() || !questConditionsPass(player, quest, p,
                            quest.startConds.stream().map(condition -> condition.raw).toList(), npcId)) return false;
                }
                case "restart" -> {
                    if (!p.done || !quest.repeats.isEmpty() || quest.stages.isEmpty() || !questConditionsPass(player, quest, p,
                            quest.startConds.stream().map(condition -> condition.raw).toList(), npcId)) return false;
                }
                case "advance", "continue", "turn-in" -> {
                    IekQuestsPlugin.Stage stage = quest.stages.get(p.stage);
                    if (!p.started || p.done || stage == null || !plugin.stageActionReady(player, quest, p, stage)) return false;
                }
                case "goto", "goto-stage" -> { if (!quest.stages.containsKey(action.targetStage)) return false; }
            case "complete" -> {
                    IekQuestsPlugin.Stage stage = quest.stages.get(p.stage);
                    if (p.done || !p.started || stage == null || !isFinalStage(quest, stage)
                            || !plugin.stageActionReady(player, quest, p, stage)) return false;
                }
                case "repeat", "repeat-contract" -> { if (!p.done || !plugin.repeatActionReady(player, quest, action.repeatId)) return false; }
                case "refuse", "close", "none", "run-rewards" -> { }
                default -> { return false; }
            }
        }
        if (!response.questId.isBlank() && fallbackQuest != null && response.questActions.isEmpty()) {
            IekQuestsPlugin.Prog p = plugin.progress(player, fallbackQuest);
            return statePass(response.questState, response.questStage, fallbackQuest, p);
        }
        return true;
    }

    private boolean isFinalStage(IekQuestsPlugin.Quest quest, IekQuestsPlugin.Stage current) {
        if (!current.gotoKey.isBlank() || !current.next.isBlank()) return false;
        boolean found = false;
        for (IekQuestsPlugin.Stage stage : quest.stages.values()) {
            if (found) return false;
            if (stage.key.equals(current.key)) found = true;
        }
        return found;
    }

    private boolean hasRepeat(IekQuestsPlugin.Quest q, String id) {
        for (IekQuestsPlugin.Repeat r : q.repeats) if (r.id.equals(id)) return true;
        return false;
    }

    private boolean onCooldown(Player player, ResponseDef response) {
        if (response.cooldownMillis < 0) return false;
        long until = runtimeData().getLong("dialogue.cooldowns." + player.getUniqueId() + "." + response.id, 0L);
        return until > System.currentTimeMillis();
    }

    private boolean dailyLimitReached(Player player, ResponseDef response, IekQuestsPlugin.Quest q) {
        if (response.limitPerDay <= 0 || q == null || response.limitKey.isBlank()) return false;
        IekQuestsPlugin.Prog p = plugin.progress(player, q);
        int today = (int) java.time.LocalDate.now().toEpochDay();
        int raw = p.points.getOrDefault(response.limitKey, 0);
        return raw / 1000 == today && raw % 1000 >= response.limitPerDay;
    }

    private void render(Player player, Session session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
        DialogueDef dialogue = dialogues.get(session.dialogueId);
        List<String> lines = new ArrayList<>(session.history);
        if (dialogue != null) lines.addAll(dialogue.lines);
        if (lines.isEmpty()) lines.add(plugin.dialogueConfig("empty-text", "&7There is nothing to say."));
        int perPage = Math.max(1, Math.min(32, plugin.getConfig().getInt("dialogue.lines-per-page", 5)));
        int textPages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        List<VisibleResponse> choices = dialogue == null || session.ended ? List.of() : visibleResponses(player, session, dialogue);
        int responsePages = Math.max(1, (choices.size() + 5) / 6);
        session.textPage = Math.max(0, Math.min(session.textPage, textPages - 1));
        session.responsePage = Math.max(0, Math.min(session.responsePage, responsePages - 1));

        List<DialogBody> body = new ArrayList<>();
        int from = session.textPage * perPage;
        int to = Math.min(lines.size(), from + perPage);
        for (int i = from; i < to; i++) body.add(DialogBody.plainMessage(plugin.legacy(lines.get(i)), 200));
        body.add(DialogBody.plainMessage(plugin.legacy(plugin.dialogueConfig("text-page", "&7Text") + " " + (session.textPage + 1) + "/" + textPages
                + " &7| " + plugin.dialogueConfig("responses-page", "&7Responses") + " " + (session.responsePage + 1) + "/" + responsePages), 200));

        List<ActionButton> buttons = new ArrayList<>();
        if (textPages > 1) {
            buttons.add(pageButton(player, session, "text:-", session.textPage > 0 ? "&e↑ Previous text" : "&8↑ Previous text"));
            buttons.add(pageButton(player, session, "text:+", session.textPage + 1 < textPages ? "&e↓ More text" : "&8↓ More text"));
        }
        if (responsePages > 1) {
            buttons.add(pageButton(player, session, "responses:-", session.responsePage > 0 ? "&7◀ Earlier responses" : "&8◀ Earlier responses"));
            buttons.add(pageButton(player, session, "responses:+", session.responsePage + 1 < responsePages ? "&7Later responses ▶" : "&8Later responses ▶"));
        }
        int first = session.responsePage * 6;
        int last = Math.min(choices.size(), first + 6);
        for (int i = first; i < last; i++) {
            VisibleResponse choice = choices.get(i);
            buttons.add(responseButton(player, session, choice.response, (i - first + 1) + ": " + choice.label));
        }
        if (buttons.isEmpty() && !session.ended) buttons.add(pageButton(player, session, "noop", plugin.dialogueConfig("empty-responses", "&7No responses are available.")));

        String title = dialogue != null && !dialogue.title.isBlank() ? dialogue.title : session.npcName;
        if (session.preview) title = "ADMIN PREVIEW - " + title;
        final String dialogTitle = title;
        ActionButton close = ActionButton.builder(plugin.legacy(plugin.dialogueConfig("close", "&cClose conversation")))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.getUniqueId().equals(session.player)) close(p);
                }, ClickCallback.Options.builder().uses(1).build())).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plugin.legacy(dialogTitle)).body(body).canCloseWithEscape(false).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE).build())
                .type(DialogType.multiAction(buttons).exitAction(close).columns(2).build()));
        player.showDialog(dialog);
    }

    private ActionButton pageButton(Player player, Session session, String action, String label) {
        return ActionButton.builder(plugin.legacy(label)).action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player p) || !validSession(p, session)) return;
            switch (action) {
                case "text:-" -> session.textPage--;
                case "text:+" -> session.textPage++;
                case "responses:-" -> session.responsePage--;
                case "responses:+" -> session.responsePage++;
                case "noop" -> { }
                default -> { return; }
            }
            render(p, session);
        }, ClickCallback.Options.builder().uses(1).build())).build();
    }

    private ActionButton responseButton(Player player, Session session, ResponseDef response, String label) {
        return ActionButton.builder(plugin.legacy(label)).action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player p) || !validSession(p, session)) return;
            if (session.preview) { render(p, session); return; }
            DialogueDef dialogue = dialogues.get(session.dialogueId);
            if (dialogue == null || !dialogue.responseIds.contains(response.id) || !eligibleResponse(p, session, dialogue, response)) {
                render(p, session);
                return;
            }
            selectResponse(p, session, dialogue, response);
        }, ClickCallback.Options.builder().uses(1).build())).build();
    }

    private boolean validSession(Player player, Session session) {
        return player.getUniqueId().equals(session.player) && sessions.get(session.player) == session && session.token > 0;
    }

    private void selectResponse(Player player, Session session, DialogueDef dialogue, ResponseDef response) {
        if (!eligibleResponse(player, session, dialogue, response)) { render(player, session); return; }
        if (!response.executeObjective.isBlank()
                && !plugin.objectivesManager().executeObjective(player, response.executeObjective)) {
            render(player, session);
            return;
        }
        if (!response.reply.isBlank()) session.history.add(response.reply);
        applyPlayerTagChanges(player, response.addPlayerTags, response.removePlayerTags);
        applyTags(player, List.of(), response.addServerTags);
        applyJournal(player, response.addJournal, response.removeJournal);
        runCommands(player, session, response.commands);
        if (!response.voice.isBlank()) plugin.speak(player, response.voice);
        applyCooldown(player, response);
        applyDailyLimit(player, response, questFor(dialogue, response));

        boolean oldSuppression = plugin.beginDialogueQuestAction(player);
        try {
            for (QuestAction action : response.questActions) executeQuestAction(player, dialogue, response, action);
            IekQuestsPlugin.Quest legacyQuest = questFor(dialogue, response);
            if (legacyQuest != null) {
                IekQuestsPlugin.Prog legacyProgress = plugin.progress(player, legacyQuest);
                for (String action : response.legacyActions) plugin.run(player, legacyQuest, legacyProgress, action);
            }
        } finally {
            plugin.endDialogueQuestAction(player, oldSuppression);
        }
        startSequences(player, response.sequenceIds);
        plugin.save();

        if (response.nextDialogue.isBlank()) {
            session.ended = true;
            session.dialogueId = "";
            render(player, session);
            return;
        }
        DialogueDef next = resolveNextDialogue(player, session, response.nextDialogue);
        if (next == null) {
            session.ended = true;
            session.dialogueId = "";
            session.history.add(plugin.dialogueConfig("dialogue-unavailable", "&7That dialogue is no longer available."));
            render(player, session);
            return;
        }
        selectDialogue(player, session, next);
    }

    private DialogueDef resolveNextDialogue(Player player, Session session, String raw) {
        NextDialoguePlan plan = parseNextDialogue(raw);
        if (plan.error != null) return null;
        List<String> candidates = new ArrayList<>(plan.ids);
        if (plan.randomized) Collections.shuffle(candidates);
        for (String id : candidates) {
            DialogueDef candidate = dialogues.get(id);
            if (candidate != null && eligibleDialogue(player, candidate, session.npcId)) return candidate;
        }
        return null;
    }

    private NextDialoguePlan parseNextDialogue(String raw) {
        if (raw == null || raw.isBlank()) return new NextDialoguePlan(List.of(), false, null);
        String value = raw.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1).trim();
        boolean open = value.startsWith("[");
        boolean close = value.endsWith("]");
        if (open != close) return new NextDialoguePlan(List.of(), false, "Unbalanced candidate brackets.");
        boolean randomized = open;
        String body = randomized ? value.substring(1, value.length() - 1).trim() : value;
        if (body.isEmpty()) return new NextDialoguePlan(List.of(), randomized, "Candidate list is empty.");
        if (randomized && body.contains(",")) return new NextDialoguePlan(List.of(), true, "Random candidates must be separated with semicolons.");
        if (!randomized && (body.contains("[") || body.contains("]") || body.contains(";")))
            return new NextDialoguePlan(List.of(), false, "Use commas for ordered candidates or bracketed semicolons for randomized candidates.");
        String separator = randomized ? ";" : ",";
        String[] parts = body.split(java.util.regex.Pattern.quote(separator), -1);
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            String id = part.trim();
            if (!validId(id)) return new NextDialoguePlan(List.of(), randomized, "Invalid or empty dialogue ID: " + id);
            if (!seen.add(id)) return new NextDialoguePlan(List.of(), randomized, "Duplicate dialogue ID: " + id);
            ids.add(id);
        }
        return new NextDialoguePlan(List.copyOf(ids), randomized, null);
    }

    private void executeQuestAction(Player player, DialogueDef source, ResponseDef response, QuestAction action) {
        String contextQuestId = response.questId.isBlank() ? source.questId : response.questId;
        IekQuestsPlugin.Quest q = plugin.questById(action.questId.isBlank() ? contextQuestId : action.questId);
        if (q == null) return;
        IekQuestsPlugin.Prog p = plugin.progress(player, q);
        switch (action.action.toLowerCase(Locale.ROOT)) {
            case "accept", "start" -> plugin.startQuest(player, q, p);
            case "restart" -> {
                p.started = false; p.done = false; p.stage = "";
                plugin.startQuest(player, q, p);
            }
            case "advance", "continue", "turn-in" -> {
                IekQuestsPlugin.Stage stage = q.stages.get(p.stage);
                if (stage != null && plugin.stageActionReady(player, q, p, stage)) plugin.advance(player, q, p, stage);
            }
            case "goto", "goto-stage" -> {
                if (q.stages.containsKey(action.targetStage)) {
                    p.started = true; p.done = false; p.stage = action.targetStage;
                    plugin.speakFirst(player, q, p);
                    plugin.save();
                }
            }
            case "complete" -> {
                IekQuestsPlugin.Stage stage = q.stages.get(p.stage);
                if (stage != null && isFinalStage(q, stage) && plugin.stageActionReady(player, q, p, stage)) plugin.advance(player, q, p, stage);
            }
            case "repeat", "repeat-contract" -> plugin.doRepeat(player, q, p, action.repeatId);
            case "run-rewards" -> plugin.runRewards(player, q, p, q.rewards);
            case "refuse", "close", "none" -> { }
            default -> plugin.getLogger().warning("Unknown dialogue quest action '" + action.action + "' for " + q.id);
        }
    }

    private void applyCooldown(Player player, ResponseDef response) {
        if (response.cooldownMillis < 0) return;
        runtimeData().set("dialogue.cooldowns." + player.getUniqueId() + "." + response.id,
                System.currentTimeMillis() + response.cooldownMillis);
    }

    private void applyDailyLimit(Player player, ResponseDef response, IekQuestsPlugin.Quest q) {
        if (response.limitPerDay <= 0 || q == null || response.limitKey.isBlank()) return;
        IekQuestsPlugin.Prog p = plugin.progress(player, q);
        int today = (int) java.time.LocalDate.now().toEpochDay();
        int raw = p.points.getOrDefault(response.limitKey, 0);
        int used = raw / 1000 == today ? raw % 1000 : 0;
        p.points.put(response.limitKey, today * 1000 + used + 1);
    }

    private void applyTags(Player player, List<String> playerTags, List<String> serverTags) {
        applyPlayerTagChanges(player, playerTags, List.of());
        YamlConfiguration data = runtimeData();
        List<String> assignedServer = new ArrayList<>(data.getStringList("dialogue.server-tags"));
        for (String tag : serverTags) {
            if (!serverTagDefinitions.contains(tag)) { plugin.getLogger().warning("Ignored undefined server tag " + tag); continue; }
            if (!assignedServer.contains(tag)) assignedServer.add(tag);
        }
        data.set("dialogue.server-tags", assignedServer);
    }

    private void applyPlayerTagChanges(Player player, List<String> add, List<String> remove) {
        YamlConfiguration data = runtimeData();
        List<String> assigned = new ArrayList<>(data.getStringList("dialogue.player-tags." + player.getUniqueId()));
        for (String tag : remove) {
            if (!playerTagDefinitions.contains(tag)) { plugin.getLogger().warning("Ignored undefined player tag removal " + tag); continue; }
            assigned.remove(tag);
        }
        for (String tag : add) {
            if (!playerTagDefinitions.contains(tag)) { plugin.getLogger().warning("Ignored undefined player tag " + tag); continue; }
            if (!assigned.contains(tag)) assigned.add(tag);
        }
        data.set("dialogue.player-tags." + player.getUniqueId(), assigned);
        refreshNpcVisibility(player);
    }

    private void applyJournal(Player player, List<String> addEntries, List<String> removeEntries) {
        plugin.journalManager().add(player, addEntries);
        plugin.journalManager().remove(player, removeEntries);
    }

    private void runCommands(Player player, Session session, List<String> commands) {
        for (String raw : commands) {
            String command = raw == null ? "" : raw.trim();
            if (command.isEmpty()) continue;
            String mode;
            String body;
            int colon = command.indexOf(':');
            if (colon > 0) { mode = command.substring(0, colon).trim().toLowerCase(Locale.ROOT); body = command.substring(colon + 1).trim(); }
            else {
                int space = command.indexOf(' ');
                mode = space < 0 ? "" : command.substring(0, space).trim().toLowerCase(Locale.ROOT);
                body = space < 0 ? "" : command.substring(space + 1).trim();
            }
            body = body.replace("%playername%", player.getName()).replace("%npcname%", session.npcName);
            body = body.replaceFirst("(?i)\\s+-s\\s*$", "").trim();
            if (body.startsWith("/")) body = body.substring(1);
            if (body.isBlank()) continue;
            try {
                if (mode.equals("server") || mode.equals("ser")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), body);
                else if (mode.equals("op")) runElevatedAsPlayer(player, body);
                else if (mode.equals("player")) player.performCommand(body);
                else plugin.getLogger().warning("Unsupported dialogue command prefix in '" + raw + "'. Use server:, op:, or player:.");
            } catch (Exception ex) {
                plugin.getLogger().warning("Dialogue command failed ('" + mode + "'): " + ex.getMessage());
            }
        }
    }

    private void runElevatedAsPlayer(Player player, String command) {
        String lower = command.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("op ") || lower.startsWith("deop ") || lower.startsWith("minecraft:op ")
                || lower.startsWith("minecraft:deop ") || lower.startsWith("lp user ")) {
            plugin.getLogger().warning("Rejected dialogue op command that could change persistent permissions: " + command);
            return;
        }
        boolean wasOp = player.isOp();
        try {
            if (!wasOp) player.setOp(true);
            player.performCommand(command);
        } finally {
            if (!wasOp && player.isOnline()) player.setOp(false);
        }
    }

    private boolean tagsPass(Player player, List<String> required, boolean playerTags) {
        for (String expression : required) {
            if (expression == null || expression.isBlank()) continue;
            boolean negated = expression.startsWith("!");
            String tag = (negated ? expression.substring(1) : expression).trim();
            if (tag.isEmpty()) continue;
            Set<String> definitions = playerTags ? playerTagDefinitions : serverTagDefinitions;
            if (!definitions.contains(tag)) return false;
            List<String> assigned = playerTags
                    ? runtimeData().getStringList("dialogue.player-tags." + player.getUniqueId())
                    : runtimeData().getStringList("dialogue.server-tags");
            boolean present = assigned.contains(tag);
            if ((!negated && !present) || (negated && present)) return false;
        }
        return true;
    }

    private YamlConfiguration runtimeData() { return plugin.dialogueData(); }

    private void loadTagDefinitions(YamlConfiguration file, Set<String> out, String path) {
        for (String tag : file.getStringList("tags")) if (tag != null && !tag.isBlank()) out.add(tag.trim());
        ConfigurationSection section = file.getConfigurationSection("tags");
        if (section != null) out.addAll(section.getKeys(false));
    }

    private void loadDialogues() {
        for (String id : dialogueFile.getKeys(false)) {
            ConfigurationSection s = dialogueFile.getConfigurationSection(id);
            if (s == null) continue;
            DialogueDef d = new DialogueDef(); d.id = id;
            d.title = s.getString("title", "");
            d.voice = s.getString("voice", "");
            d.lines = s.getStringList("lines");
            d.prerequisites = s.getStringList("prerequisites");
            d.serverPrerequisites = s.getStringList("server-prerequisites");
            d.questPrerequisites = s.getStringList("quest-prerequisites");
            d.questId = s.getString("quest-id", "");
            d.questState = s.getString("quest-state", "");
            d.questStage = s.getString("quest-stage", "");
            d.responseIds = s.getStringList("responses");
            d.commands = s.getStringList("commands");
            d.addPlayerTags = s.getStringList("add-player-tags");
            d.addServerTags = s.getStringList("add-server-tags");
            d.addJournal = s.getStringList("addjournal");
            d.removeJournal = s.getStringList("removejournal");
            Object sequenceValue = s.get("sequences");
            d.malformedSequenceIds = sequenceValue != null && (!(sequenceValue instanceof List<?>)
                    || ((List<?>) sequenceValue).stream().anyMatch(value -> !(value instanceof String)));
            d.sequenceIds = s.getStringList("sequences");
            Object rewardValue = s.contains("Reward") ? s.get("Reward") : s.get("rewards");
            if (rewardValue instanceof List<?> rewardList) {
                for (Object rewardId : rewardList) if (rewardId != null) d.rewardIds.add(String.valueOf(rewardId).trim());
            } else if (rewardValue != null) plugin.getLogger().warning("Dialogue " + id + " Reward must be a YAML list of Reward.yml IDs.");
            dialogues.put(id, d);
        }
    }

    private void runDialogueRewards(Player player, DialogueDef dialogue) {
        for (String rewardId : dialogue.rewardIds) {
            List<String> actions = rewards.get(rewardId);
            if (actions == null) {
                plugin.getLogger().warning("Dialogue " + dialogue.id + " references unknown reward " + rewardId + ".");
                continue;
            }
            plugin.runStandaloneRewards(player, actions);
        }
    }

    private void loadResponses() {
        for (String id : responseFile.getKeys(false)) {
            ConfigurationSection s = responseFile.getConfigurationSection(id);
            if (s == null) continue;
            ResponseDef r = new ResponseDef(); r.id = id;
            r.text = s.getString("text", id);
            r.reply = s.getString("reply", "");
            r.voice = s.getString("voice", "");
            r.nextDialogue = s.getString("next-dialogue", "");
            r.prerequisites = s.getStringList("prerequisites");
            r.serverPrerequisites = s.getStringList("server-prerequisites");
            r.questPrerequisites = s.getStringList("quest-prerequisites");
            r.questId = s.getString("quest-id", "");
            r.questState = s.getString("quest-state", "");
            r.questStage = s.getString("quest-stage", "");
            r.commands = s.getStringList("commands");
            r.addPlayerTags = s.getStringList("add-player-tags");
            r.addServerTags = s.getStringList("add-server-tags");
            r.addJournal = s.getStringList("addjournal");
            r.removeJournal = s.getStringList("removejournal");
            Object removeTagValue = s.get("remove-player-tags");
            if (removeTagValue != null && !(removeTagValue instanceof List<?>))
                plugin.getLogger().warning("Response " + id + " remove-player-tags must be a YAML list.");
            if (removeTagValue instanceof List<?> values && values.stream().anyMatch(value -> !(value instanceof String)))
                plugin.getLogger().warning("Response " + id + " remove-player-tags values must all be strings.");
            r.malformedRemovePlayerTags = removeTagValue != null && (!(removeTagValue instanceof List<?>)
                    || ((List<?>) removeTagValue).stream().anyMatch(value -> !(value instanceof String)));
            r.removePlayerTags = s.getStringList("remove-player-tags");
            Object sequenceValue = s.get("sequences");
            r.malformedSequenceIds = sequenceValue != null && (!(sequenceValue instanceof List<?>)
                    || ((List<?>) sequenceValue).stream().anyMatch(value -> !(value instanceof String)));
            r.sequenceIds = s.getStringList("sequences");
            r.executeObjective = s.getString("executeobjective", "").trim();
            r.legacyActions = s.getStringList("legacy-actions");
            r.cooldownMillis = parseCooldown(s.getString("per-player-cooldown", "-1"));
            r.limitPerDay = s.getInt("limit-per-day", 0);
            r.limitKey = s.getString("limit-key", "");
            ConfigurationSection actions = s.getConfigurationSection("quest-actions");
            if (actions != null && (actions.contains("action") || actions.contains("quest-id"))) {
                r.questActions.add(readQuestAction(actions));
            } else if (actions != null) {
                for (String key : actions.getKeys(false)) {
                    ConfigurationSection action = actions.getConfigurationSection(key);
                    if (action != null) r.questActions.add(readQuestAction(action));
                }
            }
            // Also accept a single action object or a list of action maps.
            if (actions == null) {
                Object value = s.get("quest-actions");
                if (value instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> map)) continue;
                        r.questActions.add(readQuestAction(map));
                    }
                } else if (value instanceof Map<?, ?> map) {
                    r.questActions.add(readQuestAction(map));
                }
            }
            responses.put(id, r);
        }
    }

    private QuestAction readQuestAction(ConfigurationSection section) {
        QuestAction action = new QuestAction();
        action.questId = section.getString("quest-id", "");
        action.action = section.getString("action", "");
        action.targetStage = section.getString("target-stage", "");
        action.repeatId = section.getString("repeat-id", "");
        return action;
    }

    private QuestAction readQuestAction(Map<?, ?> map) {
        QuestAction action = new QuestAction();
        action.questId = string(map.get("quest-id"));
        action.action = string(map.get("action"));
        action.targetStage = string(map.get("target-stage"));
        action.repeatId = string(map.get("repeat-id"));
        return action;
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private void loadNpcs() {
        ConfigurationSection root = npcFile.getConfigurationSection("npcs");
        if (root == null) root = npcFile;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            int id = s.getInt("npc-id", -1);
            if (id < 0) { plugin.getLogger().warning("Invalid Citizens NPC ID in npcs.yml entry " + key); continue; }
            NpcDef n = new NpcDef(); n.key = key; n.id = id; n.name = s.getString("display-name", key);
            n.dialogueIds = s.getStringList("dialogues");
            Object visibilityValue = s.get("visibility-prerequisites");
            if (visibilityValue != null && !(visibilityValue instanceof List<?>))
                plugin.getLogger().warning("NPC " + key + " visibility-prerequisites must be a YAML list of player-tag expressions.");
            if (visibilityValue instanceof List<?> values && values.stream().anyMatch(value -> !(value instanceof String)))
                plugin.getLogger().warning("NPC " + key + " visibility-prerequisites values must all be strings.");
            n.malformedVisibilityPrerequisites = visibilityValue != null && (!(visibilityValue instanceof List<?>)
                    || ((List<?>) visibilityValue).stream().anyMatch(value -> !(value instanceof String)));
            n.visibilityPrerequisites = s.getStringList("visibility-prerequisites");
            if (!n.visibilityPrerequisites.isEmpty()) visibilityControlledNpcIds.add(id);
            if (n.dialogueIds.isEmpty()) {
                String legacy = s.getString("dialogue", "");
                for (String part : legacy.split(",")) if (!part.isBlank()) n.dialogueIds.add(part.trim());
            }
            if (npcs.putIfAbsent(id, n) != null) plugin.getLogger().warning("Duplicate Citizens NPC ID " + id + " in npcs.yml.");
        }
    }

    private void validateReferences() {
        for (DialogueDef d : dialogues.values()) {
            if (!d.questId.isBlank() && plugin.questById(d.questId) == null) plugin.getLogger().warning("Dialogue " + d.id + " references unknown quest " + d.questId);
            for (String responseId : d.responseIds) {
                ResponseDef response = responses.get(responseId);
                if (response == null) { plugin.getLogger().warning("Dialogue " + d.id + " references unknown response " + responseId); continue; }
                for (QuestAction action : response.questActions) {
                    String questId = action.questId.isBlank() ? (response.questId.isBlank() ? d.questId : response.questId) : action.questId;
                    IekQuestsPlugin.Quest quest = questId.isBlank() ? null : plugin.questById(questId);
                    if (quest == null) { plugin.getLogger().warning("Response " + response.id + " has a quest action with unknown quest " + questId); continue; }
                    String verb = action.action.toLowerCase(Locale.ROOT);
                    if (!Set.of("accept", "start", "restart", "refuse", "close", "none", "advance", "continue", "turn-in", "goto", "goto-stage", "complete", "repeat", "repeat-contract", "run-rewards").contains(verb))
                        plugin.getLogger().warning("Response " + response.id + " has unknown quest action " + action.action);
                    if ((verb.equals("goto") || verb.equals("goto-stage")) && !quest.stages.containsKey(action.targetStage))
                        plugin.getLogger().warning("Response " + response.id + " targets unknown stage " + action.targetStage + " in quest " + quest.id);
                    if ((verb.equals("repeat") || verb.equals("repeat-contract")) && !hasRepeat(quest, action.repeatId))
                        plugin.getLogger().warning("Response " + response.id + " targets unknown repeat " + action.repeatId + " in quest " + quest.id);
                }
            }
            for (String tag : d.prerequisites) validateTag(tag, playerTagDefinitions, "player", d.id);
            for (String tag : d.serverPrerequisites) validateTag(tag, serverTagDefinitions, "server", d.id);
            for (String tag : d.addPlayerTags) validateTag(tag, playerTagDefinitions, "player", d.id + " effects");
            for (String tag : d.addServerTags) validateTag(tag, serverTagDefinitions, "server", d.id + " effects");
            validateJournalEffects(d.id, d.addJournal, d.removeJournal);
            ConfigurationSection dialogueSection = dialogueFile.getConfigurationSection(d.id);
            if (dialogueSection.contains("Reward") && dialogueSection.contains("rewards"))
                plugin.getLogger().warning("Dialogue " + d.id + " defines both Reward and rewards; only Reward is used.");
            Object rewardValue = dialogueSection.contains("Reward") ? dialogueSection.get("Reward") : dialogueSection.get("rewards");
            if (rewardValue != null && !(rewardValue instanceof List<?>))
                plugin.getLogger().warning("Dialogue " + d.id + " Reward must be a YAML list of Reward.yml IDs.");
            for (String rewardId : d.rewardIds) if (!rewards.containsKey(rewardId))
                plugin.getLogger().warning("Dialogue " + d.id + " references unknown reward " + rewardId);
        }
        for (ResponseDef r : responses.values()) {
            NextDialoguePlan nextPlan = parseNextDialogue(r.nextDialogue);
            if (nextPlan.error != null) plugin.getLogger().warning("Response " + r.id + " has invalid next-dialogue: " + nextPlan.error);
            else for (String nextId : nextPlan.ids) if (!dialogues.containsKey(nextId))
                plugin.getLogger().warning("Response " + r.id + " references unknown dialogue " + nextId);
            for (String tag : r.prerequisites) validateTag(tag, playerTagDefinitions, "player", r.id);
            for (String tag : r.serverPrerequisites) validateTag(tag, serverTagDefinitions, "server", r.id);
            for (String tag : r.addPlayerTags) validateTag(tag, playerTagDefinitions, "player", r.id + " effects");
            for (String tag : r.addServerTags) validateTag(tag, serverTagDefinitions, "server", r.id + " effects");
            validateJournalEffects(r.id, r.addJournal, r.removeJournal);
            for (String tag : r.removePlayerTags) validateTag(tag, playerTagDefinitions, "player", r.id + " effects");
            Set<String> tagOverlap = new HashSet<>(r.addPlayerTags); tagOverlap.retainAll(r.removePlayerTags);
            if (!tagOverlap.isEmpty()) plugin.getLogger().warning("Response " + r.id + " both adds and removes player tags " + tagOverlap);
            if (r.cooldownMillis == -2) plugin.getLogger().warning("Invalid cooldown for response " + r.id + "; cooldown disabled.");
        }
        for (Map.Entry<String, List<String>> reward : rewards.entrySet()) {
            if (reward.getValue().isEmpty()) plugin.getLogger().warning("Reward " + reward.getKey() + " contains no actions.");
            for (String action : reward.getValue()) {
                String problem = rewardActionProblem(action);
                if (problem != null) plugin.getLogger().warning("Reward " + reward.getKey() + " has invalid action '" + action + "': " + problem);
            }
        }
        for (Map.Entry<String, String> invalid : invalidRewardDefinitions.entrySet())
            plugin.getLogger().warning("Reward " + invalid.getKey() + " is invalid: " + invalid.getValue());
        for (NpcDef n : npcs.values()) {
            for (String id : n.dialogueIds) if (!dialogues.containsKey(id)) plugin.getLogger().warning("NPC " + n.id + " references unknown dialogue " + id);
            for (String tag : n.visibilityPrerequisites) validateTag(tag, playerTagDefinitions, "player", "NPC " + n.key + " visibility");
            if (!n.visibilityPrerequisites.isEmpty() && Bukkit.getPluginManager().getPlugin("Citizens") == null)
                plugin.getLogger().warning("NPC " + n.key + " uses visibility-prerequisites, but Citizens is unavailable; per-player visibility cannot be applied.");
            else if (!n.visibilityPrerequisites.isEmpty() && !citizensVisibilityApiAvailable())
                plugin.getLogger().warning("NPC " + n.key + " uses visibility-prerequisites, but the installed Citizens API lacks the required per-player visibility integration.");
        }
    }

    void validateObjectiveReferences() {
        for (ResponseDef response : responses.values()) {
            if (response.executeObjective.isBlank()) continue;
            if (!plugin.objectivesManager().isManuallyExecutable(response.executeObjective))
                plugin.getLogger().warning("Response " + response.id + " executeobjective must reference an existing objective with Manual: true: " + response.executeObjective);
        }
    }

    private void validateTag(String raw, Set<String> definitions, String kind, String owner) {
        if (raw == null || raw.isBlank()) return;
        String id = raw.startsWith("!") ? raw.substring(1).trim() : raw.trim();
        if (!definitions.contains(id)) plugin.getLogger().warning("Unknown " + kind + " tag '" + id + "' referenced by " + owner);
    }

    private void validateJournalEffects(String owner, List<String> addEntries, List<String> removeEntries) {
        for (String id : addEntries) if (!plugin.journalManager().contains(id))
            plugin.getLogger().warning("Dialogue/response " + owner + " references unknown Journal entry '" + id + "' in addjournal.");
        for (String id : removeEntries) if (!plugin.journalManager().contains(id))
            plugin.getLogger().warning("Dialogue/response " + owner + " references unknown Journal entry '" + id + "' in removejournal.");
    }

    private long parseCooldown(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equals("-1")) return -1;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = DURATION_PART.matcher(value);
        long millis = 0;
        int end = 0;
        while (matcher.find()) {
            if (!value.substring(end, matcher.start()).isBlank()) return -2;
            long amount;
            try { amount = Long.parseLong(matcher.group(1)); } catch (NumberFormatException ex) { return -2; }
            long factor = switch (matcher.group(2).toLowerCase(Locale.ROOT)) { case "h" -> 3_600_000L; case "m" -> 60_000L; default -> 1_000L; };
            try { millis = Math.addExact(millis, Math.multiplyExact(amount, factor)); } catch (ArithmeticException ex) { return -2; }
            end = matcher.end();
        }
        if (end != value.length() || end == 0) return -2;
        return millis;
    }

    private void migrateLegacyQuests(File dialoguePath, File responsePath, File npcPath) {
        YamlConfiguration ds = new YamlConfiguration();
        YamlConfiguration rs = new YamlConfiguration();
        YamlConfiguration ns = new YamlConfiguration();
        ds.options().header(DIALOGUE_HEADER);
        rs.options().header(RESPONSE_HEADER);
        Map<Integer, List<String>> npcDialogueIds = new LinkedHashMap<>();
        Collection<IekQuestsPlugin.Quest> quests = plugin.questsView();
        for (IekQuestsPlugin.Quest q : quests) {
            List<Integer> ids = new ArrayList<>();
            if (q.npcId >= 0) ids.add(q.npcId);
            for (int id : q.npcIds) if (id >= 0 && !ids.contains(id)) ids.add(id);
            if (ids.isEmpty() || q.stages.isEmpty()) continue;

            String startId = legacyId(q.id, "start");
            String doneId = legacyId(q.id, "done");
            IekQuestsPlugin.Stage firstStage = q.stages.values().iterator().next();
            List<String> startResponses = new ArrayList<>();
            boolean explicitStartChoice = false;
            for (int i = 0; i < firstStage.choices.size(); i++) {
                IekQuestsPlugin.Choice c = firstStage.choices.get(i);
                String action = c.action == null ? "" : c.action.toLowerCase(Locale.ROOT);
                if (List.of("start", "accept", "refuse", "close").contains(action)) explicitStartChoice = true;
                String rid = legacyId(q.id, "start_choice_" + i);
                String next = (action.equals("start") || action.equals("accept")) ? startId
                        : (c.gotoKey.isBlank() ? "" : legacyId(q.id, c.gotoKey));
                addLegacyChoiceResponse(rs, rid, q, c, next, true, i);
                if (action.equals("refuse") || action.equals("close") || c.close)
                    rs.set(rid + ".quest-actions", List.of(Map.of("quest-id", q.id, "action", "close")));
                startResponses.add(rid);
            }
            if (!explicitStartChoice) {
                String acceptId = legacyId(q.id, "accept");
                String refuseId = legacyId(q.id, "refuse");
                addQuestActionResponse(rs, acceptId, plugin.dialogueConfig("start", "&aYes, I want to begin"), q, "accept", "", "", startId);
                addQuestActionResponse(rs, refuseId, plugin.dialogueConfig("refuse", "&cNo, not now"), q, "refuse", "", "", "");
                startResponses.add(acceptId); startResponses.add(refuseId);
            }
            setDialogue(ds, startId, q, "not_started", "", firstStage.lines, q.startConds, startResponses);
            if (firstStage.voice != null && !firstStage.voice.isBlank()) ds.set(startId + ".voice", firstStage.voice);

            for (IekQuestsPlugin.Stage stage : q.stages.values()) {
                String did = legacyId(q.id, stage.key);
                List<String> stageResponses = new ArrayList<>();
                if (!stage.kind.equals("menu")) {
                    String advanceId = legacyId(q.id, stage.key + "_advance");
                    String nextStage = stage.gotoKey.isBlank() ? (stage.next.isBlank() ? nextStageKey(q, stage) : stage.next) : stage.gotoKey;
                    String nextDialogue = nextStage == null || nextStage.isBlank() ? doneId : legacyId(q.id, nextStage);
                    addQuestActionResponse(rs, advanceId, stage.label, q, "advance", "", "", nextDialogue);
                    stageResponses.add(advanceId);
                }
                for (int i = 0; i < stage.choices.size(); i++) {
                    IekQuestsPlugin.Choice c = stage.choices.get(i);
                    if (c.action.equalsIgnoreCase("start") || c.action.equalsIgnoreCase("accept")) continue;
                    String rid = legacyId(q.id, stage.key + "_choice_" + i);
                    String action = c.action.toLowerCase(Locale.ROOT);
                    String nativeAction = "none";
                    String target = "";
                    String nextDialogue = did;
                    if (action.equals("continue")) {
                        nativeAction = "advance";
                        String nextStage = stage.gotoKey.isBlank() ? (stage.next.isBlank() ? nextStageKey(q, stage) : stage.next) : stage.gotoKey;
                        nextDialogue = nextStage == null || nextStage.isBlank() ? doneId : legacyId(q.id, nextStage);
                    } else if (action.equals("refuse") || action.equals("close") || c.close) {
                        nativeAction = "close"; nextDialogue = "";
                    } else if (!c.gotoKey.isBlank()) {
                        nativeAction = "goto-stage"; target = c.gotoKey; nextDialogue = legacyId(q.id, target);
                    }
                    addLegacyChoiceResponse(rs, rid, q, c, nextDialogue, false, i);
                    if (!nativeAction.equals("none")) {
                        rs.set(rid + ".quest-actions", List.of(questActionEntry(q.id, nativeAction, target, "")));
                    }
                    if (!c.gotoKey.isBlank() && !nativeAction.equals("goto-stage") && !action.equals("continue")) nextDialogue = legacyId(q.id, c.gotoKey);
                    if (!nextDialogue.equals(rs.getString(rid + ".next-dialogue", ""))) rs.set(rid + ".next-dialogue", nextDialogue);
                    if (c.limitPerDay > 0) { rs.set(rid + ".limit-per-day", c.limitPerDay); rs.set(rid + ".limit-key", "dialogue:" + stage.key + ":" + i); }
                    stageResponses.add(rid);
                }
                setDialogue(ds, did, q, "active", stage.key, stage.lines, List.of(), stageResponses);
            }

            List<String> doneResponses = new ArrayList<>();
            if (q.repeats.isEmpty()) {
                String restartId = legacyId(q.id, "restart");
                addQuestActionResponse(rs, restartId, plugin.dialogueConfig("restart", "&aStart the quest again"),
                        q, "restart", "", "", startId);
                rs.set(restartId + ".quest-state", "completed");
                doneResponses.add(restartId);
            }
            for (IekQuestsPlugin.Repeat repeat : q.repeats) {
                String rid = legacyId(q.id, "repeat_" + repeat.id);
                addQuestActionResponse(rs, rid, repeat.label, q, "repeat-contract", "", repeat.id, "");
                rs.set(rid + ".quest-state", "completed");
                doneResponses.add(rid);
            }
            for (int i = 0; i < firstStage.choices.size(); i++) {
                IekQuestsPlugin.Choice c = firstStage.choices.get(i);
                if (c.action.equalsIgnoreCase("start") || c.action.equalsIgnoreCase("accept")) continue;
                String rid = legacyId(q.id, "done_choice_" + i);
                addLegacyChoiceResponse(rs, rid, q, c, c.gotoKey.isBlank() ? "" : legacyId(q.id, c.gotoKey), false, i);
                rs.set(rid + ".quest-state", "completed");
                if (!c.gotoKey.isBlank()) rs.set(rid + ".quest-actions", List.of(questActionEntry(q.id, "goto-stage", c.gotoKey, "")));
                doneResponses.add(rid);
            }
            List<String> doneLines = new ArrayList<>();
            doneLines.add("&aThe quest is complete!");
            for (String journal : q.journal) doneLines.add("&7" + journal);
            setDialogue(ds, doneId, q, "completed", "", doneLines, List.of(), doneResponses);

            for (int id : ids) {
                List<String> list = npcDialogueIds.computeIfAbsent(id, ignored -> new ArrayList<>());
                list.add(startId);
                for (IekQuestsPlugin.Stage stage : q.stages.values()) list.add(legacyId(q.id, stage.key));
                list.add(doneId);
            }
        }
        for (var entry : npcDialogueIds.entrySet()) {
            String key = "npc_" + entry.getKey();
            ns.set(key + ".display-name", "NPC " + entry.getKey());
            ns.set(key + ".npc-id", entry.getKey());
            ns.set(key + ".dialogues", entry.getValue());
        }
        save(ds, dialoguePath, "dialogues.yml");
        save(rs, responsePath, "responses.yml");
        save(ns, npcPath, "npcs.yml");
        plugin.getLogger().info("Migrated legacy NPC conversations into dialogues.yml/responses.yml for " + npcDialogueIds.size() + " Citizens NPC IDs.");
    }

    private void setDialogue(YamlConfiguration ds, String id, IekQuestsPlugin.Quest q, String state,
                             String stage, List<String> lines, List<IekQuestsPlugin.Cond> conditions, List<String> responseIds) {
        ds.set(id + ".title", q.title);
        ds.set(id + ".quest-id", q.id);
        ds.set(id + ".quest-state", state);
        if (!stage.isBlank()) ds.set(id + ".quest-stage", stage);
        ds.set(id + ".lines", lines);
        String voice = stageVoice(q, stage);
        if (!voice.isBlank()) ds.set(id + ".voice", voice);
        ds.set(id + ".quest-prerequisites", conditions.stream().map(c -> c.raw).toList());
        ds.set(id + ".responses", responseIds);
        ds.set(id + ".addjournal", List.of());
        ds.set(id + ".removejournal", List.of());
    }

    private void addLegacyChoiceResponse(YamlConfiguration rs, String id, IekQuestsPlugin.Quest q,
                                        IekQuestsPlugin.Choice c, String nextDialogue, boolean starting, int index) {
        rs.set(id + ".text", c.label);
        if (c.say != null && !c.say.isBlank()) rs.set(id + ".reply", c.say);
        rs.set(id + ".next-dialogue", nextDialogue);
        rs.set(id + ".addjournal", List.of());
        rs.set(id + ".removejournal", List.of());
        rs.set(id + ".executeobjective", "");
        rs.set(id + ".quest-id", q.id);
        rs.set(id + ".quest-prerequisites", c.conds.stream().map(x -> x.raw).toList());
        if (!c.doList.isEmpty()) rs.set(id + ".legacy-actions", c.doList);
        if (!c.voice.isBlank()) rs.set(id + ".voice", c.voice);
        if (starting && (c.action.equalsIgnoreCase("start") || c.action.equalsIgnoreCase("accept"))) {
            rs.set(id + ".quest-actions", List.of(Map.of("quest-id", q.id, "action", "accept")));
        }
        if (starting) rs.set(id + ".quest-state", "not_started");
    }

    private void addQuestActionResponse(YamlConfiguration rs, String id, String label, IekQuestsPlugin.Quest q,
                                        String action, String targetStage, String repeatId, String nextDialogue) {
        rs.set(id + ".text", label);
        rs.set(id + ".next-dialogue", nextDialogue);
        rs.set(id + ".addjournal", List.of());
        rs.set(id + ".removejournal", List.of());
        rs.set(id + ".executeobjective", "");
        rs.set(id + ".quest-id", q.id);
        rs.set(id + ".quest-actions", List.of(questActionEntry(q.id, action, targetStage, repeatId)));
    }

    private Map<String, String> questActionEntry(String questId, String action, String targetStage, String repeatId) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("quest-id", questId);
        entry.put("action", action);
        if ((action.equalsIgnoreCase("goto") || action.equalsIgnoreCase("goto-stage")) && targetStage != null && !targetStage.isBlank())
            entry.put("target-stage", targetStage);
        if ((action.equalsIgnoreCase("repeat") || action.equalsIgnoreCase("repeat-contract")) && repeatId != null && !repeatId.isBlank())
            entry.put("repeat-id", repeatId);
        return entry;
    }

    private String stageVoice(IekQuestsPlugin.Quest q, String stageKey) {
        IekQuestsPlugin.Stage stage = q.stages.get(stageKey);
        return stage == null || stage.voice == null ? "" : stage.voice;
    }

    private String nextStageKey(IekQuestsPlugin.Quest q, IekQuestsPlugin.Stage current) {
        boolean found = false;
        for (String key : q.stages.keySet()) {
            if (found) return key;
            if (key.equals(current.key)) found = true;
        }
        return "";
    }

    private String legacyId(String questId, String suffix) {
        return ("legacy_" + questId + "_" + suffix).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private void save(YamlConfiguration file, File target, String label) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create plugin data folder");
            plugin.questPackageManager().persistGeneratedEdit(target.getName(), file);
            file.save(target);
        } catch (Exception ex) { plugin.getLogger().severe("Could not save " + label + ": " + ex.getMessage()); }
    }

    void adminCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }
        try {
            String type = args[0].toLowerCase(Locale.ROOT);
            switch (type) {
                case "help" -> sendHelp(sender);
                case "reload" -> {
                    plugin.reloadDialogueConfiguration();
                    sender.sendMessage(Component.text("IekQuests dialogue files reloaded."));
                }
                case "list" -> list(sender, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all");
                case "validate" -> validate(sender, args.length > 1 ? args[1] : null);
                case "show" -> {
                    if (args.length < 3) throw new IllegalArgumentException("Usage: /iekdialogue show <dialogue|response|npc|tag> <id>.");
                    show(sender, args[1], args[2]);
                }
                case "delete" -> {
                    if (args.length < 3) throw new IllegalArgumentException("Usage: /iekdialogue delete <dialogue|response|npc|tag> <id>.");
                    delete(sender, args[1], args[2]);
                }
                case "npc" -> npcCommand(sender, args);
                case "tag" -> tagCommand(sender, args);
                case "cooldown" -> cooldownCommand(sender, args);
                case "test" -> {
                    if (!(sender instanceof Player player)) throw new IllegalArgumentException("Run this preview in game.");
                    if (args.length < 2) throw new IllegalArgumentException("Usage: /iekdialogue test <dialogue-id>.");
                    preview(player, args[1]);
                }
                case "events" -> plugin.eventsManager().command(sender, args);
                case "sequences" -> plugin.sequencesManager().command(sender, args);
                case "dialogue", "response" -> editCommand(sender, args, type.equals("dialogue"));
                default -> throw new IllegalArgumentException("Unknown subcommand. Use /iekdialogue help.");
            }
        } catch (Exception ex) {
            sender.sendMessage(Component.text("Dialogue command failed: " + ex.getMessage()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("/iekdialogue list [dialogue|response|npc|tag] | validate [id] | show <type> <id>"));
        sender.sendMessage(Component.text("/iekdialogue delete <dialogue|response|npc|tag> <id> | test <dialogue-id> | reload"));
        sender.sendMessage(Component.text("/iekdialogue dialogue|response create <id> | set <id> <field> <value>"));
        sender.sendMessage(Component.text("Dialogue/response fields addjournal and removejournal accept comma-separated Journal entry IDs."));
        sender.sendMessage(Component.text("Response remove-player-tags removes registered player tags after executeobjective succeeds; tags are removed before add-player-tags are assigned."));
        sender.sendMessage(Component.text("Use /iekdialogue sequences <list|show|validate> [id] to inspect Sequences.yml."));
        sender.sendMessage(Component.text("Dialogue field Reward accepts comma-separated IDs from Reward.yml and runs once each time the dialogue is opened."));
        sender.sendMessage(Component.text("Response next-dialogue supports one ID, ordered comma fallbacks, or a quoted [id; id] randomized group."));
        sender.sendMessage(Component.text("Response field executeobjective runs one Manual: true objective when selected."));
        sender.sendMessage(Component.text("/iekdialogue npc list|bind <key> <citizens-id> <display-name> <dialogue-id,...>|unbind <key-or-id>|visibility <key-or-id> <tag,...|none>"));
        sender.sendMessage(Component.text("/iekdialogue tag define player|server <id> | tag player list <player> | tag player add|remove <player> <id> | tag server add|remove <id>"));
        sender.sendMessage(Component.text("/iekdialogue cooldown clear <player> [response-id]"));
        sender.sendMessage(Component.text("/iekdialogue events <list|show|create|set|delete|validate|reload> ... | sequences <list|show|validate> [id]"));
    }

    private void editCommand(CommandSender sender, String[] args, boolean dialogue) {
        if (args.length < 3) throw new IllegalArgumentException("Use create or set.");
        String operation = args[1].toLowerCase(Locale.ROOT);
        String id = checkedId(args[2]);
        YamlConfiguration file = dialogue ? dialogueFile : responseFile;
        File target = new File(plugin.getDataFolder(), dialogue ? "dialogues.yml" : "responses.yml");
        if (operation.equals("create")) {
            if (dialogue ? responses.containsKey(id) : dialogues.containsKey(id))
                throw new IllegalArgumentException("ID is already used by the other graph entry type: " + id);
            if (file.isConfigurationSection(id)) throw new IllegalArgumentException("ID already exists: " + id);
            file.createSection(id);
            file.set(id + (dialogue ? ".lines" : ".text"), dialogue ? List.of("New dialogue line.") : "New response");
            file.set(id + (dialogue ? ".responses" : ".next-dialogue"), dialogue ? List.of() : "");
            file.set(id + ".addjournal", List.of());
            file.set(id + ".removejournal", List.of());
            file.set(id + ".sequences", List.of());
            if (!dialogue) file.set(id + ".remove-player-tags", List.of());
            if (dialogue) file.set(id + ".Reward", List.of());
            if (!dialogue) file.set(id + ".executeobjective", "");
        } else if (operation.equals("set")) {
            if (args.length < 5 || !file.isConfigurationSection(id)) throw new IllegalArgumentException("Unknown ID or missing field/value. Create the ID first.");
            String field = args[3].toLowerCase(Locale.ROOT);
            String value = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)).replace("\\n", "\n");
            String base = id + ".";
            switch (field) {
                case "title", "voice" -> file.set(base + field, value);
                case "lines" -> { if (!dialogue) throw new IllegalArgumentException("lines belongs to a dialogue."); file.set(base + "lines", List.of(value.split("\n", -1))); }
                case "text" -> { if (dialogue) throw new IllegalArgumentException("text belongs to a response."); file.set(base + "text", value); }
                case "responses", "prerequisites", "server-prerequisites", "commands", "add-player-tags", "remove-player-tags", "add-server-tags", "addjournal", "removejournal", "reward", "rewards", "sequences" -> {
                    if ((field.equals("reward") || field.equals("rewards")) && !dialogue)
                        throw new IllegalArgumentException("Reward belongs to a dialogue.");
                    if (field.equals("remove-player-tags") && dialogue) throw new IllegalArgumentException("remove-player-tags belongs to a response.");
                    List<String> values = csv(value);
                    if (field.equals("responses")) for (String responseId : values)
                        if (!responses.containsKey(responseId)) throw new IllegalArgumentException("Unknown response ID: " + responseId);
                    if (field.equals("addjournal") || field.equals("removejournal")) for (String journalId : values)
                        if (!plugin.journalManager().contains(journalId)) throw new IllegalArgumentException("Unknown Journal entry ID: " + journalId);
                    if (field.equals("remove-player-tags")) for (String tag : values)
                        if (tag.startsWith("!") || !playerTagDefinitions.contains(tag)) throw new IllegalArgumentException("Invalid or unknown player tag: " + tag);
                    if (field.equals("add-player-tags") || field.equals("remove-player-tags")) {
                        List<String> other = file.getStringList(base + (field.equals("add-player-tags") ? "remove-player-tags" : "add-player-tags"));
                        Set<String> overlap = new HashSet<>(values); overlap.retainAll(other);
                        if (!overlap.isEmpty()) throw new IllegalArgumentException("A response cannot both add and remove player tags: " + String.join(", ", overlap));
                    }
                    if (field.equals("sequences")) for (String sequence : values)
                        if (!plugin.sequencesManager().ids().contains(sequence)) throw new IllegalArgumentException("Unknown sequence ID: " + sequence);
                    if (field.equals("prerequisites") || field.equals("add-player-tags"))
                        for (String tag : values) {
                            if ((field.equals("add-player-tags") && tag.startsWith("!")) || !playerTagDefinitions.contains(stripNegation(tag)))
                                throw new IllegalArgumentException("Invalid or unknown player tag: " + tag);
                        }
                    if (field.equals("server-prerequisites") || field.equals("add-server-tags"))
                        for (String tag : values) {
                            if ((field.equals("add-server-tags") && tag.startsWith("!")) || !serverTagDefinitions.contains(stripNegation(tag)))
                                throw new IllegalArgumentException("Invalid or unknown server tag: " + tag);
                        }
                    if (field.equals("reward") || field.equals("rewards")) {
                        for (String rewardId : values) if (!rewards.containsKey(rewardId))
                            throw new IllegalArgumentException("Unknown reward ID: " + rewardId);
                        file.set(base + "Reward", values);
                        file.set(base + "rewards", null);
                    } else file.set(base + field, values);
                }
                case "next-dialogue" -> {
                    if (dialogue) throw new IllegalArgumentException("next-dialogue belongs to a response.");
                    if (value.equalsIgnoreCase("none")) value = "";
                    NextDialoguePlan plan = parseNextDialogue(value);
                    if (plan.error != null) throw new IllegalArgumentException("Invalid next-dialogue: " + plan.error);
                    for (String nextId : plan.ids) if (!dialogues.containsKey(nextId))
                        throw new IllegalArgumentException("Unknown dialogue ID: " + nextId);
                    file.set(base + field, value);
                }
                case "per-player-cooldown" -> {
                    if (dialogue || parseCooldown(value) == -2) throw new IllegalArgumentException("Invalid cooldown.");
                    file.set(base + field, value);
                }
                case "executeobjective" -> {
                    if (dialogue) throw new IllegalArgumentException("executeobjective belongs to a response.");
                    String objectiveId = value.equalsIgnoreCase("none") ? "" : checkedId(value);
                    if (!objectiveId.isBlank() && !plugin.objectivesManager().isManuallyExecutable(objectiveId))
                        throw new IllegalArgumentException("executeobjective must reference an existing objective with Manual: true.");
                    file.set(base + field, objectiveId);
                }
                case "quest-action" -> appendQuestAction(file, base, value);
                case "quest-actions" -> replaceQuestActions(file, base, value);
                default -> throw new IllegalArgumentException("Unsupported field: " + field);
            }
        } else throw new IllegalArgumentException("Use create or set.");
        save(file, target, target.getName());
        load();
        sender.sendMessage(Component.text((dialogue ? "Dialogue" : "Response") + " saved: " + id));
    }

    private void appendQuestAction(YamlConfiguration file, String base, String value) {
        String[] parts = value.split(":", -1);
        if (parts.length < 2) throw new IllegalArgumentException("Use quest-id:action[:target-stage][:repeat-id].");
        IekQuestsPlugin.Quest quest = plugin.questById(parts[0]);
        if (quest == null) throw new IllegalArgumentException("Unknown quest ID: " + parts[0]);
        String action = parts[1].toLowerCase(Locale.ROOT);
        Set<String> allowed = Set.of("accept", "refuse", "advance", "turn-in", "goto-stage", "complete", "restart", "repeat-contract", "run-rewards");
        if (!allowed.contains(action)) throw new IllegalArgumentException("Unsupported quest action: " + action);
        if (action.equals("goto-stage") && (parts.length < 3 || !quest.stages.containsKey(parts[2]))) throw new IllegalArgumentException("goto-stage requires a valid target stage.");
        if (action.equals("repeat-contract") && (parts.length < 4 || !hasRepeat(quest, parts[3]))) throw new IllegalArgumentException("repeat-contract requires a valid repeat ID.");
        String targetStage = parts.length > 2 ? parts[2] : "";
        String repeatId = parts.length > 3 ? parts[3] : "";
        Map<String, String> entry = questActionEntry(parts[0], action, targetStage, repeatId);
        List<Map<String, String>> values = new ArrayList<>();
        Object current = file.get(base + "quest-actions");
        if (current instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> map) {
            Map<String, String> copy = new LinkedHashMap<>();
            map.forEach((key, valueObject) -> copy.put(String.valueOf(key), String.valueOf(valueObject)));
            values.add(copy);
        }
        values.add(entry);
        file.set(base + "quest-actions", values);
    }

    private void replaceQuestActions(YamlConfiguration file, String base, String value) {
        List<Map<String, String>> values = new ArrayList<>();
        if (value != null && !value.isBlank() && !value.equalsIgnoreCase("none")) {
            for (String action : value.split("\\|")) {
                String[] parts = action.trim().split(":", -1);
                if (parts.length < 2) throw new IllegalArgumentException("Each action must use quest-id:action[:target-stage][:repeat-id], separated with |.");
                IekQuestsPlugin.Quest quest = plugin.questById(parts[0]);
                if (quest == null) throw new IllegalArgumentException("Unknown quest ID: " + parts[0]);
                String verb = parts[1].toLowerCase(Locale.ROOT);
                Set<String> allowed = Set.of("accept", "refuse", "advance", "turn-in", "goto-stage", "complete", "restart", "repeat-contract", "run-rewards");
                if (!allowed.contains(verb)) throw new IllegalArgumentException("Unsupported quest action: " + verb);
                if (verb.equals("goto-stage") && (parts.length < 3 || !quest.stages.containsKey(parts[2])))
                    throw new IllegalArgumentException("goto-stage requires a valid target stage.");
                if (verb.equals("repeat-contract") && (parts.length < 4 || !hasRepeat(quest, parts[3])))
                    throw new IllegalArgumentException("repeat-contract requires a valid repeat ID.");
                String targetStage = parts.length > 2 ? parts[2] : "";
                String repeatId = parts.length > 3 ? parts[3] : "";
                Map<String, String> entry = questActionEntry(parts[0], verb, targetStage, repeatId);
                values.add(entry);
            }
        }
        file.set(base + "quest-actions", values);
    }

    private void list(CommandSender sender, String type) {
        switch (type) {
            case "all" -> {
                sender.sendMessage(Component.text("Dialogues: " + String.join(", ", dialogues.keySet())));
                sender.sendMessage(Component.text("Responses: " + String.join(", ", responses.keySet())));
                sender.sendMessage(Component.text("NPC bindings: " + npcs.values().stream().map(n -> n.key + "=" + n.id).toList()));
                sender.sendMessage(Component.text("Player tags: " + String.join(", ", playerTagDefinitions)));
                sender.sendMessage(Component.text("Server tags: " + String.join(", ", serverTagDefinitions)));
                sender.sendMessage(Component.text("Sequences: " + String.join(", ", plugin.sequencesManager().ids())));
            }
            case "dialogue" -> sender.sendMessage(Component.text("Dialogues: " + String.join(", ", dialogues.keySet())));
            case "response" -> sender.sendMessage(Component.text("Responses: " + String.join(", ", responses.keySet())));
            case "sequence", "sequences" -> sender.sendMessage(Component.text("Sequences: " + String.join(", ", plugin.sequencesManager().ids())));
            case "npc", "npcs" -> sender.sendMessage(Component.text("NPC bindings: " + npcs.values().stream().map(n -> n.key + "=" + n.id).toList()));
            case "tag", "tags" -> {
                sender.sendMessage(Component.text("Player tags: " + String.join(", ", playerTagDefinitions)));
                sender.sendMessage(Component.text("Server tags: " + String.join(", ", serverTagDefinitions)));
            }
            default -> throw new IllegalArgumentException("Type must be dialogue, response, npc, tag, or sequence.");
        }
    }

    private void show(CommandSender sender, String rawType, String id) {
        String type = rawType.toLowerCase(Locale.ROOT);
        ConfigurationSection section = null;
        switch (type) {
            case "dialogue", "dialogues" -> section = dialogueFile.getConfigurationSection(id);
            case "response", "responses" -> section = responseFile.getConfigurationSection(id);
            case "npc", "npcs" -> {
                String key = findNpcKey(id);
                section = key == null ? null : npcSection(key);
                if (section != null) id = key;
            }
            case "sequence", "sequences" -> { plugin.sequencesManager().command(sender, new String[]{"sequences", "show", id}); return; }
            case "tag", "tags" -> {
                boolean player = playerTagDefinitions.contains(id);
                if (!player && !serverTagDefinitions.contains(id)) throw new IllegalArgumentException("Unknown tag ID: " + id);
                List<String> assigned = player ? assignedPlayerTags() : runtimeData().getStringList("dialogue.server-tags");
                long count = player ? playerTagAssignmentCount(id) : (assigned.contains(id) ? 1 : 0);
                sender.sendMessage(Component.text((player ? "Player" : "Server") + " tag " + id + "; assigned to " + count + " player(s)/server state(s)."));
                return;
            }
            default -> throw new IllegalArgumentException("Type must be dialogue, response, npc, tag, or sequence.");
        }
        if (section == null) throw new IllegalArgumentException("Unknown " + type + " ID: " + id);
        sender.sendMessage(Component.text(type + " " + id + ":"));
        for (String line : section.getValues(true).entrySet().stream().map(e -> e.getKey() + ": " + String.valueOf(e.getValue())).toList())
            sender.sendMessage(Component.text("  " + line));
    }

    private void delete(CommandSender sender, String rawType, String id) {
        String type = rawType.toLowerCase(Locale.ROOT);
        switch (type) {
            case "dialogue" -> {
                if (!dialogues.containsKey(id)) throw new IllegalArgumentException("Unknown dialogue ID: " + id);
                List<String> refs = new ArrayList<>();
                for (NpcDef n : npcs.values()) if (n.dialogueIds.contains(id)) refs.add("NPC " + n.key);
                for (ResponseDef r : responses.values()) if (parseNextDialogue(r.nextDialogue).ids.contains(id)) refs.add("response " + r.id);
                if (!refs.isEmpty()) throw new IllegalArgumentException("Referenced by " + String.join(", ", refs));
                dialogueFile.set(id, null); save(dialogueFile, file("dialogues.yml"), "dialogues.yml");
            }
            case "response" -> {
                if (!responses.containsKey(id)) throw new IllegalArgumentException("Unknown response ID: " + id);
                List<String> refs = new ArrayList<>();
                for (DialogueDef d : dialogues.values()) if (d.responseIds.contains(id)) refs.add("dialogue " + d.id);
                if (!refs.isEmpty()) throw new IllegalArgumentException("Referenced by " + String.join(", ", refs));
                responseFile.set(id, null); clearResponseCooldowns(id); save(responseFile, file("responses.yml"), "responses.yml");
            }
            case "npc", "npcs" -> {
                String key = findNpcKey(id);
                if (key == null) throw new IllegalArgumentException("Unknown NPC binding: " + id);
                npcFile.set(npcDataPath(key), null); save(npcFile, file("npcs.yml"), "npcs.yml");
            }
            case "tag", "tags" -> {
                boolean player = playerTagDefinitions.contains(id);
                if (!player && !serverTagDefinitions.contains(id)) throw new IllegalArgumentException("Unknown tag ID: " + id);
                List<String> refs = tagReferences(id, player);
                if (player && playerTagAssignmentCount(id) > 0) refs.add("player assignments");
                if (!player && runtimeData().getStringList("dialogue.server-tags").contains(id)) refs.add("server assignment");
                if (!refs.isEmpty()) throw new IllegalArgumentException("Tag is still in use: " + String.join(", ", refs));
                YamlConfiguration registry = player ? playerTagFile : serverTagFile;
                List<String> tags = new ArrayList<>(registry.getStringList("tags")); tags.remove(id); registry.set("tags", tags);
                save(registry, file(player ? "player-tags.yml" : "server-tags.yml"), "tag registry");
            }
            default -> throw new IllegalArgumentException("Type must be dialogue, response, npc, tag, or sequence.");
        }
        load();
        sender.sendMessage(Component.text("Deleted " + type + " " + id + "."));
    }

    private List<String> tagReferences(String id, boolean player) {
        List<String> refs = new ArrayList<>();
        for (DialogueDef d : dialogues.values()) {
            List<String> prereqs = player ? d.prerequisites : d.serverPrerequisites;
            List<String> added = player ? d.addPlayerTags : d.addServerTags;
            if (containsTag(prereqs, id) || containsTag(added, id)) refs.add("dialogue " + d.id);
        }
        for (ResponseDef r : responses.values()) {
            List<String> prereqs = player ? r.prerequisites : r.serverPrerequisites;
            List<String> added = player ? r.addPlayerTags : r.addServerTags;
            if (containsTag(prereqs, id) || containsTag(added, id)) refs.add("response " + r.id);
        }
        return refs;
    }

    private boolean containsTag(List<String> list, String id) {
        return list.stream().anyMatch(value -> stripNegation(value).equals(id));
    }

    private String stripNegation(String value) { return value != null && value.startsWith("!") ? value.substring(1).trim() : value == null ? "" : value.trim(); }

    private void npcCommand(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Use /iekdialogue npc list|bind|unbind.");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender, "npc");
            case "bind" -> {
                if (args.length < 6) throw new IllegalArgumentException("Usage: /iekdialogue npc bind <key> <citizens-id> <display-name> <dialogue-id,...>.");
                String key = checkedId(args[2]);
                int npcId = Integer.parseInt(args[3]);
                if (npcId < 0 || !citizensNpcExists(npcId)) throw new IllegalArgumentException("Citizens NPC ID does not exist or Citizens is unavailable.");
                String displayName = args[4];
                List<String> starts = csv(String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)));
                if (starts.isEmpty()) throw new IllegalArgumentException("At least one starting dialogue ID is required.");
                for (String dialogueId : starts) if (!dialogues.containsKey(dialogueId)) throw new IllegalArgumentException("Unknown dialogue ID: " + dialogueId);
                String path = npcDataPath(key);
                String oldKey = findNpcKey(String.valueOf(npcId));
                if (oldKey != null && !oldKey.equals(key)) throw new IllegalArgumentException("Citizens NPC ID is already bound as " + oldKey + ".");
                npcFile.set(path + "display-name", displayName); npcFile.set(path + "npc-id", npcId); npcFile.set(path + "dialogues", starts);
                save(npcFile, file("npcs.yml"), "npcs.yml"); load();
                sender.sendMessage(Component.text("Saved NPC binding " + key + " for Citizens ID " + npcId + "."));
            }
            case "unbind" -> {
                if (args.length < 3) throw new IllegalArgumentException("Usage: /iekdialogue npc unbind <key-or-id>.");
                String key = findNpcKey(args[2]);
                if (key == null) throw new IllegalArgumentException("Unknown NPC binding: " + args[2]);
                npcFile.set(npcDataPath(key), null); save(npcFile, file("npcs.yml"), "npcs.yml"); load();
                sender.sendMessage(Component.text("Removed NPC binding " + key + "."));
            }
            case "visibility" -> {
                if (args.length < 4) throw new IllegalArgumentException("Usage: /iekdialogue npc visibility <key-or-id> <tag,...|none>.");
                String key = findNpcKey(args[2]);
                if (key == null) throw new IllegalArgumentException("Unknown NPC binding: " + args[2]);
                List<String> tags = csv(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                for (String tag : tags) if (!playerTagDefinitions.contains(stripNegation(tag)))
                    throw new IllegalArgumentException("Unknown visibility player tag: " + tag);
                npcFile.set(npcDataPath(key) + ".visibility-prerequisites", tags);
                save(npcFile, file("npcs.yml"), "npcs.yml"); load();
                sender.sendMessage(Component.text("Saved visibility prerequisites for NPC binding " + key + "."));
            }
            default -> throw new IllegalArgumentException("Use /iekdialogue npc list|bind|unbind|visibility.");
        }
    }

    private void tagCommand(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Use /iekdialogue tag define|player|server.");
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("define")) {
            if (args.length != 4) throw new IllegalArgumentException("Usage: /iekdialogue tag define <player|server> <tag-id>.");
            boolean player = args[2].equalsIgnoreCase("player");
            if (!player && !args[2].equalsIgnoreCase("server")) throw new IllegalArgumentException("Tag type must be player or server.");
            String tag = checkedId(args[3]);
            Set<String> definitions = player ? playerTagDefinitions : serverTagDefinitions;
            if (definitions.contains(tag)) throw new IllegalArgumentException("Tag already exists: " + tag);
            YamlConfiguration registry = player ? playerTagFile : serverTagFile;
            List<String> tags = new ArrayList<>(registry.getStringList("tags")); tags.add(tag); registry.set("tags", tags);
            save(registry, file(player ? "player-tags.yml" : "server-tags.yml"), "tag registry"); load();
            sender.sendMessage(Component.text("Defined " + mode + " tag " + tag + "."));
            return;
        }
        if (mode.equals("player")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("list")) {
                if (args.length != 4) throw new IllegalArgumentException("Usage: /iekdialogue tag player list <player>.");
                org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    target = Bukkit.getOfflinePlayer(args[3]);
                    if (!target.hasPlayedBefore()) throw new IllegalArgumentException("Player is not online and has no cached UUID.");
                }
                List<String> assigned = runtimeData().getStringList("dialogue.player-tags." + target.getUniqueId());
                String targetName = target.getName() == null ? args[3] : target.getName();
                sender.sendMessage(Component.text("Assigned player tags for " + targetName + ":"));
                if (assigned.isEmpty()) {
                    sender.sendMessage(Component.text("- None"));
                } else {
                    for (String tag : assigned) sender.sendMessage(Component.text("- " + tag));
                }
                return;
            }
            if (args.length != 5) throw new IllegalArgumentException("Usage: /iekdialogue tag player add|remove <player> <tag-id>.");
            boolean add = args[2].equalsIgnoreCase("add");
            if (!add && !args[2].equalsIgnoreCase("remove")) throw new IllegalArgumentException("Use add or remove.");
            String tag = args[4]; if (!playerTagDefinitions.contains(tag)) throw new IllegalArgumentException("Unknown player tag: " + tag);
            org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[3]);
                if (!target.hasPlayedBefore()) throw new IllegalArgumentException("Player is not online and has no cached UUID.");
            }
            String path = "dialogue.player-tags." + target.getUniqueId();
            List<String> values = new ArrayList<>(runtimeData().getStringList(path));
            if (add && !values.contains(tag)) values.add(tag);
            if (!add) values.remove(tag);
            runtimeData().set(path, values); plugin.save();
            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId()); if (onlineTarget != null) refreshNpcVisibility(onlineTarget);
            sender.sendMessage(Component.text((add ? "Assigned " : "Removed ") + tag + (add ? " to " : " from ") + target.getName() + "."));
            return;
        }
        if (mode.equals("server")) {
            if (args.length != 4) throw new IllegalArgumentException("Usage: /iekdialogue tag server add|remove <tag-id>.");
            boolean add = args[2].equalsIgnoreCase("add");
            if (!add && !args[2].equalsIgnoreCase("remove")) throw new IllegalArgumentException("Use add or remove.");
            String tag = args[3]; if (!serverTagDefinitions.contains(tag)) throw new IllegalArgumentException("Unknown server tag: " + tag);
            List<String> values = new ArrayList<>(runtimeData().getStringList("dialogue.server-tags"));
            if (add && !values.contains(tag)) values.add(tag);
            if (!add) values.remove(tag);
            runtimeData().set("dialogue.server-tags", values); plugin.save();
            sender.sendMessage(Component.text((add ? "Assigned server tag " : "Removed server tag ") + tag + "."));
            return;
        }
        throw new IllegalArgumentException("Use /iekdialogue tag define|player|server.");
    }

    private void cooldownCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) throw new IllegalArgumentException("Usage: /iekdialogue cooldown clear <player> [response-id].");
        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore()) throw new IllegalArgumentException("Player is not online and has no cached UUID.");
        }
        String path = "dialogue.cooldowns." + target.getUniqueId();
        if (args.length > 3) {
            String response = args[3];
            if (!runtimeData().contains(path + "." + response)) throw new IllegalArgumentException("No cooldown stored for that response.");
            runtimeData().set(path + "." + response, null);
        } else runtimeData().set(path, null);
        plugin.save(); sender.sendMessage(Component.text("Cleared dialogue cooldowns for " + target.getName() + "."));
    }

    private void clearResponseCooldowns(String id) {
        ConfigurationSection root = runtimeData().getConfigurationSection("dialogue.cooldowns");
        if (root == null) return;
        for (String uuid : root.getKeys(false)) runtimeData().set("dialogue.cooldowns." + uuid + "." + id, null);
        plugin.save();
    }

    private long playerTagAssignmentCount(String tag) {
        ConfigurationSection root = runtimeData().getConfigurationSection("dialogue.player-tags");
        if (root == null) return 0;
        return root.getKeys(false).stream().filter(uuid -> root.getStringList(uuid).contains(tag)).count();
    }

    private List<String> assignedPlayerTags() {
        ConfigurationSection root = runtimeData().getConfigurationSection("dialogue.player-tags");
        if (root == null) return List.of();
        return root.getKeys(false).stream().flatMap(uuid -> root.getStringList(uuid).stream()).distinct().toList();
    }

    private String findNpcKey(String keyOrId) {
        if (npcFile.getConfigurationSection(npcDataPath(keyOrId)) != null) return keyOrId;
        try {
            int id = Integer.parseInt(keyOrId);
            for (NpcDef npc : npcs.values()) if (npc.id == id) return npc.key;
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private ConfigurationSection npcSection(String key) { return npcFile.getConfigurationSection(npcDataPath(key)); }
    private String npcDataPath(String key) { return npcFile.getConfigurationSection("npcs") == null ? key : "npcs." + key; }
    private File file(String name) { return plugin.organizationFile(name); }

    private boolean citizensNpcExists(int id) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Citizens") == null) return false;
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            return registry.getClass().getMethod("getById", int.class).invoke(registry, id) != null;
        } catch (Exception ignored) { return false; }
    }

    boolean canPlayerSeeNpc(Player player, int npcId) {
        NpcDef npc = npcs.get(npcId);
        return npc != null && tagsPass(player, npc.visibilityPrerequisites, true);
    }

    void refreshNpcVisibility(Player player) {
        if (player == null || !player.isOnline()) return;
        Set<Integer> all = new HashSet<>(visibilityControlledNpcIds);
        for (Integer npcId : all) {
            Entity entity = citizensEntity(npcId);
            if (entity == null || !entity.isValid()) continue;
            NpcDef npc = npcs.get(npcId);
            if (npc != null && canPlayerSeeNpc(player, npcId)) player.showEntity(plugin, entity);
            else player.hideEntity(plugin, entity);
        }
    }

    private void refreshAllNpcVisibility(Set<Integer> previouslyManaged) {
        Set<Integer> all = new HashSet<>(previouslyManaged);
        all.addAll(visibilityControlledNpcIds);
        for (Integer npcId : all) {
            Entity entity = citizensEntity(npcId);
            if (entity == null) continue;
            NpcDef npc = npcs.get(npcId);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (npc != null && !npc.visibilityPrerequisites.isEmpty() && !canPlayerSeeNpc(player, npcId))
                    player.hideEntity(plugin, entity);
                else player.showEntity(plugin, entity);
            }
        }
    }

    private Entity citizensEntity(int id) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Citizens") == null) return null;
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            Class<?> registryApi = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            Object npc = registryApi.getMethod("getById", int.class).invoke(registry, id);
            if (npc == null) return null;
            Class<?> npcApi = Class.forName("net.citizensnpcs.api.npc.NPC");
            Object entity = npcApi.getMethod("getEntity").invoke(npc);
            return entity instanceof Entity bukkitEntity ? bukkitEntity : null;
        } catch (Exception ex) { return null; }
    }

    private boolean citizensVisibilityApiAvailable() {
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Class<?> registry = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            Class<?> npc = Class.forName("net.citizensnpcs.api.npc.NPC");
            Class.forName("net.citizensnpcs.api.event.NPCSpawnEvent");
            api.getMethod("getNPCRegistry");
            registry.getMethod("getById", int.class);
            npc.getMethod("getEntity");
            npc.getMethod("getId");
            return true;
        } catch (ReflectiveOperationException ex) { return false; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerCitizensSpawnListener() {
        if (citizensSpawnListenerRegistered || Bukkit.getPluginManager().getPlugin("Citizens") == null
                || visibilityControlledNpcIds.isEmpty()) return;
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCSpawnEvent");
            org.bukkit.event.Listener listener = new org.bukkit.event.Listener() {};
            EventExecutor executor = (ignored, event) -> {
                try {
                    Object npc = event.getClass().getMethod("getNPC").invoke(event);
                    Class<?> npcApi = Class.forName("net.citizensnpcs.api.npc.NPC");
                    int id = (int) npcApi.getMethod("getId").invoke(npc);
                    if (!visibilityControlledNpcIds.contains(id)) return;
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refreshNpcVisibility));
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Could not read Citizens NPC spawn event: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true);
            citizensSpawnListenerRegistered = true;
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("Citizens is installed but NPCSpawnEvent is unavailable; NPC visibility will refresh on join, tag changes, and reload only.");
        }
    }

    private String checkedId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("IDs may contain only letters, numbers, underscores, and hyphens.");
        return id;
    }

    private void preview(Player player, String id) {
        DialogueDef dialogue = dialogues.get(id);
        if (dialogue == null) throw new IllegalArgumentException("Unknown dialogue ID: " + id);
        if (sessions.containsKey(player.getUniqueId())) throw new IllegalArgumentException("Close the current conversation before opening a preview.");
        Session session = new Session(player.getUniqueId(), -1, "Preview", nextSessionId++);
        session.preview = true; session.dialogueId = id;
        sessions.put(player.getUniqueId(), session);
        render(player, session);
        player.sendMessage(Component.text("Admin preview only: clicking a response has no effects."));
    }

    private void validate(CommandSender sender, String onlyId) {
        List<String> findings = new ArrayList<>();
        Set<String> dialogueScope = new HashSet<>();
        Set<String> responseScope = new HashSet<>();
        if (onlyId == null) {
            dialogueScope.addAll(dialogues.keySet());
            responseScope.addAll(responses.keySet());
        } else {
            if (dialogues.containsKey(onlyId)) dialogueScope.add(onlyId);
            if (responses.containsKey(onlyId)) responseScope.add(onlyId);
            String npcKey = findNpcKey(onlyId);
            if (npcKey != null) {
                NpcDef npc = npcs.values().stream().filter(n -> n.key.equals(npcKey)).findFirst().orElse(null);
                if (npc != null) dialogueScope.addAll(npc.dialogueIds);
            }
            boolean expanded;
            do {
                expanded = false;
                for (String dialogueId : new ArrayList<>(dialogueScope)) {
                    DialogueDef dialogue = dialogues.get(dialogueId);
                    if (dialogue == null) continue;
                    for (String responseId : dialogue.responseIds) if (responseScope.add(responseId)) expanded = true;
                }
                for (String responseId : new ArrayList<>(responseScope)) {
                    ResponseDef response = responses.get(responseId);
                    if (response != null) for (String nextId : parseNextDialogue(response.nextDialogue).ids)
                        if (dialogueScope.add(nextId)) expanded = true;
                }
            } while (expanded);
        }
        Set<String> known = new HashSet<>();
        known.addAll(dialogues.keySet()); known.addAll(responses.keySet());
        npcs.values().forEach(n -> { known.add(n.key); known.add(String.valueOf(n.id)); });
        known.addAll(playerTagDefinitions); known.addAll(serverTagDefinitions);
        known.addAll(rewards.keySet());
        known.addAll(invalidRewardDefinitions.keySet());
        known.addAll(plugin.sequencesManager().ids());
        if (onlyId != null && !known.contains(onlyId)) findings.add("ERROR: Unknown dialogue/configuration ID " + onlyId);
        if (onlyId == null) for (String id : dialogues.keySet()) if (responses.containsKey(id))
            findings.add("ERROR: ID " + id + " is defined as both a dialogue and a response.");
        for (DialogueDef d : dialogues.values()) {
            if (!dialogueScope.contains(d.id)) continue;
            if (!validId(d.id)) findings.add("ERROR: Invalid dialogue ID " + d.id);
            checkTags(findings, d.id, d.prerequisites, playerTagDefinitions, "player");
            checkTags(findings, d.id, d.serverPrerequisites, serverTagDefinitions, "server");
            checkTags(findings, d.id, d.addPlayerTags, playerTagDefinitions, "player effect");
            checkTags(findings, d.id, d.addServerTags, serverTagDefinitions, "server effect");
            checkJournalEffects(findings, d.id, d.addJournal, d.removeJournal);
            ConfigurationSection dialogueSection = dialogueFile.getConfigurationSection(d.id);
            if (dialogueSection.contains("Reward") && dialogueSection.contains("rewards"))
                findings.add("ERROR: Dialogue " + d.id + " defines both Reward and rewards; keep only Reward.");
            Object rewardValue = dialogueSection.contains("Reward") ? dialogueSection.get("Reward") : dialogueSection.get("rewards");
            if (rewardValue != null && !(rewardValue instanceof List<?>))
                findings.add("ERROR: Dialogue " + d.id + " Reward must be a YAML list of Reward.yml IDs.");
            for (String rewardId : d.rewardIds) if (!rewards.containsKey(rewardId))
                findings.add("ERROR: Dialogue " + d.id + " references unknown reward " + rewardId);
            checkCommands(findings, d.id, d.commands);
            if (d.malformedSequenceIds) findings.add("ERROR: Dialogue " + d.id + " sequences must be a YAML list of strings.");
            for (String sequence : d.sequenceIds) if (!plugin.sequencesManager().ids().contains(sequence)) findings.add("ERROR: Dialogue " + d.id + " references unknown sequence " + sequence);
            for (String response : d.responseIds) if (!responses.containsKey(response)) findings.add("ERROR: Dialogue " + d.id + " references unknown response " + response);
            if (!d.questId.isBlank() && plugin.questById(d.questId) == null) findings.add("ERROR: Dialogue " + d.id + " references unknown quest " + d.questId);
        }
        for (ResponseDef r : responses.values()) {
            if (!responseScope.contains(r.id)) continue;
            if (!validId(r.id)) findings.add("ERROR: Invalid response ID " + r.id);
            checkTags(findings, r.id, r.prerequisites, playerTagDefinitions, "player");
            checkTags(findings, r.id, r.serverPrerequisites, serverTagDefinitions, "server");
            checkTags(findings, r.id, r.addPlayerTags, playerTagDefinitions, "player effect");
            checkTags(findings, r.id, r.addServerTags, serverTagDefinitions, "server effect");
            for (String tag : r.removePlayerTags) {
                if (tag.startsWith("!")) findings.add("ERROR: Response " + r.id + " remove-player-tags cannot use negated tag " + tag);
                if (!playerTagDefinitions.contains(tag)) findings.add("ERROR: Response " + r.id + " references unknown player tag " + tag + " in remove-player-tags");
            }
            if (r.malformedSequenceIds) findings.add("ERROR: Response " + r.id + " sequences must be a YAML list of strings.");
            if (r.malformedRemovePlayerTags) findings.add("ERROR: Response " + r.id + " remove-player-tags must be a YAML list of strings.");
            Set<String> bothTagEffects = new HashSet<>(r.addPlayerTags); bothTagEffects.retainAll(r.removePlayerTags);
            for (String tag : bothTagEffects) findings.add("ERROR: Response " + r.id + " both adds and removes player tag " + tag);
            for (String sequence : r.sequenceIds) if (!plugin.sequencesManager().ids().contains(sequence)) findings.add("ERROR: Response " + r.id + " references unknown sequence " + sequence);
            checkJournalEffects(findings, r.id, r.addJournal, r.removeJournal);
            checkCommands(findings, r.id, r.commands);
            if (!r.executeObjective.isBlank() && !plugin.objectivesManager().isManuallyExecutable(r.executeObjective))
                findings.add("ERROR: Response " + r.id + " executeobjective must reference an existing objective with Manual: true: " + r.executeObjective);
            if (r.cooldownMillis == -2) findings.add("ERROR: Invalid cooldown in response " + r.id);
            NextDialoguePlan nextPlan = parseNextDialogue(r.nextDialogue);
            if (nextPlan.error != null) findings.add("ERROR: Response " + r.id + " has invalid next-dialogue: " + nextPlan.error);
            else for (String nextId : nextPlan.ids) if (!dialogues.containsKey(nextId))
                findings.add("ERROR: Response " + r.id + " references unknown dialogue " + nextId);
            if (!r.executeObjective.isBlank()) {
                Set<String> eventFollowUps = new HashSet<>(plugin.eventsManager().objectiveCompletionDialogues(r.executeObjective));
                for (String nextId : nextPlan.ids) if (eventFollowUps.contains(nextId))
                    findings.add("WARNING: Response " + r.id + " next-dialogue " + nextId + " is also started by on-objective-complete for " + r.executeObjective + "; duplicate starts are coalesced.");
            }
            for (QuestAction action : r.questActions) {
                String actionQuestId = action.questId;
                if (actionQuestId.isBlank()) actionQuestId = r.questId;
                if (actionQuestId.isBlank()) {
                    for (DialogueDef dialogue : dialogues.values()) {
                        if (dialogue.responseIds.contains(r.id) && !dialogue.questId.isBlank()) {
                            actionQuestId = dialogue.questId;
                            break;
                        }
                    }
                }
                IekQuestsPlugin.Quest q = actionQuestId.isBlank() ? null : plugin.questById(actionQuestId);
                if (q == null && actionQuestId.isBlank()) findings.add("ERROR: Response " + r.id + " has no resolvable quest ID for action " + action.action);
                else if (q == null) findings.add("ERROR: Response " + r.id + " references unknown quest " + actionQuestId);
                else {
                    String verb = action.action.toLowerCase(Locale.ROOT);
                    if (!Set.of("accept", "start", "restart", "refuse", "close", "none", "advance", "continue", "turn-in", "goto", "goto-stage", "complete", "repeat", "repeat-contract", "run-rewards").contains(verb)) findings.add("ERROR: Unknown quest action " + action.action + " in response " + r.id);
                    if ((verb.equals("goto") || verb.equals("goto-stage")) && !q.stages.containsKey(action.targetStage)) findings.add("ERROR: Unknown target stage " + action.targetStage + " in response " + r.id);
                    if ((verb.equals("repeat") || verb.equals("repeat-contract")) && !hasRepeat(q, action.repeatId)) findings.add("ERROR: Unknown repeat ID " + action.repeatId + " in response " + r.id);
                }
            }
        }
        if (onlyId == null) {
            checkDuplicateTopLevelKeys(findings, file("dialogues.yml"), "dialogues.yml");
            checkDuplicateTopLevelKeys(findings, file("responses.yml"), "responses.yml");
            checkDuplicateTopLevelKeys(findings, file("npcs.yml"), "npcs.yml");
            checkDuplicateTopLevelKeys(findings, file("Reward.yml"), "Reward.yml");
        }
        for (NpcDef n : npcs.values()) {
            if (onlyId != null && !matchesFilter(onlyId, n.key) && !matchesFilter(onlyId, String.valueOf(n.id))) continue;
            if (n.id < 0) findings.add("ERROR: Invalid Citizens NPC ID in binding " + n.key);
            else if (!citizensNpcExists(n.id)) findings.add("WARNING: Citizens ID " + n.id + " cannot be verified (NPC missing or Citizens unavailable)");
            for (String d : n.dialogueIds) if (!dialogues.containsKey(d)) findings.add("ERROR: NPC " + n.key + " references unknown dialogue " + d);
            for (String tag : n.visibilityPrerequisites) if (!playerTagDefinitions.contains(stripNegation(tag))) findings.add("ERROR: NPC " + n.key + " references unknown visibility player tag " + tag);
            for (String tag : n.visibilityPrerequisites) if (tag.isBlank() || tag.equals("!") || tag.startsWith("!!")
                    || !stripNegation(tag).matches("[A-Za-z0-9_-]{1,64}"))
                findings.add("ERROR: NPC " + n.key + " has malformed visibility prerequisite " + tag);
            if (n.malformedVisibilityPrerequisites) findings.add("ERROR: NPC " + n.key + " visibility-prerequisites must be a YAML list of strings.");
            if (!n.visibilityPrerequisites.isEmpty() && Bukkit.getPluginManager().getPlugin("Citizens") == null)
                findings.add("ERROR: NPC " + n.key + " requests per-player visibility, but Citizens is unavailable.");
            else if (!n.visibilityPrerequisites.isEmpty() && !citizensVisibilityApiAvailable())
                findings.add("ERROR: NPC " + n.key + " requests per-player visibility, but the installed Citizens API is unsupported.");
        }
        findings.addAll(plugin.sequencesManager().validateFindings(null));
        findings.addAll(plugin.eventsManager().validationFindings());
        for (Map.Entry<String, List<String>> reward : rewards.entrySet()) {
            if (onlyId != null && !onlyId.equals(reward.getKey())) continue;
            if (!validId(reward.getKey())) findings.add("ERROR: Invalid reward ID " + reward.getKey());
            if (reward.getValue().isEmpty()) findings.add("ERROR: Reward " + reward.getKey() + " contains no actions.");
            for (String action : reward.getValue()) {
                String problem = rewardActionProblem(action);
                if (problem != null) findings.add("ERROR: Reward " + reward.getKey() + " has invalid action '" + action + "': " + problem);
            }
        }
        for (Map.Entry<String, String> invalid : invalidRewardDefinitions.entrySet()) {
            if (onlyId != null && !onlyId.equals(invalid.getKey())) continue;
            findings.add("ERROR: Reward " + invalid.getKey() + " is invalid: " + invalid.getValue());
        }
        findings = new ArrayList<>(new java.util.LinkedHashSet<>(findings));
        if (findings.isEmpty()) sender.sendMessage(Component.text("Validation passed" + (onlyId == null ? "." : " for " + onlyId + ".")));
        else {
            findings.forEach(value -> sender.sendMessage(Component.text(value)));
            long errors = findings.stream().filter(value -> value.startsWith("ERROR:")).count();
            sender.sendMessage(Component.text("Validation: " + errors + " error(s), " + (findings.size() - errors) + " warning(s)."));
        }
    }

    private boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9_-]+"); }
    private boolean matchesFilter(String filter, String id) { return filter == null || filter.equals(id); }
    private void checkTags(List<String> findings, String owner, List<String> values, Set<String> definitions, String kind) {
        for (String value : values) if (!definitions.contains(stripNegation(value))) findings.add("ERROR: " + owner + " references unknown " + kind + " tag " + value);
    }

    private void checkCommands(List<String> findings, String owner, List<String> commands) {
        for (String command : commands) if (command == null || !command.trim().matches("(?i)^(server|ser|player|op):\\s*.+$"))
            findings.add("ERROR: " + owner + " has a command without a supported prefix: " + command);
    }

    private void checkJournalEffects(List<String> findings, String owner, List<String> addEntries, List<String> removeEntries) {
        for (String id : addEntries) if (!plugin.journalManager().contains(id))
            findings.add("ERROR: " + owner + " references unknown Journal entry '" + id + "' in addjournal.");
        for (String id : removeEntries) if (!plugin.journalManager().contains(id))
            findings.add("ERROR: " + owner + " references unknown Journal entry '" + id + "' in removejournal.");
    }

    private void checkDuplicateTopLevelKeys(List<String> findings, File source, String label) {
        if (!source.exists()) return;
        Map<String, Integer> lines = new HashMap<>();
        try {
            int number = 0;
            for (String line : java.nio.file.Files.readAllLines(source.toPath())) {
                number++;
                java.util.regex.Matcher matcher = Pattern.compile("^([A-Za-z0-9_-]+):\\s*(?:#.*)?$").matcher(line);
                if (!matcher.matches()) continue;
                String id = matcher.group(1);
                Integer previous = lines.putIfAbsent(id, number);
                if (previous != null) findings.add("ERROR: Duplicate top-level ID " + id + " in " + label + " at lines " + previous + " and " + number);
            }
        } catch (Exception ex) { findings.add("WARNING: Could not scan duplicate IDs in " + label + ": " + ex.getMessage()); }
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("none")) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) if (!part.isBlank()) out.add(part.trim());
        return out;
    }



}
