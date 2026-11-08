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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /gouw - claim and manage your gouw. Aliases /g and /claim (players knew /claim from the old plugin).
 * The help line changes with your permission; everything writes to ClaimStore (which saves claims.json).
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
                else msg(p, "Chunk " + cx + "," + cz + " claimed - your gouw now has " + store.landOf(p.getUniqueId()).chunks.size() + " chunks.", NamedTextColor.GREEN);
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
                else msg(p, "Claimed " + got + " chunk(s) around you (" + (store.landOf(p.getUniqueId()) == null ? 0 : store.landOf(p.getUniqueId()).chunks.size()) + " total).", NamedTextColor.GREEN);
            }
            case "unclaim" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("all")) {
                    Land l = store.landOf(p.getUniqueId());
                    if (l == null) msg(p, "You have no gouw.", NamedTextColor.GRAY);
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
            case "trust", "untrust" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw to share.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " " + s0 + " <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known to this server.", NamedTextColor.RED); return true; }
                if (s0.equals("trust")) {
                    if (store.trust(l, who)) msg(p, a[1] + " may now build in your gouw.", NamedTextColor.GREEN);
                    else msg(p, "Already trusted (that is you).", NamedTextColor.GRAY);
                } else {
                    if (store.untrust(l, who)) msg(p, a[1] + " is no longer trusted.", NamedTextColor.GOLD);
                    else msg(p, a[1] + " was not trusted.", NamedTextColor.GRAY);
                }
            }
            case "members" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null || l.trusted.isEmpty()) { msg(p, "You trust nobody yet - /gouw trust <player>", NamedTextColor.GRAY); return true; }
                msg(p, "Trusted in " + l.name + ":", NamedTextColor.GOLD);
                for (UUID u : l.trusted) msg(p, " - " + nameOf(u), NamedTextColor.GRAY);
                if (!l.banned.isEmpty()) {
                    msg(p, "Banned:", NamedTextColor.RED);
                    for (UUID u : l.banned) msg(p, " - " + nameOf(u), NamedTextColor.GRAY);
                }
            }
            case "rename" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Claim a chunk first.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " rename <name>", NamedTextColor.RED); return true; }
                String n = String.join(" ", List.of(a).subList(1, a.length));
                if (n.length() > 24) n = n.substring(0, 24);
                store.rename(l, n);
                msg(p, "Your gouw is now called " + n + ".", NamedTextColor.GREEN);
            }
            case "info" -> {
                if (here == null) { msg(p, "You are standing on open land.", NamedTextColor.GRAY); return true; }
                Land l = here;
                msg(p, l.name + " - gouw of " + nameOf(l.owner) + " in " + l.world, NamedTextColor.GOLD);
                msg(p, " " + l.chunks.size() + " chunk(s), " + l.trusted.size() + " trusted - you are " + (l.owner.equals(p.getUniqueId()) ? "the owner" : l.trusted.contains(p.getUniqueId()) ? "a trusted member" : "a stranger") + ".", NamedTextColor.GRAY);
                if (l.hasHome) msg(p, " Home set at " + l.homeX + ", " + l.homeY + ", " + l.homeZ + ".", NamedTextColor.DARK_GRAY);
                if (l.owner.equals(p.getUniqueId())) {
                    int max = p.hasPermission("gouw.admin") ? Integer.MAX_VALUE : plugin.getMaxChunks(p);
                    if (max != Integer.MAX_VALUE) msg(p, " Space left: " + (max - l.chunks.size()) + " chunk(s) (limit " + max + ").", NamedTextColor.DARK_GRAY);
                }
            }
            case "list" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) msg(p, "No gouw yet - stand in a chunk and /gouw claim.", NamedTextColor.GRAY);
                else msg(p, "Your gouw " + l.name + ": " + l.chunks.size() + " chunk(s) in " + l.world + ".", NamedTextColor.GOLD);
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
                    msg(p, " " + l.name + " - " + l.chunks.size() + " chunks, " + dist + " chunk(s) away" + (l.owner.equals(p.getUniqueId()) ? " (yours)" : l.trusted.contains(p.getUniqueId()) ? " (you are trusted)" : ""), NamedTextColor.GOLD);
                }
            }
            case "border" -> {
                if (here == null || !here.isMember(p.getUniqueId())) { msg(p, "Stand in your own (or a trusted) gouw to see its border.", NamedTextColor.GRAY); return true; }
                for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> Border.draw(here), i * 12L);
                msg(p, "Drawing the border of " + here.name + "...", NamedTextColor.AQUA);
            }
            case "leave" -> {
                if (here == null || here.owner.equals(p.getUniqueId())) { msg(p, "Nothing to leave here.", NamedTextColor.GRAY); return true; }
                if (store.untrust(here, p.getUniqueId())) msg(p, "You left the gouw of " + here.name + ".", NamedTextColor.GOLD);
                else msg(p, "You were not a member there.", NamedTextColor.GRAY);
            }
            case "ban" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " ban <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (who.equals(p.getUniqueId())) { msg(p, "You cannot ban yourself.", NamedTextColor.RED); return true; }
                if (store.ban(l, who)) msg(p, a[1] + " is now banned from your gouw.", NamedTextColor.GOLD);
                else msg(p, a[1] + " was already banned.", NamedTextColor.GRAY);
            }
            case "unban" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " unban <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (store.unban(l, who)) msg(p, a[1] + " is no longer banned.", NamedTextColor.GREEN);
                else msg(p, a[1] + " was not banned.", NamedTextColor.GRAY);
            }
            case "welcome" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "Claim a chunk first.", NamedTextColor.GRAY); return true; }
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
                Land l = store.landOf(p.getUniqueId());
                if (l == null) l = store.land(p.getWorld(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
                if (l == null) { msg(p, "You are not in a gouw.", NamedTextColor.GRAY); return true; }
                if (!l.isMember(p.getUniqueId()) && !p.hasPermission("gouw.admin")) { msg(p, "You are not a member of this gouw.", NamedTextColor.RED); return true; }
                if (a.length < 2) { msg(p, "/" + label + " chat <message>", NamedTextColor.RED); return true; }
                String m = String.join(" ", List.of(a).subList(1, a.length));
                Component comp = Component.text("[" + l.name + "] " + p.getName() + ": " + m, NamedTextColor.AQUA);
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (l.isMember(on.getUniqueId()) || on.getUniqueId().equals(l.owner) || on.hasPermission("gouw.admin")) on.sendMessage(comp);
                }
            }
            case "transfer" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw to transfer.", NamedTextColor.GRAY); return true; }
                if (a.length < 2) { msg(p, "/" + label + " transfer <player>", NamedTextColor.RED); return true; }
                UUID who = uuidOf(a[1]);
                if (who == null) { msg(p, "Player '" + a[1] + "' is not known.", NamedTextColor.RED); return true; }
                if (who.equals(p.getUniqueId())) { msg(p, "That is already yours.", NamedTextColor.GRAY); return true; }
                if (store.landOf(who) != null) { msg(p, a[1] + " already owns a gouw.", NamedTextColor.RED); return true; }
                String err = store.transfer(l, who, a[1] + "'s gouw");
                if (err != null) msg(p, err, NamedTextColor.RED);
                else { msg(p, "Gouw transferred to " + a[1] + ".", NamedTextColor.GREEN); Player onl = Bukkit.getPlayer(who); if (onl != null) msg(onl, "You now own the gouw '" + l.name + "'.", NamedTextColor.GOLD); }
            }
            case "sell" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw to sell.", NamedTextColor.GRAY); return true; }
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
                String err2 = store.transfer(l, buyer, buyerP.getName() + "'s gouw");
                if (err2 != null) { msg(p, err2, NamedTextColor.RED); return true; }
                msg(p, "Gouw sold to " + buyerP.getName() + " for " + price + ".", NamedTextColor.GREEN);
                msg(buyerP, "You bought the gouw '" + l.name + "' for " + price + ".", NamedTextColor.GOLD);
            }
            case "rent" -> {
                Land l = store.landOf(p.getUniqueId());
                if (l == null) { msg(p, "You have no gouw to rent out.", NamedTextColor.GRAY); return true; }
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
                msg(p, a[1] + " may now use your gouw for " + days + " day(s).", NamedTextColor.GREEN);
                if (renter != null) msg(renter, "You rented access to " + l.name + " for " + days + " day(s).", NamedTextColor.GOLD);
            }
            case "top" -> {
                msg(p, "Top gouwen by chunks claimed:", NamedTextColor.GOLD);
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
                if (buy && cost > 0 && !withdraw(t, cost)) {
                    msg(t, "You need " + cost + " to buy " + n + " more claim chunks.", NamedTextColor.RED);
                    msg(s, "Refused: not enough money.", NamedTextColor.RED);
                    return true;
                }
                plugin.setBonus(t.getUniqueId(), have + n);
                msg(t, "+" + n + " claim chunks! You now have " + (have + n) + " extra (" + plugin.getMaxChunks(t) + " in total).", NamedTextColor.GOLD);
                msg(s, "Gave " + t.getName() + " +" + n + " chunks (bonus " + (have + n) + ").", NamedTextColor.GREEN);
            }
            default -> msg(s, "/gouw admin <delete <name>|import [force]|reload|bypass [on|off]|addchunks <player> <n>|buychunks <player> <n> <cost>>", NamedTextColor.RED);
        }
        return true;
    }

    /** Takes money through Vault (reflection, no compile dependency). False if no economy or too poor. */
    private boolean withdraw(Player p, double amount) {
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return false;
            Object e = rsp.getProvider();
            if (!(boolean) eco.getMethod("has", org.bukkit.OfflinePlayer.class, double.class).invoke(e, p, amount)) return false;
            Object r = eco.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, p, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private void help(CommandSender s, boolean staff) {
        msg(s, "Gouw - chunk land claims", NamedTextColor.GOLD);
        for (String[] h : new String[][]{
                {"menu", "open the gouw menu (default)"},
                {"claim", "claim the chunk you stand in"},
                {"autoclaim [r]", "claim a square of chunks around you (r in chunks, default 2)"},
                {"unclaim | unclaim all", "release a chunk or your whole gouw"},
                {"trust <player> / untrust <player>", "share your gouw with friends"},
                {"members", "who you trust (and banned)"},
                {"rename <name>", "name your gouw"},
                {"info / list / near [r]", "what is around you"},
                {"border", "see your gouw's outline"},
                {"leave", "step out of a gouw you were trusted in"},
                {"ban <player> / unban <player>", "ban or unban a player"},
                {"welcome <text>", "set a welcome message (or 'clear')"},
                {"sethome / home", "set & teleport to your gouw home"},
                {"chat <msg> (or c)", "talk to your gouw members"},
                {"transfer <player>", "give your gouw to someone"},
                {"sell <buyer> <price>", "sell your gouw for money"},
                {"rent <player> <days> <price>", "rent access to your gouw"},
                {"top", "gouwen with the most chunks"},
        }) msg(s, " /gouw " + h[0] + " - " + h[1], NamedTextColor.GRAY);
        if (staff) msg(s, " staff: /gouw admin delete|import|reload|bypass|addchunks|buychunks", NamedTextColor.DARK_GRAY);
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
            for (String v : new String[]{"menu", "help", "claim", "autoclaim", "unclaim", "trust", "untrust", "members", "rename", "info", "list", "near", "border", "leave", "ban", "unban", "welcome", "sethome", "home", "chat", "c", "transfer", "sell", "rent", "top", "admin"})
                if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("trust") || a[0].equalsIgnoreCase("untrust") || a[0].equalsIgnoreCase("ban") || a[0].equalsIgnoreCase("unban") || a[0].equalsIgnoreCase("transfer") || a[0].equalsIgnoreCase("sell") || a[0].equalsIgnoreCase("rent"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
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