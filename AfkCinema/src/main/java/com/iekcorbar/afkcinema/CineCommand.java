package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /cine afk [player] · stop · automatisch · menu · opname <pad> · pad <beheer> · herlaad
 * Alle speler-teksten Engels; commando-namen blijven zoals ingeburgerd.
 */
final class CineCommand implements CommandExecutor, TabCompleter {

    private final AfkCinemaPlugin plugin;

    CineCommand(AfkCinemaPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "afk", "film" -> film(sender, args);
            case "stop" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("in game only"); return true; }
                CineSession s = plugin.manager().sessie(p);
                if (s.actiefFilmpje()) {
                    int punten = s.opneemPad == null ? 0 : s.opneemPad.punten().size();
                    plugin.manager().stopAlles(s);
                    p.sendMessage("§estopped — you're back where you were"
                        + (punten > 0 ? " (" + punten + " points recorded)" : ""));
                } else p.sendMessage("nothing was running");
            }
            case "auto", "automatisch" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("in game only"); return true; }
                CineSession s = plugin.manager().sessie(p);
                s.cineToegelaten = !s.cineToegelaten;
                p.sendMessage("§eafk cinema: " + (s.cineToegelaten ? "ON" : "OFF"));
            }
            case "record", "opname" -> opname(sender, args);
            case "menu" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("in game only"); return true; }
                plugin.menus().toggle(p);
            }
            case "pad" -> pad(sender, args);
            case "reload", "herlaad" -> {
                if (!sender.hasPermission("afkcinema.admin")) { sender.sendMessage("§cno permission"); return true; }
                plugin.reloadAlles();
                sender.sendMessage("§econfig and paths reloaded");
            }
            default -> help(sender);
        }
        return true;
    }

    private void film(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("in game only"); return; }
        Player doel = p;
        if (args.length > 1) {
            if (!p.hasPermission("afkcinema.admin")) {
                p.sendMessage("§cyou need admin rights to film someone else"); return;
            }
            Player ander = Bukkit.getPlayerExact(args[1]);
            if (ander == null) { p.sendMessage("player not online: " + args[1]); return; }
            doel = ander;
        }
        CineSession s = plugin.manager().sessie(p);
        if (s.actiefFilmpje()) { p.sendMessage("§esomething is already rolling — /cine stop first"); return; }
        plugin.manager().startFilm(p, doel);
    }

    private void opname(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("in game only"); return; }
        if (!p.hasPermission("afkcinema.admin")) { p.sendMessage("§cno permission"); return; }
        CineSession s = plugin.manager().sessie(p);
        if (s.modus == CineSession.Modus.OPNAME) {
            int punten = s.opneemPad == null ? 0 : s.opneemPad.punten().size();
            plugin.manager().stopAlles(s);
            p.sendMessage("§erecording off — " + punten + " points saved; fly it with /cine pad play");
            return;
        }
        if (args.length < 2) { p.sendMessage("usage: /cine record <path name>  (created if it doesn't exist yet)"); return; }
        Pad pad = plugin.pads().pad(args[1], true);
        plugin.manager().startOpname(p, pad);
        plugin.pads().opslaan();
    }

    private void pad(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("usage: /cine pad <add|remove|play|info|clear|list>"); return; }
        String actie = args[1].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (actie) {
            case "add" -> {
                if (p == null) { sender.sendMessage("in game only"); return; }
                CineSession s = plugin.manager().sessie(p);
                Pad pad = s.opneemPad;
                if (pad == null && args.length > 2) pad = plugin.pads().pad(args[2], true);
                if (pad == null) { p.sendMessage("§cno path selected - /cine record <name> or /cine pad add <name>"); return; }
                pad.voegToe(p.getLocation());
                plugin.pads().opslaan();
                p.sendMessage("§8[§6Cinema§8] §fpoint " + pad.punten().size() + " added to '" + pad.naam() + "'");
            }
            case "remove", "verwijder" -> {
                if (args.length < 3) { sender.sendMessage("usage: /cine pad remove <number> [path name]"); return; }
                Pad pad = kiesPad(sender, args, 3);
                if (pad == null) return;
                try {
                    if (pad.verwijder(Integer.parseInt(args[2]))) {
                        plugin.pads().opslaan();
                        sender.sendMessage("§epoint " + args[2] + " removed — " + pad.punten().size() + " left");
                    } else sender.sendMessage("§cno point " + args[2] + " (1.." + pad.punten().size() + ")");
                } catch (NumberFormatException e) { sender.sendMessage("§cthat is not a number"); }
            }
            case "play", "speel" -> {
                if (p == null) { sender.sendMessage("in game only"); return; }
                Pad pad = kiesPad(sender, args, 2);
                if (pad == null) return;
                plugin.manager().startPad(p, pad);
            }
            case "info" -> {
                Pad pad = kiesPad(sender, args, 2);
                if (pad == null) return;
                sender.sendMessage("§epath '" + pad.naam() + "' — " + pad.punten().size() + " points"
                    + (pad.snelheid() > 0 ? ", speed " + pad.snelheid() : ""));
                int i = 1;
                for (Pad.Punt pt : pad.punten()) {
                    sender.sendMessage("  §f" + i++ + "§8: " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pt.x(), pt.y(), pt.z())
                        + " §7look " + String.format(Locale.ROOT, "%.0f/%.0f", pt.yaw(), pt.pitch()) + " §8(" + pt.wereld() + ")");
                }
            }
            case "clear", "wis" -> {
                if (!sender.hasPermission("afkcinema.admin")) { sender.sendMessage("§cno permission"); return; }
                if (args.length < 3) { sender.sendMessage("usage: /cine pad clear <path name>"); return; }
                if (plugin.pads().verwijderPad(args[2])) {
                    plugin.pads().opslaan();
                    sender.sendMessage("§epath '" + args[2] + "' deleted");
                } else sender.sendMessage("§cno such path");
            }
            case "list", "lijst" -> {
                List<String> namen = plugin.pads().namen();
                if (namen.isEmpty()) { sender.sendMessage("no paths yet — make one with /cine record <name>"); return; }
                sender.sendMessage("§epaths§8:");
                for (String n : namen) {
                    Pad pd = plugin.pads().pad(n);
                    sender.sendMessage("  §f" + n + " §8(" + (pd == null ? 0 : pd.punten().size()) + " points)");
                }
            }
            default -> sender.sendMessage("usage: /cine pad <add|remove|play|info|clear|list>");
        }
    }

    /** Pad uit args[opname-positie], of het actieve opname-pad als die optie ontbreekt. */
    private Pad kiesPad(CommandSender sender, String[] args, int naamPositie) {
        if (args.length > naamPositie) {
            Pad pad = plugin.pads().pad(args[naamPositie]);
            if (pad == null) sender.sendMessage("§cno such path '" + args[naamPositie] + "' — /cine pad lijst");
            return pad;
        }
        if (sender instanceof Player p) {
            CineSession s = plugin.manager().sessie(p);
            if (s.opneemPad != null) return s.opneemPad;
        }
        sender.sendMessage("§cwhich path? name it: /cine pad " + args[1].toLowerCase(Locale.ROOT) + " <name>");
        return null;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§8[§6Cinema§8] §f/cine afk [player] §7· §f/cine stop §7· §f/cine auto §7· §f/cine menu");
        sender.sendMessage("  §f/cine record <path> §7→ fly freely, §f/cine pad add§7, then §f/cine pad play <path>");
        sender.sendMessage("  §f/cine pad list/info/clear/remove §7· §f/cine reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String la = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : new String[]{"afk", "stop", "auto", "menu", "record", "pad", "reload"})
                if (s.startsWith(la)) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pad")) {
            for (String s : new String[]{"add", "remove", "play", "info", "clear", "list"})
                if (s.startsWith(la)) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("afk") || args[0].equalsIgnoreCase("film"))) {
            for (Player online : Bukkit.getOnlinePlayers())
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(la)) out.add(online.getName());
        } else if (args[0].equalsIgnoreCase("record") && args.length == 2) {
            for (String n : plugin.pads().namen()) if (n.toLowerCase(Locale.ROOT).startsWith(la)) out.add(n);
        } else if (args[0].equalsIgnoreCase("pad") && args.length == 3
                && (args[1].equalsIgnoreCase("play") || args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("clear"))) {
            for (String n : plugin.pads().namen()) if (n.toLowerCase(Locale.ROOT).startsWith(la)) out.add(n);
        }
        return out;
    }
}
