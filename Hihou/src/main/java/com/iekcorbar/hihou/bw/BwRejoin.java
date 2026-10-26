package com.iekcorbar.hihou.bw;

import com.iekcorbar.hihou.HihouPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Offline-safe PlayerGoods, our take on BedWars' ReJoin store: when a participant quits mid-game,
 * their hand-in (server inventory, location, gamemode, health, food, xp) is written to
 * bw-rejoin.json as a Bukkit-object Base64 blob - the same serialization the chests use, but
 * session-sized: the entry only lives until the game ends and the person logs back in. Then the
 * goods are handed over, and while the match is still running the player gets a "/ih bw rejoin"
 * window until they use it or the arena finishes.
 */
final class BwRejoin {
    static final class Entry {
        String arena, team, loc, gm;
        double health;
        int food;
        float xp;
        int level;
        String blob;
        boolean ended;    // the match finished while they were offline
        boolean restored; // goods were already handed back after a relog (rejoin still offered)
    }

    private final HihouPlugin plugin;
    private final YamlConfiguration y = new YamlConfiguration();
    private final File file;
    private final Map<UUID, Entry> pending = new LinkedHashMap<>();

    BwRejoin(HihouPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "bw-rejoin.json");
        if (file.exists()) {
            try {
                y.load(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read bw-rejoin.json: " + e.getMessage());
            }
        }
        var root = y.getConfigurationSection("entries");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(id);
                    Entry en = new Entry();
                    en.arena = y.getString("entries." + id + ".arena", "");
                    en.team = y.getString("entries." + id + ".team", "");
                    en.loc = y.getString("entries." + id + ".loc");
                    en.gm = y.getString("entries." + id + ".gm", "SURVIVAL");
                    en.health = y.getDouble("entries." + id + ".health", 20);
                    en.food = y.getInt("entries." + id + ".food", 20);
                    en.xp = (float) y.getDouble("entries." + id + ".xp", 0);
                    en.level = y.getInt("entries." + id + ".level", 0);
                    en.blob = y.getString("entries." + id + ".blob", "");
                    en.ended = y.getBoolean("entries." + id + ".ended", false);
                    pending.put(u, en);
                } catch (IllegalArgumentException ignore) {
                    plugin.getLogger().warning("Skipped bad rejoin entry: " + id);
                }
            }
        }
    }

    Entry get(UUID u) {
        return pending.get(u);
    }

    /** Persist what a mid-game quitter handed in. */
    void put(UUID u, BwArena a, BwTeam team, BwArena.Goods g) {
        Entry en = new Entry();
        en.arena = a.name;
        en.team = team != null ? team.name() : "";
        en.loc = BwGameManager.str(g.location);
        en.gm = g.gameMode.name();
        en.health = g.health;
        en.food = g.food;
        en.xp = g.xp;
        en.level = g.level;
        en.blob = encode(g);
        pending.put(u, en);
        write(u, en);
        save();
    }

    void remove(UUID u) {
        pending.remove(u);
        y.set("entries." + u, null);
        save();
    }

    /**
     * The match for this arena is over: offline people get their goods at next login,
     * re-logged pending entries are simply gone (they were already handed back).
     */
    void markEnded(String arena) {
        for (Map.Entry<UUID, Entry> e : new LinkedHashMap<>(pending).entrySet()) {
            if (!e.getValue().arena.equals(arena)) continue;
            if (e.getValue().restored) remove(e.getKey());
            else {
                e.getValue().ended = true;
                y.set("entries." + e.getKey() + ".ended", true);
            }
        }
        save();
    }

    void restored(UUID u, Entry en) {
        en.restored = true;
        write(u, en);
        save();
    }

    BwArena.Goods toGoods(Entry en) {
        BwArena.Goods g = new BwArena.Goods();
        g.location = BwGameManager.loc(en.loc);
        try {
            g.gameMode = org.bukkit.GameMode.valueOf(en.gm.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            g.gameMode = org.bukkit.GameMode.SURVIVAL;
        }
        g.health = en.health;
        g.food = en.food;
        g.xp = en.xp;
        g.level = en.level;
        decode(en.blob, g);
        return g;
    }

    private void write(UUID u, Entry en) {
        String p = "entries." + u;
        y.set(p + ".arena", en.arena);
        y.set(p + ".team", en.team);
        y.set(p + ".loc", en.loc);
        y.set(p + ".gm", en.gm);
        y.set(p + ".health", en.health);
        y.set(p + ".food", en.food);
        y.set(p + ".xp", (double) en.xp);
        y.set(p + ".level", en.level);
        y.set(p + ".blob", en.blob);
        y.set(p + ".ended", en.ended);
        y.set(p + ".restored", en.restored);
    }

    private void save() {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            y.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save bw-rejoin.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ goods <-> base64 (ChestStore trick)

    private static String encode(BwArena.Goods g) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bo)) {
            out.writeInt(g.contents.length);
            for (ItemStack it : g.contents) out.writeObject(it == null ? new ItemStack(org.bukkit.Material.AIR) : it);
            out.writeInt(g.armor.length);
            for (ItemStack it : g.armor) out.writeObject(it == null ? new ItemStack(org.bukkit.Material.AIR) : it);
            return Base64.getEncoder().encodeToString(bo.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static void decode(String blob, BwArena.Goods g) {
        g.contents = new ItemStack[36];
        g.armor = new ItemStack[4];
        if (blob == null || blob.isEmpty()) return;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(blob)))) {
            int n = in.readInt();
            ItemStack[] c = new ItemStack[n];
            for (int i = 0; i < n; i++) c[i] = (ItemStack) in.readObject();
            int m = in.readInt();
            ItemStack[] a = new ItemStack[m];
            for (int i = 0; i < m; i++) a[i] = (ItemStack) in.readObject();
            g.contents = c;
            g.armor = a;
        } catch (Exception e) {
            // a broken blob hands back an empty inventory; the memory copy for online players is untouched
        }
    }
}
