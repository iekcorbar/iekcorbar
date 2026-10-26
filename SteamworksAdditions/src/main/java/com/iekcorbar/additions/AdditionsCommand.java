package com.iekcorbar.additions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /additions list | give <player> <prop> [amount] */
final class AdditionsCommand implements TabExecutor {
    private final AdditionsPlugin plugin;

    AdditionsCommand(AdditionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<String> ids = new ArrayList<>();
            for (DecorDef d : plugin.additions.decor.defs.values()) ids.add(d.id);
            s.sendMessage(Component.text("Additions props: " + String.join(", ", ids), NamedTextColor.GOLD));
            return true;
        }
        if (sub.equals("give")) {
            if (!s.hasPermission("additions.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (a.length < 3) {
                s.sendMessage(Component.text("/" + label + " give <player> <prop> [amount]", NamedTextColor.RED));
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(a[1]);
            if (target == null) {
                s.sendMessage(Component.text("Player not online.", NamedTextColor.RED));
                return true;
            }
            DecorDef def = plugin.additions.decor.defs.get(a[2].toLowerCase(Locale.ROOT));
            if (def == null) {
                s.sendMessage(Component.text("Unknown prop. /" + label + " list", NamedTextColor.RED));
                return true;
            }
            int amount = 1;
            if (a.length > 3) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
                } catch (NumberFormatException e) {
                    s.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                    return true;
                }
            }
            ItemStack item = plugin.additions.decor.item(def, amount);
            for (ItemStack left : target.getInventory().addItem(item).values())
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            s.sendMessage(Component.text("Gave " + def.name + " to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("reload")) {
            if (!s.hasPermission("additions.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            s.sendMessage(Component.text("Config reloaded - bell " + (plugin.bellEnabled() ? "on" : "off")
                    + ", night rest " + (plugin.nightRest() ? "on" : "off")
                    + ", time " + (plugin.realClock() ? "real (" + plugin.zone() + ")" : "in-game") + ".", NamedTextColor.GREEN));
            return true;
        }
        s.sendMessage(Component.text("/" + label + " list | give <player> <prop> [amount] | reload", NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String w = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String o : List.of("list", "give", "reload")) if (o.startsWith(w)) out.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : plugin.getServer().getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(w)) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (DecorDef d : plugin.additions.decor.defs.values()) if (d.id.startsWith(w)) out.add(d.id);
        }
        return out;
    }
}
