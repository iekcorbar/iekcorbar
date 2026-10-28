package com.iekcorbar.relics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom music discs that work in a normal jukebox. The list comes from the "discs" section of the plugin
 * config (Relics/config.yml on the server; the generated block between the BEGIN/END markers in the shipped
 * default, written by tools/make_discs.py from the built-in Christmas carols plus every audio file dropped in
 * Relics/discs/). A disc is a paper item with the jukebox_playable component pointing at the song relics:&lt;id&gt;;
 * the songs themselves are a tiny data pack that this class writes into the main world's datapacks folder (the
 * server must be restarted once after it changes).
 */
final class Discs implements Listener {
    record Disc(String id, String title, int seconds, String event, Material recipe, int colour) {
    }

    private final RelicsPlugin plugin;
    private final NamespacedKey discKey;
    final Map<String, Disc> discs = new LinkedHashMap<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    Discs(RelicsPlugin plugin) {
        this.plugin = plugin;
        this.discKey = new NamespacedKey(plugin, "disc");
        load();
    }

    private void load() {
        discs.clear();
        var section = plugin.getConfig().getConfigurationSection("discs");
        if (section == null) {
            plugin.getLogger().warning("No music discs in the Relics config (discs:) - add some there to have custom discs.");
            return;
        }
        for (String id : section.getKeys(false)) {
            var d = section.getConfigurationSection(id);
            if (d == null) continue; // a bare key with no fields: not a disc
            String did = id.toLowerCase(Locale.ROOT);
            discs.put(did, new Disc(did, d.getString("title", did), d.getInt("seconds"), d.getString("event", "disc." + did),
                    d.getString("recipe", "").isBlank() ? null : Material.matchMaterial(d.getString("recipe")),
                    d.getInt("colour")));
        }
        plugin.getLogger().info("Relics: loaded " + discs.size() + " music disc(s) from the config (discs:).");
    }

    boolean isDisc(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(discKey, PersistentDataType.STRING);
    }

    ItemStack item(Disc d) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Music Disc", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(d.title(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Play it in a jukebox", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(discKey, PersistentDataType.STRING, d.id());
        try {
            JukeboxPlayableComponent c = m.getJukeboxPlayable();
            c.setSongKey(new NamespacedKey("relics", d.id()));
            m.setJukeboxPlayable(c);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Disc " + d.id() + ": could not set the jukebox song (" + e + ")");
        }
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", "disc_" + d.id()));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        return it;
    }

    List<ItemStack> items() {
        List<ItemStack> out = new ArrayList<>();
        for (Disc d : discs.values()) out.add(item(d));
        return out;
    }

    // ------------------------------------------------------------ recipes

    void registerRecipes() {
        for (Disc d : discs.values()) {
            if (d.recipe() == null) continue;
            NamespacedKey k = new NamespacedKey(plugin, "disc_" + d.id());
            ShapelessRecipe r = new ShapelessRecipe(k, item(d));
            r.addIngredient(Material.NOTE_BLOCK);
            r.addIngredient(Material.PAPER);
            r.addIngredient(Material.BLACK_DYE);
            r.addIngredient(d.recipe());
            Bukkit.removeRecipe(k, false);
            Bukkit.addRecipe(r, false);
            recipeKeys.add(k);
        }
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    // ------------------------------------------------------------ the data pack with the songs

    /** Writes world/datapacks/relics_discs (jukebox_song files). Returns true when something changed. */
    boolean installDatapack() {
        if (discs.isEmpty() || Bukkit.getWorlds().isEmpty()) return false;
        World w = Bukkit.getWorlds().get(0);
        // Let op: Bukkit's getWorldFolder() points at the DIMENSION folder of the
        // overworld (world/dimensions/minecraft/overworld) since the 1.16 rework -
        // datapacks there are not picked up for global registries like jukebox_song.
        // The real world root (next to server.properties, where every other plugin's
        // pack also lives) is worldContainer/worldName: that is where the songs must land.
        Path base = Bukkit.getWorldContainer().toPath().resolve(w.getName()).resolve("datapacks").resolve("relics_discs");
        boolean changed = false;
        try {
            changed |= write(base.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Relics music discs\",\"pack_format\":64,"
                    + "\"min_format\":1,\"max_format\":999,\"supported_formats\":{\"min_inclusive\":1,\"max_inclusive\":999}}}");
            for (Disc d : discs.values()) {
                String json = "{\"comparator_output\":" + (1 + Math.floorMod(d.colour(), 14)) + ",\"description\":{\"text\":\"" + esc(d.title())
                        + "\"},\"length_in_seconds\":" + d.seconds() + ",\"sound_event\":{\"sound_id\":\"relics:" + d.event() + "\"}}";
                changed |= write(base.resolve("data/relics/jukebox_song/" + d.id() + ".json"), json);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the disc data pack: " + e);
        }
        if (changed) plugin.getLogger().warning("Disc songs written to " + base + " - restart the server once so jukeboxes know them.");
        return changed;
    }

    private static boolean write(Path p, String content) throws IOException {
        if (Files.exists(p) && Files.readString(p, StandardCharsets.UTF_8).equals(content)) return false;
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return true;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    Disc byId(String id) {
        return id == null ? null : discs.get(id.toLowerCase(Locale.ROOT));
    }
}
