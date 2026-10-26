package com.iekcorbar.wildlife;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The collecting side: watch an animal through a spyglass for a moment and it
 * goes into your Field Journal (stored in the player's own data, like the books'
 * read list in IekcorbarSiteSync). Every species and every rare look is a real
 * advancement in a "Wildlife" tab - with the vanilla toast - plus milestones.
 * Advancements are registered with Bukkit.getUnsafe().loadAdvancement (same
 * technique as IekcorbarSiteSync's AdvancementManager), again after /minecraft:reload.
 */
final class Journal implements Listener {

    private record Watch(UUID target, int checks) {}

    private final WildlifePlugin plugin;
    private final NamespacedKey seenKey, rareKey;
    private final Map<UUID, Watch> watching = new HashMap<>();

    Journal(WildlifePlugin plugin) {
        this.plugin = plugin;
        seenKey = new NamespacedKey(plugin, "seen");
        rareKey = new NamespacedKey(plugin, "seen_rare");
    }

    // ------------------------------------------------------------ data

    Set<String> seen(Player p, boolean rare) {
        String s = p.getPersistentDataContainer().get(rare ? rareKey : seenKey, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<>();
        if (s != null && !s.isEmpty()) for (String id : s.split(",")) out.add(id);
        return out;
    }

    private void save(Player p, boolean rare, Set<String> ids) {
        p.getPersistentDataContainer().set(rare ? rareKey : seenKey, PersistentDataType.STRING, String.join(",", ids));
    }

    // ------------------------------------------------------------ observing (every 5 ticks)

    void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.isHandRaised() || p.getActiveItem().getType() != Material.SPYGLASS) {
                watching.remove(p.getUniqueId());
                continue;
            }
            Entity t = p.getTargetEntity(96, false);
            Animals.Animal a = plugin.animals().get(t);
            if (a == null) {
                watching.remove(p.getUniqueId());
                continue;
            }
            Watch w = watching.get(p.getUniqueId());
            int checks = w != null && w.target().equals(t.getUniqueId()) ? w.checks() + 1 : 1;
            if (a.fleeUntil > plugin.tick || a.panicUntil > plugin.tick) checks = 0; // a running animal cannot be observed
            watching.put(p.getUniqueId(), new Watch(t.getUniqueId(), checks));
            // Safari (0.6.0): shy animals need longer; far-off steady watching pays, point-blank does not.
            int need = a.species.observeChecks();
            double dist = t.getLocation().distance(p.getLocation());
            if (dist > 45) need = Math.max(2, need / 2);
            else if (dist < 10) need = need * 2;
            boolean known = seen(p, false).contains(a.species.id()) && (!a.rare || seen(p, true).contains(a.species.id()));
            if (known) {
                p.sendActionBar(Component.text(a.name(), NamedTextColor.GREEN).append(Component.text("  - in your journal", NamedTextColor.GRAY)));
            } else if (checks < need) {
                p.sendActionBar(Component.text("Observing", NamedTextColor.GOLD).append(Component.text(" " + "•".repeat(Math.min(checks, need)) + "◦".repeat(Math.max(0, need - checks)), NamedTextColor.YELLOW)));
            } else {
                record(p, a);
            }
        }
    }

    private void record(Player p, Animals.Animal a) {
        Set<String> seen = seen(p, false);
        String id = a.species.id();
        boolean newSpecies = seen.add(id);
        if (newSpecies) {
            save(p, false, seen);
            award(p, key(id));
            p.sendActionBar(Component.text("New species: " + a.species.displayName, NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        }
        if (a.rare) {
            Set<String> rares = seen(p, true);
            if (rares.add(id)) {
                save(p, true, rares);
                award(p, key(id + "_rare"));
                p.sendActionBar(Component.text("Rare find: " + a.species.rareName() + "!", NamedTextColor.LIGHT_PURPLE));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            }
            if (rares.size() >= 1) award(p, key("first_rare"));
            if (rares.size() >= 5) award(p, key("rare_collector"));
        }
        int n = seen.size();
        if (n >= 5) award(p, key("naturalist"));
        if (n >= 10) award(p, key("field_biologist"));
        if (n >= Species.values().length) award(p, key("living_encyclopedia"));
    }

    // ------------------------------------------------------------ the journal book

    void open(Player p) {
        Set<String> seen = seen(p, false), rares = seen(p, true);
        List<Component> pages = new ArrayList<>();
        pages.add(Component.text("Field Journal\n\n", NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD)
                .append(Component.text("Species: " + seen.size() + " / " + Species.values().length + "\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Rare looks: " + rares.size() + " / " + Species.values().length + "\n\n", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Watch an animal through a spyglass to record it - shy ones take longer.\n\nWalk or sprint close and they bolt; sneak up slowly and keep your distance. A steady view from afar records fastest.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false)));
        for (Species s : Species.values()) {
            if (seen.contains(s.id())) {
                Component page = Component.text(s.displayName + "\n", NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD)
                        .append(Component.text(where(s) + "\n\n", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false).decorate(TextDecoration.ITALIC))
                        .append(Component.text(s.notes + "\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
                String[] fk = Facts.of(s);
                if (fk != null) {
                    page = page.append(Component.text("\nDid you know? ", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, true))
                            .append(Component.text(fk[0] + "\n", NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false))
                            .append(Component.text("Myth: ", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
                                    .append(Component.text(fk[1], NamedTextColor.DARK_GRAY)).decoration(TextDecoration.BOLD, false).decorate(TextDecoration.ITALIC))
                            .append(Component.text("\n", NamedTextColor.BLACK));
                }
                page = page.append(Component.text("\n", NamedTextColor.BLACK))
                        .append(rares.contains(s.id())
                                ? Component.text("Rare: " + s.rareName() + " ✔", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, false)
                                : Component.text("Rare: ???", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
                pages.add(page);
            } else {
                pages.add(Component.text("???\n\n", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                        .append(Component.text("Not yet seen.\n\nHint: " + s.hint, NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false)));
            }
        }
        p.openBook(Book.book(Component.text("Field Journal"), Component.text(p.getName()), pages));
    }

    private static String where(Species s) {
        String time = switch (s.time) {
            case DAY -> "by day";
            case NIGHT -> "at night";
            default -> "day and night";
        };
        return String.join(", ", s.biomes).replace('_', ' ') + " - " + time;
    }

    // ------------------------------------------------------------ advancements

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, "wildlife/" + id);
    }

    private void award(Player p, NamespacedKey k) {
        Advancement adv = Bukkit.getAdvancement(k);
        if (adv == null) return;
        AdvancementProgress pr = p.getAdvancementProgress(adv);
        for (String c : new ArrayList<>(pr.getRemainingCriteria())) pr.awardCriteria(c);
    }

    /** Re-awards everything in the player's journal (after an advancement reset or a fresh registration). */
    void sync(Player p) {
        Set<String> seen = seen(p, false), rares = seen(p, true);
        for (String id : seen) award(p, key(id));
        for (String id : rares) award(p, key(id + "_rare"));
        if (!rares.isEmpty()) award(p, key("first_rare"));
        if (rares.size() >= 5) award(p, key("rare_collector"));
        if (seen.size() >= 5) award(p, key("naturalist"));
        if (seen.size() >= 10) award(p, key("field_biologist"));
        if (seen.size() >= Species.values().length) award(p, key("living_encyclopedia"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sync(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        watching.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent e) {
        registerAdvancements(); // /minecraft:reload drops plugin-loaded advancements
    }

    void registerAdvancements() {
        Bukkit.savePlayers(); // loading advancements reloads every player's progress from disk
        load(key("root"), adv(null, "spyglass", "Wildlife", "Watch animals through a spyglass to fill your Field Journal.",
                "task", false, false, false, "minecraft:tick"), true);
        for (Species s : Species.values()) {
            load(key(s.id()), adv(key("root"), s.icon, s.displayName, "Observe a " + s.displayName.toLowerCase() + " through a spyglass.",
                    "task", true, false, false, null), false);
            load(key(s.id() + "_rare"), advGlint(key(s.id()), s.icon, s.rareName(), "Spot the rare " + s.rareName().toLowerCase() + ".",
                    "goal", true, true, true), false);
        }
        load(key("naturalist"), adv(key("root"), "writable_book", "Naturalist", "Record 5 different species.", "goal", true, true, false, null), false);
        load(key("field_biologist"), adv(key("naturalist"), "book", "Field Biologist", "Record 10 different species.", "goal", true, true, false, null), false);
        load(key("living_encyclopedia"), adv(key("field_biologist"), "enchanted_book", "Living Encyclopedia",
                "Record every species in the Field Journal.", "challenge", true, true, false, null), false);
        load(key("first_rare"), adv(key("root"), "amethyst_shard", "Lucky Eye", "Spot your first rare animal.", "goal", true, true, false, null), false);
        load(key("rare_collector"), adv(key("first_rare"), "amethyst_cluster", "Rare Collector", "Spot 5 different rare animals.",
                "challenge", true, true, false, null), false);
        if (plugin.adv() != null) plugin.adv().register(); // the extra entries hang below this root
        for (Player p : Bukkit.getOnlinePlayers()) sync(p);
    }

    private JsonObject adv(NamespacedKey parent, String icon, String title, String desc, String frame, boolean toast,
                           boolean announce, boolean hidden, String trigger) {
        JsonObject json = new JsonObject();
        if (parent != null) json.addProperty("parent", parent.toString());
        JsonObject display = new JsonObject();
        JsonObject iconObj = new JsonObject();
        iconObj.addProperty("id", "minecraft:" + icon);
        display.add("icon", iconObj);
        display.add("title", JsonParser.parseString(GsonComponentSerializer.gson().serialize(Component.text(title))));
        display.add("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(Component.text(desc))));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", false); // toasts only, nothing in chat
        display.addProperty("hidden", hidden);
        if (parent == null) display.addProperty("background", "minecraft:block/moss_block");
        json.add("display", display);
        json.add("criteria", JsonParser.parseString("{\"done\":{\"trigger\":\"" + (trigger == null ? "minecraft:impossible" : trigger) + "\"}}"));
        return json;
    }

    private JsonObject advGlint(NamespacedKey parent, String icon, String title, String desc, String frame, boolean toast,
                                boolean announce, boolean hidden) {
        JsonObject json = adv(parent, icon, title, desc, frame, toast, announce, hidden, null);
        json.getAsJsonObject("display").getAsJsonObject("icon").add("components",
                JsonParser.parseString("{\"minecraft:enchantment_glint_override\":true}"));
        return json;
    }

    @SuppressWarnings("deprecation")
    private void load(NamespacedKey key, JsonObject json, boolean root) { // root: the tab itself
        try {
            if (Bukkit.getAdvancement(key) != null) return; // already there (registered before /reload)
            if (Bukkit.getUnsafe().loadAdvancement(key, json.toString()) == null)
                plugin.getLogger().warning("Minecraft rejected advancement " + key);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load advancement " + key + ": " + e.getMessage());
        }
    }
}
