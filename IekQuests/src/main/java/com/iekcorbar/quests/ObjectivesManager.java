package com.iekcorbar.quests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ObjectivesManager implements Listener {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<String> TYPES = Set.of("kill", "deliver", "craft", "has-item", "reach", "break-block", "place-block",
            "fish", "smelt", "enchant", "use-item", "open-container", "interact-block", "inventory-click");
    private final IekQuestsPlugin plugin;
    private final LinkedHashMap<String, ConfigurationSection> definitions = new LinkedHashMap<>();
    private YamlConfiguration file = new YamlConfiguration();
    private BukkitTask availabilityTask;

    ObjectivesManager(IekQuestsPlugin plugin) { this.plugin = plugin; }

    void load() {
        File target = plugin.organizationFile("Objective.yml");
        file = YamlConfiguration.loadConfiguration(target);
        definitions.clear();
        for (String id : file.getKeys(false)) {
            ConfigurationSection section = file.getConfigurationSection(id);
            if (section == null) {
                plugin.getLogger().warning("Objective.yml entry '" + id + "' must be a mapping.");
                continue;
            }
            definitions.put(id, section);
        }
        validate(null, null);
        for (Player player : Bukkit.getOnlinePlayers()) evaluate(player);
        if (availabilityTask == null || availabilityTask.isCancelled()) {
            availabilityTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (refreshAvailability(player)) plugin.dialogueManager().refreshPlayerDialogue(player);
                }
            }, 20L, 20L);
        }
    }

    void shutdown() {
        if (availabilityTask != null) {
            availabilityTask.cancel();
            availabilityTask = null;
        }
    }

    boolean hasObjective(String id) { return definitions.containsKey(id); }

    boolean isManuallyExecutable(String id) {
        ConfigurationSection def = definitions.get(id);
        return isManualObjective(def);
    }

    void command(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> sender.sendMessage("Objectives: " + String.join(", ", definitions.keySet()));
                case "show" -> show(sender, args);
                case "validate" -> {
                    String id = args.length > 1 ? args[1] : null;
                    if (id != null && !definitions.containsKey(id)) throw new IllegalArgumentException("Unknown objective ID: " + id);
                    validate(sender, id);
                }
                case "reload" -> { load(); sender.sendMessage("Objective.yml reloaded. Progress and quest data were preserved."); }
                case "progress" -> progressCommand(sender, args);
                case "reset" -> resetCommand(sender, args);
                case "create" -> createCommand(sender, args);
                case "set" -> setCommand(sender, args);
                case "task" -> taskCommand(sender, args);
                case "delete" -> deleteCommand(sender, args);
                default -> throw new IllegalArgumentException("Unknown subcommand. Use /iekobjective help.");
            }
        } catch (Exception ex) {
            sender.sendMessage("Objective command failed: " + ex.getMessage());
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("/iekobjective list | show <id> | validate [id] | reload");
        sender.sendMessage("/iekobjective progress <player|uuid> [objective-id] | reset <player|uuid> <objective-id>");
        sender.sendMessage("/iekobjective create <id> | set <id> <PlayerTag|ServerTag|OncompleteRemoveTag|OncompleteAssignTag|AvailabilityTag|Manual> <value>");
        sender.sendMessage("/iekobjective task add <id> <type> <field=value,...> | task set <id> <index> <field> <value> | task remove <id> <index> | delete <id>");
    }

    private void show(CommandSender sender, String[] args) {
        require(args, 2, "show <id>");
        ConfigurationSection section = definitions.get(args[1]);
        if (section == null) throw new IllegalArgumentException("Unknown objective ID: " + args[1]);
        YamlConfiguration view = new YamlConfiguration();
        view.set(args[1], section.getValues(true));
        sender.sendMessage(view.saveToString());
    }

    private void progressCommand(CommandSender sender, String[] args) {
        require(args, 2, "progress <player|uuid> [objective-id]");
        UUID uuid = resolveUuid(args[1]);
        String only = args.length > 2 ? args[2] : null;
        if (only != null && !definitions.containsKey(only)) throw new IllegalArgumentException("Unknown objective ID: " + only);
        int shown = 0;
        for (String id : definitions.keySet()) {
            if (only != null && !only.equals(id)) continue;
            String base = progressPath(uuid, id);
            ConfigurationSection def = definitions.get(id);
            boolean completed = plugin.dialogueData().getBoolean(base + ".completed");
            if (isAvailabilityObjective(def)) {
                String tag = def.getString("AvailabilityTag", "");
                boolean assigned = plugin.dialogueData().getStringList("dialogue.player-tags." + uuid).contains(tag);
                sender.sendMessage(id + ": WATCHING " + tag + " — " + (assigned ? "AVAILABLE" : "WAITING") + " [" + uuid + "]");
                shown++;
                continue;
            }
            if (isManualObjective(def)) {
                Player online = Bukkit.getPlayer(uuid);
                String status = completed ? "COMPLETE" : online == null ? "MANUAL (player offline)"
                        : canExecuteObjective(online, id) ? "READY TO EXECUTE" : "WAITING FOR REQUIREMENTS";
                sender.sendMessage(id + ": " + status + " [" + uuid + "]");
                shown++;
                continue;
            }
            int total = taskMaps(definitions.get(id)).size();
            int completedTasks = 0;
            for (int index = 0; index < total; index++) if (taskDone(uuid, id, index)) completedTasks++;
            sender.sendMessage(id + ": " + (completed ? "COMPLETE" : completedTasks + "/" + total + " tasks") + " [" + uuid + "]");
            shown++;
        }
        if (shown == 0) sender.sendMessage("No objective definitions found.");
    }

    private void resetCommand(CommandSender sender, String[] args) {
        require(args, 3, "reset <player|uuid> <objective-id>");
        UUID uuid = resolveUuid(args[1]);
        String id = args[2];
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown objective ID: " + id);
        plugin.dialogueData().set(progressPath(uuid, id), null);
        plugin.save();
        sender.sendMessage("Objective progress reset for " + uuid + " / " + id + ". Quest progress was not changed.");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) evaluate(online);
    }

    private void createCommand(CommandSender sender, String[] args) {
        require(args, 2, "create <id>");
        String id = checkedId(args[1]);
        if (definitions.containsKey(id)) throw new IllegalArgumentException("Objective already exists: " + id);
        file.createSection(id);
        file.set(id + ".PlayerTag", List.of());
        file.set(id + ".ServerTag", List.of());
        file.set(id + ".Objective", List.of());
        file.set(id + ".OncompleteRemoveTag", List.of());
        file.set(id + ".OncompleteAssignTag", List.of());
        file.set(id + ".addjournal", List.of());
        file.set(id + ".removejournal", List.of());
        saveDefinitions();
        load();
        sender.sendMessage("Created objective " + id + ". Add tasks, then run /iekobjective validate.");
    }

    private void setCommand(CommandSender sender, String[] args) {
        require(args, 4, "set <id> <field> <comma-separated-values>");
        String id = args[1];
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown objective ID: " + id);
        String field = args[2];
        if (field.equals("AvailabilityTag")) {
            String tag = args[3].equalsIgnoreCase("none") ? "" : String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).trim();
            if (!tag.isEmpty() && !plugin.dialogueManager().playerTagIds().contains(tag))
                throw new IllegalArgumentException("Unknown player tag: " + tag);
            file.set(id + ".AvailabilityTag", tag.isEmpty() ? null : tag);
            saveDefinitions();
            load();
            sender.sendMessage("Updated " + id + ".AvailabilityTag.");
            return;
        }
        if (field.equals("Manual")) {
            String raw = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).trim();
            if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("Manual must be true or false.");
            file.set(id + ".Manual", Boolean.parseBoolean(raw));
            saveDefinitions();
            load();
            sender.sendMessage("Updated " + id + ".Manual.");
            return;
        }
        if (!Set.of("PlayerTag", "ServerTag", "OncompleteRemoveTag", "OncompleteAssignTag", "addjournal", "removejournal").contains(field))
            throw new IllegalArgumentException("Unsupported field. Use PlayerTag, ServerTag, OncompleteRemoveTag, OncompleteAssignTag, AvailabilityTag, Manual, addjournal, or removejournal.");
        String values = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        List<String> parsed = csv(values);
        if (field.equals("addjournal") || field.equals("removejournal")) for (String journalId : parsed)
            if (!plugin.journalManager().contains(journalId)) throw new IllegalArgumentException("Unknown Journal entry ID: " + journalId);
        file.set(id + "." + field, parsed);
        saveDefinitions();
        load();
        sender.sendMessage("Updated " + id + "." + field + ".");
    }

    private void taskCommand(CommandSender sender, String[] args) {
        require(args, 3, "task <add|set|remove> <objective-id> ...");
        String operation = args[1].toLowerCase(Locale.ROOT), id = args[2];
        ConfigurationSection definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown objective ID: " + id);
        List<Map<?, ?>> tasks = new ArrayList<>(definition.getMapList("Objective"));
        switch (operation) {
            case "add" -> {
                require(args, 5, "task add <id> <type> <field=value,...>");
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("type", args[3].toLowerCase(Locale.ROOT));
                String joined = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
                for (String pair : joined.split(",")) {
                    int equals = pair.indexOf('=');
                    if (equals < 1) throw new IllegalArgumentException("Task fields use key=value pairs separated by commas.");
                    String key = pair.substring(0, equals).trim();
                    String value = pair.substring(equals + 1).trim();
                    task.put(key, parseScalar(key, value));
                }
                tasks.add(task);
            }
            case "set" -> {
                require(args, 6, "task set <id> <index> <field> <value>");
                int index = parseIndex(args[3], tasks.size());
                String value = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));
                Map<String, Object> task = toStringMap(tasks.get(index));
                task.put(args[4], parseScalar(args[4], value));
                tasks.set(index, task);
            }
            case "remove" -> {
                require(args, 4, "task remove <id> <index>");
                tasks.remove(parseIndex(args[3], tasks.size()));
            }
            default -> throw new IllegalArgumentException("Use task add, task set, or task remove.");
        }
        file.set(id + ".Objective", tasks);
        saveDefinitions();
        load();
        sender.sendMessage("Updated tasks for " + id + ".");
    }

    private void deleteCommand(CommandSender sender, String[] args) {
        require(args, 2, "delete <id>");
        String id = args[1];
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown objective ID: " + id);
        ConfigurationSection players = plugin.dialogueData().getConfigurationSection("objectives");
        if (players != null) for (String uuid : players.getKeys(false)) {
            if (plugin.dialogueData().contains("objectives." + uuid + "." + id))
                throw new IllegalArgumentException("Objective has saved player progress. Reset that progress first with /iekobjective reset <uuid> " + id + ".");
        }
        file.set(id, null);
        saveDefinitions();
        load();
        sender.sendMessage("Deleted objective " + id + ".");
    }

    private void saveDefinitions() {
        File target = plugin.organizationFile("Objective.yml");
        File temp = new File(target.getParentFile(), "Objective.yml.tmp");
        try {
            plugin.questPackageManager().persistGeneratedEdit(target.getName(), file);
            file.save(temp);
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception unsupportedAtomic) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception ex) {
            temp.delete();
            throw new IllegalStateException("Could not save Objective.yml: " + ex.getMessage());
        }
    }

    private void validate(CommandSender sender, String onlyId) {
        List<String> findings = new ArrayList<>();
        Set<String> playerTags = plugin.dialogueManager() == null ? Set.of() : Set.copyOf(plugin.dialogueManager().playerTagIds());
        Set<String> serverTags = plugin.dialogueManager() == null ? Set.of() : Set.copyOf(plugin.dialogueManager().serverTagIds());
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey();
            if (onlyId != null && !onlyId.equals(id)) continue;
            ConfigurationSection def = entry.getValue();
            if (!ID_PATTERN.matcher(id).matches()) findings.add("ERROR: malformed objective ID " + id);
            if (def.getMapList("Objective").isEmpty()) findings.add("ERROR: " + id + " has no Objective tasks.");
            if (def.contains("Manual") && !(def.get("Manual") instanceof Boolean)) findings.add("ERROR: " + id + " Manual must be true or false.");
            checkTags(findings, id, def.getStringList("PlayerTag"), playerTags, "player prerequisite");
            checkTags(findings, id, def.getStringList("ServerTag"), serverTags, "server prerequisite");
            checkTags(findings, id, def.getStringList("OncompleteRemoveTag"), playerTags, "completion removal");
            checkTags(findings, id, def.getStringList("OncompleteAssignTag"), playerTags, "completion assignment");
            checkJournalEntries(findings, id, def.getStringList("addjournal"), "addjournal");
            checkJournalEntries(findings, id, def.getStringList("removejournal"), "removejournal");
            List<Map<?, ?>> tasks = def.getMapList("Objective");
            String availabilityTag = def.getString("AvailabilityTag", "").trim();
            if (!availabilityTag.isEmpty()) {
                if (!playerTags.contains(availabilityTag)) findings.add("ERROR: " + id + " has unknown AvailabilityTag '" + availabilityTag + "'.");
                if (tasks.isEmpty() || tasks.stream().anyMatch(task -> !str(task.get("type")).equalsIgnoreCase("has-item")))
                    findings.add("ERROR: " + id + " AvailabilityTag requires one or more has-item tasks only.");
                if (def.getBoolean("Manual")) findings.add("ERROR: " + id + " cannot combine AvailabilityTag with Manual: true.");
                if (!def.getStringList("OncompleteRemoveTag").isEmpty() || !def.getStringList("OncompleteAssignTag").isEmpty()
                        || !def.getStringList("addjournal").isEmpty() || !def.getStringList("removejournal").isEmpty())
                    findings.add("ERROR: " + id + " AvailabilityTag watchers do not complete; remove completion tags and Journal effects.");
            } else if (tasks.stream().anyMatch(task -> str(task.get("type")).equalsIgnoreCase("has-item"))) {
                findings.add("ERROR: " + id + " has-item tasks require AvailabilityTag.");
            }
            if (def.getBoolean("Manual") && (tasks.isEmpty() || tasks.stream().anyMatch(task -> !str(task.get("type")).equalsIgnoreCase("deliver"))))
                findings.add("ERROR: " + id + " Manual: true supports deliver tasks only.");
            for (int i = 0; i < tasks.size(); i++) validateTask(findings, id, i, tasks.get(i));
        }
        if (onlyId != null && !definitions.containsKey(onlyId)) findings.add("ERROR: unknown objective ID " + onlyId);
        if (sender != null && plugin.eventsManager() != null)
            findings.addAll(plugin.eventsManager().objectiveCompletionValidationFindings());
        if (sender == null) findings.forEach(plugin.getLogger()::warning);
        else if (findings.isEmpty()) sender.sendMessage(onlyId == null ? "Objective validation passed." : "Objective validation passed: " + onlyId + ".");
        else findings.forEach(sender::sendMessage);
    }

    private void checkTags(List<String> findings, String id, List<String> tags, Set<String> known, String use) {
        for (String raw : tags) {
            String tag = raw.startsWith("!") ? raw.substring(1).trim() : raw.trim();
            if (tag.isEmpty() || !known.contains(tag)) findings.add("ERROR: " + id + " has unknown " + use + " tag '" + raw + "'.");
        }
    }

    private void checkJournalEntries(List<String> findings, String id, List<String> entries, String field) {
        for (String journalId : entries) if (!plugin.journalManager().contains(journalId))
            findings.add("ERROR: " + id + " references unknown Journal entry '" + journalId + "' in " + field + ".");
    }

    private void validateTask(List<String> findings, String id, int index, Map<?, ?> task) {
        String prefix = "ERROR: " + id + " task " + index + ": ";
        String type = str(task.get("type")).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) { findings.add(prefix + "unknown type '" + type + "'."); return; }
        switch (type) {
            case "kill" -> {
                try { EntityType.valueOf(str(task.get("target")).toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { findings.add(prefix + "target must be a valid entity type."); }
                positiveAmount(findings, prefix, task, true);
            }
            case "break-block", "place-block" -> {
                checkMaterial(findings, prefix, str(task.get("target")), true);
                positiveAmount(findings, prefix, task, true);
            }
            case "deliver", "craft", "fish", "smelt", "use-item", "has-item" -> {
                checkItem(findings, prefix, str(task.get("item")) .isBlank() ? str(task.get("target")) : str(task.get("item")));
                positiveAmount(findings, prefix, task, true);
                if (type.equals("use-item") && !Set.of("consume", "interact").contains(str(valueOr(task, "trigger", "consume")).toLowerCase(Locale.ROOT)))
                    findings.add(prefix + "trigger must be consume or interact.");
            }
            case "reach" -> validateLocation(findings, prefix, task, true);
            case "open-container" -> {
                validateLocation(findings, prefix, task, false);
                String target = str(task.get("target"));
                if (!target.isBlank() && !Set.of("CHEST", "TRAPPED_CHEST", "BARREL").contains(target.toUpperCase(Locale.ROOT)))
                    findings.add(prefix + "target must be CHEST, TRAPPED_CHEST, or BARREL.");
            }
            case "interact-block" -> {
                validateLocation(findings, prefix, task, false);
                checkMaterial(findings, prefix, str(task.get("target")), false);
                String action = str(valueOr(task, "action", "RIGHT_CLICK_BLOCK"));
                try { Action.valueOf(action.toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { findings.add(prefix + "invalid action; use RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK, or PHYSICAL."); }
            }
            case "inventory-click" -> {
                int slot = intValue(task.get("slot"), -1);
                String inventory = str(valueOr(task, "inventory", "player-crafting")).toLowerCase(Locale.ROOT);
                if (inventory.equals("player-crafting") && (slot < 0 || slot > 4)) findings.add(prefix + "player-crafting slot must be 0-4.");
                else if (inventory.equals("player") && (slot < 0 || slot > 40)) findings.add(prefix + "player inventory slot must be 0-40.");
                else if (!Set.of("player-crafting", "player").contains(inventory)) findings.add(prefix + "inventory must be player-crafting or player.");
                if (task.containsKey("amount")) positiveAmount(findings, prefix, task, true);
                String click = str(task.get("click"));
                if (!click.isBlank()) try { ClickType.valueOf(click.toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { findings.add(prefix + "invalid click type."); }
            }
            case "enchant" -> {
                positiveAmount(findings, prefix, task, true);
                checkMaterial(findings, prefix, str(task.get("target")), false);
                String enchant = str(task.get("enchantment"));
                if (!enchant.isBlank() && resolveEnchantment(enchant) == null) findings.add(prefix + "unknown enchantment '" + enchant + "'.");
            }
        }
        if ((type.equals("smelt") || type.equals("fish")) && !str(task.get("item")).isBlank()) checkItem(findings, prefix, str(task.get("item")));
    }

    private void checkItem(List<String> findings, String prefix, String item) {
        if (item.isBlank()) { findings.add(prefix + "item or target is required."); return; }
        if (item.regionMatches(true, 0, "item:", 0, 5)) {
            if (plugin.items() == null || !plugin.items().exists(item.substring(5))) findings.add(prefix + "unknown IekItem '" + item.substring(5) + "'.");
            return;
        }
        if (Material.matchMaterial(item) == null && (plugin.items() == null || !plugin.items().exists(item))) findings.add(prefix + "unknown material or IekItem '" + item + "'.");
    }

    private void checkMaterial(List<String> findings, String prefix, String value, boolean required) {
        if (value.isBlank() && !required) return;
        if (Material.matchMaterial(value) == null) findings.add(prefix + "invalid material '" + value + "'.");
    }

    private void positiveAmount(List<String> findings, String prefix, Map<?, ?> task, boolean required) {
        int amount = intValue(task.get("amount"), -1);
        if (required && amount < 1) findings.add(prefix + "amount must be a positive integer.");
    }

    private void validateLocation(List<String> findings, String prefix, Map<?, ?> task, boolean radius) {
        String worldName = str(task.get("world"));
        if (worldName.isBlank() || Bukkit.getWorld(worldName) == null) findings.add(prefix + "world is missing or not loaded: " + worldName);
        for (String axis : List.of("x", "y", "z")) {
            try { Double.parseDouble(str(task.get(axis))); }
            catch (Exception ex) { findings.add(prefix + "invalid or missing coordinate " + axis + "."); }
        }
        if (radius) try { if (Double.parseDouble(str(task.get("radius"))) <= 0) throw new NumberFormatException(); }
        catch (Exception ex) { findings.add(prefix + "radius must be positive."); }
    }

    private void increment(Player player, String type, String target, int amount, String secondary) {
        if (amount <= 0) return;
        boolean changed = false;
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey();
            if (!eligible(player, id, entry.getValue()) || isCompleted(player.getUniqueId(), id)) continue;
            List<Map<?, ?>> tasks = taskMaps(entry.getValue());
            for (int i = 0; i < tasks.size(); i++) {
                Map<?, ?> task = tasks.get(i);
                if (!str(task.get("type")).equalsIgnoreCase(type) || taskDone(player.getUniqueId(), id, i)) continue;
                if (!targetMatches(type, target, task, secondary)) continue;
                int required = intValue(task.get("amount"), 1);
                int current = taskCount(player.getUniqueId(), id, i, task);
                setTaskCount(player.getUniqueId(), id, i, task, Math.min(required, current + amount));
                changed = true;
            }
            if (completeIfReady(player, id, entry.getValue())) changed = true;
        }
        if (changed) plugin.save();
    }

    private void incrementEnchant(Player player, ItemStack item, Set<String> enchantments) {
        boolean changed = false;
        String target = itemIdentifier(item);
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey();
            ConfigurationSection definition = entry.getValue();
            if (!eligible(player, id, definition) || isCompleted(player.getUniqueId(), id)) continue;
            List<Map<?, ?>> tasks = taskMaps(definition);
            for (int index = 0; index < tasks.size(); index++) {
                Map<?, ?> task = tasks.get(index);
                if (!str(task.get("type")).equalsIgnoreCase("enchant") || taskDone(player.getUniqueId(), id, index)) continue;
                String wantedItem = str(task.get("target"));
                if (!wantedItem.isBlank() && !itemMatches(target, wantedItem)) continue;
                String wantedEnchantment = str(task.get("enchantment"));
                if (!wantedEnchantment.isBlank() && enchantments.stream().noneMatch(wantedEnchantment::equalsIgnoreCase)) continue;
                int required = intValue(task.get("amount"), 1);
                int current = taskCount(player.getUniqueId(), id, index, task);
                setTaskCount(player.getUniqueId(), id, index, task, Math.min(required, current + 1));
                changed = true;
            }
            if (completeIfReady(player, id, definition)) changed = true;
        }
        if (changed) plugin.save();
    }

    private boolean targetMatches(String type, String target, Map<?, ?> task, String secondary) {
        String wanted = switch (type) {
            case "kill", "break-block", "place-block", "fish", "smelt", "craft" -> str(task.get("target")).isBlank() ? str(task.get("item")) : str(task.get("target"));
            case "deliver", "use-item" -> str(task.get("item")).isBlank() ? str(task.get("target")) : str(task.get("item"));
            default -> "";
        };
        if (!wanted.isBlank() && !itemMatches(target, wanted)) return false;
        if (type.equals("use-item") && !str(valueOr(task, "trigger", "consume")).equalsIgnoreCase(secondary)) return false;
        if (type.equals("enchant")) {
            if (!str(task.get("target")).isBlank() && !itemMatches(target, str(task.get("target")))) return false;
            if (!str(task.get("enchantment")).isBlank() && !str(task.get("enchantment")).equalsIgnoreCase(secondary)) return false;
        }
        return true;
    }

    private boolean itemMatches(String actual, String expected) {
        if (actual == null || actual.isBlank()) return false;
        String expectedId = expected.regionMatches(true, 0, "item:", 0, 5) ? expected.substring(5) : expected;
        if (plugin.items() != null && plugin.items().exists(expectedId)) return actual.equalsIgnoreCase("item:" + expectedId);
        if (actual.regionMatches(true, 0, "item:", 0, 5)) {
            Material customMaterial = plugin.items() == null ? null : plugin.items().materialOf(actual.substring(5));
            Material expectedMaterial = Material.matchMaterial(expectedId);
            return customMaterial != null && customMaterial == expectedMaterial;
        }
        return actual.equalsIgnoreCase(expectedId) || actual.equalsIgnoreCase(expectedId.toUpperCase(Locale.ROOT));
    }

    private void conditional(Player player, String type, Location location, String target, String action) {
        boolean changed = false;
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey();
            ConfigurationSection def = entry.getValue();
            if (!eligible(player, id, def) || isCompleted(player.getUniqueId(), id)) continue;
            List<Map<?, ?>> tasks = taskMaps(def);
            for (int i = 0; i < tasks.size(); i++) {
                Map<?, ?> task = tasks.get(i);
                if (!str(task.get("type")).equalsIgnoreCase(type) || taskDone(player.getUniqueId(), id, i)) continue;
                if (type.equals("reach")) {
                    if (!locationMatches(location, task, true)) continue;
                } else if (type.equals("open-container") || type.equals("interact-block")) {
                    if (!locationMatches(location, task, false)) continue;
                    String wanted = str(task.get("target"));
                    if (!wanted.isBlank() && (target == null || !wanted.equalsIgnoreCase(target))) continue;
                    String wantedAction = str(task.get("action"));
                    if (!wantedAction.isBlank() && !wantedAction.equalsIgnoreCase(action)) continue;
                }
                plugin.dialogueData().set(taskPath(player.getUniqueId(), id, i) + ".done", true);
                changed = true;
            }
            if (completeIfReady(player, id, def)) changed = true;
        }
        if (changed) plugin.save();
    }

    private boolean locationMatches(Location actual, Map<?, ?> task, boolean radius) {
        if (actual == null || actual.getWorld() == null || !actual.getWorld().getName().equalsIgnoreCase(str(task.get("world")))) return false;
        double x = doubleValue(task.get("x"), Double.NaN), y = doubleValue(task.get("y"), Double.NaN), z = doubleValue(task.get("z"), Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if (radius) {
            double r = doubleValue(task.get("radius"), 0);
            return actual.distanceSquared(new Location(actual.getWorld(), x, y, z)) <= r * r;
        }
        return actual.getBlockX() == (int) x && actual.getBlockY() == (int) y && actual.getBlockZ() == (int) z;
    }

    private boolean eligible(Player player, String id, ConfigurationSection def) {
        if (isCompleted(player.getUniqueId(), id)) return false;
        return prerequisitesPass(player, def);
    }

    private boolean prerequisitesPass(Player player, ConfigurationSection def) {
        if (!tagRequirements(player, def.getStringList("PlayerTag"), true)) return false;
        return tagRequirements(player, def.getStringList("ServerTag"), false);
    }

    private boolean tagRequirements(Player player, List<String> tags, boolean playerTag) {
        Set<String> definitions = playerTag ? Set.copyOf(plugin.dialogueManager().playerTagIds()) : Set.copyOf(plugin.dialogueManager().serverTagIds());
        List<String> assigned = playerTag
                ? plugin.dialogueData().getStringList("dialogue.player-tags." + player.getUniqueId())
                : plugin.dialogueData().getStringList("dialogue.server-tags");
        for (String raw : tags) {
            boolean negated = raw.startsWith("!");
            String tag = (negated ? raw.substring(1) : raw).trim();
            if (tag.isEmpty() || !definitions.contains(tag)) return false;
            boolean has = assigned.contains(tag);
            if ((!negated && !has) || (negated && has)) return false;
        }
        return true;
    }

    private void evaluate(Player player) {
        boolean changed = false;
        boolean availabilityChanged = false;
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            ConfigurationSection def = entry.getValue();
            if (isAvailabilityObjective(def)) {
                if (updateAvailability(player, def)) {
                    changed = true;
                    availabilityChanged = true;
                }
                continue;
            }
            if (isManualObjective(def)) continue;
            if (eligible(player, entry.getKey(), def) && completeIfReady(player, entry.getKey(), def)) changed = true;
        }
        if (changed) plugin.save();
        if (availabilityChanged) Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.dialogueManager().refreshPlayerDialogue(player);
        });
    }

    boolean refreshAvailability(Player player) {
        boolean changed = false;
        for (ConfigurationSection def : definitions.values())
            if (isAvailabilityObjective(def) && updateAvailability(player, def)) changed = true;
        if (changed) plugin.save();
        return changed;
    }

    private boolean isAvailabilityObjective(ConfigurationSection def) {
        return def != null && !def.getString("AvailabilityTag", "").isBlank();
    }

    private boolean isManualObjective(ConfigurationSection def) {
        return def != null && def.getBoolean("Manual", false);
    }

    private boolean updateAvailability(Player player, ConfigurationSection def) {
        String tag = def.getString("AvailabilityTag", "").trim();
        if (tag.isEmpty()) return false;
        String path = "dialogue.player-tags." + player.getUniqueId();
        List<String> assigned = new ArrayList<>(plugin.dialogueData().getStringList(path));
        boolean tagKnown = plugin.dialogueManager().playerTagIds().contains(tag);
        boolean available = tagKnown && prerequisitesPass(player, def);
        List<Map<?, ?>> tasks = taskMaps(def);
        if (tasks.isEmpty()) available = false;
        for (Map<?, ?> task : tasks) {
            if (!str(task.get("type")).equalsIgnoreCase("has-item")) { available = false; break; }
            String item = str(task.get("item")).isBlank() ? str(task.get("target")) : str(task.get("item"));
            if (countItem(player, item) < intValue(task.get("amount"), 1)) { available = false; break; }
        }
        if (available && !assigned.contains(tag)) assigned.add(tag);
        else if (!available) assigned.remove(tag);
        if (assigned.equals(plugin.dialogueData().getStringList(path))) return false;
        plugin.dialogueData().set(path, assigned);
        plugin.dialogueManager().refreshNpcVisibility(player);
        return true;
    }

    boolean canExecuteObjective(Player player, String id) {
        refreshAvailability(player);
        ConfigurationSection def = definitions.get(id);
        if (!isManualObjective(def) || isCompleted(player.getUniqueId(), id) || !eligible(player, id, def)) return false;
        List<Map<?, ?>> tasks = taskMaps(def);
        if (tasks.isEmpty()) return false;
        for (int i = 0; i < tasks.size(); i++) if (!taskSatisfied(player, id, i, tasks.get(i))) return false;
        return true;
    }

    boolean executeObjective(Player player, String id) {
        if (!canExecuteObjective(player, id)) return false;
        ConfigurationSection def = definitions.get(id);
        return completeIfReady(player, id, def, true, false);
    }

    private boolean completeIfReady(Player player, String id, ConfigurationSection def) {
        return completeIfReady(player, id, def, false, true);
    }

    private boolean completeIfReady(Player player, String id, ConfigurationSection def, boolean manualExecution, boolean notify) {
        if (isAvailabilityObjective(def) || isManualObjective(def) && !manualExecution) return false;
        UUID uuid = player.getUniqueId();
        if (isCompleted(uuid, id) || !eligible(player, id, def)) return false;
        List<Map<?, ?>> tasks = taskMaps(def);
        if (tasks.isEmpty()) return false;
        for (int i = 0; i < tasks.size(); i++) if (!taskSatisfied(player, id, i, tasks.get(i))) return false;
        if (!eligible(player, id, def)) return false;
        Map<String, Integer> deliverTotals = new LinkedHashMap<>();
        for (Map<?, ?> task : tasks) if (str(task.get("type")).equalsIgnoreCase("deliver")) {
            String item = str(task.get("item")).isBlank() ? str(task.get("target")) : str(task.get("item"));
            deliverTotals.merge(item, intValue(task.get("amount"), 1), Integer::sum);
        }
        for (Map.Entry<String, Integer> requirement : deliverTotals.entrySet()) if (countItem(player, requirement.getKey()) < requirement.getValue()) return false;
        for (Map.Entry<String, Integer> requirement : deliverTotals.entrySet()) consumeItem(player, requirement.getKey(), requirement.getValue());
        String base = progressPath(uuid, id);
        plugin.dialogueData().set(base + ".completed", true);
        applyCompletionTags(player, def.getStringList("OncompleteRemoveTag"), def.getStringList("OncompleteAssignTag"));
        plugin.journalManager().add(player, def.getStringList("addjournal"));
        plugin.journalManager().remove(player, def.getStringList("removejournal"));
        if (refreshAvailability(player)) Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.dialogueManager().refreshPlayerDialogue(player);
        });
        plugin.save();
        plugin.eventsManager().fire("on-objective-complete", player, Map.of("objective-id", id));
        if (notify) player.sendMessage("Objective completed: " + id);
        return true;
    }

    private boolean taskSatisfied(Player player, String id, int index, Map<?, ?> task) {
        String type = str(task.get("type")).toLowerCase(Locale.ROOT);
        if (type.equals("deliver")) return countItem(player, str(task.get("item")).isBlank() ? str(task.get("target")) : str(task.get("item")))
                >= intValue(task.get("amount"), 1);
        if (type.equals("has-item")) return countItem(player, str(task.get("item")).isBlank() ? str(task.get("target")) : str(task.get("item")))
                >= intValue(task.get("amount"), 1);
        if (type.equals("reach")) return locationMatches(player.getLocation(), task, true);
        return taskDone(player.getUniqueId(), id, index) || isCounter(type) && taskCount(player.getUniqueId(), id, index, task) >= intValue(task.get("amount"), 1);
    }

    private void applyCompletionTags(Player player, List<String> removeTags, List<String> assignTags) {
        YamlConfiguration data = plugin.dialogueData();
        String path = "dialogue.player-tags." + player.getUniqueId();
        List<String> assigned = new ArrayList<>(data.getStringList(path));
        Set<String> known = Set.copyOf(plugin.dialogueManager().playerTagIds());
        for (String tag : removeTags) {
            if (!known.contains(tag)) { plugin.getLogger().warning("Objective completion ignored unknown player tag removal: " + tag); continue; }
            assigned.remove(tag);
        }
        for (String tag : assignTags) {
            if (!known.contains(tag)) { plugin.getLogger().warning("Objective completion ignored unknown player tag assignment: " + tag); continue; }
            if (!assigned.contains(tag)) assigned.add(tag);
        }
        data.set(path, assigned);
        plugin.dialogueManager().refreshNpcVisibility(player);
    }

    private int countItem(Player player, String item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (stack != null && itemMatchesStack(stack, item)) total += stack.getAmount();
        return total;
    }

    private void consumeItem(Player player, String item, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !itemMatchesStack(stack, item)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            remaining -= take;
            if (take == stack.getAmount()) contents[i] = null;
            else stack.setAmount(stack.getAmount() - take);
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean itemMatchesStack(ItemStack stack, String item) {
        String id = item.regionMatches(true, 0, "item:", 0, 5) ? item.substring(5) : item;
        if (plugin.items() != null && plugin.items().exists(id)) return id.equalsIgnoreCase(plugin.itemIdOf(stack));
        Material material = Material.matchMaterial(id);
        return material != null && stack.getType() == material;
    }

    private void markTask(Player player, String type, int slot, String click) {
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey(); ConfigurationSection def = entry.getValue();
            if (!eligible(player, id, def) || isCompleted(player.getUniqueId(), id)) continue;
            List<Map<?, ?>> tasks = taskMaps(def);
            for (int i = 0; i < tasks.size(); i++) {
                Map<?, ?> task = tasks.get(i);
                if (!str(task.get("type")).equalsIgnoreCase(type) || taskDone(player.getUniqueId(), id, i)) continue;
                if (type.equals("inventory-click")) {
                    if (intValue(task.get("slot"), -1) != slot) continue;
                    String expectedClick = str(task.get("click"));
                    if (!expectedClick.isBlank() && !expectedClick.equalsIgnoreCase(click)) continue;
                    Map<String, Object> mutable = toStringMap(task);
                    int count = taskCount(player.getUniqueId(), id, i, task) + 1;
                    int required = intValue(task.get("amount"), 1);
                    setTaskCount(player.getUniqueId(), id, i, mutable, Math.min(count, required));
                } else plugin.dialogueData().set(taskPath(player.getUniqueId(), id, i) + ".done", true);
            }
            completeIfReady(player, id, def);
        }
        plugin.save();
    }

    private void countInventoryClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        Inventory clicked = event.getClickedInventory();
        String inventoryName;
        int slot = event.getSlot();
        if (clicked == event.getView().getTopInventory() && clicked.getType() == InventoryType.CRAFTING) inventoryName = "player-crafting";
        else if (clicked instanceof PlayerInventory) inventoryName = "player";
        else return;
        for (Map.Entry<String, ConfigurationSection> entry : definitions.entrySet()) {
            String id = entry.getKey(); ConfigurationSection def = entry.getValue();
            if (!eligible(player, id, def) || isCompleted(player.getUniqueId(), id)) continue;
            List<Map<?, ?>> tasks = taskMaps(def);
            for (int i = 0; i < tasks.size(); i++) {
                Map<?, ?> task = tasks.get(i);
                if (!str(task.get("type")).equalsIgnoreCase("inventory-click") || taskDone(player.getUniqueId(), id, i)) continue;
                String expectedInventory = str(task.get("inventory")).isBlank() ? "player-crafting" : str(task.get("inventory"));
                if (!expectedInventory.equalsIgnoreCase(inventoryName) || intValue(task.get("slot"), -1) != slot) continue;
                String expectedClick = str(task.get("click"));
                if (!expectedClick.isBlank() && !expectedClick.equalsIgnoreCase(event.getClick().name())) continue;
                int current = taskCount(player.getUniqueId(), id, i, task);
                int amount = intValue(task.get("amount"), 1);
                setTaskCount(player.getUniqueId(), id, i, task, Math.min(amount, current + 1));
            }
            completeIfReady(player, id, def);
        }
        plugin.save();
    }

    private void trackPickup(Player player) { Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) evaluate(player); }); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) increment(killer, "kill", event.getEntityType().name(), 1, "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack output = event.getCurrentItem();
        if (output != null && !output.getType().isAir()) increment(player, "craft", itemIdentifier(output), output.getAmount(), "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { increment(event.getPlayer(), "break-block", event.getBlock().getType().name(), 1, ""); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { increment(event.getPlayer(), "place-block", event.getBlockPlaced().getType().name(), 1, ""); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Item item) increment(event.getPlayer(), "fish", itemIdentifier(item.getItemStack()), item.getItemStack().getAmount(), "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) { increment(event.getPlayer(), "smelt", event.getItemType().name(), event.getItemAmount(), ""); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Set<String> enchantments = event.getEnchantsToAdd().keySet().stream()
                .map(enchantment -> enchantment.getKey().toString()).collect(java.util.stream.Collectors.toSet());
        incrementEnchant(event.getEnchanter(), event.getItem(), enchantments);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { increment(event.getPlayer(), "use-item", itemIdentifier(event.getItem()), 1, "consume"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand().name().equals("OFF_HAND")) return;
        ItemStack item = event.getItem();
        if (item != null) increment(event.getPlayer(), "use-item", itemIdentifier(item), 1, "interact");
        if (event.getClickedBlock() != null) conditional(event.getPlayer(), "interact-block", event.getClickedBlock().getLocation(), event.getClickedBlock().getType().name(), event.getAction().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Container container)) return;
        conditional(player, "open-container", container.getLocation(), container.getType().name(), "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        countInventoryClick(player, event);
        trackPickup(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) trackPickup(player); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !eligibleForReach(event.getPlayer())) return;
        conditional(event.getPlayer(), "reach", to, "", "");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (event.getPlayer().isOnline()) evaluate(event.getPlayer()); });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { evaluate(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { plugin.save(); }

    private boolean eligibleForReach(Player player) {
        return definitions.values().stream().anyMatch(def -> def.getMapList("Objective").stream().anyMatch(task -> str(task.get("type")).equalsIgnoreCase("reach")));
    }

    private boolean isCounter(String type) { return Set.of("kill", "craft", "break-block", "place-block", "fish", "smelt", "enchant", "use-item", "inventory-click").contains(type); }

    private boolean targetMatches(String type, String target, Map<?, ?> task) { return targetMatches(type, target, task, ""); }
    private List<Map<?, ?>> taskMaps(ConfigurationSection def) { return def.getMapList("Objective"); }

    private String taskPath(UUID uuid, String id, int index) { return progressPath(uuid, id) + ".tasks." + index; }
    private String progressPath(UUID uuid, String id) { return "objectives." + uuid + "." + id; }
    private boolean isCompleted(UUID uuid, String id) { return plugin.dialogueData().getBoolean(progressPath(uuid, id) + ".completed"); }
    private boolean taskDone(UUID uuid, String id, int index) { return plugin.dialogueData().getBoolean(taskPath(uuid, id, index) + ".done"); }

    private int taskCount(UUID uuid, String id, int index, Map<?, ?> task) {
        String path = taskPath(uuid, id, index);
        String signature = Integer.toHexString(toStringMap(task).toString().hashCode());
        String stored = plugin.dialogueData().getString(path + ".signature", "");
        if (!stored.equals(signature)) {
            plugin.dialogueData().set(path + ".signature", signature);
            plugin.dialogueData().set(path + ".count", 0);
            plugin.dialogueData().set(path + ".done", false);
            return 0;
        }
        return plugin.dialogueData().getInt(path + ".count");
    }

    private void setTaskCount(UUID uuid, String id, int index, Map<?, ?> task, int count) {
        String path = taskPath(uuid, id, index);
        plugin.dialogueData().set(path + ".signature", Integer.toHexString(toStringMap(task).toString().hashCode()));
        plugin.dialogueData().set(path + ".count", count);
        if (count >= intValue(task.get("amount"), 1)) plugin.dialogueData().set(path + ".done", true);
    }

    private Enchantment resolveEnchantment(String value) {
        NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        if (key == null && !value.contains(":")) key = NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private Object valueOr(Map<?, ?> map, String key, Object fallback) { return map.containsKey(key) ? map.get(key) : fallback; }
    private String itemIdentifier(ItemStack stack) {
        String id = plugin.itemIdOf(stack);
        return id == null || id.isBlank() ? stack.getType().name() : "item:" + id;
    }
    private static String str(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int intValue(Object value, int fallback) { try { return Integer.parseInt(str(value)); } catch (Exception ignored) { return fallback; } }
    private static double doubleValue(Object value, double fallback) { try { return Double.parseDouble(str(value)); } catch (Exception ignored) { return fallback; } }
    private static List<String> csv(String value) { return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(); }
    private static Map<String, Object> toStringMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }
    private static Object parseScalar(String key, String value) {
        if (Set.of("amount", "slot", "x", "y", "z").contains(key)) {
            try { return key.equals("x") || key.equals("y") || key.equals("z") ? Double.parseDouble(value) : Integer.parseInt(value); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be numeric."); }
        }
        if (key.equals("radius")) try { return Double.parseDouble(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException("radius must be numeric."); }
        return value;
    }
    private static int parseIndex(String value, int size) {
        int index;
        try { index = Integer.parseInt(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Task index must be an integer."); }
        if (index < 0 || index >= size) throw new IllegalArgumentException("Task index is out of range: " + index);
        return index;
    }
    private static String checkedId(String id) { if (!ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("Invalid objective ID: " + id); return id; }
    private static void require(String[] args, int length, String usage) { if (args.length < length) throw new IllegalArgumentException("Usage: /iekobjective " + usage); }
    private UUID resolveUuid(String nameOrUuid) {
        try { return UUID.fromString(nameOrUuid); } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online == null) throw new IllegalArgumentException("Use an online player name or a player UUID for offline progress.");
        return online.getUniqueId();
    }
}
