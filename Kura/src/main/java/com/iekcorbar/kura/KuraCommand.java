package com.iekcorbar.kura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /kura help | give | giveall | list | reload */
final class KuraCommand implements CommandExecutor, TabCompleter {
    private final KuraPlugin plugin;

    KuraCommand(KuraPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!s.hasPermission("kura.admin")) return deny(s);
                if (args.length < 2) {
                    s.sendMessage(Component.text("/kura give <item> [player] [amount]", NamedTextColor.GRAY));
                    return true;
                }
                KItem t = KItem.byId(args[1]);
                if (t == null) {
                    s.sendMessage(Component.text("Unknown item. /kura list", NamedTextColor.RED));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : s instanceof Player p ? p : null;
                if (target == null) {
                    s.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                int n = 1;
                if (args.length > 3) {
                    try {
                        n = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                        // keep 1
                    }
                }
                Menus.give(target, plugin.items().create(t, n));
                s.sendMessage(Component.text("Gave " + n + "x " + t.displayName + " to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "giveall" -> {
                if (!s.hasPermission("kura.admin")) return deny(s);
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : s instanceof Player p ? p : null;
                if (target == null) {
                    s.sendMessage(Component.text("/kura giveall [player]", NamedTextColor.GRAY));
                    return true;
                }
                for (KItem t : KItem.values()) Menus.give(target, plugin.items().create(t, 1));
                s.sendMessage(Component.text("Gave one of every Kura item (" + KItem.values().length + ") to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "list" -> {
                s.sendMessage(Component.text("Kura items:", NamedTextColor.GOLD));
                for (KItem t : KItem.values()) s.sendMessage(Component.text(" " + t.id() + " - " + t.displayName, NamedTextColor.GRAY));
                if (s.hasPermission("kura.admin")) {
                    s.sendMessage(Component.text(plugin.parts().count() + " parts loaded, " + plugin.networks().cachedNetworks() + " networks cached.", NamedTextColor.DARK_GRAY));
                }
            }
            case "reload" -> {
                if (!s.hasPermission("kura.admin")) return deny(s);
                plugin.reload();
                s.sendMessage(Component.text("Kura reloaded.", NamedTextColor.GREEN));
            }
            default -> {
                s.sendMessage(Component.text("Kura - storage network", NamedTextColor.GOLD));
                s.sendMessage(Component.text("Put chests next to an Inventory Connector, then place a Storage Terminal against it.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Cables + Cable Connectors reach chests further away.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Guide: " + plugin.docsUrl(), NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(plugin.docsUrl())));
            }
        }
        return true;
    }

    private static boolean deny(CommandSender s) {
        s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String o : new String[]{"help", "list", "give", "giveall", "reload"}) if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (KItem t : KItem.values()) if (t.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(t.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giveall")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
