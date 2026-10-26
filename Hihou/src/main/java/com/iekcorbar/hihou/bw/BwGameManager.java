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
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    private int bedsAt(BwArena a) {
        return cfgI(a.name, "bedsAt", BEDS_DESTROY);
    }

    private int endAt(BwArena a) {
        return cfgI(a.name, "endAt", GAME_END);
    }

    private final HihouPlugin plugin;
    private final YamlConfiguration y = new YamlConfiguration();
    private final File file;
    private final Map<String, BwArena> arenas = new LinkedHashMap<>();
    private final BwRejoin rejoin;
    /** Lobby-dead players are restored on quit; their home spot waits here for the respawn click. */
    private final Map<UUID, Location> pendingReturn = new LinkedHashMap<>();

    public BwGameManager(HihouPlugin plugin) {
        this.plugin = plugin;
        rejoin = new BwRejoin(plugin);
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
                        a.teams.put(t, team);
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
        buildGens(a);
        for (BwGen g : a.gens) g.startTick();
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
                spawnFresh(p, t);
                a.protectUntil.put(u, System.currentTimeMillis() + PROTECT_MS);
                p.showTitle(Title.title(Component.text("GO!", chat(t.color())), Component.text(t.name() + " team", NamedTextColor.GRAY)));
            }
        }
        msg(a.online(), a.name + " is ON - " + teamsLine, NamedTextColor.GOLD);
    }

    /** Fresh game state at a team spawn: survival, empty hands, full belly, challenge sound. */
    private void spawnFresh(Player p, BwTeam t) {
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(t.spawn());
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 0.9f, 1.4f);
    }

    /**
     * BedWars' LegacyTeamAssigner minus the party support: shuffle, then give every player without
     * an explicit pick the currently-smallest team that still has room.
     */
    private void balance(BwArena a) {
        List<BwTeam> order = new ArrayList<>(a.teams.values());
        Collections.shuffle(order);
        for (UUID u : new ArrayList<>(a.players)) {
            if (a.pickedTeam.containsKey(u)) continue;
            BwTeam best = null;
            for (BwTeam t : order) {
                if (t.members().size() < a.maxInTeam() && (best == null || t.members().size() < best.members().size())) best = t;
            }
            if (best != null) a.addTo(best, u);
        }
    }

    // ------------------------------------------------------------ 0.4.0: the playing tick

    private void tickPlaying(BwArena a) {
        a.elapsed++;

        for (BwGen g : a.gens) if (g.shouldRun(a)) g.tick();

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
                spawnFresh(p, t);
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
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 32, 0.5, 0.6, 0.5, 0.02);
                    p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 1f, 1.2f);
                }
            }
        } else {
            broadcast(a.name + " ended with nobody standing - draw.", NamedTextColor.GRAY);
        }
        finish(a);
    }

    /** End of a match: everyone handed back, placed blocks and dropped gear cleaned, beds restored. */
    private void finish(BwArena a) {
        a.stopTask();
        for (Player p : new ArrayList<>(a.everyone())) {
            BwArena.Goods g = a.goods.remove(p.getUniqueId());
            if (g != null) takeBack(p, g);
        }
        rejoin.markEnded(a.name);
        for (Location l : a.placed) {
            Block b = l.getBlock();
            if (b.getType() != Material.AIR) b.setType(Material.AIR); // bridges and walls made during this game
        }
        for (BwTeam t : a.teams.values()) restoreBed(t);
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
            if (a.status != BwArena.Status.PLAYING) return "the " + a.name + " match is not running";
            if (!a.players.contains(breaker.getUniqueId())) return "this bed is part of a running match";
            if (a.teamOf(breaker.getUniqueId()) == t) return "you cannot break your own bed";
            clearBedBlocks(t);
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
            if (a.status != BwArena.Status.PLAYING) return "the " + a.name + " match is not running";
            if (!a.players.contains(sleeper.getUniqueId())) return "this bed is part of a running match";
            if (a.teamOf(sleeper.getUniqueId()) == t) return "you cannot sleep in your own team's bed";
            clearBedBlocks(t);
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
        if (a != null && a.status == BwArena.Status.PLAYING && a.players.contains(p.getUniqueId()))
            a.placed.add(b.getLocation());
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
        spawnFresh(p, t);
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
