package com.iekcorbar.hihou.bw;

import com.iekcorbar.hihou.HihouPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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
 * BedWars etappe 0.3.0 (port of BedWars1058 v25.2 - RE'd from the jar, re-imagined Hihou-style):
 * arenas, teams and the join flow. Arenas live in arenas.json in our own format: waiting spawn,
 * the center+size region primitive (for 0.4.0's generators/beds), min/max limits and named teams
 * with a DyeColor and a spawn. Players hand in inventory+location at join (PlayerGoods in the
 * original) and get them back on leave/stop/quit/death. Once min players are waiting the arena
 * auto-counts down (10s, like the original's starting countdown) and every un-picked player is
 * balanced into the smallest open team - no Party-API, our own state. Generators, beds, shop,
 * upgrades and kits are etappes 0.4.0/0.5.0.
 */
public final class BwGameManager {
    /** Colors staff will most often reach for (all 16 DyeColor names are accepted). */
    public static final String[] TEAM_COLORS = {"red", "blue", "green", "yellow", "cyan", "white", "pink", "gray"};

    private final HihouPlugin plugin;
    private final YamlConfiguration y = new YamlConfiguration();
    private final File file;
    private final Map<String, BwArena> arenas = new LinkedHashMap<>();

    public BwGameManager(HihouPlugin plugin) {
        this.plugin = plugin;
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
                var tsec = y.getConfigurationSection("arenas." + name + ".teams");
                if (tsec != null) {
                    for (String t : tsec.getKeys(false)) {
                        BwTeam team = new BwTeam(t, readColor(y.getString("arenas." + name + ".teams." + t + ".color", "RED")),
                                loc(y.getString("arenas." + name + ".teams." + t + ".spawn")));
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

    String cfg(String arena, String key, String def) {
        return y.getString("arenas." + arena + "." + key, def);
    }

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

    /** Locations as one string "world:x:y:z:yaw:pitch" - one value per point, like the chests.json ids. */
    private static String str(Location l) {
        return l.getWorld().getName() + ":" + l.getX() + ":" + l.getY() + ":" + l.getZ() + ":" + l.getYaw() + ":" + l.getPitch();
    }

    private static Location loc(String s) {
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
        if (a.center() == null) sb.append(", no region yet");
        sb.append(" - teams:");
        for (BwTeam t : a.teams.values()) sb.append(" ").append(t.name()).append(t.spawn() == null ? "?" : "");
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
        if (!a.players.isEmpty()) return "Stop it first (/ih bw stop " + a.name + ").";
        arenas.remove(a.name);
        y.set("arenas." + a.name, null);
        save();
        return "";
    }

    public String setSpawn(Player stander, String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        set(a.name, "spawn", str(stander.getLocation()));
        return "";
    }

    public String setCenter(Player stander, String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        set(a.name, "center", List.of(stander.getLocation().getBlockX(), stander.getLocation().getBlockZ()));
        if (cfgI(a.name, "size", 0) <= 0) y.set("arenas." + a.name + ".size", 40); // until 'size' is set, same courtesy as /ih game
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

    // ------------------------------------------------------------ joining and leaving

    /** The arena a player is in (as participant), or null. */
    public BwArena arenaOf(UUID u) {
        for (BwArena a : arenas.values()) if (a.players.contains(u)) return a;
        return null;
    }

    public String join(Player p, String arena, String team) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (arenaOf(p.getUniqueId()) != null) return "You are already in a BedWars game.";
        String err = a.readyError();
        if (!err.isEmpty()) return err;
        if (a.status == BwArena.Status.PLAYING)
            return "The game is running - spectating comes with 0.4.0 (beds and generators).";
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
        msg(a.online(), p.getName() + " joined " + a.name + " (" + a.players.size() + "/" + a.maxPlayers() + ")", NamedTextColor.GRAY);

        // auto-start when the lobby has enough people (BedWars' waiting -> starting)
        if (a.status == BwArena.Status.WAITING && a.players.size() >= a.min()) beginCountdown(a, 10, false);
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
        msg(List.of(p), "You joined " + a.name + ". Teams are balanced when the game starts - or pick one: /ih bw join " + a.name + " <team>.", NamedTextColor.GOLD);
    }

    public String leave(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return "You are not in a BedWars game.";
        removePlayer(a, p, true, p.getName() + " left " + a.name + ".");
        return "";
    }

    /** The counterpart of join: goods back, lobby slot freed, countdown watched (BedWars removePlayer). */
    private void removePlayer(BwArena a, Player p, boolean restore, String note) {
        a.players.remove(p.getUniqueId());
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
        if (a.players.isEmpty()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            a.forced = false;
        }
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
        if (!p.isDead()) {
            p.teleport(g.location);
            p.setHealth(Math.min(g.health, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        } else {
            // dying players respawn first; the scheduler moves them back a tick later
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(g.location));
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
        if (!a.forced && a.players.size() < a.min()) {
            a.stopTask();
            a.status = BwArena.Status.WAITING;
            msg(a.online(), "Countdown stopped - not enough players.", NamedTextColor.RED);
            return;
        }
        if (a.countdown <= 0) {
            a.stopTask();
            launch(a);
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

    /** Countdown hit zero: balance the unpicked players and send everyone to their team spawn. */
    private void launch(BwArena a) {
        balance(a);
        a.status = BwArena.Status.PLAYING;
        StringBuilder teamsLine = new StringBuilder();
        for (BwTeam t : a.teams.values()) {
            if (t.members().isEmpty()) continue;
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
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(t.spawn());
                p.setHealth(20);
                p.setFoodLevel(20);
                p.setFireTicks(0);
                p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 0.9f, 1.4f);
                p.showTitle(Title.title(Component.text("GO!", chat(t.color())), Component.text(t.name() + " team", NamedTextColor.GRAY)));
            }
        }
        msg(a.online(), a.name + " is ON - " + teamsLine, NamedTextColor.GOLD);
        // beds, generators and the game timer (sudden death, map reset, winner) arrive with 0.4.0
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

    public String stop(String arena) {
        BwArena a = arenas.get(arena.toLowerCase(Locale.ROOT));
        if (a == null) return "No such arena.";
        if (a.status == BwArena.Status.WAITING && a.players.isEmpty()) return "That arena is not running.";
        a.stopTask();
        for (Player p : new ArrayList<>(a.online())) removePlayer(a, p, true, null);
        a.clearRuntime();
        broadcast(a.name + " was stopped - everyone is handed back.", NamedTextColor.GRAY);
        return "";
    }

    // ------------------------------------------------------------ listener hooks

    public void handleQuit(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return;
        // Quit during a game: goods are restored straight in the event, so the relog happens where
        // they came from with their stuff. A proper offline-safe store is 0.4.0's ReJoin work.
        removePlayer(a, p, true, p.getName() + " disconnected.");
    }

    public void handleDeath(Player p) {
        BwArena a = arenaOf(p.getUniqueId());
        if (a == null) return;
        removePlayer(a, p, true, null);
        msg(a.online(), p.getName() + " died and is out of " + a.name + ".", NamedTextColor.RED);
        // team elimination and winner detection belong to the bed etappe (0.4.0)
    }

    /** Returns the reason this PvP hit must be blocked, or null when the hit may land. */
    public String pvpDeny(Player victim, Player attacker) {
        BwArena va = arenaOf(victim.getUniqueId());
        BwArena aa = arenaOf(attacker.getUniqueId());
        if (va == null && aa == null) return null;
        if (va != aa) return "you can only fight BedWars players inside their arena";
        if (va.status != BwArena.Status.PLAYING) return "no fighting in the lobby";
        BwTeam vt = va.teamOf(victim.getUniqueId());
        BwTeam at = va.teamOf(attacker.getUniqueId());
        if (vt != null && vt == at) return "that is your own team";
        return null;
    }

    // ------------------------------------------------------------ start/stop plumbing

    /** Called on plugin enable: arenas survive a restart, running games do not (like Games.java). */
    public void onEnableCleanupAnnounce() {
        if (!arenas.isEmpty())
            plugin.getLogger().info("BedWars arenas: " + String.join(", ", arenas.keySet())
                    + " (none running after a restart - just /ih bw join again).");
    }

    /** On disable: hand every waiting/playing person their inventory and place back. */
    public void restoreAll() {
        for (BwArena a : arenas.values()) {
            a.stopTask();
            for (Player p : new ArrayList<>(a.online())) {
                BwArena.Goods g = a.goods.remove(p.getUniqueId());
                if (g != null) takeBack(p, g);
            }
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
