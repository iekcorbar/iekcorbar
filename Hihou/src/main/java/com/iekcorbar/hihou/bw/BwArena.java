package com.iekcorbar.hihou.bw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A BedWars arena: the persistent setup (waiting spawn, region, teams, limits) lives in arenas.json;
 * everything about a running game lives in this object and is gone after a restart - same deal as
 * the Games sessions. Status follows BedWars1058's state machine minus the parts the next etappes
 * need: waiting -> starting -> playing (restarting/map-reset = 0.4.0).
 *
 * The region (center + size) uses the same square->circle primitive as /ih game last: not needed to
 * run a match yet, but generators, bed protection and map reset (0.4.0) will all live inside it.
 */
final class BwArena {

    enum Status {WAITING, STARTING, PLAYING}

    /** What a player handed in at join, so leave/stop/quit can hand it back (BedWars' PlayerGoods). */
    static final class Goods {
        Location location;
        GameMode gameMode;
        ItemStack[] contents;
        ItemStack[] armor;
        double health;
        int food;
        float xp;
        int level;
    }

    final String name;
    private final BwGameManager mgr;

    Status status = Status.WAITING;
    BukkitTask task;
    int countdown;
    /** Staff /ih bw start: don't bounce back to waiting when players drop below min. */
    boolean forced;

    final Set<UUID> players = new LinkedHashSet<>();
    /** Explicit team picks (/ih bw join <arena> <team>). Everyone else is balanced at start. */
    final Map<UUID, String> pickedTeam = new LinkedHashMap<>();
    final Map<UUID, Goods> goods = new LinkedHashMap<>();
    /** name -> team, in add-order. */
    final Map<String, BwTeam> teams = new LinkedHashMap<>();

    BwArena(BwGameManager mgr, String name) {
        this.mgr = mgr;
        this.name = name;
    }

    /** Team size cap from arenas.json (default 4, like the classic 4x4). */
    int maxInTeam() {
        return mgr.cfgI(name, "maxTeam", 4);
    }

    /** minPlayers from arenas.json (default 2). */
    int min() {
        return mgr.cfgI(name, "min", 2);
    }

    int maxPlayers() {
        return teams.size() * maxInTeam();
    }

    Location spawn() {
        return mgr.cfgLoc(name, "spawn");
    }

    /** Region center [x,z] + radius - our center+size primitive, for 0.4.0 (generators/bed/map). */
    int[] center() {
        List<Integer> c = mgr.cfgIntList(name, "center");
        return c.size() == 2 ? new int[]{c.get(0), c.get(1)} : null;
    }

    int radius() {
        return mgr.cfgI(name, "size", 40);
    }

    List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    BwTeam team(String t) {
        return t == null ? null : teams.get(t.toLowerCase(Locale.ROOT));
    }

    BwTeam teamOf(UUID u) {
        return team(pickedTeam.get(u));
    }

    /** Assign a player to a team; picks win over balancing, team lists stay authoritative. */
    void addTo(BwTeam t, UUID u) {
        t.members().add(u);
        pickedTeam.put(u, t.name());
    }

    /** Can a (new) game actually run here? Used before auto-countdown and before staff start. */
    String readyError() {
        if (spawn() == null) return "No waiting spawn yet - stand there and /ih bw set " + name + " spawn.";
        if (teams.size() < 2) return "BedWars needs at least 2 teams - /ih bw team " + name + " add <team> <color>.";
        for (BwTeam t : teams.values()) {
            if (t.spawn() == null) return "Team " + t.name() + " has no spawn - /ih bw team " + name + " spawn " + t.name() + ".";
        }
        return "";
    }

    void clearRuntime() {
        status = Status.WAITING;
        forced = false;
        players.clear();
        pickedTeam.clear();
        goods.clear();
        for (BwTeam t : teams.values()) t.members().clear();
    }

    void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
