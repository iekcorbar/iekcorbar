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
 * /cine afk [speler] · stop · automatisch · opname <pad> · pad <beheer> · herlaad
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
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                CineSession s = plugin.manager().sessie(p);
                if (s.actiefFilmpje()) {
                    int punten = s.opneemPad == null ? 0 : s.opneemPad.punten().size();
                    plugin.manager().stopAlles(s);
                    p.sendMessage("§e gestopt — je staat weer waar je was"
                        + (punten > 0 ? " (" + punten + " punten opgenomen)" : ""));
                } else p.sendMessage("niets gestopt");
            }
            case "automatisch" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                CineSession s = plugin.manager().sessie(p);
                s.cineToegelaten = !s.cineToegelaten;
                p.sendMessage("§e afk-bioskoop: " + (s.cineToegelaten ? "AAN" : "UIT"));
            }
            case "opname" -> opname(sender, args);
            case "menu" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                plugin.menus().toggle(p);
            }
            case "pad" -> pad(sender, args);
            case "herlaad" -> {
                if (!sender.hasPermission("afkcinema.admin")) { sender.sendMessage("§c geen recht"); return true; }
                plugin.reloadAlles();
                sender.sendMessage("§e config en pads herladen");
            }
            default -> help(sender);
        }
        return true;
    }

    private void film(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return; }
        Player doel = p;
        if (args.length > 1) {
            if (!p.hasPermission("afkcinema.admin")) {
                p.sendMessage("§c alleen met admin-rechten kun je een ander filmen"); return;
            }
            Player ander = Bukkit.getPlayerExact(args[1]);
            if (ander == null) { p.sendMessage("speler niet online: " + args[1]); return; }
            doel = ander;
        }
        CineSession s = plugin.manager().sessie(p);
        if (s.actiefFilmpje()) { p.sendMessage("§e er draait al iets — /cine stop eerst"); return; }
        plugin.manager().startFilm(p, doel);
    }

    private void opname(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return; }
        if (!p.hasPermission("afkcinema.admin")) { p.sendMessage("§c geen recht"); return; }
        CineSession s = plugin.manager().sessie(p);
        if (s.modus == CineSession.Modus.OPNAME) {
            int punten = s.opneemPad == null ? 0 : s.opneemPad.punten().size();
            plugin.manager().stopAlles(s);
            p.sendMessage("§e opname uit — " + punten + " punten; vlieg ze na met /cine pad speel");
            return;
        }
        if (args.length < 2) { p.sendMessage("gebruik: /cine opname <padnaam>  (bestaat het nog niet, dan maak ik 'm)"); return; }
        Pad pad = plugin.pads().pad(args[1], true);
        plugin.manager().startOpname(p, pad);
        plugin.pads().opslaan();
    }

    private void pad(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("gebruik: /cine pad <add|verwijder|speel|info|wis|lijst>"); return; }
        String actie = args[1].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (actie) {
            case "add" -> {
                if (p == null) { sender.sendMessage("alleen in game"); return; }
                CineSession s = plugin.manager().sessie(p);
                Pad pad = s.opneemPad;
                if (pad == null && args.length > 2) pad = plugin.pads().pad(args[2], true);
                if (pad == null) { p.sendMessage("§c geen pad gekozen — /cine opname <naam> of /cine pad add <naam>"); return; }
                pad.voegToe(p.getLocation());
                plugin.pads().opslaan();
                p.sendMessage("§8[§ecinema§8] §fpunt " + pad.punten().size() + " toegevoegd aan '" + pad.naam() + "'");
            }
            case "verwijder" -> {
                if (args.length < 3) { sender.sendMessage("gebruik: /cine pad verwijder <nummer> [padnaam]"); return; }
                Pad pad = kiesPad(sender, args, 3);
                if (pad == null) return;
                try {
                    if (pad.verwijder(Integer.parseInt(args[2]))) {
                        plugin.pads().opslaan();
                        sender.sendMessage("§e punt " + args[2] + " verwijderd — nog " + pad.punten().size() + " over");
                    } else sender.sendMessage("§c nummer " + args[2] + " bestaat niet (1.." + pad.punten().size() + ")");
                } catch (NumberFormatException e) { sender.sendMessage("§c dat is geen nummer"); }
            }
            case "speel" -> {
                if (p == null) { sender.sendMessage("alleen in game"); return; }
                Pad pad = kiesPad(sender, args, 2);
                if (pad == null) return;
                plugin.manager().startPad(p, pad);
            }
            case "info" -> {
                Pad pad = kiesPad(sender, args, 2);
                if (pad == null) return;
                sender.sendMessage("§e pad '" + pad.naam() + "' — " + pad.punten().size() + " punten"
                    + (pad.snelheid() > 0 ? ", snelheid " + pad.snelheid() : ""));
                int i = 1;
                for (Pad.Punt pt : pad.punten()) {
                    sender.sendMessage("  §f" + i++ + "§8: " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pt.x(), pt.y(), pt.z())
                        + " §7kijk " + String.format(Locale.ROOT, "%.0f/%.0f", pt.yaw(), pt.pitch()) + " §8(" + pt.wereld() + ")");
                }
            }
            case "wis" -> {
                if (!sender.hasPermission("afkcinema.admin")) { sender.sendMessage("§c geen recht"); return; }
                if (args.length < 3) { sender.sendMessage("gebruik: /cine pad wis <padnaam>"); return; }
                if (plugin.pads().verwijderPad(args[2])) {
                    plugin.pads().opslaan();
                    sender.sendMessage("§e pad '" + args[2] + "' gewist");
                } else sender.sendMessage("§c dat pad bestaat niet");
            }
            case "lijst" -> {
                List<String> namen = plugin.pads().namen();
                if (namen.isEmpty()) { sender.sendMessage("nog geen pads — maak er een met /cine opname <naam>"); return; }
                sender.sendMessage("§epads§8:");
                for (String n : namen) {
                    Pad pd = plugin.pads().pad(n);
                    sender.sendMessage("  §f" + n + " §8(" + (pd == null ? 0 : pd.punten().size()) + " punten)");
                }
            }
            default -> sender.sendMessage("gebruik: /cine pad <add|verwijder|speel|info|wis|lijst>");
        }
    }

    /** Pad uit args[opname-positie], of het actieve opname-pad als die optie ontbreekt. */
    private Pad kiesPad(CommandSender sender, String[] args, int naamPositie) {
        if (args.length > naamPositie) {
            Pad pad = plugin.pads().pad(args[naamPositie]);
            if (pad == null) sender.sendMessage("§c pad '" + args[naamPositie] + "' bestaat niet — /cine pad lijst");
            return pad;
        }
        if (sender instanceof Player p) {
            CineSession s = plugin.manager().sessie(p);
            if (s.opneemPad != null) return s.opneemPad;
        }
        sender.sendMessage("§c welk pad? noem de naam: /cine pad " + args[1].toLowerCase(Locale.ROOT) + " <naam>");
        return null;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§8[§ecinema§8] §f/cine afk [speler] §7· §f/cine stop §7· §f/cine automatisch §7· §f/cine menu");
        sender.sendMessage("  §f/cine opname <pad> §7→ vlieg vrij, §f/cine pad add§7, dan §f/cine pad speel <pad>");
        sender.sendMessage("  §f/cine pad lijst/info/wis/verwijder §7· §f/cine herlaad");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String la = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : new String[]{"afk", "film", "stop", "automatisch", "opname", "menu", "pad", "herlaad"})
                if (s.startsWith(la)) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pad")) {
            for (String s : new String[]{"add", "verwijder", "speel", "info", "wis", "lijst"})
                if (s.startsWith(la)) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("afk") || args[0].equalsIgnoreCase("film"))) {
            for (Player online : Bukkit.getOnlinePlayers())
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(la)) out.add(online.getName());
        } else if (args[0].equalsIgnoreCase("opname") && args.length == 2) {
            for (String n : plugin.pads().namen()) if (n.toLowerCase(Locale.ROOT).startsWith(la)) out.add(n);
        } else if (args[0].equalsIgnoreCase("pad") && args.length == 3
                && (args[1].equalsIgnoreCase("speel") || args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("wis"))) {
            for (String n : plugin.pads().namen()) if (n.toLowerCase(Locale.ROOT).startsWith(la)) out.add(n);
        }
        return out;
    }
}
