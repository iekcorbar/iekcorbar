package com.iekcorbar.satchel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared advancement helper (propkit/Adv.java, copied into each plugin by propkit/sync.py - edit it only there).
 * One tab per plugin in the advancement screen, vanilla toasts, nothing in chat. Advancements are loaded with
 * Bukkit.getUnsafe().loadAdvancement (again after /minecraft:reload) and awarded by code: {@link #grant}, or
 * automatically for items the player holds ({@link #item} + {@link #scanner}, checked every few seconds, which also
 * covers crafting, trading, picking up and /give).
 */
final class Adv implements Listener {
    private record Def(String id, String parent, String icon, String title, String desc, String frame, boolean hidden, boolean glint) {
    }

    private final JavaPlugin plugin;
    private final String tab;
    private final String title, desc, icon, background;
    private final boolean ownRoot;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, String> byItem = new HashMap<>();
    private final Map<String, List<String>> composites = new LinkedHashMap<>();
    private Function<ItemStack, String> keyOf;
    private Def last;

    /** ownRoot false = the tab's root is made elsewhere (Wildlife's journal); advancements then hang below "root". */
    Adv(JavaPlugin plugin, String tab, String title, String desc, String icon, String background, boolean ownRoot) {
        this.plugin = plugin;
        this.tab = tab;
        this.title = title;
        this.desc = desc;
        this.icon = icon;
        this.background = background;
        this.ownRoot = ownRoot;
    }

    // ------------------------------------------------------------ definitions

    /** frame: task | goal | challenge. parent null = directly below the tab root. */
    Adv add(String id, String parent, String icon, String title, String desc, String frame) {
        last = new Def(id, parent == null ? "root" : parent, icon, title, desc, frame, false, false);
        defs.put(id, last);
        return this;
    }

    /** The advancement added last is hidden until earned. */
    Adv hidden() {
        defs.put(last.id, last = new Def(last.id, last.parent, last.icon, last.title, last.desc, last.frame, true, last.glint));
        return this;
    }

    /** The advancement added last gets an enchantment glint on its icon. */
    Adv glint() {
        defs.put(last.id, last = new Def(last.id, last.parent, last.icon, last.title, last.desc, last.frame, last.hidden, true));
        return this;
    }

    /** The advancement added last is global (server-wide milestone; the awarding plugin decides what that means). */
    Adv global() {
        return this;
    }

    /** Holding the item with this key (see {@link #scanner}) earns the advancement. */
    Adv item(String itemKey, String advId) {
        byItem.put(itemKey, advId);
        return this;
    }

    /** Tells the item scan how to name an item: return a key used with {@link #item}, or null. */
    Adv scanner(Function<ItemStack, String> keyOf) {
        this.keyOf = keyOf;
        return this;
    }

    /** The advancement added last... use after add(): earned automatically once all the listed advancements are earned. */
    Adv allOf(String... ids) {
        composites.put(last.id, List.of(ids));
        return this;
    }

    int size() {
        return defs.size();
    }

    // ------------------------------------------------------------ registering

    NamespacedKey key(String id) {
        return new NamespacedKey(plugin, tab + "/" + id);
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        register();
        if (keyOf != null && !byItem.isEmpty()) Bukkit.getScheduler().runTaskTimer(plugin, this::scanAll, 200L, 100L);
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent e) {
        if (ownRoot) register(); // /minecraft:reload drops plugin-loaded advancements (a tab with a foreign root is re-registered by its owner)
    }

    void register() {
        if (!ownRoot && Bukkit.getAdvancement(key("root")) == null) return; // the root comes first
        boolean anyNew = false;
        if (ownRoot && Bukkit.getAdvancement(key("root")) == null) anyNew = true;
        for (String id : defs.keySet()) if (Bukkit.getAdvancement(key(id)) == null) anyNew = true;
        if (!anyNew) return;
        Bukkit.savePlayers(); // loading advancements reloads every player's progress from disk
        if (ownRoot) load(key("root"), json(null, icon, title, desc, "task", false, false, background));
        for (Def d : defs.values()) load(key(d.id), json(key(d.parent), d.icon, d.title, d.desc, d.frame, d.hidden, d.glint, null));
    }

    private JsonObject json(NamespacedKey parent, String icon, String title, String desc, String frame, boolean hidden, boolean glint, String bg) {
        JsonObject json = new JsonObject();
        if (parent != null) json.addProperty("parent", parent.toString());
        JsonObject display = new JsonObject();
        JsonObject iconObj = new JsonObject();
        iconObj.addProperty("id", "minecraft:" + icon);
        if (glint) iconObj.add("components", JsonParser.parseString("{\"minecraft:enchantment_glint_override\":true}"));
        display.add("icon", iconObj);
        display.add("title", JsonParser.parseString(GsonComponentSerializer.gson().serialize(Component.text(title))));
        display.add("description", JsonParser.parseString(GsonComponentSerializer.gson().serialize(Component.text(desc))));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", bg == null);   // the tab root never pops up
        display.addProperty("announce_to_chat", false);  // toasts only, nothing in chat
        display.addProperty("hidden", hidden);
        if (bg != null) display.addProperty("background", bg);
        json.add("display", display);
        json.add("criteria", JsonParser.parseString("{\"done\":{\"trigger\":\"minecraft:impossible\"}}"));
        return json;
    }

    @SuppressWarnings("deprecation")
    private void load(NamespacedKey key, JsonObject json) {
        try {
            if (Bukkit.getAdvancement(key) != null) return;
            if (Bukkit.getUnsafe().loadAdvancement(key, json.toString()) == null)
                plugin.getLogger().warning("Minecraft rejected advancement " + key);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load advancement " + key + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ awarding

    /** Awards the advancement (and the tab root first, so the tab shows up). No-op when already earned. */
    void grant(Player p, String id) {
        if (p == null) return;
        Advancement adv = Bukkit.getAdvancement(key(id));
        if (adv == null) return;
        AdvancementProgress pr = p.getAdvancementProgress(adv);
        if (pr.isDone()) return;
        if (ownRoot) {
            Advancement root = Bukkit.getAdvancement(key("root"));
            if (root != null) for (String c : new ArrayList<>(p.getAdvancementProgress(root).getRemainingCriteria())) p.getAdvancementProgress(root).awardCriteria(c);
        }
        for (String c : new ArrayList<>(pr.getRemainingCriteria())) pr.awardCriteria(c);
        for (Map.Entry<String, List<String>> en : composites.entrySet()) {
            if (en.getKey().equals(id) || en.getValue().contains(id) == false) continue;
            boolean all = true;
            for (String need : en.getValue()) if (!has(p, need)) all = false;
            if (all) grant(p, en.getKey());
        }
    }

    boolean has(Player p, String id) {
        Advancement adv = Bukkit.getAdvancement(key(id));
        return adv != null && p.getAdvancementProgress(adv).isDone();
    }

    /** Awards an advancement when the player holds one of the listed items; also useful from event handlers. */
    void scan(Player p) {
        if (keyOf == null) return;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            String k = keyOf.apply(it);
            if (k == null) continue;
            for (String one : k.split("[|]")) { // a scanner may return several keys joined with |
                String id = byItem.get(one);
                if (id != null) grant(p, id);
            }
        }
    }

    private void scanAll() {
        for (Player p : Bukkit.getOnlinePlayers()) scan(p);
    }
}
