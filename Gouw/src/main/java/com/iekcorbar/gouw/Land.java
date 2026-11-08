package com.iekcorbar.gouw;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A gouw: a claimed area, since 0.4 run like a small town. The owner is the mayor; trusted
 * players are members with a Rank (ASSISTANT may manage, RESIDENT just lives there). A mayor
 * can hand out a job (a Jobs Reborn job name) per member, paid daily from the town bank. The chunk
 * limit is rank-based and grows when the town buys extra chunks (ClaimStore.buyChunks).
 *
 * Old behaviour stays: members get the gouw-wide memberFlags (build/open/mobs/pvp) and the owner may
 * always do everything. Data is one JSON file (claims.json) - see ClaimStore.
 */
final class Land {
    /** What a member may do inside the gouw. The owner may always do everything. */
    enum Flag { BUILD, ACCESS, MOB, PVP }

    /** Member roles. The owner is the mayor and never appears in ranks. */
    enum Rank { ASSISTANT, RESIDENT }

    String name;
    UUID owner;
    final String world;
    final Set<Long> chunks = new LinkedHashSet<>();
    /** members (was: the trusted set) with their role */
    final Map<UUID, Rank> ranks = new HashMap<>();
    /** uuid -> job (Jobs Reborn job name), only meaningful for members */
    final Map<UUID, String> jobs = new HashMap<>();
    /** town bank - money put in via deposit, spent on chunk purchases and job salaries */
    double bank;
    /** chunks the town bought on top of the mayor's rank limit */
    int purchased;
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

    /** The owner (mayor) plus every member. */
    boolean isMember(UUID who) {
        return owner.equals(who) || ranks.containsKey(who);
    }

    /** null for strangers; mayor is returned as Rank.ASSISTANT-equivalent via isMayor. */
    Rank rank(UUID who) {
        return ranks.get(who);
    }

    boolean isMayor(UUID who) {
        return owner.equals(who);
    }

    /** Burgemeester and assistants manage; residents only use their flags. */
    boolean mayManage(UUID who) {
        return owner.equals(who) || ranks.get(who) == Rank.ASSISTANT;
    }

    boolean may(UUID who, Flag f) {
        return owner.equals(who) || (ranks.containsKey(who) && memberFlags.contains(f));
    }
}
