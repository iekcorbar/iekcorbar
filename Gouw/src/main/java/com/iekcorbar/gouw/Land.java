package com.iekcorbar.gouw;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A gouw: one player's claimed area. The claim unit is a chunk (x, z) - exactly what Lands used, so
 * importing a Lands database is a straight copy. Members live in trusted; since 0.2 they can be limited
 * per flag (memberFlags): build, open, hit mobs, attack players. Default is everything - so an imported
 * or plain gouw behaves like v0.1 and like the old Lands setup the server used.
 */
final class Land {
    /** What a trusted member may do inside the gouw. The owner may always do everything. */
    enum Flag { BUILD, ACCESS, MOB, PVP }

    String name;
    UUID owner;
    final String world;
    final Set<Long> chunks = new LinkedHashSet<>();
    final Set<UUID> trusted = new LinkedHashSet<>();
    final Set<Flag> memberFlags = EnumSet.allOf(Flag.class);

    Land(String name, UUID owner, String world) {
        this.name = name;
        this.owner = owner;
        this.world = world;
    }

    static long key(int x, int z) {
        return (((long) x) << 32) ^ (((long) z) & 0xFFFFFFFFL);
    }

    static int kx(long k) {
        return (int) (k >> 32);
    }

    static int kz(long k) {
        return (int) k;
    }

    boolean has(int cx, int cz) {
        return chunks.contains(key(cx, cz));
    }

    boolean isMember(UUID who) {
        return owner.equals(who) || trusted.contains(who);
    }

    boolean may(UUID who, Flag f) {
        return owner.equals(who) || (trusted.contains(who) && memberFlags.contains(f));
    }
}
