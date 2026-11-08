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
 * All gouwen in memory, saved as plain readable JSON (plugins/Gouw/claims.json). Since 0.4 a gouw also
 * carries town data: ranks (assistant/resident), a job per member, the town bank and bought chunks.
 * The old "trusted" array is still written (derived from ranks) so SiteSync's GouwClaims keeps reading
 * the file the same way. Simple on purpose - the day Gouw itself is replaced, the next plugin reads this
 * file with the same ease Gouw reads Lands' database.
 */
final class ClaimStore {
    private final GouwPlugin plugin;
    private final File file;
    /** one gouw per mayor (everyone had exactly one, like the old server's usage) */
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
                // old format: trusted array (everyone RESIDENT)
                if (o.has("trusted"))
                    for (JsonElement t : o.getAsJsonArray("trusted")) l.ranks.put(UUID.fromString(t.getAsString()), Land.Rank.RESIDENT);
                // new format: ranks object (wins over the plain trusted list)
                if (o.has("ranks"))
                    for (Map.Entry<String, JsonElement> r : o.getAsJsonObject("ranks").entrySet())
                        l.ranks.put(UUID.fromString(r.getKey()), Land.Rank.valueOf(r.getValue().getAsString()));
                if (o.has("jobs"))
                    for (Map.Entry<String, JsonElement> j : o.getAsJsonObject("jobs").entrySet())
                        l.jobs.put(UUID.fromString(j.getKey()), j.getValue().getAsString());
                if (o.has("bank")) l.bank = o.get("bank").getAsDouble();
                if (o.has("purchased")) l.purchased = o.get("purchased").getAsInt();
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
                    .append("\", \"world\": \"").append(esc(l.world))
                    .append("\", \"bank\": ").append(round2(l.bank))
                    .append(", \"purchased\": ").append(l.purchased)
                    .append(", \"chunks\": [");
            boolean fc = true;
            for (long k : l.chunks) {
                if (!fc) sb.append(", ");
                fc = false;
                sb.append('[').append(Land.kx(k)).append(", ").append(Land.kz(k)).append(']');
            }
            // trusted stays for old readers (SiteSync GouwClaims)
            sb.append("], \"trusted\": [");
            boolean ft = true;
            for (UUID u : l.ranks.keySet()) {
                if (!ft) sb.append(", ");
                ft = false;
                sb.append('"').append(u).append('"');
            }
            sb.append("], \"ranks\": {");
            boolean fr = true;
            for (Map.Entry<UUID, Land.Rank> r : l.ranks.entrySet()) {
                if (!fr) sb.append(", ");
                fr = false;
                sb.append('"').append(r.getKey()).append("\": \"").append(r.getValue().name()).append('"');
            }
            sb.append("}, \"jobs\": {");
            boolean fj = true;
            for (Map.Entry<UUID, String> j : l.jobs.entrySet()) {
                if (!fj) sb.append(", ");
                fj = false;
                sb.append('"').append(j.getKey()).append("\": \"").append(esc(j.getValue())).append('"');
            }
            sb.append("}, \"flags\": [");
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

