package com.iekcorbar.hihou;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Game Island automation (v0.2). The games themselves stay hand-built vanilla blocks; a game only
 * needs to be told ONE thing about the world: which pressure plate is the finish (race), or where
 * its circle of play is (last-one-standing), or nothing at all (manual award). Stored in games.json.
 *
 * type=race   : while running, the first player to step the marked plate wins and the session ends.
 * type=last   : participants are locked at start (in-circle, alive, survival/adventure); when the
 *               timer expires, everyone of them still in the circle wins (survivor round).
 * type=manual : nobody can win by code; staff ends it with /ih game win <game> <player>.
 *
 * Prize per game (set once): none | key <type> [amount] | chest (a Victory Key + teleport to the
 * nearest placed victory chest - if no chest exists yet staff gets told, the key still lands).
 */
final class Games implements Listener {
    static final String[] GAME_TYPES = {"race", "last", "manual"};

    private final HihouPlugin plugin;
    private final Keys keys;
    private final YamlConfiguration y = new YamlConfiguration();
    private final File file;
    private final Map<String, Session> running = new HashMap<>();

    private static final class Session {
        final String game;
        final Set<UUID> participants = new HashSet<>();
        BukkitTask endTask;
        boolean done;

        Session(String game) {
            this.game = game;
        }
    }

