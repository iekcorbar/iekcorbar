package com.iekcorbar.afkcinema;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /cine automatisch | film [speler] | stop  — handmatige bediening + testknoppen. */
final class CineCommand implements CommandExecutor {

    private final AfkCinemaPlugin plugin;

    CineCommand(AfkCinemaPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "automatisch" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                CineSession s = plugin.manager().sessie(p);
                s.cinemaToegelaten = !s.cinemaToegelaten;
                p.sendMessage("§ecinema-automaat: " + (s.cinemaToegelaten ? "AAN" : "UIT"));
            }
            case "film" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                Player doel = p;
                if (args.length > 1) {
                    Player ander = org.bukkit.Bukkit.getPlayerExact(args[1]);
                    if (ander == null) { sender.sendMessage("speler niet online: " + args[1]); return true; }
                    doel = ander;
                }
                CineSession s = plugin.manager().sessie(doel);
                if (s.filmLoopt) { sender.sendMessage("die draait al"); return true; }
                s.markeerActief();
                s.afk = true;
                plugin.manager().forceFilm(doel);
                sender.sendMessage("bioscoop draait rond " + doel.getName());
            }
            case "stop" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("alleen in game"); return true; }
                CineSession s = plugin.manager().sessie(p);
                if (s.filmLoopt) { plugin.manager().stopViaCommand(s); sender.sendMessage("gestopt"); }
                else sender.sendMessage("niets gestopt");
            }
            default -> sender.sendMessage("gebruik: /cine automatisch | film [speler] | stop");
        }
        return true;
    }
}
