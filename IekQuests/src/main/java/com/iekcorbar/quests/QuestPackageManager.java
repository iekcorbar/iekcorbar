package com.iekcorbar.quests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the runtime configuration files from the server-local Quest bundle directory. */
final class QuestPackageManager {
    private static final LinkedHashMap<String, String> CHAPTER_FILES = new LinkedHashMap<>();
    private static final Set<String> LIST_MERGE_CHAPTERS = Set.of("player-tags", "server-tags");
    static {
        CHAPTER_FILES.put("server-tags", "server-tags.yml");
        CHAPTER_FILES.put("Reward", "Reward.yml");
        CHAPTER_FILES.put("responses", "responses.yml");
        CHAPTER_FILES.put("quests", "quests.yml");
        CHAPTER_FILES.put("player-tags", "player-tags.yml");
        CHAPTER_FILES.put("Objective", "Objective.yml");
        CHAPTER_FILES.put("npcs", "npcs.yml");
        CHAPTER_FILES.put("Journal", "Journal.yml");
        CHAPTER_FILES.put("events", "events.yml");
        CHAPTER_FILES.put("dialogues", "dialogues.yml");
        CHAPTER_FILES.put("Sequences", "Sequences.yml");
    }

    private final File dataFolder;
    private final File questFolder;
    private final File organizationFolder;
    private Map<String, File> questSources = Map.of();

    QuestPackageManager(IekQuestsPlugin plugin) {
        this.dataFolder = plugin.getDataFolder();
        this.questFolder = new File(dataFolder, "Quest");
        this.organizationFolder = new File(dataFolder, "organization");
    }