    Games(HihouPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
        file = new File(plugin.getDataFolder(), "games.json");
        if (file.exists()) {
            try {
                y.load(file);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read games.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        try {
            y.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save games.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ data

    Set<String> names() {
        var root = y.getConfigurationSection("games");
        return root == null ? new HashSet<>() : root.getKeys(false);
    }

    private String cfg(String game, String key, String def) {
        return y.getString("games." + game + "." + key, def);
    }

    private int cfgI(String game, String key, int def) {
        return y.getInt("games." + game + "." + key, def);
    }

    boolean exists(String game) {
        return y.get("games." + game) != null;
    }

    String type(String game) {
        return cfg(game, "type", "race");
    }

    /** Summary line for /ih game list. */
    String info(String game) {
        String t = type(game);
        StringBuilder sb = new StringBuilder(game + " (" + t);
        if (t.equals("race") && y.get("games." + game + ".finish") != null) sb.append(", plate set");
        if (t.equals("last") && y.get("games." + game + ".center") != null)
            sb.append(", circle r=").append(cfgI(game, "size", 0));
        String mode = cfg(game, "prize.mode", "none");
        sb.append(", prize ").append(mode.equals("key") ? cfg(game, "prize.key", "victory") + " key" : mode);
        if (running.containsKey(game)) sb.append(" - RUNNING");
        return sb.append(")").toString();
    }

    // ------------------------------------------------------------ editing

    String create(String game, String type) {
        if (!game.matches("[a-zA-Z0-9_]{2,24}")) return "Use 2-24 letters/numbers for the game name.";
        if (exists(game)) return "That game already exists.";
        y.set("games." + game + ".type", type);
        y.set("games." + game + ".prize.mode", "none");
        save();
        return "";
    }

    String delete(String game) {
        if (!exists(game)) return "No such game.";
        if (running.containsKey(game)) return "Stop it first (/ih game stop " + game + ").";
        y.set("games." + game, null);
        save();
        return "";
    }

    String setFinish(Player looker, String game) {
        if (!exists(game)) return "No such game.";
        Block b = looker.getTargetBlock(null, 8);
        if (b == null || b.getType() == org.bukkit.Material.AIR) return "Look at the finish plate (within 8 blocks).";
        y.set("games." + game + ".finish", java.util.List.of(b.getX(), b.getY(), b.getZ()));
        y.set("games." + game + ".finishWorld", b.getWorld().getName());
        save();
        return "";
    }

    String setCenter(Player p, String game) {
        if (!exists(game)) return "No such game.";
        y.set("games." + game + ".center", java.util.List.of(p.getLocation().getBlockX(), p.getLocation().getBlockZ()));
        y.set("games." + game + ".centerWorld", p.getWorld().getName());
        if (cfgI(game, "size", 0) <= 0) y.set("games." + game + ".size", 10); // until 'size' is set
        save();
        return "";
    }

    String setSize(String game, int r) {
        if (!exists(game)) return "No such game.";
        if (r < 2 || r > 200) return "Pick a radius between 2 and 200.";
        if (y.get("games." + game + ".center") == null) return "Set the center first (stand in the middle, /ih game set " + game + " center).";
        y.set("games." + game + ".size", r);
        save();
        return "";
    }

    String setPrize(String game, String mode, String keyType, int amount) {
        if (!exists(game)) return "No such game.";
        switch (mode) {
            case "none" -> y.set("games." + game + ".prize", null);
            case "key" -> {
                String kt = keyType == null || !Keys.validType(keyType) ? "victory" : Keys.norm(keyType);
                y.set("games." + game + ".prize.mode", "key");
                y.set("games." + game + ".prize.key", kt);
                y.set("games." + game + ".prize.amount", Math.max(1, Math.min(16, amount)));
            }
            case "chest" -> {
                y.set("games." + game + ".prize.mode", "chest");
                y.set("games." + game + ".prize.key", "victory");
                y.set("games." + game + ".prize.amount", 1);
            }
            default -> {
                return "Prize mode: none | key [type] [amount] | chest";
            }
        }
        if (!mode.equals("none")) y.set("games." + game + ".prize.mode", mode);
        save();
        return "";
    }

    // ------------------------------------------------------------ running a game

    String start(String game, int seconds) {
        if (!exists(game)) return "No such game.";
        if (running.containsKey(game)) return "Already running.";
        String t = type(game);
        int dur = Math.max(30, Math.min(3600, seconds <= 0 ? 600 : seconds));
        Session s = new Session(game);
        running.put(game, s);
        if (t.equals("race")) {
            if (y.get("games." + game + ".finish") == null) {
                running.remove(game);
                return "No finish plate yet - look at it and /ih game set " + game + " finish.";
            }
            broadcast("GO! " + game + " - first to the finish plate wins! (" + dur + "s window)", NamedTextColor.GOLD);
            s.endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(game), dur * 20L);
        } else if (t.equals("last")) {
            if (y.get("games." + game + ".center") == null) {
                running.remove(game);
                return "No circle yet - stand in the middle, then /ih game set " + game + " center.";
            }
            s.participants.addAll(inCircle(game));
            if (s.participants.isEmpty()) {
                running.remove(game);
                return "Nobody is standing in the circle.";
            }
            broadcast("GO! " + game + " - " + s.participants.size() + " players in the circle, last one standing after " + dur + "s wins!", NamedTextColor.GOLD);
            s.endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> roundEnd(game), dur * 20L);
        } else {
            broadcast(game + " is open - staff will decide the winner.", NamedTextColor.GOLD);
        }
        return "";
    }

    String stop(String game) {
        Session s = running.remove(game);
        if (s == null) return "That game is not running.";
        if (s.endTask != null) s.endTask.cancel();
        broadcast(game + " was stopped.", NamedTextColor.GRAY);
        return "";
    }

    /** Manual award - and the staff override for race/last. */
    String win(String game, Player target) {
        if (!exists(game)) return "No such game.";
        Session s = running.remove(game);
        if (s != null && s.endTask != null) s.endTask.cancel();
        award(game, target);
        return "";
    }

    @EventHandler
    public void onPlate(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        for (Map.Entry<String, Session> en : new HashMap<>(running).entrySet()) {
            String game = en.getKey();
            Session s = en.getValue();
            if (s.done || !type(game).equals("race")) continue;
            var f = y.getIntegerList("games." + game + ".finish");
            if (f.size() != 3 || !cfg(game, "finishWorld", "").equals(b.getWorld().getName())) continue;
            if (b.getX() != f.get(0) || b.getY() != f.get(1) || b.getZ() != f.get(2)) continue;
            s.done = true;
            running.remove(game);
            if (s.endTask != null) s.endTask.cancel();
            broadcast(Keys.cap(game) + " - " + e.getPlayer().getName() + " reached the plate first!", NamedTextColor.GREEN);
            award(game, e.getPlayer());
            return;
        }
    }

    private void expire(String game) {
        Session s = running.remove(game);
        if (s != null && !s.done) broadcast(game + " ran out of time - nobody reached the plate.", NamedTextColor.GRAY);
    }

    private void roundEnd(String game) {
        Session s = running.remove(game);
        if (s == null || s.done) return;
        Set<UUID> still = inCircle(game);
        still.retainAll(s.participants);
            if (still.isEmpty()) {
                broadcast(game + ": the circle was empty at the end - no winner.", NamedTextColor.RED);
                return;
            }
            announceWinners(still, game);
    }

    private void announceWinners(Set<UUID> winners, String game) {
        if (winners.size() == 1) {
            Player w = Bukkit.getPlayer(winners.iterator().next());
            broadcast("Last one standing in " + game + ": " + (w != null ? w.getName() : "someone who left") + "!", NamedTextColor.GREEN);
            if (w != null) award(game, w);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID u : winners) {
            Player w = Bukkit.getPlayer(u);
            if (w != null) {
                sb.append(sb.length() == 0 ? "" : ", ").append(w.getName());
                award(game, w);
            }
        }
        broadcast(winners.size() + " survivors in " + game + ": " + sb, NamedTextColor.GREEN);
    }

    private Set<UUID> inCircle(String game) {
        Set<UUID> out = new HashSet<>();
        var c = y.getIntegerList("games." + game + ".center");
        World w = Bukkit.getWorld(cfg(game, "centerWorld", ""));
        if (c.size() != 2 || w == null) return out;
        int r = cfgI(game, "size", 10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() != w || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            double dx = p.getLocation().getX() + 0.5 - c.get(0);
            double dz = p.getLocation().getZ() + 0.5 - c.get(1);
            if (dx * dx + dz * dz <= (double) r * r) out.add(p.getUniqueId());
        }
        return out;
    }

    // ------------------------------------------------------------ prizes

    private void award(String game, Player w) {
        String mode = cfg(game, "prize.mode", "none");
        if (mode.equals("none")) return;
        String kt = cfg(game, "prize.key", "victory");
        int amount = Math.max(1, cfgI(game, "prize.amount", 1));
        w.getInventory().addItem(keys.item(kt, amount));
        w.playSound(w.getLocation(), "minecraft:ui.toast.challenge_complete", 0.9f, 1.2f);
        w.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, w.getLocation().add(0, 1.2, 0), 24, 0.4, 0.5, 0.4, 0.02);
        w.sendMessage(Component.text("Prize: " + amount + "x " + Keys.cap(kt) + " Key!", NamedTextColor.LIGHT_PURPLE));
        if (mode.equals("chest")) {
            Decor.Prop vc = nearestVictoryChest(w);
            if (vc != null) {
                Location tp = vc.center().add(0, 1.2, 0);
                w.teleport(tp);
                w.sendMessage(Component.text("The victory chest awaits!", NamedTextColor.GOLD));
            } else {
                broadcast("No victory chest is placed yet - the key is yours to keep.", NamedTextColor.GRAY);
            }
        }
    }

    private Decor.Prop nearestVictoryChest(Player p) {
        Decor.Prop best = null;
        double bd = 300 * 300;
        for (Decor.Prop q : plugin.chests().decor.all()) {
            if (!q.def.id.equals("victory_chest") || q.world != p.getWorld()) continue;
            double d = q.center().distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        return best;
    }

    private void broadcast(String text, NamedTextColor c) {
        Bukkit.broadcast(Component.text("秘宝 " + text, c));
    }

    // ------------------------------------------------------------ restart hygiene

    void onEnableCleanupAnnounce() {
        if (!names().isEmpty())
            plugin.getLogger().info("Games: " + String.join(", ", names()) + " (none running after a restart - just /ih game start again).");
    }
}
