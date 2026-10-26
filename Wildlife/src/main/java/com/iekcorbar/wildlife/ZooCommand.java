package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /zoo set name|price|minutes|open &lt;value&gt; (look at your Ticket Kiosk) | give &lt;item&gt; [player] (admin) | list | info | ticket [player] [min] (staff) | gate open|close (staff) | help */
final class ZooCommand implements TabExecutor {
    private final WildlifePlugin plugin;

    ZooCommand(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length < 3) {
                    msg(s, "/" + label + " set <name|price|minutes|open> <value> - look at your Ticket Kiosk", NamedTextColor.RED);
                    return true;
                }
                String value = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
                String err = plugin.zoo().set(p, a[1].toLowerCase(Locale.ROOT), value);
                msg(s, err.isEmpty() ? "Saved." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "info" -> {
                if (!(s instanceof Player p)) return true;
                plugin.zoo().info(p);
            }
            case "ticket" -> {
                if (!(s instanceof Player p)) return true;
                if (!p.hasPermission("wildlife.zoo.staff")) {
                    msg(s, "You don't have permission for that.", NamedTextColor.RED);
                    return true;
                }
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) {
                    msg(s, "/" + label + " ticket <player> [minutes] - a free ticket from the kiosk you look at", NamedTextColor.RED);
                    return true;
                }
                Integer min = null;
                if (a.length > 2) {
                    try {
                        min = Integer.parseInt(a[2]);
                    } catch (NumberFormatException e) {
                        msg(s, "Minutes must be a number (0 = one entry).", NamedTextColor.RED);
                        return true;
                    }
                }
                String err = plugin.zoo().giveTicket(p, t, min);
                msg(s, err.isEmpty() ? "Gave a ticket to " + t.getName() + "." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "gate" -> {
                if (!(s instanceof Player p)) return true;
                if (!p.hasPermission("wildlife.zoo.staff")) {
                    msg(s, "You don't have permission for that.", NamedTextColor.RED);
                    return true;
                }
                var hit = p.rayTraceBlocks(8);
                Decor.Prop prop = hit == null || hit.getHitBlock() == null ? null : plugin.zoo().decor.at(hit.getHitBlock());
                if (prop == null || !(prop.def.id.endsWith("_gate") || prop.def.id.endsWith("turnstile"))) {
                    msg(s, "Look at a gate or turnstile.", NamedTextColor.RED);
                    return true;
                }
                if (a.length > 1 && a[1].equalsIgnoreCase("close")) {
                    plugin.zoo().decor.close(prop);
                    msg(s, "Closed.", NamedTextColor.GREEN);
                } else {
                    plugin.zoo().decor.open(prop, 400); // ~20 s - long enough to walk a group through
                    msg(s, "Open for a moment.", NamedTextColor.GREEN);
                }
            }
            case "give" -> {
                if (!s.hasPermission("wildlife.admin")) {
                    msg(s, "You don't have permission for that.", NamedTextColor.RED);
                    return true;
                }
                DecorDef d = a.length > 1 ? plugin.zoo().decor.defs.get(a[1].toLowerCase(Locale.ROOT)) : null;
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player self ? self : null;
                if (d == null || t == null) {
                    msg(s, "/" + label + " give <item> [player]  (items: " + String.join(", ", plugin.zoo().decor.defs.keySet()) + ")", NamedTextColor.RED);
                    return true;
                }
                t.getInventory().addItem(plugin.zoo().decor.item(d, 1));
                msg(s, "Gave a " + d.name + " to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "list" -> msg(s, "Zoo items: " + String.join(", ", plugin.zoo().decor.defs.keySet()), NamedTextColor.GRAY);
            default -> {
                msg(s, "Zoo - build your own zoo with Wildlife animals. Craft a Ticket Kiosk, a Zoo Gate and fences (see /zukan zoo).", NamedTextColor.GOLD);
                msg(s, "Kiosk: right-click twice to buy a ticket; the owner sneaks + right-clicks to open the settings menu, or uses /" + label + " set name|price|minutes|open <value>.", NamedTextColor.GRAY);
                msg(s, "Gates and turnstiles open when you stand near them with a valid ticket. /" + label + " info | ticket <player> [min] | gate open|close for zoo staff.", NamedTextColor.GRAY);
                msg(s, "Full guide: " + plugin.getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=wildlife"), NamedTextColor.GRAY);
            }
        }
        return true;
    }

    private static void msg(CommandSender s, String t, NamedTextColor c) {
        s.sendMessage(Component.text(t, c));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("set", "give", "list", "info", "ticket", "gate", "help"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("set")) out.addAll(List.of("name", "price", "minutes", "open"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("gate")) out.addAll(List.of("open", "close"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) out.addAll(plugin.zoo().decor.defs.keySet());
        else if (a.length == 2 && a[0].equalsIgnoreCase("ticket")) {
            for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(prefix));
        return out;
    }
}
