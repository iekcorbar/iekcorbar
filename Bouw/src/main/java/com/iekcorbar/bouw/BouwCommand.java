package com.iekcorbar.bouw;

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

/** /bouw list [family] | give <player> <piece> [amount] | giveall [family] */
final class BouwCommand implements TabExecutor {
    private final BouwPlugin plugin;

    BouwCommand(BouwPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.isEmpty() || sub.equals("menu")) {
            if (!(s instanceof Player p)) return true;
            plugin.menu.open(p, a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "");
            return true;
        }
        if (sub.equals("list")) {
            if (a.length > 1) {
                List<String> ids = new ArrayList<>();
                for (DecorDef d : plugin.bouw.decor.defs.values()) if (d.id.endsWith(a[1].toLowerCase(Locale.ROOT))) ids.add(d.id);
                s.sendMessage(Component.text(ids.isEmpty() ? "Unknown family." : String.join(", ", ids), NamedTextColor.GOLD));
            } else {
                List<String> f = new ArrayList<>();
                for (String[] x : plugin.bouw.families) f.add(x[0] + " (" + x[1] + ")");
                s.sendMessage(Component.text("Families: " + String.join(", ", f), NamedTextColor.GOLD));
            }
            return true;
        }
        if (sub.equals("give")) {
            if (!s.hasPermission("bouw.admin")) {
                s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (a.length < 3) {
                s.sendMessage(Component.text("/" + label + " give <player> <piece> [amount]", NamedTextColor.RED));
                return true;
            }
            Player t = plugin.getServer().getPlayerExact(a[1]);
            DecorDef def = plugin.bouw.decor.defs.get(a[2].toLowerCase(Locale.ROOT));
            ItemStack piece = def == null ? plugin.shapes.itemById(a[2].toLowerCase(Locale.ROOT), 1) : null;
            if (t != null && piece != null) {
                int cnt = 1;
                try {
                    if (a.length > 3) cnt = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
                } catch (NumberFormatException ex) {
                    cnt = 1;
                }
                piece.setAmount(cnt);
                for (ItemStack left : t.getInventory().addItem(piece).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
                s.sendMessage(Component.text("Gave " + piece.getItemMeta().getDisplayName() + " to " + t.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
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
            for (ItemStack left : t.getInventory().addItem(plugin.bouw.decor.item(def, n)).values())
                t.getWorld().dropItemNaturally(t.getLocation(), left);
            s.sendMessage(Component.text("Gave " + def.name + " to " + t.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("reload")) {
            if (!s.hasPermission("bouw.admin")) return true;
            plugin.reloadConfig();
            plugin.shapes.reloadConfig();
            s.sendMessage(Component.text("Bouw config reloaded.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("giveall")) {
            if (!s.hasPermission("bouw.admin") || !(s instanceof Player p)) {
                s.sendMessage(Component.text("Players with bouw.admin only.", NamedTextColor.RED));
                return true;
            }
            String fam = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
            int c = 0;
            for (DecorDef d : plugin.bouw.decor.defs.values()) {
                if (!fam.isEmpty() && !d.id.endsWith(fam)) continue;
                for (ItemStack left : p.getInventory().addItem(plugin.bouw.decor.item(d, 1)).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                c++;
            }
            if (fam.isEmpty()) for (ItemStack it : plugin.shapes.allItems()) {
                for (ItemStack left : p.getInventory().addItem(it).values())
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
        if (a.length == 1) out.addAll(List.of("menu", "list", "give", "giveall", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) plugin.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 2) for (String[] f : plugin.bouw.families) out.add(f[0]);
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) { out.addAll(plugin.bouw.decor.defs.keySet()); for (ItemStack it : plugin.shapes.allItems()) out.add(it.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "piece"), org.bukkit.persistence.PersistentDataType.STRING).replace(":", "_")); }
        String pre = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(pre));
        return out;
    }
}
