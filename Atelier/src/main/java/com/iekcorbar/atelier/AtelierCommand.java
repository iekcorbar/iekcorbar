package com.iekcorbar.atelier;

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

/** /atelier list [family] | give <player> <piece> [amount] | giveall [family] */
final class AtelierCommand implements TabExecutor {
    private final AtelierPlugin plugin;

    AtelierCommand(AtelierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            if (a.length > 1) {
                List<String> ids = new ArrayList<>();
                for (DecorDef d : plugin.atelier.decor.defs.values()) if (d.id.endsWith(a[1].toLowerCase(Locale.ROOT))) ids.add(d.id);
                s.sendMessage(Component.text(ids.isEmpty() ? "Unknown family." : String.join(", ", ids), NamedTextColor.GOLD));
            } else {
                List<String> f = new ArrayList<>();
                for (String[] x : plugin.atelier.families) f.add(x[0] + " (" + x[1] + ")");
                s.sendMessage(Component.text("Families: " + String.join(", ", f), NamedTextColor.GOLD));
            }
            return true;
        }
        if (sub.equals("give")) {
            if (!s.hasPermission("atelier.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (a.length < 3) {
                s.sendMessage(Component.text("/" + label + " give <player> <piece> [amount]", NamedTextColor.RED));
                return true;
            }
            Player t = plugin.getServer().getPlayerExact(a[1]);
            DecorDef def = plugin.atelier.decor.defs.get(a[2].toLowerCase(Locale.ROOT));
            if (t == null || def == null) {
                s.sendMessage(Component.text(t == null ? "Player not online." : "Unknown piece. /" + label + " list", NamedTextColor.RED));
                return true;
            }
            int n = 1;
            try {
                if (a.length > 3) n = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException e) {
                s.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return true;
            }
            for (ItemStack left : t.getInventory().addItem(plugin.atelier.decor.item(def, n)).values())
                t.getWorld().dropItemNaturally(t.getLocation(), left);
            s.sendMessage(Component.text("Gave " + def.name + " to " + t.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("giveall")) {
            if (!s.hasPermission("atelier.admin") || !(s instanceof Player p)) {
                s.sendMessage(Component.text("Players with atelier.admin only.", NamedTextColor.RED));
                return true;
            }
            String fam = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
            int c = 0;
            for (DecorDef d : plugin.atelier.decor.defs.values()) {
                if (!fam.isEmpty() && !d.id.endsWith(fam)) continue;
                for (ItemStack left : p.getInventory().addItem(plugin.atelier.decor.item(d, 1)).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                c++;
            }
            s.sendMessage(Component.text("Gave " + c + " pieces.", NamedTextColor.GREEN));
            return true;
        }
        s.sendMessage(Component.text("/" + label + " list [family] | give <player> <piece> [amount] | giveall [family]", NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("list", "give", "giveall"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) plugin.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 2) for (String[] f : plugin.atelier.families) out.add(f[0]);
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) out.addAll(plugin.atelier.decor.defs.keySet());
        String pre = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(pre));
        return out;
    }
}
