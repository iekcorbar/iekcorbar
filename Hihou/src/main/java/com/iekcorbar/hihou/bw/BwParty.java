package com.iekcorbar.hihou.bw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.6.0: lobby parties (BedWars' Parties minus the cross-server persistence). A party is
 * purely runtime - leader maps to everyone including himself - and its only promise is
 * the balance() rule: members of one party land on the same team when the match starts,
 * split off in order when the team cannot hold them all.
 *
 * Invites are the 0.5.1 way: clickable chat, accept carries the leader's name; a restart
 * simply forgets the parties, which for a lobby group is the right amount of persistence.
 */
final class BwParty {
    /** member -> leader (the leader maps to itself). */
    private final Map<UUID, UUID> memberOf = new LinkedHashMap<>();
    /** invited player -> waiting leaders (accept one by name, like town invites). */
    private final Map<UUID, Set<UUID>> invites = new LinkedHashMap<>();

    String invite(Player leader, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) return "'" + name + "' is not online.";
        UUID u = target.getUniqueId();
        if (u.equals(leader.getUniqueId())) return "You are already a party of one.";
        if (memberOf.get(u) != null && memberOf.get(u).equals(leader.getUniqueId())) return "Already in your party.";
        invites.computeIfAbsent(u, k -> new LinkedHashSet<>()).add(leader.getUniqueId());
        target.sendMessage(Component.text(leader.getName() + " invites you to join their party.  ", NamedTextColor.GOLD)
                .append(Component.text("[Join]", NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ih bw party join " + leader.getName())))
                .append(Component.text("  or  ", NamedTextColor.GRAY))
                .append(Component.text("[Ignore]", NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ih bw party ignore " + leader.getName()))));
        return "";
    }

    String join(Player p, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        UUID l = leader != null ? leader.getUniqueId() : null;
        Set<UUID> open = l == null ? null : invites.get(p.getUniqueId());
        if (open == null || !open.remove(l)) return "No party invite from " + leaderName + " waiting for you.";
        UUID mine = memberOf.get(p.getUniqueId());
        if (mine != null && !mine.equals(l)) leave(p);
        memberOf.put(p.getUniqueId(), l);
        for (UUID m : memberOf.keySet()) if (m.equals(l) || memberOf.get(m).equals(l)) {
            Player q = Bukkit.getPlayer(m);
            if (q != null && !q.getUniqueId().equals(p.getUniqueId())) q.sendMessage(Component.text(p.getName() + " joined the party.", NamedTextColor.GRAY));
        }
        return "";
    }

    String ignore(Player p, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        Set<UUID> open = leader == null ? null : invites.get(p.getUniqueId());
        if (open == null || !open.remove(leader.getUniqueId())) return "No invite from " + leaderName + " waiting.";
        return "";
    }

    String leave(Player p) {
        UUID mine = memberOf.remove(p.getUniqueId());
        if (mine == null) return "You are not in a party.";
        if (mine.equals(p.getUniqueId())) {
            // the leader walked away: the party dissolves, members hear it
            for (UUID m : new ArrayList<>(memberOf.keySet()))
                if (memberOf.get(m).equals(mine)) {
                    memberOf.remove(m);
                    Player q = Bukkit.getPlayer(m);
                    if (q != null) q.sendMessage(Component.text("Your party leader left - the party is gone.", NamedTextColor.RED));
                }
        }
        return "";
    }

    UUID leaderOf(UUID u) {
        return memberOf.get(u);
    }

    /** Every member of u's party INCLUDING u itself, leader first - the unit the balancer moves. */
    List<UUID> unit(UUID u) {
        List<UUID> out = new ArrayList<>();
        UUID l = memberOf.getOrDefault(u, u);
        out.add(l);
        for (Map.Entry<UUID, UUID> e : memberOf.entrySet())
            if (e.getValue().equals(l) && !e.getKey().equals(l)) out.add(e.getKey());
        if (!out.contains(u)) out.add(u);
        return out;
    }

    String list(Player p) {
        UUID l = memberOf.getOrDefault(p.getUniqueId(), p.getUniqueId());
        StringBuilder sb = new StringBuilder("party: ");
        boolean first = true;
        for (UUID m : unit(p.getUniqueId())) {
            if (!first) sb.append(", ");
            first = false;
            Player q = Bukkit.getPlayer(m);
            sb.append(q != null ? q.getName() : "?");
        }
        p.sendMessage(Component.text(sb.toString(), NamedTextColor.GRAY));
        return "";
    }

    /** Match started: the lobby party did its one job - release everyone. */
    void dissolve(Iterable<UUID> players) {
        for (UUID u : players) memberOf.remove(u);
        invites.keySet().removeAll(toSet(players));
    }

    private static Set<UUID> toSet(Iterable<UUID> it) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID u : it) out.add(u);
        return out;
    }
}