    private static double round2(double d) {
        return Math.round(d * 100) / 100.0;
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

    /** The gouw this player is mayor of. */
    Land landOf(UUID owner) {
        return byOwner.get(owner);
    }

    /** The town this player is a plain member of (not their own). */
    Land membership(UUID who) {
        for (Land l : byOwner.values())
            if (l.ranks.containsKey(who)) return l;
        return null;
    }

    /** Own gouw first, then the town they are a member of. */
    Land homeOf(UUID who) {
        Land own = landOf(who);
        return own != null ? own : membership(who);
    }

    /** True when this player may `flag` here: no gouw, bypassing, owner, or member with that flag on. */
    boolean may(Player p, World w, int blockX, int blockZ, Land.Flag flag) {
        if (plugin.isBypassing(p.getUniqueId())) return true;
        Land l = land(w, blockX, blockZ);
        return l == null || l.may(p.getUniqueId(), flag);
    }

    /** Chunk ceiling of a gouw: mayor rank limit + personal bonus + what the town bought. */
    int limitOf(Land l) {
        Player mayor = org.bukkit.Bukkit.getPlayer(l.owner);
        int base = mayor != null ? plugin.getRankChunks(mayor) : plugin.getConfig().getInt("max-chunks", 16);
        return base + plugin.bonusOf(l.owner) + l.purchased;
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

    // ------------------------------------------------------------ town mutations

    /** @return null on success, otherwise the reason it could not claim. */
    String claim(Player p, World w, int cx, int cz) {
        if (land(w.getName(), cx, cz) != null) return "this chunk is already claimed";
        if (!p.hasPermission("gouw.claim")) return "you are not allowed to claim";
        Land l = byOwner.get(p.getUniqueId());
        boolean own = l != null;
        if (l == null) {
            Land town = membership(p.getUniqueId());
            if (town != null) {
                // members claim INTO the town: only mayor and officer may
                if (town.rank(p.getUniqueId()) != Land.Rank.ASSISTANT)
                    return "as a resident you do not claim - ask your mayor (or become officer)";
                if (!town.world.equals(w.getName())) return town.name + " lies in " + town.world;
                l = town;
            } else {
                l = new Land(p.getName(), p.getUniqueId(), w.getName());
                byOwner.put(l.owner, l);
            }
        } else if (!l.world.equals(w.getName())) {
            return "your gouw lies in " + l.world;
        }
        int max = p.hasPermission("gouw.admin") ? Integer.MAX_VALUE : limitOf(l);
        if (l.chunks.size() >= max) return "the gouw is at its limit of " + max + " chunks (/gouw buy to raise it)";
        addChunk(l, cx, cz);
        save();
        return null;
    }

    String unclaim(Player p, World w, int cx, int cz) {
        Land l = land(w.getName(), cx, cz);
        if (l == null) return "nothing claimed here";
        if (!l.mayManage(p.getUniqueId()) && !p.hasPermission("gouw.admin"))
            return "only the mayor and officer release chunks here";
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

    /** Invite (= the old trust): immediately resident. Fails if they already run their own gouw. */
    String invite(Land l, UUID who) {
        if (who.equals(l.owner)) return "that is you";
        if (landOf(who) != null) return "they still own a gouw themselves - they must /gouw unclaim all first";
        if (membership(who) != null) return "they are already a member of another town";
        if (l.ranks.putIfAbsent(who, Land.Rank.RESIDENT) != null) return "already a member";
        save();
        return null;
    }

    String kick(Land l, UUID who) {
        if (l.ranks.remove(who) == null) return "not a member";
        l.jobs.remove(who);
        save();
        return null;
    }

    String promote(Land l, UUID who) {
        if (l.ranks.get(who) != Land.Rank.RESIDENT) return "only residents can be promoted";
        l.ranks.put(who, Land.Rank.ASSISTANT);
        save();
        return null;
    }

    String demote(Land l, UUID who) {
        if (l.ranks.get(who) != Land.Rank.ASSISTANT) return "only officers can be demoted";
        l.ranks.put(who, Land.Rank.RESIDENT);
        save();
        return null;
    }

    String assignJob(Land l, UUID who, String job) {
        if (!l.ranks.containsKey(who)) return "not a member of this gouw";
        if (job == null || job.isEmpty() || job.equalsIgnoreCase("none")) l.jobs.remove(who);
        else l.jobs.put(who, job);
        save();
        return null;
    }

    // ------------------------------------------------------------ bank & purchases

    String deposit(Player actor, Land l, double amount) {
        if (amount <= 0) return "amount must be above 0";
        if (!EconomyRef.withdraw(actor, amount)) return "not enough money in your own wallet";
        l.bank += amount;
        save();
        return null;
    }

    String withdraw(Player actor, Land l, double amount) {
        if (amount <= 0) return "amount must be above 0";
        if (!l.mayManage(actor.getUniqueId())) return "only mayor and officer withdraw from the bank";
        if (l.bank < amount) return "the bank holds only " + round2(l.bank);
        if (!EconomyRef.deposit(actor, amount)) return "Vault could not pay out";
        l.bank -= amount;
        save();
        return null;
    }

    /** Buy chunk quota for the town; paid from the town bank. @return null on success. */
    String buyChunks(Player actor, Land l, int n) {
        if (n <= 0) return "number of chunks must be above 0";
        if (!l.mayManage(actor.getUniqueId())) return "only the mayor and officer buy chunks";
        int price = plugin.getConfig().getInt("price-per-chunk", 250);
        int cap = plugin.getConfig().getInt("town-buy-max", 100);
        if (l.purchased + n > cap) return "this town bought the maximum of " + cap + " extra chunks";
        double cost = (double) price * n;
        if (l.bank < cost) return "the bank holds " + round2(l.bank) + " but " + n + " chunk(s) cost " + price * n;
        l.bank -= cost;
        l.purchased += n;
        save();
        return null;
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
