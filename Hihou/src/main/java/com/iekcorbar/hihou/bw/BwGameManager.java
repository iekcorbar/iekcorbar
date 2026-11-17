package com.iekcorbar.hihou.bw;

import com.iekcorbar.hihou.HihouPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BedWars etappe 0.3.0 was arenas + teams + join; etappe 0.4.0 adds the match itself: per-team
 * iron/gold pads and shared diamond/emerald fields (the original generators.yml "Default" values),
 * beds destroyed by enemies (breaking or sleeping in them), a respawn session, the sudden-death
 * timeline (diamond II 6:00, emerald II 12:00, diamond III 18:00, emerald III 24:00, ALL BEDS GONE
 * at 25:00, draw at 40:00 - GamePlayingTask's NextEvent lane with the stock numbers), winner
 * detection (last team with members), mid-game spectators and the offline-safe ReJoin store.
 * Still no shop/upgrades/kits: etappe 0.5.0. Data stays ours: arenas.json in our format,
 * center+size region powering the end-of-game cleanup, no Party-API, no Vault.
 */
public final class BwGameManager {
    /** Colors staff will most often reach for (all 16 DyeColor names are accepted). */
    public static final String[] TEAM_COLORS = {"red", "blue", "green", "yellow", "cyan", "white", "pink", "gray"};
    public static final String[] GEN_TYPES = {"iron", "gold", "diamond", "emerald"};
    /** The sudden-death timeline in seconds, matching the original's Default config values. */
    private static final int DIAMOND_II = 360, EMERALD_II = 720, DIAMOND_III = 1080, EMERALD_III = 1440,
            BEDS_DESTROY = 1500, GAME_END = 2400, RESPAWN_SECONDS = 5, PROTECT_MS = 2000;

    int bedsAt(BwArena a) {
        return cfgI(a.name, "bedsAt", BEDS_DESTROY);
    }

    int endAt(BwArena a) {
        return cfgI(a.name, "endAt", GAME_END);
    }

    private final HihouPlugin plugin;
    private final YamlConfiguration y = new YamlConfiguration();
    private final File file;
    private final Map<String, BwArena> arenas = new LinkedHashMap<>();
    private final BwRejoin rejoin;
    /** Lobby-dead players are restored on quit; their home spot waits here for the respawn click. */
    private final Map<UUID, Location> pendingReturn = new LinkedHashMap<>();
    /** 0.5.0: team shop + team upgrades GUI and its purchase rules. */
    final BwStore store;
    /** 0.5.1: the per-player sidebar + the victory show. */
    final BwHud hud;
    /** 0.6.0: season stats (bwstats.json) and the lobby-only party group. */
    final BwStats stats;
    final BwParty party = new BwParty();
    /** 0.6.0: arena -> mode picked at launch (classic/no-beds/double-iron/speed). */
    final Map<String, BwMode> modes = new LinkedHashMap<>();
    private final org.bukkit.NamespacedKey breachKey;

    /** Random match flavors; each one is applied once at launch and announced like the original's specials. */
    enum BwMode {
        CLASSIC("classic"), NO_BEDS("no beds - last life each!"), DOUBLE_IRON("double iron"), NEED_FOR_SPEED("need for speed");

        final String label;

        BwMode(String label) {
            this.label = label;
        }
    }

    public BwGameManager(HihouPlugin plugin) {
        this.plugin = plugin;
        rejoin = new BwRejoin(plugin);
        store = new BwStore(this);
        hud = new BwHud(this, plugin);
        stats = new BwStats(new File(plugin.getDataFolder(), "bwstats.json"));
        breachKey = new org.bukkit.NamespacedKey(plugin, "breach");
        file = new File(plugin.getDataFolder(), "arenas.json");
        if (file.exists()) {
            try {
                y.load(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read arenas.json: " + e.getMessage());
            }
        }
        var root = y.getConfigurationSection("arenas");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                BwArena a = new BwArena(this, name);
                a.genDiamond.addAll(y.getStringList("arenas." + name + ".gens.diamond"));
                a.genEmerald.addAll(y.getStringList("arenas." + name + ".gens.emerald"));
                var tsec = y.getConfigurationSection("arenas." + name + ".teams");
                if (tsec != null) {
                    for (String t : tsec.getKeys(false)) {
                        BwTeam team = new BwTeam(t, readColor(y.getString("arenas." + name + ".teams." + t + ".color", "RED")),
                                loc(y.getString("arenas." + name + ".teams." + t + ".spawn")));
                        team.setBed(loc(y.getString("arenas." + name + ".teams." + t + ".bed")),
                                loc(y.getString("arenas." + name + ".teams." + t + ".bed2")));
                        team.genLocs("Iron").addAll(y.getStringList("arenas." + name + ".teams." + t + ".iron"));
                        team.genLocs("Gold").addAll(y.getStringList("arenas." + name + ".teams." + t + ".gold"));
                        team.setShop(y.getString("arenas." + name + ".teams." + t + ".shop"));
                        team.setChest(y.getString("arenas." + name + ".teams." + t + ".chest"));
                        a.teams.put(t, team);
                    }
                }
                var sn = y.getConfigurationSection("arenas." + name + ".snapshot");
                if (sn != null) {
                    a.snapWorld = sn.getString("world");
                    var ys = sn.getIntegerList("y");
                    if (ys.size() == 2) {
                        a.snapLo = ys.get(0);
                        a.snapHi = ys.get(1);
                    }
                    for (String row : sn.getStringList("blocks")) {
                        int eq = row.indexOf('=');
                        if (eq > 0) a.snapshot.put(row.substring(0, eq), row.substring(eq + 1));
                    }
                }
                arenas.put(name, a);
            }
        }
    }

    private void save() {
        try {
            y.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save arenas.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ config helpers

    int cfgI(String arena, String key, int def) {
        return y.getInt("arenas." + arena + "." + key, def);
    }

    List<Integer> cfgIntList(String arena, String key) {
        return y.getIntegerList("arenas." + arena + "." + key);
    }

    Location cfgLoc(String arena, String key) {
        return loc(y.getString("arenas." + arena + "." + key));
    }

    private void set(String arena, String key, Object v) {
        y.set("arenas." + arena + "." + key, v);
        save();
    }

    /** Locations as one string "world:x:y:z[:yaw:pitch]" - one value per point, like chests.json ids. */
    static String str(Location l) {
        if (l == null) return null;
        return l.getWorld().getName() + ":" + l.getX() + ":" + l.getY() + ":" + l.getZ() + ":" + l.getYaw() + ":" + l.getPitch();
    }

    /** Plain "world:x:y:z" for block positions (generators, beds). */
    private static String blockStr(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static Block blockFrom(String s) {
        if (s == null) return null;
        String[] p = s.split(":");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Location loc(String s) {
        if (s == null) return null;
        String[] p = s.split(":");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            float yaw = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isColor(String s) {
        try {
            DyeColor.valueOf(s.toUpperCase(Locale.ROOT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static DyeColor readColor(String s) {
        try {
            return DyeColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return DyeColor.RED;
        }
    }

    /** Team color as chat color, for join/launch/elimination messages. */
    static NamedTextColor chat(DyeColor c) {
        return switch (c) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN, LIME -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
            case ORANGE, BROWN -> NamedTextColor.GOLD;
            case LIGHT_BLUE -> NamedTextColor.AQUA;
            case CYAN -> NamedTextColor.DARK_AQUA;
            case PINK, MAGENTA -> NamedTextColor.LIGHT_PURPLE;
            case PURPLE -> NamedTextColor.DARK_PURPLE;
            case GRAY, LIGHT_GRAY -> NamedTextColor.GRAY;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.BLACK;
        };
    }

    // ------------------------------------------------------------ data views

    public Set<String> names() {
        return arenas.keySet();
    }

    // ------------------------------------------------------------ 0.5.0: shop / upgrades / kits

    java.util.Collection<BwArena> arenas() {
        return arenas.values();
    }

    BwArena arena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    /** The shop's announcement lane: participants and spectators hear it (秘宝 BW prefixed like the rest). */
    void toArena(BwArena a, String text, NamedTextColor c) {
        msg(a.everyone(), text, c);
    }

    /** Forge re-tunes the team's IRON generators: 1 -> +1 per spawn, 2 -> also faster, 3 -> faster and richer. */
    void applyForge(BwArena a, BwTeam t, int lvl) {
        for (BwGen g : a.gens) {
            if (g.type != BwGen.Type.IRON || !t.name().equals(g.teamName)) continue;
            if (lvl == 1) g.amount = 3;
            else if (lvl == 2) {
                g.amount = 3;
                g.delay = 1;
            } else if (lvl >= 3) {
                g.amount = 4;
                g.delay = 1;
            }
        }
    }

    /** Player right-clicked a block; true when it was some team's shop marker (listener cancels then). */
    public boolean shopClick(Player p, Block b) {
        return b != null && store.blockClick(p, b);
    }

    public void storeClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        store.onClick(e);
    }

    /** /ih bw shop - opens the shop of the team the player plays for. */
    public String shop(Player p) {
        return store.openCommand(p);
    }

    /** /ih bw kit <name> - choice lives in the session; every base spawn hands it out. */
    public String kit(Player p, String name) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return "Join an arena first (/ih bw join <arena>) - kits are chosen per game.";
        return BwKits.select(p, a, name);
    }

    public Set<String> teamNames(String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        return a == null ? new LinkedHashSet<>() : a.teams.keySet();
    }

    /** Summary line for /ih bw list and info. */
    public String info(String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        StringBuilder sb = new StringBuilder(a.name + " [" + a.status.name().toLowerCase(Locale.ROOT) + "] "
                + a.players.size() + "/" + a.maxPlayers() + " (min " + a.min() + ", team x" + a.maxInTeam() + ")");
        if (a.spawn() == null) sb.append(", NO SPAWN");
        if (a.center() == null) sb.append(", no region yet (end-cleanup off)");
        int beds = 0, gens = a.genDiamond.size() + a.genEmerald.size();
        for (BwTeam t : a.teams.values()) {
            if (t.bed() != null) beds++;
            gens += t.genLocs("Iron").size() + t.genLocs("Gold").size();
        }
        sb.append(" - teams:");
        for (BwTeam t : a.teams.values())
            sb.append(" ").append(t.name()).append(t.spawn() == null ? "?" : t.bed() == null ? "(no bed)" : "");
        sb.append(" | beds ").append(beds).append("/").append(a.teams.size()).append(", gens ").append(gens);
        return sb.toString();
    }

    // ------------------------------------------------------------ setup commands

    public String create(String arena) {
        String n = arena.toLowerCase(Locale.ROOT);
        if (!n.matches("[a-zA-Z0-9_]{2,24}")) return "Use 2-24 letters/numbers for the arena name.";
        if (arenas.containsKey(n)) return "That arena already exists.";
        arenas.put(n, new BwArena(this, n));
        set(n, "min", 2);
        set(n, "maxTeam", 4);
        return "";
    }

    public String delete(String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (!a.players.isEmpty() || !a.spectators.isEmpty()) return "Stop it first (/ih bw stop " + a.name + ").";
        arenas.remove(a.name);
        y.set("arenas." + a.name, null);
        save();
        rejoin.markEnded(a.name); // offline hand-ins of a deleted arena still need to reach their owners
        return "";
    }

    public String setSpawn(Player stander, String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        set(a.name, "spawn", str(stander.getLocation()));
        return "";
    }

    public String setSpec(Player stander, String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        set(a.name, "spec", str(stander.getLocation()));
        return "";
    }

    public String setCenter(Player stander, String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        set(a.name, "center", List.of(stander.getLocation().getBlockX(), stander.getLocation().getBlockZ()));
        if (cfgI(a.name, "size", 0) <= 0) y.set("arenas." + a.name + ".size", 40); // same courtesy as /ih game
        save();
        return "";
    }

    public String setSize(String arena, int r) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (r < 4 || r > 400) return "Pick a radius between 4 and 400.";
        if (a.center() == null) return "Set the center first (stand in the middle, /ih bw set " + a.name + " center).";
        set(a.name, "size", r);
        return "";
    }

    public String setMin(String arena, int n) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (n < 1 || n > 40) return "Min players: 1 to 40.";
        set(a.name, "min", n);
        return "";
    }

    public String setMaxTeam(String arena, int n) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (n < 1 || n > 10) return "Max per team: 1 to 10.";
        set(a.name, "maxTeam", n);
        return "";
    }

    /** Sudden-death overrides, for arenas that should finish sooner than the stock 25:00/40:00. */
    public String setBedsAt(String arena, int sec) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (sec < 30 || sec > 5400) return "Beds-destroy between 30 and 5400 seconds.";
        if (sec >= cfgI(a.name, "endAt", GAME_END)) return "Beds must go before the game-end second.";
        set(a.name, "bedsAt", sec);
        return "";
    }

    public String setEndAt(String arena, int sec) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (sec < 60 || sec > 7200) return "Game-end between 60 and 7200 seconds.";
        if (sec <= cfgI(a.name, "bedsAt", BEDS_DESTROY)) return "Game-end must come after the beds-destroy second.";
        set(a.name, "endAt", sec);
        return "";
    }

    public String teamAdd(String arena, String team, String color) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        String t = team.toLowerCase(Locale.ROOT);
        if (!t.matches("[a-zA-Z0-9_]{2,16}")) return "Use 2-16 letters/numbers for the team name.";
        if (a.teams.containsKey(t)) return "That team already exists here.";
        if (!isColor(color)) return "Pick a dye color (red, blue, green, yellow, cyan, white, pink, gray or any DyeColor).";
        DyeColor dc = readColor(color);
        a.teams.put(t, new BwTeam(t, dc, null));
        y.set("arenas." + a.name + ".teams." + t + ".color", dc.name());
        save();
        return "";
    }

    public String teamDel(String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (a.status != BwArena.Status.WAITING) return "Stop the game first (/ih bw stop " + a.name + ").";
        String t = team.toLowerCase(Locale.ROOT);
        if (a.teams.remove(t) == null) return "No such team.";
        a.pickedTeam.values().removeIf(v -> v.equals(t));
        y.set("arenas." + a.name + ".teams." + t, null);
        save();
        return "";
    }

    public String teamSetSpawn(Player stander, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwTeam t = a.team(team);
        if (t == null) return "No such team.";
        t.setSpawn(stander.getLocation());
        y.set("arenas." + a.name + ".teams." + t.name() + ".spawn", str(stander.getLocation()));
        save();
        return "";
    }

    /** Look at your team's bed block: both halves are captured and tracked from now on. */
    public String teamSetBed(Player looker, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwTeam t = a.team(team);
        if (t == null) return "No such team.";
        Block b = looker.getTargetBlock(null, 6);
        if (b == null || !isBed(b)) return "Look at the bed block (within 6 blocks).";
        Block partner = bedPartner(b);
        if (partner == null) return "That bed is not complete - the other half is missing.";
        t.setBed(loc(blockStr(b)), loc(blockStr(partner)));
        y.set("arenas." + a.name + ".teams." + t.name() + ".bed", blockStr(b));
        y.set("arenas." + a.name + ".teams." + t.name() + ".bed2", blockStr(partner));
        save();
        return "";
    }

    /** 0.5.0: mark the block that opens this team's shop (look at it - same trick as the bed). */
    public String teamSetShop(Player looker, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwTeam t = a.team(team);
        if (t == null) return "No such team.";
        Block b = looker.getTargetBlock(null, 6);
        if (b == null || b.getType().isAir()) return "Look at the shop marker block (within 6 blocks).";
        t.setShop(blockStr(b));
        y.set("arenas." + a.name + ".teams." + t.name() + ".shop", blockStr(b));
        save();
        return "Shop marker for " + t.name() + " set on the block you are looking at.";
    }

    /** 0.5.1: mark this team's chest (the script-built bases ship one next to the anvil). */
    public String teamSetChest(Player looker, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwTeam t = a.team(team);
        if (t == null) return "No such team.";
        Block b = looker.getTargetBlock(null, 6);
        if (b == null || !(b.getBlockData() instanceof org.bukkit.block.data.type.Chest)) return "Look at a chest block (within 6 blocks).";
        t.setChest(blockStr(b));
        y.set("arenas." + a.name + ".teams." + t.name() + ".chest", blockStr(b));
        save();
        return "Team chest for " + t.name() + " marked.";
    }

    /**
     * Inventory-open gate for team chests. null = nothing to do (not a tracked chest, or the match
     * is not running), "" = tracked and allowed (let the open through), otherwise the denial reason.
     */
    public String chestDeny(Player p, Block b) {
        String key = b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        for (BwArena a : arenas.values()) {
            for (BwTeam t : a.teams.values()) {
                if (!key.equals(t.chest())) continue;
                if (a.status != BwArena.Status.PLAYING || a.showing) return null;   // outside a match it is an ordinary chest
                if (!a.players.contains(p.getUniqueId())) return "this chest belongs to a running match";
                if (a.teamOf(p.getUniqueId()) != t) return "that is " + t.name() + "'s team chest";
                return "";
            }
        }
        return null;
    }

    /** Match over: whatever a team stored goes back on top of the chest (BedWars empties, we hand out). */
    private void emptyChest(BwTeam t) {
        Block b = blockFrom(t.chest());
        if (b == null || !(b.getState() instanceof org.bukkit.block.Container c)) return;
        Inventory inv = c.getInventory();
        if (inv == null) return;
        for (ItemStack it : inv.getContents())
            if (it != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 1.1, 0.5), it);
        inv.clear();
    }

    /** 0.5.1: spectators (and the dead on their respawn screen) fly to a player they name. */
    public String follow(Player p, String name) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return "You are not in a BedWars game.";
        Player q = Bukkit.getPlayerExact(name);
        if (q == null || arenaOf(q.getUniqueId()) != a) return q == null ? "'" + name + "' is not online." : name + " is not in your arena.";
        p.teleport(q.getLocation());
        msg(List.of(p), "Following " + q.getName() + " - walk away or /ih bw leave to stop.", NamedTextColor.GRAY);
        return "";
    }

    // ------------------------------------------------------------ 0.6.0: party, stats, modes, breach, rewards

    public String partyInvite(Player leader, String name) {
        return party.invite(leader, name);
    }

    public String partyJoin(Player p, String leaderName) {
        return party.join(p, leaderName);
    }

    public String partyIgnore(Player p, String leaderName) {
        return party.ignore(p, leaderName);
    }

    public String partyLeave(Player p) {
        return party.leave(p);
    }

    public String partyList(Player p) {
        return party.list(p);
    }

    private static java.util.UUID findPlayer(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on.getUniqueId();
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        return off == null ? null : off.getUniqueId();
    }

    /** /ih bw stats [player] - one readable season line. */
    public String statsLine(Player p, String name) {
        if (name == null) return stats.line(p.getUniqueId());
        java.util.UUID u = findPlayer(name);
        return u == null ? "Player '" + name + "' is not known to this server." : stats.line(u);
    }

    /** /ih bw stats top - the ten loudest winners of this season. */
    public List<String> statsTop() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<java.util.UUID, int[]> e : stats.top(10)) out.add(stats.line(e.getKey()));
        return out;
    }

    public String seasonReset() {
        stats.resetSeason();
        return "Season " + stats.season() + " started - everyone's numbers are fresh.";
    }

    public int season() {
        return stats.season();
    }

    /** Staff pre-picks a mode (otherwise the launch rolls one, if bw-random-modes is on). */
    public String modeForce(String arena, String name) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (a.status == BwArena.Status.PLAYING) return "Match running - stop it first.";
        BwMode m = switch (name.toLowerCase(Locale.ROOT)) {
            case "classic" -> BwMode.CLASSIC;
            case "no-beds", "nobeds" -> BwMode.NO_BEDS;
            case "double-iron", "doubleiron" -> BwMode.DOUBLE_IRON;
            case "speed" -> BwMode.NEED_FOR_SPEED;
            default -> null;
        };
        if (m == null) return "Mode: classic|no-beds|double-iron|speed.";
        modes.put(a.name, m);
        return "Next game in " + a.name + ": " + m.label + ".";
    }

    String modeName(BwArena a) {
        BwMode m = modes.get(a.name);
        return m == null ? "classic" : m.label;
    }

    // ------------------------------------------------------------ 0.6.1: snapshot - the real map reset

    /**
     * Capture the arena: every non-air blockstate in the center/size square between lo..hi
     * (default: spawn y +/- 64). One synchronous pass, a second or so on r=70 - staff-only,
     * run it when the map looks like it should. conclude() restores exactly this afterwards.
     */
    public String snapshot(Player stander, String arena, Integer lo, Integer hi) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        int[] c = a.center();
        Location sp = a.spawn();
        if (c == null || sp == null) return "Set center and spawn first (/ih bw set " + a.name + " ...).";
        World w = sp.getWorld();
        int r = a.radius();
        int yLo = lo != null ? lo : sp.getBlockY() - 64;
        int yHi = hi != null ? hi : sp.getBlockY() + 64;
        yLo = Math.max(w.getMinHeight(), yLo);
        yHi = Math.min(w.getMaxHeight() - 1, yHi);
        if (yHi - yLo < 8) return "That y-band is too small to hold an arena.";
        a.snapshot.clear();
        a.snapWorld = w.getName();
        a.snapLo = yLo;
        a.snapHi = yHi;
        for (int x = c[0] - r; x <= c[0] + r; x++)
            for (int z = c[1] - r; z <= c[1] + r; z++) {
                if (Math.max(Math.abs(x - c[0]), Math.abs(z - c[1])) > r) continue;
                for (int y = yLo; y <= yHi; y++) {
                    org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    a.snapshot.put(x + ":" + y + ":" + z, b.getBlockData().getAsString());
                }
            }
        if (a.snapshot.isEmpty()) return "Nothing captured - is the center/size right over the islands?";
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : a.snapshot.entrySet()) rows.add(e.getKey() + "=" + e.getValue());
        y.set("arenas." + a.name + ".snapshot.world", a.snapWorld);
        y.set("arenas." + a.name + ".snapshot.y", List.of(a.snapLo, a.snapHi));
        y.set("arenas." + a.name + ".snapshot.blocks", rows);
        save();
        return "Snapshot armed: " + a.snapshot.size() + " blocks in " + a.snapWorld + " (r=" + r + ", y " + a.snapLo + ".." + a.snapHi + ").";
    }

    /**
     * The full reset: everything the participants touched goes back to the snapshot, and
     * snapshot blocks that were destroyed are re-placed with their exact state (facing,
     * waterlogged, the lot). Without a snapshot conclude() falls back to the old lite path.
     */
    private int restoreSnapshot(BwArena a) {
        World w = Bukkit.getWorld(a.snapWorld);
        if (w == null) return -1;
        Set<String> coords = new LinkedHashSet<>(a.snapshot.keySet());
        coords.addAll(a.touched);
        for (String k : coords) {
            String[] p = k.split(":");
            org.bukkit.block.Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            String data = a.snapshot.get(k);
            if (data == null) {
                if (!b.getType().isAir()) b.setType(Material.AIR, false);
            } else if (!data.equals(b.getBlockData().getAsString())) {
                try {
                    b.setBlockData(Bukkit.createBlockData(data), false);
                } catch (IllegalArgumentException ignored) {   // stale state from an old MC version - skip it
                }
            }
        }
        return coords.size();
    }

    /** Placed, broken or blown: remember where the match got its hands dirty. */
    private void touch(BwArena a, org.bukkit.block.Block b) {
        if (a.snapWorld == null || !b.getWorld().getName().equals(a.snapWorld)) return;
        a.touched.add(b.getX() + ":" + b.getY() + ":" + b.getZ());
    }

    private boolean inRegion(BwArena a, org.bukkit.block.Block b) {
        int[] c = a.center();
        return c != null && Math.max(Math.abs(b.getX() - c[0]), Math.abs(b.getZ() - c[1])) <= a.radius();
    }

    /** BlockBreakEvent lane (participants only, during a match). */
    public void trackBreak(Player p, org.bukkit.block.Block b) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null || a.status != BwArena.Status.PLAYING || a.snapWorld == null) return;
        if (inRegion(a, b)) touch(a, b);
    }

    /** Explosions remove blocks without a break event - grab the coordinates while the list is intact. */
    public void trackExploded(List<org.bukkit.block.Block> blocks) {
        for (BwArena a : arenas.values()) {
            if (a.status != BwArena.Status.PLAYING || a.snapWorld == null) continue;
            for (org.bukkit.block.Block b : blocks) if (inRegion(a, b)) touch(a, b);
        }
    }

    /** The Bed Breach Charge: a marked fire charge that detonates an enemy bed from 16 blocks. */
    ItemStack breachItem() {
        ItemStack it = new ItemStack(Material.FIRE_CHARGE);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Bed Breach Charge", NamedTextColor.DARK_RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Right-click within 16 blocks of an enemy bed - it is gone.", NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(breachKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isBreach(ItemStack it) {
        return it != null && it.getType() == Material.FIRE_CHARGE && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(breachKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** @return "" on success (listener consumes the charge), otherwise the reason shown to the player. */
    public String breach(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return "You are not in a BedWars game.";
        if (a.status != BwArena.Status.PLAYING) return "No match running.";
        BwTeam mine = a.teamOf(p.getUniqueId());
        BwTeam hit = null;
        double bd = 16 * 16;
        for (BwTeam t : a.teams.values()) {
            if (t == mine || t.bedDestroyed() || t.bed() == null || !t.bed().getWorld().equals(p.getWorld())) continue;
            double d = t.bed().distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                hit = t;
            }
        }
        if (hit == null) return "No enemy bed within 16 blocks.";
        clearBedBlocks(hit);
        stats.bed(p.getUniqueId());
        pay(p, "bed");
        msg(a.everyone(), hit.name().toUpperCase(Locale.ROOT) + "'s bed was BREACHED by " + p.getName() + "!", chat(hit.color()));
        hit.bed().getWorld().playSound(hit.bed().add(0.5, 0.5, 0.5), "minecraft:entity.ender_dragon.growl", 0.7f, 1.3f);
        hit.bed().getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, hit.bed().add(0.5, 0.5, 0.5), 4);
        checkEnd(a);
        return "";
    }

    /** Shockwave upgrade: one in four (chance grows with the level) attackers get blasted off you. */
    public void shockwave(Player victim, Player attacker) {
        BwArena a = arenaOf(victim.getUniqueId());
        if (a == null || a.status != BwArena.Status.PLAYING) return;
        BwTeam t = a.teamOf(victim.getUniqueId());
        if (t == null || a.upgTeam(t.name(), 6) <= 0) return;
        long now = System.currentTimeMillis();
        Long until = a.shockCool.get(attacker.getUniqueId());
        if (until != null && until > now) return;
        a.shockCool.put(attacker.getUniqueId(), now + 3000);
        if (new java.util.Random().nextDouble() > 0.25 + 0.1 * (a.upgTeam(t.name(), 6) - 1)) return;
        org.bukkit.util.Vector dir = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) dir = victim.getLocation().getDirection().multiply(-1);
        attacker.setVelocity(dir.normalize().multiply(1.6).add(new org.bukkit.util.Vector(0, 0.5, 0)));
        victim.playSound(victim.getLocation(), "minecraft:entity.generic.explode", 0.5f, 1.7f);
    }

    /** Coin reward via Vault (config bw-rewards.*). No economy plugin, or 0: nobody hears about it. */
    private void pay(Player p, String what) {
        int amount = plugin.getConfig().getInt("bw-rewards." + what, switch (what) {
            case "kill" -> 5;
            case "bed" -> 10;
            case "win" -> 25;
            default -> 0;
        });
        if (amount <= 0 || !Eco.pay(p, amount)) return;
        stats.coins(p.getUniqueId(), amount);
        p.sendMessage(Component.text("+" + amount + " BedWars reward (" + what + ").", NamedTextColor.GOLD));
    }

    private static boolean isBed(Block b) {
        return b != null && b.getType().name().endsWith("_BED");
    }

    /** The other half of a two-block bed, found by checking the four horizontal neighbours. */
    private static Block bedPartner(Block bed) {
        for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block n = bed.getRelative(f);
            if (isBed(n)) return n;
        }
        return null;
    }

    // ------------------------------------------------------------ generators

    /** Look at the pad block; iron/gold need a team, diamond/emerald are the shared fields. */
    public String genAdd(Player looker, String arena, String type, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwGen.Type gt = genType(type);
        if (gt == null) return "Generator type: iron|gold|diamond|emerald.";
        Block b = looker.getTargetBlock(null, 6);
        if (b == null || b.getType().isAir()) return "Look at the generator pad (within 6 blocks).";
        String s = blockStr(b);
        if (gt == BwGen.Type.IRON || gt == BwGen.Type.GOLD) {
            BwTeam t = a.team(team);
            if (t == null) return "Iron and gold belong to a team: /ih bw gen " + a.name + " add " + type + " <team>.";
            List<String> list = t.genLocs(gt == BwGen.Type.IRON ? "Iron" : "Gold");
            list.add(s);
            y.set("arenas." + a.name + ".teams." + t.name() + "." + type.toLowerCase(Locale.ROOT), list);
        } else {
            List<String> list = gt == BwGen.Type.DIAMOND ? a.genDiamond : a.genEmerald;
            list.add(s);
            y.set("arenas." + a.name + ".gens." + type.toLowerCase(Locale.ROOT), list);
        }
        save();
        return "";
    }

    /** Remove the pad of this type closest to where the staff member is standing. */
    public String genDel(Player stander, String arena, String type, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        BwGen.Type gt = genType(type);
        if (gt == null) return "Generator type: iron|gold|diamond|emerald.";
        List<String> list;
        BwTeam t = null;
        if (gt == BwGen.Type.IRON || gt == BwGen.Type.GOLD) {
            t = a.team(team);
            if (t == null) return "Which team's " + type + " pad? /ih bw gen " + a.name + " del " + type + " <team>.";
            list = t.genLocs(gt == BwGen.Type.IRON ? "Iron" : "Gold");
        } else {
            list = gt == BwGen.Type.DIAMOND ? a.genDiamond : a.genEmerald;
        }
        if (list.isEmpty()) return "No " + type + " pads here.";
        String best = null;
        double bd = Double.MAX_VALUE;
        for (String s : list) {
            Block b = blockFrom(s);
            if (b == null) continue;
            double d = b.getLocation().distanceSquared(stander.getLocation());
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        if (best == null) return "No readable pad of that type.";
        list.remove(best);
        if (t != null) y.set("arenas." + a.name + ".teams." + t.name() + "." + type.toLowerCase(Locale.ROOT), list);
        else y.set("arenas." + a.name + ".gens." + type.toLowerCase(Locale.ROOT), list);
        save();
        return "";
    }

    private static BwGen.Type genType(String s) {
        try {
            return BwGen.Type.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    /** Runtime generators for a new match, from the persisted pads (built fresh every launch). */
    private void buildGens(BwArena a) {
        a.gens.clear();
        for (BwTeam t : a.teams.values()) {
            for (String s : t.genLocs("Iron")) addGen(a, BwGen.Type.IRON, s, t.name());
            for (String s : t.genLocs("Gold")) addGen(a, BwGen.Type.GOLD, s, t.name());
        }
        for (String s : a.genDiamond) addGen(a, BwGen.Type.DIAMOND, s, null);
        for (String s : a.genEmerald) addGen(a, BwGen.Type.EMERALD, s, null);
    }

    private void addGen(BwArena a, BwGen.Type type, String s, String teamName) {
        Block b = blockFrom(s);
        if (b == null) return;
        a.gens.add(new BwGen(type, b.getLocation(), teamName));
    }

    // ------------------------------------------------------------ joining and leaving

    /** The arena a player is in (participant or spectator), or null. */
    public BwArena arenaOf(UUID u) {
        for (BwArena a : arenas.values()) if (a.players.contains(u) || a.spectators.contains(u)) return a;
        return null;
    }

    public String join(Player p, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (arenaOf(p.getUniqueId()) != null) return "You are already in a BedWars game.";
        String err = a.readyError();
        if (!err.isEmpty()) return err;
        if (a.status == BwArena.Status.PLAYING) return spectate(p, a);
        if (a.players.size() >= a.maxPlayers()) return "That arena is full.";

        BwTeam picked = null;
        if (team != null) {
            picked = a.team(team);
            if (picked == null) return "No such team in " + a.name + ".";
            if (picked.members().size() >= a.maxInTeam()) return "Team " + picked.name() + " is full.";
        }

        handIn(p, a);
        a.players.add(p.getUniqueId());
        if (picked != null) a.addTo(picked, p.getUniqueId());
        msg(List.of(p), "You joined " + a.name + ". Teams are balanced when the game starts - or pick one: /ih bw join "
                + a.name + " <team>.", NamedTextColor.GOLD);
        msg(List.of(p), "Kit for this game: /ih bw kit " + BwKits.names() + " (default classic) - you get it at every respawn.",
                NamedTextColor.GRAY);
        msg(a.online(), p.getName() + " joined " + a.name + " (" + a.players.size() + "/" + a.maxPlayers() + ")", NamedTextColor.GRAY);

        // auto-start when the lobby has enough people (BedWars' waiting -> starting)
        if (a.status == BwArena.Status.WAITING && a.players.size() >= a.min()) beginCountdown(a, 10, false);
        return "";
    }

    /** Mid-game join = spectator, like addSpectator in the original. */
    private String spectate(Player p, BwArena a) {
        handIn(p, a);
        a.spectators.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(a.specSpot());
        msg(List.of(p), "Spectating " + a.name + " - the match is running. /ih bw leave to stop.", NamedTextColor.GOLD);
        msg(a.online(), p.getName() + " is now spectating.", NamedTextColor.GRAY);
        return "";
    }

    /** Save what the player came with, then hand them the lobby look (BedWars addPlayer core). */
    private void handIn(Player p, BwArena a) {
        BwArena.Goods g = new BwArena.Goods();
        g.location = p.getLocation();
        g.gameMode = p.getGameMode();
        g.contents = p.getInventory().getContents().clone();
        g.armor = p.getInventory().getArmorContents().clone();
        g.health = p.getHealth();
        g.food = p.getFoodLevel();
        g.xp = p.getExp();
        g.level = p.getLevel();
        g.mainboard = p.getScoreboard();
        a.goods.put(p.getUniqueId(), g);

        p.closeInventory();
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(a.spawn());
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setExp(0);
        p.setLevel(0);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setFlying(false);
        p.setAllowFlight(false);
        hud.give(a, p);
    }

    public String leave(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return "You are not in a BedWars game.";
        if (a.spectators.remove(p.getUniqueId())) {
            BwArena.Goods g = a.goods.remove(p.getUniqueId());
            if (g != null) takeBack(p, g);
            msg(a.online(), p.getName() + " stopped spectating.", NamedTextColor.GRAY);
            if (a.players.isEmpty()) {
                a.stopTask();
                a.status = BwArena.Status.WAITING;
            }
            return "";
        }
        removePlayer(a, p, true, p.getName() + " left " + a.name + ".");
        return "";
    }

    /** The counterpart of join: goods back, lobby slot freed, countdown watched (BedWars removePlayer). */
    private void removePlayer(BwArena a, Player p, boolean restore, String note) {
        a.players.remove(p.getUniqueId());
        a.respawn.remove(p.getUniqueId());
        BwTeam t = a.teamOf(p.getUniqueId());
        if (t != null) t.members().remove(p.getUniqueId());
        a.pickedTeam.remove(p.getUniqueId());
        BwArena.Goods g = a.goods.remove(p.getUniqueId());
        if (note != null) msg(a.online(), note, NamedTextColor.GRAY);

        if (a.status == BwArena.Status.STARTING && !a.forced && a.players.size() < a.min()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            msg(a.online(), "Countdown stopped - not enough players.", NamedTextColor.RED);
        }
        if (a.players.isEmpty() && a.spectators.isEmpty()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            a.forced = false;
        }
        if (a.status == BwArena.Status.PLAYING) checkEnd(a);
        if (g != null && restore) takeBack(p, g);
    }

    private void takeBack(Player p, BwArena.Goods g) {
        hud.release(p, g.mainboard);
        p.setGameMode(g.gameMode);
        p.getInventory().setContents(g.contents);
        p.getInventory().setArmorContents(g.armor);
        p.setFoodLevel(g.food);
        p.setLevel(g.level);
        p.setExp(g.xp);
        p.setFireTicks(0);
        p.setFlying(false);
        p.setAllowFlight(false);
        if (g.location != null) {
            if (!p.isDead()) {
                p.teleport(g.location);
                p.setHealth(Math.min(g.health, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            } else {
                // they are on the death screen right now; the respawn event sends them home
                pendingReturn.put(p.getUniqueId(), g.location);
            }
        }
    }

    // ------------------------------------------------------------ the match itself

    /** Begin the waiting -> starting countdown. seconds<=0 uses the default 10 like the original. */
    private void beginCountdown(BwArena a, int seconds, boolean forced) {
        a.status = BwArena.Status.STARTING;
        a.forced = forced;
        a.countdown = Math.max(3, Math.min(120, seconds <= 0 ? 10 : seconds));
        msg(a.online(), a.name + " starts in " + a.countdown + " seconds!", NamedTextColor.GOLD);
        a.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(a), 20L, 20L);
    }

    public String start(String arena, int seconds) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (a.status != BwArena.Status.WAITING) return "Already running.";
        String err = a.readyError();
        if (!err.isEmpty()) return err;
        if (a.players.size() < 2) return "At least 2 players must be waiting.";
        beginCountdown(a, seconds, true);
        return "";
    }

    private void tick(BwArena a) {
        if (a.status == BwArena.Status.STARTING) tickStarting(a);
        else if (a.status == BwArena.Status.PLAYING) tickPlaying(a);
    }

    private void tickStarting(BwArena a) {
        if (!a.forced && a.players.size() < a.min()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            msg(a.online(), "Countdown stopped - not enough players.", NamedTextColor.RED);
            return;
        }
        if (a.countdown <= 0) {
            launch(a); // the same timer task keeps running: it now ticks the match itself
            return;
        }
        if (a.countdown % 10 == 0 || a.countdown <= 5) {
            for (Player p : a.online()) {
                p.showTitle(Title.title(Component.text(a.countdown, NamedTextColor.GOLD), Component.text("get ready", NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 0.7f, 1.0f + (10 - Math.min(10, a.countdown)) * 0.06f);
                p.sendMessage(Component.text("秘宝 BW Game starts in " + a.countdown + " seconds.", NamedTextColor.GOLD));
            }
        }
        a.countdown--;
    }

    /** Countdown hit zero: balance the unpicked players, arm the generators, send teams to base. */
    private void launch(BwArena a) {
        balance(a);
        a.status = BwArena.Status.PLAYING;
        a.elapsed = 0;
        a.firedEvents.clear();
        a.upg.clear();       // team investments belong to ONE match
        a.trapCool.clear();
        buildGens(a);
        for (BwGen g : a.gens) g.startTick();
        // 0.6.0: pick + arm the match mode (unless staff forced one), and parties have done their job
        BwMode mode = modes.remove(a.name);
        if (mode == null && plugin.getConfig().getBoolean("bw-random-modes", true))
            mode = BwMode.values()[new java.util.Random().nextInt(BwMode.values().length)];
        if (mode == null) mode = BwMode.CLASSIC;
        modes.put(a.name, mode);
        if (mode == BwMode.DOUBLE_IRON) for (BwGen g : a.gens) if (g.type == BwGen.Type.IRON) g.amount += 2;
        party.dissolve(a.players);
        for (UUID u : a.players) stats.game(u);
        StringBuilder teamsLine = new StringBuilder();
        for (BwTeam t : a.teams.values()) {
            if (t.members().isEmpty()) {
                t.setBedDestroyed(true); // empty team = bed already lost, like GameStartingTask does
                continue;
            }
            List<String> names = new ArrayList<>();
            for (UUID u : t.members()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) names.add(p.getName());
            }
            if (!names.isEmpty()) {
                if (teamsLine.length() > 0) teamsLine.append("  |  ");
                teamsLine.append(t.name()).append(": ").append(String.join(", ", names));
            }
            for (UUID u : t.members()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                spawnFresh(p, t, a);
                a.protectUntil.put(u, System.currentTimeMillis() + PROTECT_MS);
                p.showTitle(Title.title(Component.text("GO!", chat(t.color())), Component.text(t.name() + " team", NamedTextColor.GRAY)));
            }
        }
        msg(a.online(), a.name + " is ON - " + teamsLine, NamedTextColor.GOLD);
        if (mode == BwMode.NO_BEDS)
            for (BwTeam t : a.teams.values()) if (!t.members().isEmpty()) clearBedBlocks(t);
        if (mode != BwMode.CLASSIC) announce(a, "Match mode: " + mode.label + "!", NamedTextColor.LIGHT_PURPLE);
    }

    /** Fresh game state at a team spawn: survival, empty hands, kit, full belly, challenge sound. */
    private void spawnFresh(Player p, BwTeam t, BwArena a) {
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(t.spawn());
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        BwKits.apply(p, a);
        store.wallet(p);   // 0.5.1: empty pockets first, then the shop can fill them
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 0.9f, 1.4f);
    }

    /**
     * BedWars' LegacyTeamAssigner minus the party support: shuffle, then give every player without
     * an explicit pick the currently-smallest team that still has room.
     */
    /**
     * Team assignment. 0.6.0: parties move as a unit - the smallest team gets the whole unit
     * first, and members who no longer fit fall back to the next smallest team in order.
     */
    private void balance(BwArena a) {
        List<BwTeam> order = new ArrayList<>(a.teams.values());
        Collections.shuffle(order);
        Set<UUID> done = new HashSet<>();
        for (UUID u : new ArrayList<>(a.players)) {
            if (a.pickedTeam.containsKey(u) || done.contains(u)) continue;
            for (UUID mate : party.unit(u)) {
                if (!a.players.contains(mate) || a.pickedTeam.containsKey(mate)) continue;
                BwTeam best = null;
                for (BwTeam t : order) {
                    if (t.members().size() < a.maxInTeam() && (best == null || t.members().size() < best.members().size())) best = t;
                }
                if (best != null) a.addTo(best, mate);
                done.add(mate);
            }
        }
    }

    // ------------------------------------------------------------ 0.4.0: the playing tick

    private void tickPlaying(BwArena a) {
        a.elapsed++;

        for (BwGen g : a.gens) if (g.shouldRun(a)) g.tick(a);
        store.tick(a);   // 0.5.0: protection/sharpness/healers/trap hum along every second
        hud.update(a);   // 0.5.1: the sidebar is the match as the player sees it
        if (modes.get(a.name) == BwMode.NEED_FOR_SPEED)
            for (Player q : a.online()) q.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 120, 1, false, false, false));

        // respawn sessions: the death screen holds them; when the timer hits zero we respawn for them
        if (!a.respawn.isEmpty()) {
            for (Map.Entry<UUID, Integer> e : new LinkedHashMap<>(a.respawn).entrySet()) {
                if (a.status != BwArena.Status.PLAYING) return; // finish() ran inside this loop earlier
                int left = e.getValue() - 1;
                if (left <= 0) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) a.respawn.remove(e.getKey());
                    else p.spigot().respawn(); // the respawn event removes the entry and re-deploys them
                } else {
                    a.respawn.replace(e.getKey(), left);
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) {
                        p.showTitle(Title.title(Component.text(left, NamedTextColor.RED), Component.text("respawning", NamedTextColor.GRAY)));
                        p.playSound(p.getLocation(), "minecraft:block.note_block.hat", 0.7f, 1.0f);
                    }
                }
            }
        }

        timeline(a);
    }

    /** The original's NextEvent lane, on the stock generators.yml/main.yml numbers. */
    private void timeline(BwArena a) {
        fire(a, DIAMOND_II, "diamond-II", () -> {
            upgradeGens(a, BwGen.Type.DIAMOND, 2);
            announce(a, "Diamond generators are now TIER 2 (every 20s)!", NamedTextColor.AQUA);
        });
        fire(a, EMERALD_II, "emerald-II", () -> {
            upgradeGens(a, BwGen.Type.EMERALD, 2);
            announce(a, "Emerald generators are now TIER 2 (every 50s)!", NamedTextColor.GREEN);
        });
        fire(a, DIAMOND_III, "diamond-III", () -> {
            upgradeGens(a, BwGen.Type.DIAMOND, 3);
            announce(a, "Diamond generators are now TIER 3 (every 15s)!", NamedTextColor.AQUA);
        });
        fire(a, EMERALD_III, "emerald-III", () -> {
            upgradeGens(a, BwGen.Type.EMERALD, 3);
            announce(a, "Emerald generators are now TIER 3 (every 30s)!", NamedTextColor.GREEN);
        });
        fire(a, bedsAt(a), "beds", () -> {
            for (BwTeam t : a.teams.values()) if (!t.bedDestroyed()) clearBedBlocks(t);
            announce(a, "ALL BEDS ARE GONE - sudden death!", NamedTextColor.RED);
            for (Player p : a.everyone())
                p.showTitle(Title.title(Component.text("BEDS DESTROYED", NamedTextColor.DARK_RED), Component.text("final lives now", NamedTextColor.GRAY)));
            checkEnd(a);
        });
        fire(a, endAt(a), "end", () -> {
            BwTeam w = lastStanding(a);
            broadcast(a.name + " ran out of time (" + mmss(endAt(a)) + ") - "
                    + (w == null ? "it's a draw." : w.name() + " takes it!"), NamedTextColor.GOLD);
            finish(a);
        });
    }

    private static String mmss(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private void fire(BwArena a, int atSecond, String id, Runnable r) {
        if (a.elapsed == atSecond && !a.firedEvents.contains(id)) {
            a.firedEvents.add(id);
            r.run();
        }
    }

    private void upgradeGens(BwArena a, BwGen.Type type, int tier) {
        for (BwGen g : a.gens) if (g.type == type) g.upgradeTo(tier);
    }

    private void announce(BwArena a, String text, NamedTextColor c) {
        msg(a.everyone(), text, c);
        for (Player p : a.everyone()) p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 0.8f, 1.6f);
    }

    // ------------------------------------------------------------ death, respawn, beds, elimination

    /** BedWars1058's DeathListener in miniature: with a bed you wait and respawn, without it you're out. */
    public void handleDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return;
        if (a.spectators.contains(p.getUniqueId())) return; // void deaths are handled by the respawn event
        if (a.status != BwArena.Status.PLAYING) {
            removePlayer(a, p, true, null);
            msg(a.online(), p.getName() + " died and is out of " + a.name + ".", NamedTextColor.RED);
            return;
        }
        e.setDroppedExp(0); // levels are not a BedWars currency - the kit on the ground is loot enough
        BwTeam t = a.teamOf(p.getUniqueId());
        stats.death(p.getUniqueId());
        Player killer = p.getKiller();
        if (killer != null && a.players.contains(killer.getUniqueId()) && a.teamOf(killer.getUniqueId()) != t) {
            stats.kill(killer.getUniqueId());
            pay(killer, "kill");
        }
        msg(a.everyone(), p.getName() + " died" + (t != null ? " (" + t.name() + ")" : "") + ".", NamedTextColor.RED);
        if (t != null && !t.bedDestroyed() && t.members().contains(p.getUniqueId())) {
            a.respawn.put(p.getUniqueId(), RESPAWN_SECONDS); // stay a member; the timer sends them back
        } else {
            eliminate(a, p);
        }
    }

    /** PlayerRespawnEvent hook: decide where the freshly-alive player lands. */
    public void handleRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        BwArena a = arenaOf(u);
        if (a == null) {
            Location home = pendingReturn.remove(u);
            if (home != null) e.setRespawnLocation(home);
            return;
        }
        if (a.spectators.contains(u)) {
            e.setRespawnLocation(a.specSpot());
            // eliminated players died in survival; the camera mode only sticks after the respawn
            Bukkit.getScheduler().runTask(plugin, () -> p.setGameMode(GameMode.SPECTATOR));
            return;
        }
        boolean wasRespawning = a.respawn.remove(u) != null;
        BwTeam t = a.teamOf(u);
        if (wasRespawning && a.status == BwArena.Status.PLAYING && t != null && !t.bedDestroyed()) {
            e.setRespawnLocation(t.spawn());
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawnFresh(p, t, a);
                a.protectUntil.put(u, System.currentTimeMillis() + PROTECT_MS);
                msg(a.everyone(), p.getName() + " respawned at " + t.name() + " base.", NamedTextColor.GRAY);
            });
        } else if (wasRespawning) {
            Bukkit.getScheduler().runTask(plugin, () -> eliminate(a, p)); // bed went away during the session
        }
    }

    /** Final death: out of the team lists, in as spectator, team elimination checked. */
    private void eliminate(BwArena a, Player p) {
        a.respawn.remove(p.getUniqueId());
        a.protectUntil.remove(p.getUniqueId());
        BwTeam t = a.teamOf(p.getUniqueId());
        a.players.remove(p.getUniqueId());
        if (t != null) {
            t.members().remove(p.getUniqueId());
            if (t.members().isEmpty()) {
                msg(a.everyone(), "TEAM " + t.name().toUpperCase(Locale.ROOT) + " IS ELIMINATED!", chat(t.color()));
                for (Player q : a.online()) q.playSound(q.getLocation(), "minecraft:entity.wither.death", 0.8f, 1.4f);
            }
        }
        a.pickedTeam.remove(p.getUniqueId());
        a.spectators.add(p.getUniqueId());
        if (!p.isDead()) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(a.specSpot());
        }
        if (a.players.isEmpty() && a.spectators.isEmpty()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            a.forced = false;
            return;
        }
        checkEnd(a);
    }

    private BwTeam lastStanding(BwArena a) {
        BwTeam alive = null;
        int n = 0;
        for (BwTeam t : a.teams.values()) if (!t.members().isEmpty()) {
            n++;
            alive = t;
        }
        return n == 1 ? alive : null;
    }

    private void checkEnd(BwArena a) {
        if (a.status != BwArena.Status.PLAYING) return;
        int alive = 0;
        BwTeam winner = null;
        for (BwTeam t : a.teams.values()) if (!t.members().isEmpty()) {
            alive++;
            winner = t;
        }
        if (alive > 1) return;
        if (alive == 1) {
            msg(a.everyone(), "TEAM " + winner.name().toUpperCase(Locale.ROOT) + " WINS " + a.name + "!", chat(winner.color()));
            broadcast(winner.name() + " wins " + a.name + "!", NamedTextColor.GOLD);
            for (UUID u : winner.members()) {
                stats.win(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    pay(p, "win");
                    p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 32, 0.5, 0.6, 0.5, 0.02);
                    p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 1f, 1.2f);
                }
            }
        } else {
            broadcast(a.name + " ended with nobody standing - draw.", NamedTextColor.GRAY);
        }
        finish(a);
    }

    /** End of a match: a 15 s victory show (rockets + no accidents), then the real hand-over and re-arm. */
    private void finish(BwArena a) {
        if (a.showing) return;
        a.stopTask();
        store.closeFor(a.name);
        a.showing = true;
        for (Player q : a.everyone()) if (q.getGameMode() == GameMode.SURVIVAL) q.setGameMode(GameMode.ADVENTURE);
        BwTeam winner = lastStanding(a);
        if (winner != null) hud.celebrate(a, winner);
        msg(a.everyone(), "Arena resets in 15 seconds - thanks for playing!", NamedTextColor.GOLD);
        Bukkit.getScheduler().runTaskLater(plugin, () -> conclude(a), 20L * 15);
    }

    /** After the show: everything handed back, team chests drop their match loot, map tidied, beds restored. */
    private void conclude(BwArena a) {
        a.showing = false;
        for (Player p : new ArrayList<>(a.everyone())) {
            BwArena.Goods g = a.goods.remove(p.getUniqueId());
            if (g != null) takeBack(p, g);
        }
        rejoin.markEnded(a.name);
        for (BwTeam t : a.teams.values()) emptyChest(t);
        if (!a.snapshot.isEmpty()) {
            int n = restoreSnapshot(a);   // 0.6.1: the real reset - every touched block back to the capture
            if (n >= 0) plugin.getLogger().info("BedWars " + a.name + ": map restored (" + n + " blocks checked against the snapshot).");
            else msg(a.online(), "Snapshot world '" + a.snapWorld + "' is not loaded - map NOT restored!", NamedTextColor.RED);
        } else {
            for (Location l : a.placed) {
                Block b = l.getBlock();
                if (b.getType() != Material.AIR) b.setType(Material.AIR); // bridges and walls made during this game
            }
            for (BwTeam t : a.teams.values()) restoreBed(t);
        }
        int[] c = a.center();
        if (c != null && a.spawn() != null) {
            World w = a.spawn().getWorld();
            double r = a.radius();
            for (org.bukkit.entity.Entity en : w.getNearbyEntities(
                    new Location(w, c[0], (w.getMaxHeight() + w.getMinHeight()) / 2.0, c[1]), r, w.getMaxHeight(), r)) {
                if (en instanceof Item || en instanceof ExperienceOrb) en.remove(); // leftover generator loot
            }
        }
        a.clearRuntime();
        stats.flush();
    }

    private void clearBedBlocks(BwTeam t) {
        for (Location l : new Location[]{t.bed(), t.bed2()}) {
            if (l == null) continue;
            Block b = l.getBlock();
            if (isBed(b)) b.setType(Material.AIR);
        }
        t.setBedDestroyed(true);
    }

    /** Re-place a team's bed for the next game (both halves; the game links them again). */
    private void restoreBed(BwTeam t) {
        if (!t.bedDestroyed() || t.bed() == null || t.bed2() == null) return;
        Material m;
        try {
            m = Material.valueOf(t.color().name() + "_BED");
        } catch (Exception e) {
            m = Material.RED_BED;
        }
        for (Location l : new Location[]{t.bed(), t.bed2()}) {
            if (l.getBlock().getType() == Material.AIR) l.getBlock().setType(m);
        }
        t.setBedDestroyed(false);
    }

    /**
     * Bed block interaction gate. Returns null when the block is not a tracked bed (let the event
     * run), "" when the bed was destroyed for the attacker (cancel the event, no message), or the
     * reason the hit on the bed must bounce (cancel + show it). Tracked beds are safe out of game
     * too - the arena is part of the live world and staff rebuild happens via /ih bw team bed.
     */
    public String breakBed(Player breaker, Block bed) {
        for (BwArena a : arenas.values()) {
            BwTeam t = a.bedOwner(bed);
            if (t == null) continue;
            if (a.showing) return "the match just ended - enjoy the show";
            if (a.status != BwArena.Status.PLAYING) return "the " + a.name + " match is not running";
            if (!a.players.contains(breaker.getUniqueId())) return "this bed is part of a running match";
            if (a.teamOf(breaker.getUniqueId()) == t) return "you cannot break your own bed";
            clearBedBlocks(t);
            stats.bed(breaker.getUniqueId());
            pay(breaker, "bed");
            msg(a.everyone(), t.name().toUpperCase(Locale.ROOT) + "'s bed was destroyed by " + breaker.getName() + "!", chat(t.color()));
            for (Player q : a.everyone()) q.playSound(q.getLocation(), "minecraft:entity.item.break", 0.9f, 0.8f);
            checkEnd(a);
            return "";
        }
        return null;
    }

    /** Sleeping in an enemy bed destroys it; own beds and bystanders are denied. Same contract as breakBed. */
    public String sleepBed(Player sleeper, Block bed) {
        for (BwArena a : arenas.values()) {
            BwTeam t = a.bedOwner(bed);
            if (t == null) continue;
            if (a.showing) return "the match just ended - enjoy the show";
            if (a.status != BwArena.Status.PLAYING) return "the " + a.name + " match is not running";
            if (!a.players.contains(sleeper.getUniqueId())) return "this bed is part of a running match";
            if (a.teamOf(sleeper.getUniqueId()) == t) return "you cannot sleep in your own team's bed";
            clearBedBlocks(t);
            stats.bed(sleeper.getUniqueId());
            pay(sleeper, "bed");
            msg(a.everyone(), t.name().toUpperCase(Locale.ROOT) + "'s bed was destroyed by " + sleeper.getName() + "!", chat(t.color()));
            checkEnd(a);
            return "";
        }
        return null;
    }

    /** True when the block belongs to any tracked bed - explosions and pistons must not move it. */
    public boolean bedProtected(Block b) {
        for (BwArena a : arenas.values()) if (a.bedOwner(b) != null) return true;
        return false;
    }

    /** Track participant-built blocks so finish() can tidy the map again. */
    public void trackPlacement(Player p, Block b) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a != null && a.status == BwArena.Status.PLAYING && a.players.contains(p.getUniqueId())) {
            a.placed.add(b.getLocation());
            touch(a, b);
        }
    }

    // ------------------------------------------------------------ quit, rejoin, reconnect

    public void handleQuit(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return;
        if (a.spectators.contains(p.getUniqueId())) {
            BwArena.Goods g = a.goods.remove(p.getUniqueId());
            if (g != null) rejoin.put(p.getUniqueId(), a, null, g); // handed back at next login
            a.spectators.remove(p.getUniqueId());
            if (a.players.isEmpty()) {
                a.stopTask();
                a.status = BwArena.Status.WAITING;
            }
            return;
        }
        if (a.status == BwArena.Status.PLAYING) {
            // drop their kit where they stood (the original kills logout players for this), store the
            // hand-in, and free the slot - /ih bw rejoin opens again at their next login.
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) p.getWorld().dropItemNaturally(p.getLocation(), it);
            }
            p.getInventory().clear();
            BwArena.Goods g = a.goods.remove(p.getUniqueId());
            BwTeam t = a.teamOf(p.getUniqueId());
            if (g != null) rejoin.put(p.getUniqueId(), a, t, g);
            msg(a.everyone(), p.getName() + " disconnected" + (t != null ? " (" + t.name() + ")" : "") + ".", NamedTextColor.RED);
            a.respawn.remove(p.getUniqueId());
            if (t != null) t.members().remove(p.getUniqueId());
            a.players.remove(p.getUniqueId());
            a.pickedTeam.remove(p.getUniqueId());
            checkEnd(a);
            return;
        }
        removePlayer(a, p, true, p.getName() + " disconnected.");
    }

    public void handleJoin(Player p) {
        BwRejoin.Entry en = rejoin.get(p.getUniqueId());
        if (en == null) return;
        BwArena a = arenas.get(en.arena);
        boolean rejoinable = a != null && a.status == BwArena.Status.PLAYING && !en.ended && !en.team.isEmpty() && a.team(en.team) != null;
        takeBack(p, rejoin.toGoods(en));
        if (rejoinable) {
            rejoin.restored(p.getUniqueId(), en);
            msg(List.of(p), "Your match " + a.name + " is still running - /ih bw rejoin to fight again.", NamedTextColor.GOLD);
        } else {
            rejoin.remove(p.getUniqueId());
        }
    }

    public String rejoin(Player p) {
        BwRejoin.Entry en = rejoin.get(p.getUniqueId());
        if (en == null) return "Nothing to rejoin.";
        BwArena a = arenas.get(en.arena);
        if (a == null || a.status != BwArena.Status.PLAYING || en.ended) return "That match has ended.";
        BwTeam t = a.team(en.team);
        if (t == null) return "Your team is gone.";
        if (arenaOf(p.getUniqueId()) != null) return "You are already in a BedWars game.";
        // their server goods were handed back at login; capture them fresh as the new hand-in
        handIn(p, a);
        a.players.add(p.getUniqueId());
        a.addTo(t, p.getUniqueId());
        spawnFresh(p, t, a);
        a.protectUntil.put(p.getUniqueId(), System.currentTimeMillis() + PROTECT_MS);
        rejoin.remove(p.getUniqueId());
        msg(a.everyone(), p.getName() + " rejoins for " + t.name() + "!", chat(t.color()));
        return "";
    }

    // ------------------------------------------------------------ stop & listener surface

    public String stop(String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (a.status == BwArena.Status.WAITING && a.players.isEmpty() && a.spectators.isEmpty()) return "That arena is not running.";
        a.stopTask();
        for (Player p : new ArrayList<>(a.online())) removePlayer(a, p, true, null);
        for (UUID u : new ArrayList<>(a.spectators)) {
            Player p = Bukkit.getPlayer(u);
            BwArena.Goods g = a.goods.remove(u);
            a.spectators.remove(u);
            if (p != null && g != null) takeBack(p, g);
        }
        rejoin.markEnded(a.name);
        a.clearRuntime();
        broadcast(a.name + " was stopped - everyone is handed back.", NamedTextColor.GRAY);
        return "";
    }

    /** Returns the reason this PvP hit must be blocked, or null when the hit may land. */
    public String pvpDeny(Player victim, Player attacker) {
        BwArena va = arenaOf(victim.getUniqueId());
        BwArena aa = arenaOf(attacker.getUniqueId());
        if (va == null && aa == null) return null;
        if (va != aa) return "you can only fight BedWars players inside their arena";
        Long until = va.protectUntil.get(victim.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) return "that player just respawned";
        if (va.status != BwArena.Status.PLAYING) return "no fighting in the lobby";
        if (va.spectators.contains(victim.getUniqueId()) || va.spectators.contains(attacker.getUniqueId()))
            return "spectators are off-limits";
        BwTeam vt = va.teamOf(victim.getUniqueId());
        BwTeam at = va.teamOf(attacker.getUniqueId());
        if (vt != null && vt == at) return "that is your own team";
        return null;
    }

    // ------------------------------------------------------------ enable/disable plumbing

    /** Called on plugin enable: arenas survive a restart, running games do not (like Games.java). */
    public void onEnableCleanupAnnounce() {
        if (!arenas.isEmpty())
            plugin.getLogger().info("BedWars arenas: " + String.join(", ", arenas.keySet())
                    + " (none running after a restart - join-back goods live in bw-rejoin.json).");
    }

    /** On disable: hand every waiting/playing person their inventory and place back. */
    public void restoreAll() {
        for (BwArena a : arenas.values()) {
            a.stopTask();
            for (Player p : new ArrayList<>(a.everyone())) {
                BwArena.Goods g = a.goods.remove(p.getUniqueId());
                if (g != null) takeBack(p, g);
            }
            rejoin.markEnded(a.name);
            a.clearRuntime();
        }
    }

    private void msg(Iterable<? extends Player> targets, String text, NamedTextColor c) {
        for (Player p : targets) p.sendMessage(Component.text("秘宝 BW " + text, c));
    }

    private void broadcast(String text, NamedTextColor c) {
        Bukkit.broadcast(Component.text("秘宝 BW " + text, c));
    }
}
