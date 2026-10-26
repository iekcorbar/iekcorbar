package com.iekcorbar.power;

import com.iekcorbar.steamworks.api.CustomPart;
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

/** /power list | give <player> <machine> [amount] | reload */
final class PowerCommand implements TabExecutor {
    private final PowerPlugin plugin;

    PowerCommand(PowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<String> ids = new ArrayList<>();
            for (CustomPart p : plugin.parts().parts()) ids.add(p.id() + " (" + p.displayName() + ")");
            s.sendMessage(Component.text("Power machines: " + String.join(", ", ids), NamedTextColor.GOLD));
            return true;
        }
        if (sub.equals("reload")) {
            if (!s.hasPermission("power.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            plugin.press.loadRecipes();
            s.sendMessage(Component.text("Config reloaded - " + plugin.press.recipes().size() + " press recipes.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("give")) {
            if (!s.hasPermission("power.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (a.length < 3) {
                s.sendMessage(Component.text("/" + label + " give <player> <machine> [amount]", NamedTextColor.RED));
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(a[1]);
            if (target == null) {
                s.sendMessage(Component.text("Player not online.", NamedTextColor.RED));
                return true;
            }
            CustomPart part = plugin.parts().byId(a[2].toLowerCase(Locale.ROOT));
            if (part == null) {
                s.sendMessage(Component.text("Unknown machine. /" + label + " list", NamedTextColor.RED));
                return true;
            }
            int amount = 1;
            if (a.length > 3) try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException e) {
                s.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return true;
            }
            ItemStack item = plugin.parts().item(part, amount);
            for (ItemStack left : target.getInventory().addItem(item).values())
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            s.sendMessage(Component.text("Gave " + part.displayName() + " to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        s.sendMessage(Component.text("/" + label + " list | give <player> <machine> [amount] | reload", NamedTextColor.GOLD));
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
            for (CustomPart c : plugin.parts().parts()) if (c.id().startsWith(w)) out.add(c.id());
        }
        return out;
    }
}