    ReloadReport reload() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Files.createDirectories(questFolder.toPath());
            Files.createDirectories(organizationFolder.toPath());
            migrateFlatConfigurationIfNeeded(warnings);
            File[] files = questFolder.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".yml"));
            if (files == null) files = new File[0];
            java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            LinkedHashMap<String, YamlConfiguration> output = emptyOutputs();
            LinkedHashMap<String, File> nextQuestSources = new LinkedHashMap<>();
            Map<String, File> entityOwners = new LinkedHashMap<>();
            Map<Integer, NpcBindingOrigin> npcBindings = new LinkedHashMap<>();
            int loadedBundles = 0;

            for (File source : files) {
                YamlConfiguration bundle = new YamlConfiguration();
                try {
                    validateYamlSyntax(source);
                    bundle.load(source);
                } catch (IOException | InvalidConfigurationException ex) {
                    errors.add(source.getName() + ": " + ex.getMessage());
                    continue;
                }
                loadedBundles++;
                for (String chapter : bundle.getKeys(false)) {
                    String outputName = CHAPTER_FILES.get(chapter);
                    if (outputName == null) {
                        errors.add(source.getName() + ": unsupported chapter '" + chapter + "'. Supported chapters: "
                                + String.join(", ", CHAPTER_FILES.keySet()));
                        continue;
                    }
                    Object raw = bundle.get(chapter);
                    if (!(raw instanceof ConfigurationSection section)) {
                        errors.add(source.getName() + ": chapter '" + chapter + "' must be a YAML mapping.");
                        continue;
                    }
                    Object tagDefinitions = section.get("tags");
                    if (LIST_MERGE_CHAPTERS.contains(chapter) && tagDefinitions != null
                            && !(tagDefinitions instanceof List<?>) && !(tagDefinitions instanceof ConfigurationSection)) {
                        errors.add(source.getName() + ": chapter '" + chapter + ".tags' must be a YAML list or mapping.");
                        continue;
                    }
                    if (chapter.equals("npcs")) mergeNpcChapter(section, output.get(outputName), source, npcBindings, warnings, errors);
                    else {
                        reportDuplicateEntities(chapter, section, source, entityOwners, errors);
                        ConfigurationSection target = chapter.equals("quests")
                                ? output.get(outputName).getConfigurationSection("quests") : output.get(outputName);
                        mergeSection(target, "", section, chapter, source, errors);
                    }
                    if (chapter.equals("quests")) {
                        for (String id : section.getKeys(false)) {
                            if (!id.matches("[A-Za-z0-9_-]{1,64}"))
                                errors.add(source.getName() + ": malformed quest ID '" + id + "'; use 1-64 letters, numbers, underscores, or hyphens.");
                            else nextQuestSources.putIfAbsent(id, source);
                        }
                    }
                }
            }

            if (!errors.isEmpty()) return new ReloadReport(false, loadedBundles, 0, warnings, errors);
            for (Map.Entry<String, YamlConfiguration> entry : output.entrySet()) {
                if (entry.getKey().equals("events.yml")) addMissingEventSections(entry.getValue());
            }

            File staging = new File(dataFolder, ".organization-staging");
            if (staging.exists()) deleteTree(staging);
            Files.createDirectories(staging.toPath());
            for (Map.Entry<String, YamlConfiguration> entry : output.entrySet()) {
                File target = new File(staging, entry.getKey());
                entry.getValue().save(target);
                prependGeneratedHeader(target, entry.getKey());
            }
            replaceOrganization(staging);
            questSources = Map.copyOf(nextQuestSources);
            int questCount = output.get("quests.yml").getConfigurationSection("quests") == null ? 0
                    : output.get("quests.yml").getConfigurationSection("quests").getKeys(false).size();
            return new ReloadReport(true, loadedBundles, questCount, warnings, errors);
        } catch (Exception ex) {
            errors.add(exceptionReport(ex));
            return new ReloadReport(false, 0, 0, warnings, errors);
        }
    }

    File questSource(String questId) { return questSources.get(questId); }

    File questFolder() { return questFolder; }

    boolean isWizardQuest(String questId) {
        File source = questSources.get(questId);
        return source != null && source.getName().startsWith("_wizard-");
    }

    void removeWizardQuest(String questId) {
        File source = questSources.get(questId);
        if (source == null || !source.getName().startsWith("_wizard-") || !source.getParentFile().equals(questFolder)) return;
        try {
            Files.deleteIfExists(source.toPath());
        } catch (IOException ex) {
            pluginLog("Could not remove generated quest file " + source.getName() + ": " + ex.getMessage());
        }
    }

    private void pluginLog(String message) {
        org.bukkit.Bukkit.getLogger().warning("[IekQuests] " + message);
    }

    /** Writes admin-editor changes back to their owning bundle so the next rebuild retains them. */
    void persistGeneratedEdit(String fileName, YamlConfiguration edited) throws IOException {
        String chapter = CHAPTER_FILES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(fileName))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (chapter == null) throw new IOException("Unknown generated configuration file: " + fileName);
        File generated = new File(organizationFolder, fileName);
        YamlConfiguration previous = new YamlConfiguration();
        if (generated.isFile()) {
            try { previous.load(generated); }
            catch (IOException | InvalidConfigurationException ex) { throw new IOException("Could not read generated file " + fileName + ": " + ex.getMessage(), ex); }
        }

        if (LIST_MERGE_CHAPTERS.contains(chapter)) {
            List<?> oldTags = previous.getList("tags", List.of());
            List<?> newTags = edited.getList("tags", List.of());
            if (!oldTags.equals(newTags)) {
                List<Object> removed = oldTags.stream().filter(tag -> !newTags.contains(tag)).map(tag -> (Object) tag).toList();
                List<Object> added = newTags.stream().filter(tag -> !oldTags.contains(tag)).map(tag -> (Object) tag).toList();
                for (File source : bundleFiles()) {
                    YamlConfiguration bundle = loadBundle(source);
                    if (bundle.isList(chapter + ".tags")) {
                        List<Object> sourceTags = new ArrayList<>(bundle.getList(chapter + ".tags", List.of()));
                        if (sourceTags.removeAll(removed)) {
                            bundle.set(chapter + ".tags", sourceTags);
                            saveBundle(bundle, source);
                        }
                    }
                }
                if (!added.isEmpty() || !removed.isEmpty()) {
                    YamlConfiguration managed = loadManagedBundle();
                    List<Object> managedTags = new ArrayList<>(managed.getList(chapter + ".tags", List.of()));
                    managedTags.removeAll(removed);
                    for (Object tag : added) if (!managedTags.contains(tag)) managedTags.add(tag);
                    managed.set(chapter + ".tags", managedTags);
                    saveBundle(managed, managedFile());
                }
            }
            return;
        }

        Set<String> paths = new LinkedHashSet<>();
        if (chapter.equals("events")) {
            Set<String> eventTypes = new LinkedHashSet<>(previous.getKeys(false));
            eventTypes.addAll(edited.getKeys(false));
            for (String type : eventTypes) {
                Set<String> ids = new LinkedHashSet<>();
                ConfigurationSection oldEntries = previous.getConfigurationSection(type);
                ConfigurationSection newEntries = edited.getConfigurationSection(type);
                if (oldEntries != null) ids.addAll(oldEntries.getKeys(false));
                if (newEntries != null) ids.addAll(newEntries.getKeys(false));
                for (String id : ids) paths.add(type + "." + id);
            }
        } else {
            String root = chapter.equals("quests") ? "quests" : "";
            ConfigurationSection oldRoot = root.isEmpty() ? previous : previous.getConfigurationSection(root);
            ConfigurationSection newRoot = root.isEmpty() ? edited : edited.getConfigurationSection(root);
            Set<String> ids = new LinkedHashSet<>();
            if (oldRoot != null) ids.addAll(oldRoot.getKeys(false));
            if (newRoot != null) ids.addAll(newRoot.getKeys(false));
            for (String id : ids) paths.add(root.isEmpty() ? id : root + "." + id);
        }

        for (String path : paths) {
            Object oldValue = normalize(previous.get(path));
            Object newValue = normalize(edited.get(path));
            if (java.util.Objects.equals(oldValue, newValue)) continue;
            File owner = findOwner(chapter, path);
            if (owner == null) owner = managedFile();
            YamlConfiguration bundle = loadBundle(owner);
            String packagePath = chapter.equals("quests") ? path : chapter + "." + path;
            bundle.set(packagePath, null);
            Object updatedValue = edited.get(path);
            if (updatedValue != null) bundle.set(packagePath, normalizeForYaml(updatedValue));
            removeEmptyChapter(bundle, chapter);
            saveBundle(bundle, owner);
        }
    }

    private Object normalizeForYaml(Object value) {
        if (value instanceof ConfigurationSection section) return section.getValues(false);
        return value;
    }

    private Object normalize(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) values.put(key, normalize(section.get(key)));
            return values;
        }
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, item) -> values.put(String.valueOf(key), normalize(item)));
            return values;
        }
        return value;
    }

    private File findOwner(String chapter, String path) throws IOException {
        String packagePath = chapter.equals("quests") ? path : chapter + "." + path;
        for (File source : bundleFiles()) {
            YamlConfiguration bundle = loadBundle(source);
            if (bundle.contains(packagePath)) return source;
        }
        return null;
    }

    private List<File> bundleFiles() {
        File[] files = questFolder.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".yml"));
        if (files == null) return List.of();
        java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return List.of(files);
    }

    private YamlConfiguration loadBundle(File file) throws IOException {
        YamlConfiguration bundle = new YamlConfiguration();
        if (!file.isFile()) return bundle;
        try { bundle.load(file); }
        catch (IOException | InvalidConfigurationException ex) { throw new IOException("Could not edit " + file.getName() + ": " + ex.getMessage(), ex); }
        return bundle;
    }

    private void validateYamlSyntax(File source) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var reader = Files.newBufferedReader(source.toPath())) {
            new Yaml(new SafeConstructor(options)).load(reader);
        } catch (org.yaml.snakeyaml.error.YAMLException ex) {
            throw new IOException("Invalid YAML: " + ex.getMessage(), ex);
        }
    }

    private File managedFile() { return new File(questFolder, "_server-managed.yml"); }

    private YamlConfiguration loadManagedBundle() throws IOException { return loadBundle(managedFile()); }

    private void removeEmptyChapter(YamlConfiguration bundle, String chapter) {
        ConfigurationSection section = bundle.getConfigurationSection(chapter);
        if (section != null && section.getKeys(true).isEmpty()) bundle.set(chapter, null);
    }

    private void saveBundle(YamlConfiguration bundle, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File temp = new File(parent, target.getName() + ".tmp");
        bundle.save(temp);
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    private LinkedHashMap<String, YamlConfiguration> emptyOutputs() {
        LinkedHashMap<String, YamlConfiguration> output = new LinkedHashMap<>();
        for (String name : CHAPTER_FILES.values()) output.putIfAbsent(name, new YamlConfiguration());
        output.get("quests.yml").createSection("quests");
        output.get("server-tags.yml").set("tags", new ArrayList<>());
        output.get("player-tags.yml").set("tags", new ArrayList<>());
        return output;
    }

    private void prependGeneratedHeader(File target, String fileName) throws IOException {
        String detail = switch (fileName) {
            case "events.yml" -> "Event entries are grouped by trigger; all matching eligible entries run in declaration order.";
            case "Objective.yml" -> "Every objective task is required. In a 2x2 player crafting inventory, result slot is 0 and input slots are 1-4.";
            case "Sequences.yml" -> "Reusable delayed command, tag, Journal, reward, and dialogue steps are started by other quest chapters.";
            case "quests.yml" -> "Legacy quest-engine definitions are generated only from quests chapters in Quest bundles.";
            case "player-tags.yml", "server-tags.yml" -> "Tag registration defines valid IDs; assignment is stored separately in player data.";
            default -> "This generated runtime file is rebuilt from Quest/*.yml. Edit the source bundle or use an admin editor.";
        };
        String current = Files.readString(target.toPath());
        Files.writeString(target.toPath(), "# IekQuests generated configuration.\n# " + detail + "\n# Do not edit this output directly; use Quest bundles and /iekquest reload quest.\n\n" + current);
    }

    private void mergeSection(ConfigurationSection target, String prefix, ConfigurationSection sourceSection,
                              String chapter, File source, List<String> errors) {
        for (String key : sourceSection.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = sourceSection.get(key);
            if (value instanceof ConfigurationSection child) {
                ConfigurationSection current = target.getConfigurationSection(path);
                if (current == null) current = target.createSection(path);
                mergeSection(current, path, child, chapter, source, errors);
            } else if (LIST_MERGE_CHAPTERS.contains(chapter) && path.equals("tags") && value instanceof List<?> incoming) {
                List<Object> merged = new ArrayList<>(target.getList(path, List.of()));
                for (Object item : incoming) if (!merged.contains(item)) merged.add(item);
                target.set(path, merged);
            } else if (target.contains(path)) {
                Object existing = target.get(path);
                if (!java.util.Objects.equals(existing, value)) {
                    errors.add(source.getName() + ": conflicting value for '" + chapter + "." + path + "'.");
                }
            } else {
                target.set(path, value);
            }
        }
    }

    private void reportDuplicateEntities(String chapter, ConfigurationSection section, File source,
                                         Map<String, File> owners, List<String> errors) {
        if (chapter.equals("server-tags") || chapter.equals("player-tags")) return;
        if (chapter.equals("events")) {
            for (String eventType : section.getKeys(false)) {
                ConfigurationSection entries = section.getConfigurationSection(eventType);
                if (entries == null) continue;
                for (String id : entries.getKeys(false)) reportEntity(chapter + "." + eventType, id, source, owners, errors);
            }
            return;
        }
        ConfigurationSection entities = section;
        for (String id : entities.getKeys(false)) reportEntity(chapter, id, source, owners, errors);
    }

    private void reportEntity(String chapter, String id, File source, Map<String, File> owners, List<String> errors) {
        String key = chapter + "/" + id;
        File previous = owners.putIfAbsent(key, source);
        if (previous != null && !previous.equals(source))
            errors.add(source.getName() + ": duplicate " + chapter + " ID '" + id + "' also declared in " + previous.getName() + ".");
    }

    private void mergeNpcChapter(ConfigurationSection sourceSection, YamlConfiguration output, File source,
                                 Map<Integer, NpcBindingOrigin> npcBindings, List<String> warnings, List<String> errors) {
        for (String bindingKey : sourceSection.getKeys(false)) {
            ConfigurationSection binding = sourceSection.getConfigurationSection(bindingKey);
            if (binding == null) {
                errors.add(source.getName() + ": NPC binding '" + bindingKey + "' must be a mapping.");
                continue;
            }
            int npcId = binding.getInt("npc-id", -1);
            if (npcId < 0) {
                errors.add(source.getName() + ": NPC binding '" + bindingKey + "' has an invalid npc-id.");
                continue;
            }
            NpcBindingOrigin previous = npcBindings.get(npcId);
            if (previous == null) {
                if (output.contains(bindingKey)) {
                    errors.add(source.getName() + ": duplicate NPC binding ID '" + bindingKey + "'.");
                    continue;
                }
                copySection(output, bindingKey, binding);
                npcBindings.put(npcId, new NpcBindingOrigin(bindingKey, source));
                continue;
            }
            String existingKey = previous.key();
            ConfigurationSection existing = output.getConfigurationSection(existingKey);
            List<String> oldVisibility = existing.getStringList("visibility-prerequisites");
            List<String> newVisibility = binding.getStringList("visibility-prerequisites");
            if (!oldVisibility.equals(newVisibility)) {
                errors.add(source.getName() + ": Citizens NPC " + npcId + " has incompatible visibility-prerequisites; first binding is in "
                        + previous.source().getName() + ".");
                continue;
            }
            List<String> dialogues = new ArrayList<>(existing.getStringList("dialogues"));
            for (String dialogue : binding.getStringList("dialogues")) if (!dialogues.contains(dialogue)) dialogues.add(dialogue);
            existing.set("dialogues", dialogues);
            warnings.add("Citizens NPC " + npcId + " appears in multiple quest bundles; dialogue lists were merged under binding '" + existingKey + "'.");
        }
    }

    private void copySection(ConfigurationSection target, String path, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            String childPath = path + "." + key;
            if (value instanceof ConfigurationSection section) {
                target.createSection(childPath);
                copySection(target, childPath, section);
            } else target.set(childPath, value);
        }
    }

    private void addMissingEventSections(YamlConfiguration events) {
        for (String event : EventsManager.TYPES) if (!events.contains(event)) events.createSection(event);
    }

    private void replaceOrganization(File staging) throws IOException {
        File backup = new File(dataFolder, "organization-backup-" + System.currentTimeMillis());
        boolean hadOrganization = organizationFolder.exists();
        if (hadOrganization) Files.move(organizationFolder.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(staging.toPath(), organizationFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            if (hadOrganization && backup.exists()) Files.move(backup.toPath(), organizationFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            throw ex;
        }
    }

    private void migrateFlatConfigurationIfNeeded(List<String> warnings) throws IOException {
        File marker = new File(dataFolder, ".quest-package-imported");
        if (marker.exists()) return;
        File[] existingBundles = questFolder.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".yml"));
        if (existingBundles != null && existingBundles.length > 0) {
            archiveFlatConfiguration(warnings);
            Files.writeString(marker.toPath(), "Quest bundles already existed; flat configuration import was skipped.\n");
            warnings.add("Quest bundles already exist; old flat YAML was archived but not imported to avoid duplicate or conflicting definitions.");
            return;
        }

        YamlConfiguration imported = new YamlConfiguration();
        boolean hasImportedContent = false;
        for (Map.Entry<String, String> chapter : CHAPTER_FILES.entrySet()) {
            if (chapter.getKey().equals("quests")) continue;
            File legacyFile = new File(dataFolder, chapter.getValue());
            if (!legacyFile.isFile()) continue;
            YamlConfiguration old = new YamlConfiguration();
            try {
                old.load(legacyFile);
            } catch (IOException | InvalidConfigurationException ex) {
                warnings.add("Could not import " + legacyFile.getName() + "; it was left untouched: " + ex.getMessage());
                continue;
            }
            ConfigurationSection oldRoot = chapter.getKey().equals("npcs") && old.getConfigurationSection("npcs") != null
                    ? old.getConfigurationSection("npcs") : old;
            for (String key : oldRoot.getKeys(false)) {
                Object value = oldRoot.get(key);
                if (value instanceof ConfigurationSection section) {
                    imported.createSection(chapter.getKey() + "." + key);
                    copySection(imported, chapter.getKey() + "." + key, section);
                } else imported.set(chapter.getKey() + "." + key, value);
                hasImportedContent = true;
            }
        }

        if (hasImportedContent) imported.save(new File(questFolder, "_server-config-import.yml"));
        archiveFlatConfiguration(warnings);
        Files.writeString(marker.toPath(), "Flat modular configurations imported once. Legacy quest definitions are archived and inactive.\n");
    }

    private void archiveFlatConfiguration(List<String> warnings) throws IOException {
        File disabled = new File(questFolder, "_legacy-disabled");
        Files.createDirectories(disabled.toPath());
        List<String> filesToArchive = new ArrayList<>(CHAPTER_FILES.values());
        filesToArchive.add("wizard-quests.yml");
        for (String legacyName : filesToArchive.stream().distinct().toList()) {
            File legacy = new File(dataFolder, legacyName);
            if (legacy.isFile()) {
                File archived = new File(disabled, legacyName);
                Files.copy(legacy.toPath(), archived.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(legacy.toPath()) != Files.size(archived.toPath()))
                    throw new IOException("Could not verify archived copy of " + legacyName);
                Files.delete(legacy.toPath());
                warnings.add(legacyName + " was archived under Quest/_legacy-disabled/ and is no longer an active configuration file.");
            }
        }
    }

    private void deleteTree(File path) throws IOException {
        if (!path.exists()) return;
        File[] children = path.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        Files.deleteIfExists(path.toPath());
    }

    private String exceptionReport(Exception ex) {
        StackTraceElement source = null;
        for (StackTraceElement element : ex.getStackTrace()) {
            if (element.getClassName().startsWith("com.iekcorbar.quests.")) { source = element; break; }
        }
        String location = source == null ? "" : " at " + source.getFileName() + ":" + source.getLineNumber();
        return "Quest reload failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + location;
    }

    record ReloadReport(boolean success, int bundles, int quests, List<String> warnings, List<String> errors) {}
    private record NpcBindingOrigin(String key, File source) {}
}
