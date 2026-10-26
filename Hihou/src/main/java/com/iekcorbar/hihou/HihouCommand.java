package com.iekcorbar.hihou;

import com.iekcorbar.hihou.bw.BwGameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ih give|edit|remove|map <...> (staff) plus /ih key <type> [player] [amount] and
 * /ih event open|close. "key" also works from the console on purpose: the site's vote-reward
 * command (vote_reward_config) can then be "ih key %player% vote", giving one key per vote with
 * zero extra code in SiteSync or this plugin.
 */
final class HihouCommand implements TabExecutor {
    private final HihouPlugin plugin;

    HihouCommand(HihouPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!admin(s)) return true;
                if (a.length < 2 || !Keys.validType(a[1])) {
                    msg(s, "/ih give <" + String.join("|", Chests.TYPES) + "> [player] [amount]", NamedTextColor.RED);
                    return true;
                }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (t == null) {
                    msg(s, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                int amount = a.length > 3 ? parseInt(a[3], 1) : 1;
                var d = plugin.chests().def(Keys.norm(a[1]));
                t.getInventory().addItem(plugin.chests().decor.item(d, amount));
                msg(s, "Gave " + amount + "x " + d.name + " to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "key" -> {
                if (a.length < 2 || !Keys.validType(a[1])) {
                    msg(s, "/ih key <" + String.join("|", Keys.TYPES) + "> [player] [amount]", NamedTextColor.RED);
                    return true;
                }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (t == null) {
                    msg(s, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                if (!(s instanceof Player) && !s.hasPermission("hihou.admin") && !Bukkit.getConsoleSender().equals(s)) {
                    msg(s, "Only the console or staff may mint keys.", NamedTextColor.RED);
                    return true;
                }
                int amount = a.length > 3 ? parseInt(a[3], 1) : 1;
                t.getInventory().addItem(plugin.keys().item(Keys.norm(a[1]), amount));
                msg(s, "Gave " + amount + "x " + Keys.cap(a[1]) + " Key to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "edit", "remove", "map" -> {
                if (!admin(s) || !(s instanceof Player p)) return true;
                Block hitB = p.getTargetBlock(null, 6);
                Decor.Prop prop = hitB == null ? null : plugin.chests().decor.at(hitB);
                if (prop == null && hitB != null) prop = plugin.chests().decor.at(hitB.getRelative(org.bukkit.block.BlockFace.UP));
                if (prop == null) {
                    msg(s, "Look at a Hihou chest.", NamedTextColor.RED);
                    return true;
                }
                switch (sub) {
                    case "edit" -> plugin.chests().openEdit(p, prop);
                    case "remove" -> {
                        plugin.chests().store.clear(ChestStore.id(prop.center()));
                        plugin.chests().decor.breakProp(prop, false); // no drop: the item is re-givable via /ih give
                        plugin.chests().store.flush();
                        msg(s, "Chest removed (loot deleted with it).", NamedTextColor.GREEN);
                    }
                    default -> {
                        p.getInventory().addItem(plugin.chests().map(prop));
                        msg(s, "Treasure map for that chest created.", NamedTextColor.GREEN);
                    }
                }
            }
            case "event" -> {
                if (!admin(s)) return true;
                boolean open = a.length > 1 && a[1].equalsIgnoreCase("open");
                plugin.chests().eventOpen(open);
                Bukkit.broadcast(Component.text("秘宝 Event chests are now " + (open ? "OPEN - go and vote, hunt and win!" : "closed. Thanks for playing!"),
                        open ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            }
            case "game" -> game(s, a);
            case "bw" -> bw(s, a);
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reloadConfig();
                msg(s, "Config reloaded (break-hits applies to newly placed props on restart).", NamedTextColor.GREEN);
            }
            default -> {
                msg(s, "秘宝 Hihou - chests, keys, lucky draws and treasure maps.", NamedTextColor.GOLD);
                msg(s, "/ih give <type> [player] [amount] - place a chest item", NamedTextColor.GRAY);
                msg(s, "/ih key <type> [player] [amount] - mint keys (console: vote rewards hook)", NamedTextColor.GRAY);
                msg(s, "/ih edit | remove | map - look at a chest", NamedTextColor.GRAY);
                msg(s, "/ih event open|close - the event-chest window", NamedTextColor.GRAY);
            }
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("hihou.admin")) return true;
        msg(s, "You don't have permission for that.", NamedTextColor.RED);
        return false;
    }

    private static void msg(CommandSender s, String t, NamedTextColor c) {
        s.sendMessage(Component.text(t, c));
    }

    // ------------------------------------------------------------ /ih game ...

    private void game(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "/ih game create|set|start|stop|win|delete|list <name> ...", NamedTextColor.RED);
            return;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        var games = plugin.games();
        switch (sub) {
            case "list" -> {
                if (games.names().isEmpty()) msg(s, "No games yet - /ih game create <name> race|last|manual", NamedTextColor.GRAY);
                for (String g : games.names()) msg(s, games.info(g), NamedTextColor.GRAY);
            }
            case "create" -> {
                if (!admin(s)) return;
                if (a.length < 4) { msg(s, "/ih game create <name> race|last|manual", NamedTextColor.RED); return; }
                String err = games.create(a[2].toLowerCase(Locale.ROOT), a[3].toLowerCase(Locale.ROOT));
                msg(s, err.isEmpty() ? "Created. Prize: /ih game set " + a[2] + " prize key victory" : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "set" -> {
                if (!admin(s) || !(s instanceof Player p)) return;
                if (a.length < 4) { msg(s, "/ih game set <name> finish|center|size <r>|prize <none|key [type] [amt]|chest>", NamedTextColor.RED); return; }
                String game = a[2].toLowerCase(Locale.ROOT), what = a[3].toLowerCase(Locale.ROOT);
                String err = switch (what) {
                    case "finish" -> games.setFinish(p, game);
                    case "center" -> games.setCenter(p, game);
                    case "size" -> a.length > 4 ? games.setSize(game, parseInt(a[4], 0)) : "Give a radius.";
                    case "prize" -> games.setPrize(game, a.length > 4 ? a[4].toLowerCase(Locale.ROOT) : "none",
                            a.length > 5 ? a[5].toLowerCase(Locale.ROOT) : null, a.length > 6 ? parseInt(a[6], 1) : 1);
                    default -> "Unknown setter.";
                };
                msg(s, err.isEmpty() ? "Saved." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "start" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih game start <name> [seconds]", NamedTextColor.RED); return; }
                String err = games.start(a[2].toLowerCase(Locale.ROOT), a.length > 3 ? parseInt(a[3], 0) : 0);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "stop" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih game stop <name>", NamedTextColor.RED); return; }
                String err = games.stop(a[2].toLowerCase(Locale.ROOT));
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "win" -> {
                if (!admin(s)) return;
                if (a.length < 4) { msg(s, "/ih game win <name> <player>", NamedTextColor.RED); return; }
                Player t = Bukkit.getPlayerExact(a[3]);
                if (t == null) { msg(s, "Player offline.", NamedTextColor.RED); return; }
                String err = games.win(a[2].toLowerCase(Locale.ROOT), t);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "delete" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih game delete <name>", NamedTextColor.RED); return; }
                String err = games.delete(a[2].toLowerCase(Locale.ROOT));
                msg(s, err.isEmpty() ? "Deleted." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            default -> msg(s, "/ih game create|set|start|stop|win|delete|list", NamedTextColor.RED);
        }
    }

    // ------------------------------------------------------------ /ih bw ... (etappe 0.3.0: arenas, teams, join)

    private void bw(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "/ih bw list|info|create|delete|set|team|gen|join|leave|rejoin|start|stop <...>", NamedTextColor.RED);
            return;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        var mgr = plugin.bw();
        switch (sub) {
            case "list" -> {
                if (mgr.names().isEmpty()) msg(s, "No arenas yet - /ih bw create <name>", NamedTextColor.GRAY);
                for (String name : mgr.names()) msg(s, mgr.info(name), NamedTextColor.GRAY);
            }
            case "info" -> {
                if (a.length < 3) { msg(s, "/ih bw info <arena>", NamedTextColor.RED); return; }
                msg(s, mgr.info(a[2].toLowerCase(Locale.ROOT)), NamedTextColor.GRAY);
            }
            case "create" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih bw create <arena>", NamedTextColor.RED); return; }
                String err = mgr.create(a[2]);
                msg(s, err.isEmpty() ? "Arena created. Stand at the lobby spot: /ih bw set " + a[2].toLowerCase(Locale.ROOT)
                        + " spawn, then /ih bw team " + a[2].toLowerCase(Locale.ROOT) + " add <team> <color>." : err,
                        err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "delete" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih bw delete <arena>", NamedTextColor.RED); return; }
                String err = mgr.delete(a[2]);
                msg(s, err.isEmpty() ? "Deleted." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "set" -> {
                if (!admin(s) || !(s instanceof Player p)) return;
                if (a.length < 4) { msg(s, "/ih bw set <arena> spawn|spec|center|size <r>|min <n>|maxteam <n>|beds <sec>|end <sec>", NamedTextColor.RED); return; }
                String arena = a[2].toLowerCase(Locale.ROOT), what = a[3].toLowerCase(Locale.ROOT);
                String err = switch (what) {
                    case "spawn" -> mgr.setSpawn(p, arena);
                    case "spec" -> mgr.setSpec(p, arena);
                    case "center" -> mgr.setCenter(p, arena);
                    case "size" -> a.length > 4 ? mgr.setSize(arena, parseInt(a[4], 0)) : "Give a radius.";
                    case "min" -> a.length > 4 ? mgr.setMin(arena, parseInt(a[4], 0)) : "Give a number.";
                    case "maxteam" -> a.length > 4 ? mgr.setMaxTeam(arena, parseInt(a[4], 0)) : "Give a number.";
                    case "beds" -> a.length > 4 ? mgr.setBedsAt(arena, parseInt(a[4], 0)) : "Give a second.";
                    case "end" -> a.length > 4 ? mgr.setEndAt(arena, parseInt(a[4], 0)) : "Give a second.";
                    default -> "Unknown setter.";
                };
                msg(s, err.isEmpty() ? "Saved." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "team" -> {
                if (!admin(s) || !(s instanceof Player p)) return;
                if (a.length < 4) { msg(s, "/ih bw team <arena> add <name> <color>|del <name>|spawn <name>|bed <name>", NamedTextColor.RED); return; }
                String arena = a[2].toLowerCase(Locale.ROOT), op = a[3].toLowerCase(Locale.ROOT);
                String err = switch (op) {
                    case "add" -> a.length > 5 ? mgr.teamAdd(arena, a[4], a[5]) : "Give a team name and a color.";
                    case "del" -> a.length > 4 ? mgr.teamDel(arena, a[4]) : "Give a team name.";
                    case "spawn" -> a.length > 4 ? mgr.teamSetSpawn(p, arena, a[4]) : "Give a team name (stand where the team spawns).";
                    case "bed" -> a.length > 4 ? mgr.teamSetBed(p, arena, a[4]) : "Give a team name (look at the bed).";
                    default -> "Unknown team op.";
                };
                msg(s, err.isEmpty() ? "Saved." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "gen" -> {
                if (!admin(s) || !(s instanceof Player p)) return;
                if (a.length < 5) { msg(s, "/ih bw gen <arena> add|del iron|gold <team>|diamond|emerald", NamedTextColor.RED); return; }
                String arena = a[2].toLowerCase(Locale.ROOT), op = a[3].toLowerCase(Locale.ROOT);
                boolean teamType = a[4].equalsIgnoreCase("iron") || a[4].equalsIgnoreCase("gold");
                String err = switch (op) {
                    case "add" -> a.length > 5 || !teamType
                            ? mgr.genAdd(p, arena, a[4], teamType ? a[5] : null) : "Which team owns this iron/gold pad?";
                    case "del" -> a.length > 5 || !teamType
                            ? mgr.genDel(p, arena, a[4], teamType ? a[5] : null) : "Which team's iron/gold pad?";
                    default -> "Unknown gen op.";
                };
                msg(s, err.isEmpty() ? "Saved (live at next start)." : err, err.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            case "join" -> {
                if (!(s instanceof Player p)) { msg(s, "Players only.", NamedTextColor.RED); return; }
                if (a.length < 3) { msg(s, "/ih bw join <arena> [team]", NamedTextColor.RED); return; }
                String err = mgr.join(p, a[2], a.length > 3 ? a[3] : null);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "leave" -> {
                if (!(s instanceof Player p)) { msg(s, "Players only.", NamedTextColor.RED); return; }
                String err = mgr.leave(p);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "rejoin" -> {
                if (!(s instanceof Player p)) { msg(s, "Players only.", NamedTextColor.RED); return; }
                String err = mgr.rejoin(p);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "start" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih bw start <arena> [seconds]", NamedTextColor.RED); return; }
                String err = mgr.start(a[2], a.length > 3 ? parseInt(a[3], 0) : 0);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            case "stop" -> {
                if (!admin(s)) return;
                if (a.length < 3) { msg(s, "/ih bw stop <arena>", NamedTextColor.RED); return; }
                String err = mgr.stop(a[2]);
                if (!err.isEmpty()) msg(s, err, NamedTextColor.RED);
            }
            default -> msg(s, "/ih bw list|info|create|delete|set|team|gen|join|leave|rejoin|start|stop", NamedTextColor.RED);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("give", "key", "edit", "remove", "map", "event", "game", "bw", "reload", "help"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("game")) out.addAll(List.of("create", "set", "start", "stop", "win", "delete", "list"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("bw")) out.addAll(List.of("list", "info", "create", "delete", "set", "team", "gen", "join", "leave", "rejoin", "start", "stop"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("bw")
                && List.of("info", "delete", "set", "team", "gen", "join", "start", "stop").contains(a[1].toLowerCase(Locale.ROOT)))
            out.addAll(plugin.bw().names());
        else if (a.length == 4 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("set")) out.addAll(List.of("spawn", "spec", "center", "size", "min", "maxteam", "beds", "end"));
        else if (a.length == 4 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("team")) out.addAll(List.of("add", "del", "spawn", "bed"));
        else if (a.length == 4 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("gen")) out.addAll(List.of("add", "del"));
        else if (a.length == 5 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("gen")) out.addAll(List.of(BwGameManager.GEN_TYPES));
        else if (a.length == 6 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("gen")
                && (a[4].equalsIgnoreCase("iron") || a[4].equalsIgnoreCase("gold"))) out.addAll(plugin.bw().teamNames(a[2]));
        else if (a.length == 4 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("join")) out.addAll(plugin.bw().teamNames(a[2]));
        else if (a.length == 5 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("team"))
            out.addAll(plugin.bw().teamNames(a[2]));
        else if (a.length == 6 && a[0].equalsIgnoreCase("bw") && a[1].equalsIgnoreCase("team") && a[3].equalsIgnoreCase("add"))
            out.addAll(List.of(BwGameManager.TEAM_COLORS));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("key"))) out.addAll(List.of(Keys.TYPES));
        else if (a.length == 2 && a[0].equalsIgnoreCase("event")) out.addAll(List.of("open", "close"));
        else if (a.length == 4 && a[1].equalsIgnoreCase("create")) out.addAll(List.of(Games.GAME_TYPES));
        else if (a.length == 4 && a[1].equalsIgnoreCase("set")) out.addAll(List.of("finish", "center", "size", "prize"));
        else if (a.length == 5 && a[1].equalsIgnoreCase("set") && a[3].equalsIgnoreCase("prize")) out.addAll(List.of("none", "key", "chest"));
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(prefix));
        return out;
    }
}
