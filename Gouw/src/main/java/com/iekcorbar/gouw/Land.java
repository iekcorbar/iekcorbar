package com.iekcorbar.gouw;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A gouw: a claimed area, since 0.4 run like a small town. The owner is the mayor; trusted
 * players are members with a Rank (ASSISTANT manages the town, RESIDENT lives there within the
 * permissions handed out per resident - see perms/memberFlags). The town bank is filled by the
 * residents' own deposits (job earnings come from JobsReborn/onboarding, never from Gouw) and
 * pays for chunk purchases. The chunk limit is rank-based and grows when the town buys
 * (ClaimStore.buyChunks).
 */
final class Land {
    /**
     * What a member may do inside the gouw. The owner and officers may always do everything;
     * each resident can be granted a personal set (with {@link #memberFlags} as the default
     * for everyone who has no personal set yet).
     * CONTAINERS = chests/ect. containers, INTERACT = doors/beds/buttons, CLAIM = add chunks to town.
     */
    enum Flag { BUILD, CONTAINERS, INTERACT, MOB, PVP, CLAIM }

    /** Member roles. The owner is the mayor and never appears in ranks. */
    enum Rank { ASSISTANT, RESIDENT }

    /** Default rights for residents without a personal set - BUILD/CONTAINERS/INTERACT/MOB/PVP on, CLAIM off. */
    static final Set<Flag> DEFAULT_FLAGS = EnumSet.of(Flag.BUILD, Flag.CONTAINERS, Flag.INTERACT, Flag.MOB, Flag.PVP);

    String name;
    UUID owner;
    final String world;
    final Set<Long> chunks = new LinkedHashSet<>();
    /** members (was: the trusted set) with their role */
    final Map<UUID, Rank> ranks = new HashMap<>();
    /** personal rights per resident; absent = follow memberFlags */
    final Map<UUID, EnumSet<Flag>> perms = new HashMap<>();
    /** town bank - money put in via deposit, spent on chunk purchases */
    double bank;
    /** chunks the town bought on top of the mayor's rank limit */
    int purchased;
    /** defaults handed to every new resident (toggled on the DEFAULTS menu page) */
    final Set<Flag> memberFlags = EnumSet.copyOf(DEFAULT_FLAGS);

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

    /** The owner (mayor) plus every member. */
    boolean isMember(UUID who) {
        return owner.equals(who) || ranks.containsKey(who);
    }

    Rank rank(UUID who) {
        return ranks.get(who);
    }

    boolean isMayor(UUID who) {
        return owner.equals(who);
    }

    /** Mayor and officers manage (invite, kick, buy, flags, unclaim, ...). */
    boolean mayManage(UUID who) {
        return owner.equals(who) || ranks.get(who) == Rank.ASSISTANT;
    }

    /** Effective rights of one player: mayor/officer = everything; resident = personal set or defaults. */
    Set<Flag> rights(UUID who) {
        EnumSet<Flag> personal = perms.get(who);
        return personal != null ? personal : memberFlags;
    }

    boolean may(UUID who, Flag f) {
        if (owner.equals(who) || ranks.get(who) == Rank.ASSISTANT) return true;
        return ranks.containsKey(who) && rights(who).contains(f);
    }
}
