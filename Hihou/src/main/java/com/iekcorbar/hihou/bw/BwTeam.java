package com.iekcorbar.hihou.bw;

import org.bukkit.DyeColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One BedWars team: a name, a color, a spawn point and (since 0.4.0) a bed. Members and the
 * bed-destroyed flag are runtime-only - the team itself lives in arenas.json (name + color +
 * spawn + bed), who is in it during a game lives in the session. Team names are free
 * (red/blue/green/... but any word works); the DyeColor drives chat color and bed tinting.
 */
public final class BwTeam {
    private final String name;
    private final DyeColor color;
    private Location spawn;
    /** Both bed halves (the original tracks the clicked block plus its partner). */
    private Location bed;
    private Location bed2;
    private boolean bedDestroyed;
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Persisted generator positions for this team's iron/gold pads. */
    private final List<String> ironLocs = new ArrayList<>();
    private final List<String> goldLocs = new ArrayList<>();

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

    public Location bed() {
        return bed;
    }

    public Location bed2() {
        return bed2;
    }

    void setBed(Location bed, Location bed2) {
        this.bed = bed;
        this.bed2 = bed2;
    }

    /** True for either half of this team's bed block. */
    boolean isBedBlock(org.bukkit.block.Block b) {
        return !bedDestroyed && bed != null && bed2 != null
                && (sameBlock(bed, b) || sameBlock(bed2, b));
    }

    private static boolean sameBlock(Location l, org.bukkit.block.Block b) {
        return l.getWorld() == b.getWorld() && l.getBlockX() == b.getX() && l.getBlockY() == b.getY() && l.getBlockZ() == b.getZ();
    }

    public boolean bedDestroyed() {
        return bedDestroyed;
    }

    /** Destroy keeps the coordinates: finish() re-places the bed blocks for the next game. */
    void setBedDestroyed(boolean v) {
        bedDestroyed = v;
    }

    public Set<UUID> members() {
        return members;
    }

    List<String> genLocs(String kind) {
        return kind.equals("Iron") ? ironLocs : goldLocs;
    }
}
