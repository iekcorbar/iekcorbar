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
        Land l = store.menuFor(p);
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
                    plugin.adv().grant(p, "first_claim");
                    if (l.chunks.size() >= 8) plugin.adv().grant(p, "full_town");
                    if (l.chunks.size() >= store.limitOf(l)) plugin.adv().grant(p, "maxed");
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
                Land l = store.menuFor(p);
                if (l == null || !l.mayManage(p.getUniqueId())) { if (l != null) msg(p, "Only the mayor and officer invite members.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " invite <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known to this server.", NamedTextColor.RED); return true; }
                String err = store.invite(l, who);
                if (err != null) msg(p, "Cannot invite " + a[1] + ": " + err + ".", NamedTextColor.RED);
                else {
                    msg(p, "Invite sent to " + a[1] + " - they join with /gouw accept.", NamedTextColor.GREEN);
                    notifyInvite(l, who);
                    plugin.adv().grant(p, "trusted");
                }
            }
            case "accept" -> {
                List<Land> joined = store.accept(p.getUniqueId());
                if (joined.isEmpty()) { msg(p, "You have no open invites (check /gouw info where you stand).", NamedTextColor.GRAY); return true; }
                for (Land l : joined) {
                    msg(p, "You are now a resident of " + l.name + " - " + nameOf(l.owner) + " runs that town.", NamedTextColor.GREEN);
                    Player mayor = Bukkit.getPlayer(l.owner);
                    if (mayor != null) msg(mayor, p.getName() + " accepted your invite and joined " + l.name + ".", NamedTextColor.GOLD);
                }
                msg(p, "Type /gouw info inside the town to see what you may do there.", NamedTextColor.GRAY);
            }
            case "decline" -> {
                int n = store.decline(p.getUniqueId());
                msg(p, n == 0 ? "You have no open invites." : "Declined " + n + " invite(s).", n == 0 ? NamedTextColor.GRAY : NamedTextColor.GOLD);
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
                else { msg(p, a[1] + " is " + (s0.equals("promote") ? "now officer of " : "back to resident in ") + l.name + ".", NamedTextColor.GREEN); if (s0.equals("promote")) plugin.adv().grant(p, "officer"); }
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
                if (err == null) plugin.adv().grant(p, "bank");
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
                else { msg(p, "Bought " + n + " chunk(s) for " + money((double) plugin.getConfig().getInt("price-per-chunk", 250) * n) + " - new limit: " + store.limitOf(l) + ".", NamedTextColor.GREEN); plugin.adv().grant(p, "bought"); }
            }
            case "rename" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Only the mayor renames a gouw (claim one first).", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " rename <name>", NamedTextColor.RED); return true; }
                String n = String.join(" ", List.of(a).subList(1, a.length));
                if (n.length() > 24) n = n.substring(0, 24);
                store.rename(l, n);
                msg(p, "The gouw is now called " + n + ".", NamedTextColor.GREEN);
                plugin.adv().grant(p, "renamed");
            }
            case "info" -> {
                if (here == null) { msg(p, "You are standing on open land.", NamedTextColor.GRAY); return true; }
                Land l = here;
                Land.Rank myRank = l.isMayor(p.getUniqueId()) ? null : l.rank(p.getUniqueId());
                msg(p, l.name + " - gouw of " + nameOf(l.owner) + " in " + l.world, NamedTextColor.GOLD);
                msg(p, " " + l.chunks.size() + "/" + store.limitOf(l) + " chunks (" + (store.limitOf(l) - l.purchased) + " rank+bonus + " + l.purchased + " bought), "
                        + l.ranks.size() + " residents, bank " + money(l.bank) + ".", NamedTextColor.GRAY);
                boolean invitedHere = l.invites.contains(p.getUniqueId());
                msg(p, " You are " + (l.isMayor(p.getUniqueId()) ? "the mayor" : myRank == Land.Rank.ASSISTANT ? "an officer" : myRank == Land.Rank.RESIDENT ? "a resident"
                        : l.banned.contains(p.getUniqueId()) ? "banned here" : invitedHere ? "invited here - accept with /gouw accept" : "a stranger") + ".", NamedTextColor.GRAY);
                if (myRank == Land.Rank.RESIDENT || invitedHere) {
                    StringBuilder r = new StringBuilder();
                    for (Land.Flag f : Land.Flag.values())
                        r.append(f.name().toLowerCase(Locale.ROOT)).append(l.may(p.getUniqueId(), f) ? " yes  " : " NO   ");
                    msg(p, " Your rights HERE: " + r.toString().trim() + " - other towns you belong to can differ; stand there and run /gouw info again.", NamedTextColor.YELLOW);
                }
                if (l.isMayor(p.getUniqueId()) && !l.invites.isEmpty())
                    msg(p, " " + l.invites.size() + " invite(s) still waiting for an accept.", NamedTextColor.GRAY);
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
            case "ban" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { l = needTown(p); if (l == null) return true; }
                if (!l.mayManage(p.getUniqueId())) { msg(p, "Only the mayor and officer ban members.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " ban <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (who.equals(l.owner)) { msg(p, "You cannot ban the mayor.", NamedTextColor.RED); return true; }
                if (store.ban(l, who)) { msg(p, a[1] + " is now banned from " + l.name + ".", NamedTextColor.GOLD); plugin.adv().grant(p, "banned"); }
                else msg(p, a[1] + " was already banned.", NamedTextColor.GRAY);
            }
            case "unban" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { l = needTown(p); if (l == null) return true; }
                if (!l.mayManage(p.getUniqueId())) { msg(p, "Only the mayor and officer unban.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " unban <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (store.unban(l, who)) msg(p, a[1] + " is no longer banned.", NamedTextColor.GREEN);
                else msg(p, a[1] + " was not banned.", NamedTextColor.GRAY);
            }
            case "welcome" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Only the mayor sets a welcome message.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " welcome <text> (or 'clear')", NamedTextColor.RED); return true; }
                if (a[1].equalsIgnoreCase("clear")) { store.setWelcome(l, ""); msg(p, "Welcome message cleared.", NamedTextColor.GREEN); }
                else { String t = String.join(" ", List.of(a).subList(1, a.length)); store.setWelcome(l, t); msg(p, "Welcome message set.", NamedTextColor.GREEN); }
            }
            case "sethome" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null || !l.has(p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4) || !l.world.equals(p.getWorld().getName())) {
                    msg(p, "Stand inside your own gouw to set a home.", NamedTextColor.RED); return true;
                }
                store.setHome(l, p.getLocation());
                msg(p, "Home set inside your gouw.", NamedTextColor.GREEN);
                plugin.adv().grant(p, "home");
            }
            case "home" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null || !l.hasHome) { msg(p, "You have no gouw home - use /" + label + " sethome.", NamedTextColor.GRAY); return true; }
                World w2 = Bukkit.getWorld(l.homeWorld);
                if (w2 == null) { msg(p, "Your gouw's world is not loaded.", NamedTextColor.RED); return true; }
                p.teleport(new Location(w2, l.homeX + 0.5, l.homeY, l.homeZ + 0.5));
                msg(p, "Teleported to your gouw home.", NamedTextColor.GREEN);
            }
            case "chat", "c" -> {
                Land l = needTown(p);
                if (l == null) return true;
                if (a.length < 2) { msg(p, "/" + label + " chat <message>", NamedTextColor.RED); return true; }
                String m = String.join(" ", List.of(a).subList(1, a.length));
                Component comp = Component.text("[" + l.name + "] " + p.getName() + ": " + m, NamedTextColor.AQUA);
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (l.isMember(on.getUniqueId()) || on.hasPermission("gouw.admin")) on.sendMessage(comp);
                }
            }
            case "transfer" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Only the mayor transfers a gouw.", NamedTextColor.GRAY); return true; }
                if (!l.isMayor(p.getUniqueId())) { msg(p, "Only the mayor transfers a gouw.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " transfer <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (who.equals(p.getUniqueId())) { msg(p, "That is already yours.", NamedTextColor.GRAY); return true; }
                if (store.landOf(who) != null) { msg(p, a[1] + " already owns a gouw.", NamedTextColor.RED); return true; }
                String err = store.transfer(l, who);
                if (err != null) msg(p, err, NamedTextColor.RED);
                else { msg(p, "Gouw transferred to " + a[1] + ".", NamedTextColor.GREEN); plugin.adv().grant(p, "transferred"); Player onl = Bukkit.getPlayer(who); if (onl != null) msg(onl, "You now own the gouw '" + l.name + "'.", NamedTextColor.GOLD); }
            }
            case "sell" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Only the mayor sells a gouw.", NamedTextColor.GRAY); return true; }
                if (!l.isMayor(p.getUniqueId())) { msg(p, "Only the mayor sells a gouw.", NamedTextColor.RED); return true; }
                if (a.length < 3) { msg(p, "/" + label + " sell <buyer> <price>", NamedTextColor.RED); return true; }
                UUID buyer = uuidOf(a[1]);
                if (buyer == null) { msg(p, "Buyer '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (buyer.equals(p.getUniqueId())) { msg(p, "You cannot sell to yourself.", NamedTextColor.RED); return true; }
                int price;
                try { price = Integer.parseInt(a[2]); } catch (NumberFormatException ex) { msg(p, "Price must be a number.", NamedTextColor.RED); return true; }
                if (price < 0) { msg(p, "Price must be >= 0.", NamedTextColor.RED); return true; }
                Player buyerP = Bukkit.getPlayer(buyer);
                if (buyerP == null) { msg(p, "Buyer must be online to complete the sale.", NamedTextColor.RED); return true; }
                if (store.landOf(buyer) != null) { msg(p, buyerP.getName() + " already owns a gouw.", NamedTextColor.RED); return true; }
                try {
                    Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
                    var rsp = Bukkit.getServicesManager().getRegistration(eco);
                    if (rsp == null) { msg(p, "No economy plugin available.", NamedTextColor.RED); return true; }
                    Object e = rsp.getProvider();
                    if (!(boolean) eco.getMethod("has", org.bukkit.OfflinePlayer.class, double.class).invoke(e, buyerP, (double) price)) {
                        msg(p, buyerP.getName() + " cannot afford " + price + ".", NamedTextColor.RED); return true;
                    }
                    Object r = eco.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, buyerP, (double) price);
                    if (!(boolean) r.getClass().getMethod("transactionSuccess").invoke(r)) { msg(p, "Payment failed.", NamedTextColor.RED); return true; }
                    if (price > 0) eco.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, p, (double) price);
                } catch (ReflectiveOperationException ex) { msg(p, "Economy error: " + ex.getMessage(), NamedTextColor.RED); return true; }
                String err2 = store.transfer(l, buyer);
                if (err2 != null) { msg(p, err2, NamedTextColor.RED); return true; }
                msg(p, "Gouw sold to " + buyerP.getName() + " for " + price + ".", NamedTextColor.GREEN);
                plugin.adv().grant(p, "sold");
                msg(buyerP, "You bought the gouw '" + l.name + "' for " + price + ".", NamedTextColor.GOLD);
            }
            case "rent" -> {
                Land l = needTown(p);
                if (l == null) return true;
                if (!l.mayManage(p.getUniqueId())) { msg(p, "Only the mayor and officer rent out a gouw.", NamedTextColor.RED); return true; }
                if (a.length < 4) { msg(p, "/" + label + " rent <player> <days> <price>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                int days, price;
                try { days = Integer.parseInt(a[2]); price = Integer.parseInt(a[3]); } catch (NumberFormatException ex) { msg(p, "Numbers please.", NamedTextColor.RED); return true; }
                if (days <= 0 || price < 0) { msg(p, "Days must be > 0, price >= 0.", NamedTextColor.RED); return true; }
                Player renter = Bukkit.getPlayer(who);
                if (price > 0 && renter != null) {
                    try {
                        Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
                        var rsp = Bukkit.getServicesManager().getRegistration(eco);
                        if (rsp != null) {
                            Object e = rsp.getProvider();
                            if (!(boolean) eco.getMethod("has", org.bukkit.OfflinePlayer.class, double.class).invoke(e, renter, (double) price)) {
                                msg(p, renter.getName() + " cannot afford " + price + ".", NamedTextColor.RED); return true;
                            }
                            Object r = eco.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, renter, (double) price);
                            if (!(boolean) r.getClass().getMethod("transactionSuccess").invoke(r)) { msg(p, "Payment failed.", NamedTextColor.RED); return true; }
                            if (price > 0) eco.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, p, (double) price);
                        }
                    } catch (ReflectiveOperationException ex) { msg(p, "Economy error: " + ex.getMessage(), NamedTextColor.RED); return true; }
                }
                store.rent(l, who, days);
                msg(p, a[1] + " may now use " + l.name + " for " + days + " day(s).", NamedTextColor.GREEN);
                plugin.adv().grant(p, "rented");
                if (renter != null) msg(renter, "You rented access to " + l.name + " for " + days + " day(s).", NamedTextColor.GOLD);
            }
            case "top" -> {
                msg(p, "Top gouwen by chunks claimed:", NamedTextColor.GOLD);
                plugin.adv().grant(p, "top");
                int i = 1;
                for (Land o : store.top()) {
                    if (i > 10) break;
                    msg(p, " " + i + ". " + o.name + " - " + o.chunks.size() + " chunks (" + nameOf(o.owner) + ")", NamedTextColor.GRAY);
                    i++;
                }
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
                {"invite <p> / kick <p>", "send an invite (they accept with /gouw accept) or remove a resident"},
                {"accept / decline", "take or drop the open invites for you"},
                {"promote <p> / demote <p>", "make a resident officer (mayor only)"},
                {"bank / deposit <a> / withdraw <a>", "the town's money"},
                {"members / info / list / near [r]", "who and what is around you"},
                {"menu rights", "per-resident rights (Build/Containers/Interact/Mobs/PvP/Claim) via the Residents page"},
                {"rename <name>", "name your gouw (mayor)"},
                {"border", "see the gouw's outline"},
                {"leave", "step out of a gouw you are a member of"},
                {"ban <p> / unban <p>", "ban or unban a player (mayor/officer)"},
                {"welcome <text>", "set a welcome message (mayor; or 'clear')"},
                {"sethome / home", "set & teleport to your gouw home (mayor)"},
                {"chat <msg> (or c)", "talk to your gouw members"},
                {"transfer <p>", "give your gouw to someone (mayor)"},
                {"sell <buyer> <price>", "sell your gouw for money (mayor)"},
                {"rent <p> <days> <price>", "rent access to your gouw"},
                {"top", "gouwen with the most chunks"},
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

    /**
     * 0.5.0: tell the invited player they may join - clickable, so nobody has to remember the
     * command. Offline targets get the same message on their next join (see GouwMenu.onJoin).
     */
    static void notifyInvite(Land l, UUID who) {
        Player on = Bukkit.getPlayer(who);
        if (on != null) sendInviteNotice(on, l);
    }

    static void sendInviteNotice(Player target, Land l) {
        target.sendMessage(Component.text(nameStatic(l.owner) + " invites you to live in the town ", NamedTextColor.GOLD)
                .append(Component.text(l.name, NamedTextColor.YELLOW))
                .append(Component.text(" as resident.  ", NamedTextColor.GOLD))
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/gouw accept")))
                .append(Component.text("  or  ", NamedTextColor.GRAY))
                .append(Component.text("[Decline]", NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/gouw decline"))));
        target.playSound(target.getLocation(), "minecraft:block.note_block.pling", 1f, 1.4f);
    }

    private static String nameStatic(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "someone" : n;
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
            for (String v : new String[]{"menu", "help", "claim", "autoclaim", "buy", "unclaim", "invite", "trust", "accept", "decline", "kick", "promote", "demote", "bank", "deposit", "withdraw", "members", "rename", "info", "list", "near", "border", "leave", "ban", "unban", "welcome", "sethome", "home", "chat", "c", "transfer", "sell", "rent", "top", "admin"})
                if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && List.of("invite", "trust", "kick", "promote", "demote").contains(a[0].toLowerCase(Locale.ROOT))) {
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
