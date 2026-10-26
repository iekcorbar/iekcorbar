package com.iekcorbar.hihou.bw;

import org.bukkit.DyeColor;
import org.bukkit.Location;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One BedWars team: a name, a color and a spawn point. Members are runtime-only - the team itself
 * lives in arenas.json (name + color + spawn), who is in it during a game lives in the session.
 * Team names are free (red/blue/green/... but any word works); the DyeColor drives chat color and
 * the later bed/generator tinting (0.4.0).
 */
public final class BwTeam {
    private final String name;
    private final DyeColor color;
    private Location spawn;
    private final Set<UUID> members = new LinkedHashSet<>();

    BwTeam(String name, DyeColor color, Location spawn) {
        this.name = name;
        this.color = color;
        this.spawn = spawn;
    }

    public String name() {
        return name;
    }

    public DyeColor color() {
        return color;
    }

    public Location spawn() {
        return spawn;
    }

    void setSpawn(Location l) {
        spawn = l;
    }

    public Set<UUID> members() {
        return members;
    }
}
