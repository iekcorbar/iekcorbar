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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All gouwen in memory, saved as plain readable JSON (plugins/Gouw/claims.json). Since 0.4 a gouw also
 * carries town data: ranks (assistant/resident), the town bank and bought chunks. Every resident
 * may deposit and withdraw from the bank; buying chunks and releasing land stay mayor/officer work.
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
                if (o.has("perms"))
                    for (Map.Entry<String, JsonElement> pe : o.getAsJsonObject("perms").entrySet()) {
                        EnumSet<Land.Flag> set = EnumSet.noneOf(Land.Flag.class);
                        for (JsonElement f : pe.getValue().getAsJsonArray()) parseFlag(f.getAsString(), set);
                        l.perms.put(UUID.fromString(pe.getKey()), set);
                    }
                if (o.has("bank")) l.bank = o.get("bank").getAsDouble();
                if (o.has("purchased")) l.purchased = o.get("purchased").getAsInt();
                if (o.has("flags")) {
                    l.memberFlags.clear();
                    for (JsonElement f : o.getAsJsonArray("flags")) parseFlag(f.getAsString(), l.memberFlags);
                } else {
                    l.memberFlags.addAll(Land.DEFAULT_FLAGS);
                }
                if (o.has("banned"))
                    for (JsonElement t : o.getAsJsonArray("banned")) l.banned.add(UUID.fromString(t.getAsString()));
                if (o.has("rentals")) {
                    JsonObject ro = o.getAsJsonObject("rentals");
                    for (Map.Entry<String, JsonElement> ren : ro.entrySet())
                        try { l.rentals.put(UUID.fromString(ren.getKey()), ren.getValue().getAsLong()); } catch (Exception ignored) { }
                }
                // 0.5.0: open invites (sent, not yet accepted)
                if (o.has("invites"))
                    for (JsonElement t : o.getAsJsonArray("invites")) l.invites.add(UUID.fromString(t.getAsString()));
                if (o.has("welcome")) l.welcome = o.get("welcome").getAsString();
                if (o.has("home") && o.get("home").isJsonObject()) {
                    JsonObject h = o.getAsJsonObject("home");
                    l.homeWorld = h.get("world").getAsString();
                    l.homeX = h.get("x").getAsInt();
                    l.homeY = h.get("y").getAsInt();
                    l.homeZ = h.get("z").getAsInt();
                    l.hasHome = true;
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
            sb.append("}, \"perms\": {");
            boolean fp = true;
            for (Map.Entry<UUID, EnumSet<Land.Flag>> pe : l.perms.entrySet()) {
                if (!fp) sb.append(", ");
                fp = false;
                sb.append('"').append(pe.getKey()).append("\": [");
                boolean fz = true;
                for (Land.Flag f : pe.getValue()) {
                    if (!fz) sb.append(", ");
                    fz = false;
                    sb.append('"').append(f.name()).append('"');
                }
                sb.append("]");
            }
            sb.append("}, \"flags\": [");
            boolean ff = true;
            for (Land.Flag f : Land.Flag.values()) {
                if (!l.memberFlags.contains(f)) continue;
                if (!ff) sb.append(", ");
                ff = false;
                sb.append('"').append(f.name()).append('"');
            }
            sb.append("], \"banned\": [");
            boolean fb = true;
            for (UUID u : l.banned) {
                if (!fb) sb.append(", ");
                fb = false;
                sb.append('"').append(u).append('"');
            }
            sb.append("], \"rentals\": {");
            boolean fr2 = true;
            for (Map.Entry<UUID, Long> e : l.rentals.entrySet()) {
                if (!fr2) sb.append(", ");
                fr2 = false;
                sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
            }
            sb.append("}, \"invites\": [");
            boolean fi = true;
            for (UUID u : l.invites) {
                if (!fi) sb.append(", ");
                fi = false;
                sb.append('"').append(u).append('"');
            }
            sb.append("], \"welcome\": \"").append(esc(l.welcome)).append("\"");
            if (l.hasHome) sb.append(", \"home\": {\"world\": \"").append(esc(l.homeWorld)).append("\", \"x\": ").append(l.homeX).append(", \"y\": ").append(l.homeY).append(", \"z\": ").append(l.homeZ).append("}");
            sb.append("}");
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

    /** New flag names plus the legacy single ACCESS flag (0.2/0.3 files) which splits into CONTAINERS+INTERACT. */
    private static void parseFlag(String name, java.util.Collection<Land.Flag> into) {
        if (name.equals("ACCESS")) {
            into.add(Land.Flag.CONTAINERS);
            into.add(Land.Flag.INTERACT);
            return;
        }
        try {
            into.add(Land.Flag.valueOf(name));
        } catch (IllegalArgumentException ignored) { }
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

    /** 0.5.0: since a resident may belong to several towns, claiming goes to the NEAREST one in this world. */
    Land nearestMembership(UUID who, String world, int cx, int cz) {
        Land best = null;
        int bestD = Integer.MAX_VALUE;
        for (Land l : byOwner.values()) {
            if (!l.ranks.containsKey(who) || !l.world.equals(world)) continue;
            int d = Integer.MAX_VALUE;
            for (long k : l.chunks) d = Math.min(d, Math.max(Math.abs(Land.kx(k) - cx), Math.abs(Land.kz(k) - cz)));
            if (d < bestD) { bestD = d; best = l; }
        }
        return best;
    }

    /**
     * 0.5.0: the town a menu or command should act ON for this player. Since residents may belong
     * to several towns, the town they are physically standing in wins (that is the one whose chest
     * confused them); otherwise: own gouw, then first membership - the old homeOf order.
     */
    Land menuFor(Player p) {
        Land here = land(p.getWorld(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        if (here != null && here.isMember(p.getUniqueId())) return here;
        return homeOf(p.getUniqueId());
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
            Land town = nearestMembership(p.getUniqueId(), w.getName(), cx, cz);
            if (town != null) {
                // members claim INTO the town: officers always, residents only with the CLAIM right
                if (!town.may(p.getUniqueId(), Land.Flag.CLAIM))
                    return "you need the CLAIM right in " + town.name + " - ask the mayor or an officer (RESIDENTS page)";
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

    boolean ban(Land l, UUID who) {
        if (who.equals(l.owner)) return false;
        l.ranks.remove(who);
        l.perms.remove(who);
        l.invites.remove(who);
        if (!l.banned.add(who)) return false;
        save();
        return true;
    }

    boolean unban(Land l, UUID who) {
        if (!l.banned.remove(who)) return false;
        save();
        return true;
    }

    void setWelcome(Land l, String text) {
        l.welcome = text;
        save();
    }

    void setHome(Land l, org.bukkit.Location loc) {
        l.homeWorld = loc.getWorld().getName();
        l.homeX = loc.getBlockX();
        l.homeY = loc.getBlockY();
        l.homeZ = loc.getBlockZ();
        l.hasHome = true;
        save();
    }

    String transfer(Land l, UUID newOwner) {
        if (byOwner.containsKey(newOwner)) return "that player already owns a gouw";
        byOwner.remove(l.owner);
        l.owner = newOwner;
        l.ranks.remove(newOwner);
        l.perms.remove(newOwner);
        l.banned.remove(newOwner);
        l.invites.remove(newOwner);
        byOwner.put(newOwner, l);
        save();
        return null;
    }

    void rent(Land l, UUID who, int days) {
        long until = System.currentTimeMillis() + (long) days * 24L * 60L * 60L * 1000L;
        l.rentals.put(who, until);
        if (!l.ranks.containsKey(who)) l.ranks.put(who, Land.Rank.RESIDENT);
        save();
    }

    int expireRentals() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Land l : byOwner.values()) {
            java.util.Iterator<Map.Entry<UUID, Long>> it = l.rentals.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (e.getValue() <= now) {
                    it.remove();
                    n++;
                }
            }
        }
        if (n > 0) save();
        return n;
    }

    java.util.List<Land> top() {
        java.util.List<Land> out = new java.util.ArrayList<>(byOwner.values());
        out.sort((a, b) -> Integer.compare(b.chunks.size(), a.chunks.size()));
        return out;
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

    /** Toggle one right for ONE resident; the first toggle materialises the current defaults. */
    boolean togglePerm(Land l, UUID who, Land.Flag f) {
        EnumSet<Land.Flag> set = l.perms.get(who);
        if (set == null) {
            set = l.memberFlags.isEmpty() ? EnumSet.noneOf(Land.Flag.class) : EnumSet.copyOf(l.memberFlags);
        }
        boolean nowOn;
        if (set.remove(f)) nowOn = false;
        else { set.add(f); nowOn = true; }
        l.perms.put(who, set);
        save();
        return nowOn;
    }

    /**
     * 0.5.0: an invite is an OPEN INVITE, not a membership. The mayor/officer sends it (the target
     * may be offline); the target becomes resident only by accepting (/gouw accept or the clickable
     * chat message). Owning a gouw yourself or being resident elsewhere no longer blocks anything -
     * neighbouring towns trusting each other is what players always did anyway.
     * @return null on success (invite sent), otherwise the reason it could not be sent.
     */
    String invite(Land l, UUID who) {
        if (who.equals(l.owner)) return "that is you";
        if (l.banned.contains(who)) return "they are banned from this town";
        if (l.ranks.containsKey(who)) return "they are already a resident here";
        if (l.invites.contains(who)) return "an invite is already waiting for them";
        l.invites.add(who);
        save();
        return null;
    }

    /** All towns with an open invite for this player (0.5.0 - a player can hold several). */
    List<Land> invitesFor(UUID who) {
        List<Land> out = new ArrayList<>();
        for (Land l : byOwner.values()) if (l.invites.contains(who)) out.add(l);
        return out;
    }

    /**
     * 0.5.1: ONE invite is accepted at a time (the clickable [Accept] carries the town name).
     * The old bulk accept silently joined players into every town that had ever invited them -
     * residents turning up in towns they had forgotten, the root of the "I am suddenly mayor in
     * someone else's gouw" reports. Callers pass a Land from invitesFor(); reason or null back.
     */
    String acceptInvite(Land l, UUID who) {
        if (!l.invites.remove(who)) return "there is no open invite from " + l.name + " for you";
        if (l.ranks.containsKey(who)) { save(); return "you already live in " + l.name; }
        l.ranks.put(who, Land.Rank.RESIDENT);
        save();
        return null;
    }

    /** Drop every open invite for this player. @return how many were declined. */
    int decline(UUID who) {
        int n = 0;
        for (Land l : invitesFor(who)) { l.invites.remove(who); n++; }
        if (n > 0) save();
        return n;
    }

    /** Mayor/officer cancels one open invite again (shift-click on the grey head in LEDEN). */
    boolean cancelInvite(Land l, UUID who) {
        if (!l.invites.remove(who)) return false;
        save();
        return true;
    }

    String kick(Land l, UUID who) {
        l.invites.remove(who);
        if (l.ranks.remove(who) == null) return "not a member";
        l.perms.remove(who);
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
        if (!l.isMember(actor.getUniqueId())) return "only residents of this town touch the bank";
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
