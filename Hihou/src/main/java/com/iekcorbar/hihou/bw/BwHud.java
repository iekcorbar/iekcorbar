package com.iekcorbar.hihou.bw;

import com.iekcorbar.hihou.HihouPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.5.1 klusjes: the per-player sidebar (who still has a bed, how many are left, the clock
 * to sudden death, your own kit and wallet) and the victory show.
 *
 * One scoreboard PER participant on purpose: the match lines are shared, but the wallet and
 * kit lines are personal - the board is the reason players stop asking "who is still in?".
 * The board is drawn once per playing tick (cheap: strings only) and the player's original
 * board - usually the main one with the TAB plugin on it - comes back in takeBack().
 */
final class BwHud {
    private final BwGameManager mgr;
    private final HihouPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new LinkedHashMap<>();
    private final Map<Scoreboard, List<String>> written = new LinkedHashMap<>();

    BwHud(BwGameManager mgr, HihouPlugin plugin) {
        this.mgr = mgr;
        this.plugin = plugin;
    }

    /** Hand out the arena board (join, spectate, launch - idempotent per player). */
    void give(BwArena a, Player p) {
        if (boards.containsKey(p.getUniqueId())) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("bw", org.bukkit.scoreboard.Criteria.DUMMY,
                Component.text("BedWars - " + a.name, NamedTextColor.GOLD));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(p.getUniqueId(), sb);
        written.put(sb, new ArrayList<>());
        draw(a, p, sb);
    }

    /** Back to the board the player carried in (TAB/main) - every takeBack path ends here. */
    void release(Player p, Scoreboard back) {
        Scoreboard mine = boards.remove(p.getUniqueId());
        if (mine == null) return;
        written.remove(mine);
        if (p.isOnline()) p.setScoreboard(back != null ? back : Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Once per second with the playing tick: refresh every pair of eyes in the arena. */
    void update(BwArena a) {
        for (UUID u : a.players) tick(a, Bukkit.getPlayer(u));
        for (UUID u : a.spectators) tick(a, Bukkit.getPlayer(u));
    }

    private void tick(BwArena a, Player p) {
        if (p == null) return;
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) give(a, p);
        else draw(a, p, sb);
    }

    private void draw(BwArena a, Player p, Scoreboard sb) {
        Objective o = sb.getObjective("bw");
        if (o == null) return;
        for (String e : written.get(sb)) sb.resetScores(e);
        List<String> lines = new ArrayList<>();
        if (a.status == BwArena.Status.STARTING) {
            lines.add("starts in " + Math.max(0, a.countdown) + "s");
        } else if (a.status == BwArena.Status.PLAYING) {
            int beds = 0;
            for (BwTeam t : a.teams.values()) if (!t.bedDestroyed()) beds++;
            lines.add(beds > 0 ? "beds left: " + beds : "SUDDEN DEATH");
            int at = mgr.bedsAt(a);
            lines.add(at - a.elapsed > 0 ? "beds go in " + clock(at - a.elapsed) : "ends in " + clock(mgr.endAt(a) - a.elapsed));
            lines.add("mode: " + mgr.modeName(a));
        } else {
            lines.add("waiting for players");
        }
        lines.add("");
        for (BwTeam t : a.teams.values())
            lines.add(t.name() + ": " + (t.bedDestroyed() ? "no bed" : "bed") + " [" + t.members().size() + "]");
        if (a.spectators.contains(p.getUniqueId())) {
            lines.add("");
            lines.add("spectating - /ih bw leave");
        } else {
            lines.add("");
            lines.add("kit: " + a.kitChoice.getOrDefault(p.getUniqueId(), "classic"));
            lines.add("wallet: " + count(p, Material.IRON_INGOT) + "i " + count(p, Material.GOLD_INGOT) + "g"
                    + " " + count(p, Material.DIAMOND) + "d " + count(p, Material.EMERALD) + "e");
        }
        int score = lines.size();
        for (String line : lines) {
            String key = line.isEmpty() ? " " + score : line;   // entries must be unique
            o.getScore(key).setScore(score--);
            written.get(sb).add(key);
        }
    }

    private static String clock(int sec) {
        int s = Math.max(0, sec);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    private static int count(Player p, Material ore) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == ore) n += it.getAmount();
        return n;
    }

    // ------------------------------------------------------------ the victory show

    /** Title for the winners plus six largish rockets in the team color over their base. */
    void celebrate(BwArena a, BwTeam winner) {
        if (winner.spawn() == null) return;
        for (UUID u : winner.members()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.showTitle(Title.title(Component.text("VICTORY!", BwGameManager.chat(winner.color())),
                    Component.text("the hand-over comes in 15 seconds", NamedTextColor.GRAY)));
        }
        Location base = winner.spawn();
        World w = base.getWorld();
        for (int i = 0; i < 6; i++)
            Bukkit.getScheduler().runTaskLater(plugin, () -> rocket(w,
                    base.getBlockX() + 0.5 + Math.random() * 6 - 3, base.getY() + 4,
                    base.getBlockZ() + 0.5 + Math.random() * 6 - 3, winner.color()), i * 12L);
    }

    private static void rocket(World w, double x, double y, double z, DyeColor c) {
        Firework fw = w.spawn(new Location(w, x, y, z), Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffects(org.bukkit.FireworkEffect.builder().with(org.bukkit.FireworkEffect.Type.BALL_LARGE).withColor(colorOf(c)).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
        fw.detonate();
    }

    private static Color colorOf(DyeColor c) {
        return switch (c) {
            case RED, ORANGE -> Color.fromRGB(0xD84A32);
            case BLUE -> Color.fromRGB(0x324CD8);
            case GREEN, LIME -> Color.fromRGB(0x4E8E2C);
            case YELLOW -> Color.fromRGB(0xF0C832);
            case CYAN, LIGHT_BLUE -> Color.fromRGB(0x32B0D8);
            case PURPLE, MAGENTA -> Color.fromRGB(0x8A32D8);
            case PINK -> Color.fromRGB(0xF078A0);
            case WHITE -> Color.fromRGB(0xE6E6E6);
            default -> Color.fromRGB(0x808080);
        };
    }
}
