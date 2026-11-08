package com.iekcorbar.gouw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /gouw - claim and manage your gouw. Aliases /g and /claim (players knew /claim from the old plugin).
 * Since 0.4 also the town side: ranks (mayor/officer/resident), per-resident rights (build/chests/
 * interact/mobs/pvp/claim), the town bank (every resident deposits and withdraws; deposits come
 * from their job earnings, which are paid by JobsReborn - Gouw never assigns or pays jobs) and
 * /gouw buy to raise the chunk limit from the bank.
 * Everything writes through ClaimStore (which saves claims.json).
 */
final class GouwCommand implements TabExecutor {
    private final GouwPlugin plugin;
    private final ClaimStore store;

    GouwCommand(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
    }

    private static void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    static String money(double d) {
        return String.format(Locale.US, "%,.2f", d);
    }

    private static double amount(String s) {
        try {
            return Math.max(0, Double.parseDouble(s.replace(",", ".")));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** The gouw the player belongs to (own or town), with a ready error message if there is none. */
    private Land needTown(Player p) {
        Land l = store.homeOf(p.getUniqueId());
        if (l == null) msg(p, "You have no gouw yet - stand in a chunk and /gouw claim.", NamedTextColor.GRAY);
        return l;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String s0 = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);

        if (s0.equals("admin")) return admin(s, label, a);
        if (!(s instanceof Player p)) {
            msg(s, "Only players can claim land - use /" + label + " admin.", NamedTextColor.RED);
            return true;
        }
        Location loc = p.getLocation();
        World w = loc.getWorld();
        int cx = loc.getBlockX() >> 4, cz = loc.getBlockZ() >> 4;
        Land here = store.land(w, loc.getBlockX(), loc.getBlockZ());

        switch (s0) {
            case "claim" -> {
                String err = store.claim(p, w, cx, cz);
                if (err != null) msg(p, "Cannot claim: " + err + ".", NamedTextColor.RED);
                else {
                    Land l = store.homeOf(p.getUniqueId());
                    msg(p, "Chunk " + cx + "," + cz + " claimed - " + l.name + " now has " + l.chunks.size() + " chunks (limit " + store.limitOf(l) + ").", NamedTextColor.GREEN);
                }
            }
            case "autoclaim" -> {
                int r = 2;
                if (a.length > 1) try { r = Math.max(0, Math.min(8, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                int got = 0;
                String last = null;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        last = store.claim(p, w, cx + dx, cz + dz);
                        if (last == null) got++;
                    }
                if (got == 0) msg(p, "Nothing claimed: " + last + ".", NamedTextColor.RED);
                else msg(p, "Claimed " + got + " chunk(s) around you.", NamedTextColor.GREEN);
            }
            case "unclaim" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("all")) {
                    Land l = store.landOf(p.getUniqueId());
                    if (l == null) msg(p, "You have no gouw of your own.", NamedTextColor.GRAY);
                    else if (!l.isMayor(p.getUniqueId())) msg(p, "Only the mayor releases the whole gouw.", NamedTextColor.RED);
                    else {
                        int n = l.chunks.size();
                        store.unclaimAll(l);
                        msg(p, "Released all " + n + " chunks.", NamedTextColor.GREEN);
                    }
                    return true;
                }
                String err = store.unclaim(p, w, cx, cz);
                msg(p, err != null ? "Cannot unclaim: " + err + "." : "Chunk released.", err == null ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "trust", "invite" -> {
                Land l = needTown(p);
                if (l == null || !l.mayManage(p.getUniqueId())) { if (l != null) msg(p, "Only the mayor and officer invite members.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " invite <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known to this server.", NamedTextColor.RED); return true; }
                String err = store.invite(l, who);
                if (err != null) msg(p, "Cannot invite " + a[1] + ": " + err + ".", NamedTextColor.RED);
                else msg(p, a[1] + " is now a resident of " + l.name + ".", NamedTextColor.GREEN);
            }
            case "untrust", "kick" -> {
                Land l = needTown(p);
                if (l == null || !l.mayManage(p.getUniqueId())) { if (l != null) msg(p, "Only the mayor and officer kick members.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " kick <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known to this server.", NamedTextColor.RED); return true; }
                String err = store.kick(l, who);
                msg(p, err != null ? a[1] + " was not a member." : a[1] + " is no longer a member of " + l.name + ".", err == null ? NamedTextColor.GOLD : NamedTextColor.GRAY);
            }
            case "promote", "demote" -> {
                Land l = needTown(p);
                if (l == null) return true;
                if (!l.isMayor(p.getUniqueId())) { msg(p, "Only the mayor " + (s0.equals("promote") ? "promotes officers." : "demotes them again."), NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " " + s0 + " <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                String err = who == null ? "player unknown" : (s0.equals("promote") ? store.promote(l, who) : store.demote(l, who));
                if (err != null) msg(p, "Cannot " + s0 + ": " + err + ".", NamedTextColor.RED);
                else msg(p, a[1] + " is " + (s0.equals("promote") ? "now officer of " : "back to resident in ") + l.name + ".", NamedTextColor.GREEN);
            }
            case "members" -> {
                Land l = needTown(p);
                if (l == null) return true;
                if (l.ranks.isEmpty()) { msg(p, "No members yet - /gouw invite <player>", NamedTextColor.GRAY); return true; }
                msg(p, "Residents of " + l.name + " (mayor: " + nameOf(l.owner) + "):", NamedTextColor.GOLD);
                for (Map.Entry<UUID, Land.Rank> r : l.ranks.entrySet()) {
                    EnumSet<Land.Flag> personal = l.perms.get(r.getKey());
                    String rights;
                    if (r.getValue() == Land.Rank.ASSISTANT) rights = "all";
                    else rights = (personal == null ? "defaults" : personal.stream().map(f -> f.name()).sorted().toList().toString());
                    msg(p, " - " + nameOf(r.getKey()) + " [" + (r.getValue() == Land.Rank.ASSISTANT ? "officer" : "resident") + "] rights: " + rights, NamedTextColor.GRAY);
                }
            }
            case "bank" -> {
                Land l = needTown(p);
                if (l == null) return true;
                msg(p, l.name + " bank: " + money(l.bank) + " (every resident deposits and withdraws; mayor & officer buy chunks).", NamedTextColor.GOLD);
                msg(p, "Chunks: " + l.chunks.size() + " / " + store.limitOf(l) + " - /gouw buy <n> raises the limit from the bank (" + plugin.getConfig().getInt("price-per-chunk", 250) + " each).", NamedTextColor.GRAY);
            }
            case "deposit" -> {
                Land l = needTown(p);
                if (l == null) return true;
                double amt = a.length > 1 ? amount(a[1]) : -1;
                if (amt <= 0) { msg(p, "/" + label + " deposit <amount>", NamedTextColor.RED); return true; }
                String err = store.deposit(p, l, amt);
                msg(p, err != null ? "Deposit failed: " + err + "." : "Deposited " + money(amt) + " - the bank holds " + money(l.bank) + ".", err == null ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "withdraw" -> {
                Land l = needTown(p);
                if (l == null) return true;
                double amt = a.length > 1 ? amount(a[1]) : -1;
                if (amt <= 0) { msg(p, "/" + label + " withdraw <amount>", NamedTextColor.RED); return true; }
                String err = store.withdraw(p, l, amt);
                msg(p, err != null ? "Withdraw failed: " + err + "." : "Withdrew " + money(amt) + " - the bank holds " + money(l.bank) + ".", err == null ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "buy" -> {
                Land l = needTown(p);
                if (l == null) return true;
                int n = 1;
                if (a.length > 1) try { n = Math.max(1, Math.min(50, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                String err = store.buyChunks(p, l, n);
                if (err != null) msg(p, "Cannot buy: " + err + ".", NamedTextColor.RED);
                else msg(p, "Bought " + n + " chunk(s) for " + money((double) plugin.getConfig().getInt("price-per-chunk", 250) * n) + " - new limit: " + store.limitOf(l) + ".", NamedTextColor.GREEN);
            }
            case "rename" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Only the mayor renames a gouw (claim one first).", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " rename <name>", NamedTextColor.RED); return true; }
                String n = String.join(" ", List.of(a).subList(1, a.length));
                if (n.length() > 24) n = n.substring(0, 24);
                store.rename(l, n);
                msg(p, "The gouw is now called " + n + ".", NamedTextColor.GREEN);
            }
            case "info" -> {
                if (here == null) { msg(p, "You are standing on open land.", NamedTextColor.GRAY); return true; }
                Land l = here;
                Land.Rank myRank = l.isMayor(p.getUniqueId()) ? null : l.rank(p.getUniqueId());
                msg(p, l.name + " - gouw of " + nameOf(l.owner) + " in " + l.world, NamedTextColor.GOLD);
                msg(p, " " + l.chunks.size() + "/" + store.limitOf(l) + " chunks (" + (store.limitOf(l) - l.purchased) + " rank+bonus + " + l.purchased + " bought), "
                        + l.ranks.size() + " residents, bank " + money(l.bank) + ".", NamedTextColor.GRAY);
                msg(p, " You are " + (l.isMayor(p.getUniqueId()) ? "the mayor" : myRank == Land.Rank.ASSISTANT ? "an officer" : myRank == Land.Rank.RESIDENT ? "a resident" : "a stranger") + ".", NamedTextColor.GRAY);
            }
            case "list" -> {
                Land l = store.homeOf(p.getUniqueId());
                if (l == null) msg(p, "No gouw yet - stand in a chunk and /gouw claim.", NamedTextColor.GRAY);
                else msg(p, (l.isMayor(p.getUniqueId()) ? "Your gouw " : "Your town ") + l.name + ": " + l.chunks.size() + " chunk(s) in " + l.world + ".", NamedTextColor.GOLD);
                int total = 0;
                for (Land o : store.all()) total += o.chunks.size();
                msg(p, "Server-wide: " + store.all().size() + " gouwen, " + total + " chunks claimed.", NamedTextColor.DARK_GRAY);
            }
            case "near" -> {
                int r = 16;
                if (a.length > 1) try { r = Math.max(1, Math.min(64, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                List<Land> ls = store.nearby(w.getName(), cx, cz, r);
                if (ls.isEmpty()) msg(p, "No gouwen within " + r + " chunks.", NamedTextColor.GRAY);
                else for (Land l : ls) {
                    int dist = Integer.MAX_VALUE;
                    for (long k : l.chunks) dist = Math.min(dist, Math.max(Math.abs(Land.kx(k) - cx), Math.abs(Land.kz(k) - cz)));
                    msg(p, " " + l.name + " - " + l.chunks.size() + " chunks, " + dist + " chunk(s) away" + (l.owner.equals(p.getUniqueId()) ? " (mayor)" : l.ranks.containsKey(p.getUniqueId()) ? " (you are a member)" : ""), NamedTextColor.GOLD);
                }
            }
            case "border" -> {
                if (here == null || !here.isMember(p.getUniqueId())) { msg(p, "Stand in your own (or a trusted) gouw to see its border.", NamedTextColor.GRAY); return true; }
                for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> Border.draw(here), i * 12L);
                msg(p, "Drawing the border of " + here.name + "...", NamedTextColor.AQUA);
            }
            case "leave" -> {
                if (here == null || here.owner.equals(p.getUniqueId())) { msg(p, "Nothing to leave here.", NamedTextColor.GRAY); return true; }
                String err = store.kick(here, p.getUniqueId());
                msg(p, err == null ? "You left the gouw of " + here.name + "." : "You were not a member there.", err == null ? NamedTextColor.GOLD : NamedTextColor.GRAY);
            }
            case "menu" -> plugin.menu().open(p);
            default -> help(s, p.hasPermission("gouw.admin"));
        }
        return true;
    }

    private boolean admin(CommandSender s, String label, String[] a) {
        if (!s.hasPermission("gouw.admin")) { msg(s, "No permission.", NamedTextColor.RED); return true; }
        String what = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (what) {
            case "delete" -> {
                if (a.length < 3) { msg(s, "/gouw admin delete <player or gouw name>", NamedTextColor.RED); return true; }
                Land l = null;
                String n = a[2];
                for (Land o : store.all()) if (o.name.equalsIgnoreCase(n) || nameOf(o.owner).equalsIgnoreCase(n)) { l = o; break; }
                if (l == null) { msg(s, "No gouw found for '" + n + "'.", NamedTextColor.GRAY); return true; }
                int n2 = l.chunks.size();
                store.unclaimAll(l);
                msg(s, "Deleted " + l.name + " (" + n2 + " chunks released).", NamedTextColor.GREEN);
            }
            case "import" -> {
                boolean force = a.length > 2 && a[2].equalsIgnoreCase("force");
                if (!force && store.totalChunks() > 0) { msg(s, "claims.json already has chunks - add 'force' to import on top anyway.", NamedTextColor.RED); return true; }
                int n = LandsImporter.run(plugin, store);
                msg(s, n > 0 ? "Imported " + n + " gouwen from Lands." : "Nothing imported (see the console for the reason).", n > 0 ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "reload" -> {
                plugin.reloadConfig();
                store.save();
                msg(s, "Config reloaded, claims saved.", NamedTextColor.GREEN);
            }
            case "bypass" -> {
                if (!(s instanceof Player p)) { msg(s, "Players only.", NamedTextColor.RED); return true; }
                if (!p.hasPermission("gouw.bypass")) { msg(s, "You need gouw.bypass for that.", NamedTextColor.RED); return true; }
                boolean on = !plugin.isBypassing(p.getUniqueId());
                if (a.length > 2) on = a[2].equalsIgnoreCase("on");
                plugin.setBypassing(p.getUniqueId(), on);
                msg(s, "Bypass " + (on ? "ON - build anywhere." : "off."), on ? NamedTextColor.GOLD : NamedTextColor.GREEN);
            }
            case "addchunks", "buychunks" -> {
                boolean buy = what.equals("buychunks");
                if (a.length < 4 || (buy && a.length < 5)) { msg(s, buy ? "/gouw admin buychunks <player> <chunks> <cost>" : "/gouw admin addchunks <player> <chunks>", NamedTextColor.RED); return true; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { msg(s, a[2] + " is not online.", NamedTextColor.RED); return true; }
                int n, cost = 0;
                try { n = Integer.parseInt(a[3]); if (buy) cost = Integer.parseInt(a[4]); } catch (NumberFormatException ex) { msg(s, "Numbers please.", NamedTextColor.RED); return true; }
                int cap = plugin.getConfig().getInt("bonus-max", 100);
                int have = plugin.bonusOf(t.getUniqueId());
                if (n <= 0) { msg(s, "Chunks must be above 0.", NamedTextColor.RED); return true; }
                if (buy && have + n > cap) {
                    msg(t, "You can't buy more claim chunks: the limit is " + cap + " extra (you have " + have + ").", NamedTextColor.RED);
                    msg(s, "Refused: bonus cap " + cap + ".", NamedTextColor.RED);
                    return true;
                }
                if (buy && cost > 0 && !EconomyRef.withdraw(t, cost)) {
                    msg(t, "You need " + cost + " to buy " + n + " more claim chunks.", NamedTextColor.RED);
                    msg(s, "Refused: not enough money.", NamedTextColor.RED);
                    return true;
                }
                plugin.setBonus(t.getUniqueId(), have + n);
                msg(t, "+" + n + " personal bonus chunks! You now have " + (have + n) + " extra.", NamedTextColor.GOLD);
                msg(s, "Gave " + t.getName() + " +" + n + " bonus chunks (personal bonus " + (have + n) + ").", NamedTextColor.GREEN);
            }
            default -> msg(s, "/gouw admin <delete <name>|import [force]|reload|bypass [on|off]|addchunks <p> <n>|buychunks <p> <n> <cost>>", NamedTextColor.RED);
        }
        return true;
    }

    private void help(CommandSender s, boolean staff) {
        msg(s, "Gouw - chunk land claims & towns", NamedTextColor.GOLD);
        for (String[] h : new String[][]{
                {"menu", "open the gouw menu (default)"},
                {"claim", "claim the chunk you stand in (mayor/officer)"},
                {"autoclaim [r]", "claim a square of chunks around you (r in chunks, default 2)"},
                {"buy [n]", "buy n more chunks for the gouw - paid from the town bank"},
                {"unclaim | unclaim all", "release a chunk or your whole gouw"},
                {"invite <p> / kick <p>", "add or remove residents (was: trust/untrust)"},
                {"promote <p> / demote <p>", "make a resident officer (mayor only)"},
                {"bank / deposit <a> / withdraw <a>", "the town's money"},
                {"members / info / list / near [r]", "who and what is around you"},
                {"menu rights", "per-resident rights (Build/Containers/Interact/Mobs/PvP/Claim) via the Residents page"},
                {"rename <name>", "name your gouw (mayor)"},
                {"border", "see the gouw's outline"},
                {"leave", "step out of a gouw you are a member of"},
        }) msg(s, " /gouw " + h[0] + " - " + h[1], NamedTextColor.GRAY);
        if (staff) msg(s, " staff: /gouw admin delete|import|reload|bypass", NamedTextColor.DARK_GRAY);
    }

    /** Online player first, then the profile cache (must have joined at least once). */
    static UUID uuidOf(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return on.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off != null && (off.isOnline() || off.hasPlayedBefore())) return off.getUniqueId();
        return null;
    }

    private String nameOf(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "unknown" : n;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String v : new String[]{"menu", "help", "claim", "autoclaim", "buy", "unclaim", "invite", "kick", "promote", "demote", "bank", "deposit", "withdraw", "members", "rename", "info", "list", "near", "border", "leave", "admin"})
                if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && List.of("invite", "kick", "promote", "demote").contains(a[0].toLowerCase(Locale.ROOT))) {
            Land l = s instanceof Player pl ? store.homeOf(pl.getUniqueId()) : null;
            if (l != null) for (UUID u : l.ranks.keySet()) {
                String n = Bukkit.getOfflinePlayer(u).getName();
                if (n != null && n.toLowerCase(Locale.ROOT).startsWith(last)) out.add(n);
            }
            else for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("unclaim")) {
            if ("all".startsWith(last)) out.add("all");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            for (String v : new String[]{"delete", "import", "reload", "bypass", "addchunks", "buychunks"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("import")) {
            if ("force".startsWith(last)) out.add("force");
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("delete")) {
            for (Land l : store.all()) if (l.name.toLowerCase(Locale.ROOT).startsWith(last)) out.add(l.name);
        }
        return out;
    }
}
