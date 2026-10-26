package com.iekcorbar.zukan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /zukan [words] | hand | uses | givebook [player] [amount] | reload. */
final class ZukanCommand implements CommandExecutor, TabCompleter {
    private final ZukanPlugin plugin;

    ZukanCommand(ZukanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("zukan.admin")) {
                sender.sendMessage(Component.text("You are not allowed to do that.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(Component.text("Zukan reloaded: " + plugin.catalog().entries.size() + " items, "
                    + plugin.catalog().recipeCount + " recipes.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("givebook")) {
            if (!sender.hasPermission("zukan.admin")) {
                sender.sendMessage(Component.text("You are not allowed to do that.", NamedTextColor.RED));
                return true;
            }
            org.bukkit.entity.Player target = args.length > 1 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : sender instanceof Player self ? self : null;
            if (target == null) {
                sender.sendMessage(Component.text("/" + label + " givebook [player] [amount]", NamedTextColor.RED));
                return true;
            }
            int amount = 1;
            if (args.length > 2) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                    return true;
                }
            }
            ItemStack book = plugin.book();
            book.setAmount(amount);
            for (ItemStack left : target.getInventory().addItem(book).values()) target.getWorld().dropItemNaturally(target.getLocation(), left);
            sender.sendMessage(Component.text("Gave " + amount + " Zukan book(s) to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only (use /zukan reload from the console).");
            return true;
        }
        if (!p.hasPermission("zukan.use")) {
            p.sendMessage(Component.text("You are not allowed to do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("hand") || args[0].equalsIgnoreCase("uses"))) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.isEmpty() || held.getType() == Material.AIR) {
                p.sendMessage(Component.text("Hold an item first.", NamedTextColor.RED));
                return true;
            }
            if (!plugin.ready()) {
                plugin.open(p, "");
                return true;
            }
            plugin.gui().openRecipes(p, held, args[0].equalsIgnoreCase("uses"));
            return true;
        }
        plugin.open(p, String.join(" ", args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("hand", "uses", "givebook", "reload")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("givebook")) {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
