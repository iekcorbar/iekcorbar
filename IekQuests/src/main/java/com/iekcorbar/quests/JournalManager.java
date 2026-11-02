package com.iekcorbar.quests;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class JournalManager {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int PAGE_LIMIT = 900;

    private record Entry(String id, String title, List<String> description) {}

    private final IekQuestsPlugin plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private YamlConfiguration config = new YamlConfiguration();

    JournalManager(IekQuestsPlugin plugin) { this.plugin = plugin; }

    void load() {
        File file = new File(plugin.getDataFolder(), "Journal.yml");
        if (!file.exists()) plugin.saveResource("Journal.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                plugin.getLogger().warning("Journal.yml entry '" + id + "' must be a mapping.");
                continue;
            }
            if (!ID.matcher(id).matches()) plugin.getLogger().warning("Malformed journal entry ID: " + id);
            String title = strings(section, "Titolo").stream().filter(value -> !value.isBlank()).findFirst().orElse(id);
            List<String> description = strings(section, "descrizione");
            if (description.isEmpty()) plugin.getLogger().warning("Journal.yml entry '" + id + "' has no descrizione.");
            entries.put(id, new Entry(id, title, List.copyOf(description)));
        }
        validateStoredEntries();
    }

    List<String> entryIds() { return List.copyOf(entries.keySet()); }
    boolean contains(String id) { return entries.containsKey(id); }

    int add(Player player, List<String> ids) { return change(player.getUniqueId(), ids, true); }
    int remove(Player player, List<String> ids) { return change(player.getUniqueId(), ids, false); }

    int changeOnlinePlayer(CommandSender sender, String playerName, String entryId, boolean add) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) throw new IllegalArgumentException("Player must be online: " + playerName);
        if (!contains(entryId)) throw new IllegalArgumentException("Unknown Journal entry: " + entryId);
        int changed = change(player.getUniqueId(), List.of(entryId), add);
        sender.sendMessage((add ? "Added " : "Removed ") + "Journal entry '" + entryId + "' "
                + (add ? "to " : "from ") + player.getName() + (changed == 0 ? " (no change)." : "."));
        return changed;
    }

    private int change(UUID uuid, List<String> ids, boolean add) {
        String path = "journal." + uuid + ".entries";
        List<String> assigned = new ArrayList<>(plugin.dialogueData().getStringList(path));
        int changed = 0;
        for (String id : ids) {
            if (!entries.containsKey(id)) {
                plugin.getLogger().warning("Ignored unknown Journal entry '" + id + "' for " + uuid + ".");
                continue;
            }
            if (add) {
                if (!assigned.contains(id)) { assigned.add(id); changed++; }
            } else if (assigned.remove(id)) changed++;
        }
        if (changed > 0) {
            plugin.dialogueData().set(path, assigned);
            plugin.save();
        }
        return changed;
    }

    void open(Player player) {
        List<Component> pages = new ArrayList<>();
        List<String> assigned = plugin.dialogueData().getStringList("journal." + player.getUniqueId() + ".entries");
        for (String id : assigned) {
            Entry entry = entries.get(id);
            if (entry == null) {
                plugin.getLogger().warning("Player " + player.getUniqueId() + " has unknown Journal entry '" + id + "'.");
                continue;
            }
            String text = entry.title() + "\n\n" + String.join("\n", entry.description());
            for (String page : splitPages(text)) pages.add(Component.text(page));
        }
        if (pages.isEmpty()) pages.add(Component.text("Your Journal is empty."));

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Journal"));
        meta.author(Component.text("IekQuests"));
        meta.pages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private List<String> splitPages(String value) {
        List<String> pages = new ArrayList<>();
        String remaining = value == null ? "" : value;
        while (remaining.length() > PAGE_LIMIT) {
            int split = remaining.lastIndexOf('\n', PAGE_LIMIT);
            if (split < PAGE_LIMIT / 2) split = remaining.lastIndexOf(' ', PAGE_LIMIT);
            if (split < PAGE_LIMIT / 2) split = PAGE_LIMIT;
            pages.add(remaining.substring(0, split));
            remaining = remaining.substring(split).stripLeading();
        }
        pages.add(remaining);
        return pages;
    }

    private List<String> strings(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (value instanceof List<?> list) return list.stream().filter(item -> item != null).map(String::valueOf).toList();
        if (value instanceof String string && !string.isBlank()) return List.of(string);
        return List.of();
    }

    private void validateStoredEntries() {
        ConfigurationSection root = plugin.dialogueData().getConfigurationSection("journal");
        if (root == null) return;
        for (String uuid : root.getKeys(false)) {
            List<String> assigned = plugin.dialogueData().getStringList("journal." + uuid + ".entries");
            for (String id : assigned) if (!entries.containsKey(id))
                plugin.getLogger().warning("Stored Journal entry '" + id + "' for " + uuid + " is not defined in Journal.yml.");
        }
    }
}
