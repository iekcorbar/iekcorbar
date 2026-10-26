package com.iekcorbar.gouw;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All gouwen in memory, saved as plain readable JSON (plugins/Gouw/claims.json): a few hundred lines of
 * name + owner + world + chunk list + trusted + member flags. Simple on purpose - the day Gouw itself is
 * replaced, the next plugin reads this file with the same ease Gouw reads Lands' database.
 */
final class ClaimStore {
    private final GouwPlugin plugin;
    private final File file;
    /** one gouw per owner (like the old server's usage: everyone had exactly one) */
    private final Map<UUID, Land> byOwner = new HashMap<>();
    /** world + chunk key -> land */
    private final Map<String, Land> index = new HashMap<>();

    ClaimStore(GouwPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    // ------------------------------------------------------------ load / save

    /** @return false when claims.json does not exist yet (first start: the importer gets a chance). */
    boolean load() {
        if (!file.exists()) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("lands")) {
                JsonObject o = e.getAsJsonObject();
                Land l = new Land(o.get("name").getAsString(), UUID.fromString(o.get("owner").getAsString()), o.get("world").getAsString());
                for (JsonElement c : o.getAsJsonArray("chunks")) {
                    JsonArray xy = c.getAsJsonArray();
                    addChunk(l, xy.get(0).getAsInt(), xy.get(1).getAsInt());
                }
                if (o.has("trusted"))
                    for (JsonElement t : o.getAsJsonArray("trusted")) l.trusted.add(UUID.fromString(t.getAsString()));
                if (o.has("flags")) {
                    l.memberFlags.clear();
                    for (JsonElement f : o.getAsJsonArray("flags")) l.memberFlags.add(Land.Flag.valueOf(f.getAsString()));
                }
                byOwner.put(l.owner, l);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("claims.json is unreadable (" + ex.getMessage() + ") - claims left in memory as they are; fix the file and /gouw admin reload.");
            return true;
        }
    }

    void save() {
        StringBuilder sb = new StringBuilder("{\n  \"lands\": [\n");
        boolean first = true;
        for (Land l : byOwner.values()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {\"name\": \"").append(esc(l.name)).append("\", \"owner\": \"").append(l.owner)
                    .append("\", \"world\": \"").append(esc(l.world)).append("\", \"chunks\": [");
            boolean fc = true;
            for (long k : l.chunks) {
                if (!fc) sb.append(", ");
                fc = false;
                sb.append('[').append(Land.kx(k)).append(", ").append(Land.kz(k)).append(']');
            }
            sb.append("], \"trusted\": [");
            boolean ft = true;
            for (UUID u : l.trusted) {
                if (!ft) sb.append(", ");
                ft = false;
                sb.append('"').append(u).append('"');
            }
            sb.append("], \"flags\": [");
            boolean ff = true;
            for (Land.Flag f : Land.Flag.values()) {
                if (!l.memberFlags.contains(f)) continue;
                if (!ff) sb.append(", ");
                ff = false;
                sb.append('"').append(f.name()).append('"');
            }
            sb.append("]}");
        }
        sb.append("\n  ]\n}\n");
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not save claims.json: " + ex.getMessage());
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------ queries

    Collection<Land> all() {
        return byOwner.values();
    }

    int totalChunks() {
        return index.size();
    }

    Land land(String world, int cx, int cz) {
        return index.get(world + "|" + Land.key(cx, cz));
    }

    Land land(World w, int blockX, int blockZ) {
        return land(w.getName(), blockX >> 4, blockZ >> 4);
    }

    Land landOf(UUID owner) {
        return byOwner.get(owner);
    }

    /** True when this player may `flag` here: no gouw, bypassing, owner, or member with that flag on. */
    boolean may(Player p, World w, int blockX, int blockZ, Land.Flag flag) {
        if (plugin.isBypassing(p.getUniqueId())) return true;
        Land l = land(w, blockX, blockZ);
        return l == null || l.may(p.getUniqueId(), flag);
    }

    /** Distinct other gouwen within r chunks (Chebyshev) of this chunk. */
    List<Land> nearby(String world, int cx, int cz, int r) {
        List<Land> out = new ArrayList<>();
        for (Land l : byOwner.values()) {
            if (!l.world.equals(world)) continue;
            for (long k : l.chunks) {
                if (Math.max(Math.abs(Land.kx(k) - cx), Math.abs(Land.kz(k) - cz)) <= r) {
                    out.add(l);
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ mutations

    /** @return null on success, otherwise the reason it could not claim. */
    String claim(Player p, World w, int cx, int cz) {
        if (land(w.getName(), cx, cz) != null) return "this chunk is already claimed";
        if (!p.hasPermission("gouw.claim")) return "you are not allowed to claim";
        Land l = byOwner.get(p.getUniqueId());
        if (l == null) {
            l = new Land(p.getName(), p.getUniqueId(), w.getName());
            byOwner.put(l.owner, l);
        } else if (!l.world.equals(w.getName())) {
            return "your gouw lies in " + l.world;
        }
        int max = p.hasPermission("gouw.admin") ? Integer.MAX_VALUE : plugin.getMaxChunks();
        if (l.chunks.size() >= max) return "you are at your limit of " + max + " chunks";
        addChunk(l, cx, cz);
        save();
        return null;
    }

    String unclaim(Player p, World w, int cx, int cz) {
        Land l = land(w.getName(), cx, cz);
        if (l == null) return "nothing claimed here";
        if (!l.owner.equals(p.getUniqueId()) && !p.hasPermission("gouw.admin")) return "this is " + l.name + "'s gouw";
        l.chunks.remove(Land.key(cx, cz));
        index.remove(w.getName() + "|" + Land.key(cx, cz));
        if (l.chunks.isEmpty()) byOwner.remove(l.owner);
        save();
        return null;
    }

    void unclaimAll(Land l) {
        for (long k : l.chunks) index.remove(l.world + "|" + k);
        byOwner.remove(l.owner);
        save();
    }

    void rename(Land l, String name) {
        l.name = name;
        save();
    }

    boolean toggleFlag(Land l, Land.Flag f) {
        if (!l.memberFlags.remove(f)) {
            l.memberFlags.add(f);
            save();
            return true;
        }
        save();
        return false;
    }

    boolean trust(Land l, UUID who) {
        if (who.equals(l.owner) || !l.trusted.add(who)) return false;
        save();
        return true;
    }

    boolean untrust(Land l, UUID who) {
        if (!l.trusted.remove(who)) return false;
        save();
        return true;
    }

    /** Used by the importer and by claim(): merge into an existing owner's gouw. */
    Land add(String name, UUID owner, String world) {
        return byOwner.computeIfAbsent(owner, o -> new Land(name, o, world));
    }

    boolean addChunk(Land l, int cx, int cz) {
        String k = l.world + "|" + Land.key(cx, cz);
        if (index.containsKey(k)) return false;
        l.chunks.add(Land.key(cx, cz));
        index.put(k, l);
        return true;
    }
}
