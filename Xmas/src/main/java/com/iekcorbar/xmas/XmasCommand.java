package com.iekcorbar.xmas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /xmas list                     - every building block with its id
 * /xmas give <id|all> [aantal]   - hand out a block (staff: xmas.admin)
 * /xmas reload                   - re-read config.yml and re-register the recipes (staff)
 */
final class XmasCommand implements CommandExecutor, TabCompleter {

    private final XmasPlugin plugin;

    XmasCommand(XmasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Xmas Town building blocks:", NamedTextColor.GOLD));
            for (DecorDef d : plugin.decor().decor.defs.values())
                sender.sendMessage(Component.text("  " + d.id, NamedTextColor.YELLOW)
                        .append(Component.text(" - " + d.name, NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("Give one with /xmas give <id> [amount].", NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xmas.admin")) {
                sender.sendMessage(Component.text("Staff only.", NamedTextColor.RED));
                return true;
            }
            plugin.reload();
            sender.sendMessage(Component.text("Xmas config reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("santa")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Santa comes to a player, not to the console.",
                        NamedTextColor.RED));
                return true;
            }
            if (!p.hasPermission("xmas.admin")) {
                p.sendMessage(Component.text("Only staff can place Santa.", NamedTextColor.RED));
                return true;
            }
            String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
            switch (sub) {
                case "spawn" -> {
                    plugin.santa().summon(p.getLocation());
                    p.sendMessage(Component.text("Santa stands where you stood. He will wander from there.",
                            NamedTextColor.GREEN));
                }
                case "remove" -> {
                    plugin.santa().dismiss();
                    p.sendMessage(Component.text("Santa is gone until the next /xmas santa spawn.",
                            NamedTextColor.YELLOW));
                }
                case "goto" -> {
                    if (args.length < 3) {
                        p.sendMessage(Component.text("Who? /xmas santa goto <player>", NamedTextColor.RED));
                        return true;
                    }
                    Player t = Bukkit.getPlayerExact(args[2]);
                    if (t == null) {
                        p.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
                        return true;
                    }
                    plugin.santa().summon(t.getLocation().add(2, 0, 2));
                    p.sendMessage(Component.text("Santa arrives next to " + t.getName() + ".",
                            NamedTextColor.GREEN));
                }
                default -> p.sendMessage(Component.text("Santa: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.santa().status(), NamedTextColor.YELLOW)));
            }
            return true;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /xmas list | give <block> [amount] | "
                    + "santa <spawn|remove|goto|status> | reload", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Only players can hold presents.", NamedTextColor.RED));
            return true;
        }
        if (!p.hasPermission("xmas.admin")) {
            p.sendMessage(Component.text("You can buy these at the market: craft them (see /zukan) or ask staff.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("Which block? /xmas list shows the ids.", NamedTextColor.RED));
            return true;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                p.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return true;
            }
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        List<ItemStack> give = new ArrayList<>();
        if (id.equals("all")) {
            for (DecorDef d : plugin.decor().decor.defs.values()) give.add(plugin.decor().decor.item(d, amount));
        } else {
            DecorDef d = plugin.decor().decor.defs.get(id);
            if (d == null) {
                p.sendMessage(Component.text("No Xmas block called " + id + ". /xmas list", NamedTextColor.RED));
                return true;
            }
            give.add(plugin.decor().decor.item(d, amount));
        }
        for (ItemStack left : p.getInventory().addItem(give.toArray(new ItemStack[0])).values())
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.sendMessage(Component.text("Gave " + give.size() + " kind(s) of Xmas block.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("list", "give", "santa", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("santa"))
            return List.of("spawn", "remove", "goto", "status");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            ids.addAll(plugin.decor().decor.defs.keySet());
            return ids;
        }
        return List.of();
    }
}
