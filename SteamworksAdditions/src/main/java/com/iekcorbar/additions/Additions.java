package com.iekcorbar.additions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * The Additions props. The Steam Clock gets a floating time plate and strikes its bell on the
 * hour; the railing joins its neighbours; the vent grille may hang on a wall.
 */
final class Additions {
    final Decor decor;
    private final AdditionsPlugin plugin;

    Additions(AdditionsPlugin plugin) {
        this.plugin = plugin;
        decor = new Decor(plugin, "additions", defs(), plugin.getConfig().getInt("props.break-hits", 2));
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        l.add(new DecorDef("steam_clock", "Steam Clock", "A brass tower clock. It strikes the hour - listen for the bell - and reads the time above the dial.")
                .height(2)
                .tick(1, this::clock)
                .use(this::clockUse)
                .recipe(1, Material.CLOCK, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.GOLD_NUGGET, Material.GOLD_NUGGET, Material.REDSTONE)
                .sound("minecraft:block.anvil.place"));
        l.add(new DecorDef("brass_railing", "Brass Railing", "A low brass rail on iron posts. Joins its neighbours; polite queues start here.")
                .connect("brass_railing")
                .recipe(4, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, Material.STICK, Material.STICK)
                .sound("minecraft:block.chain.place"));
        l.add(new DecorDef("vent_grille", "Vent Grille", "A copper vent with green brass slats. On a wall, or free-standing as a street grille.")
                .wall()
                .recipe(1, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.IRON_NUGGET, Material.IRON_NUGGET)
                .sound("minecraft:block.chain.place"));
        return l;
    }

    /**
     * Steam Clock: the floating plate keeps HH:MM time from the configured source - the real
     * wall clock (default, one strike per real hour) or the Minecraft world clock (an hour every
     * 50 seconds). On every hour change the bell strikes, twice at 12 and 0, unless silenced
     * (sounds.clock-bell) or night-rested. Right-click: time, next strike and sky on the action bar.
     */
    private void clock(Decor d, Decor.Prop p, long tick) {
        int[] hm = time(p.world);
        int hour = hm[0], minute = hm[1];
        String show = String.format("%02d:%02d", hour, minute);

        TextDisplay plate = (TextDisplay) p.mem.get("plate");
        if (plate == null || !plate.isValid()) {
            plate = p.world.spawn(p.center().add(0, 2.45, 0), TextDisplay.class, x -> {
                x.setPersistent(false);
                x.setBillboard(Billboard.CENTER);
                x.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            p.mem.put("plate", plate);
            p.onRemove = plate::remove;
        }
        if (!show.equals(p.mem.get("show"))) {                 // redraw only when the shown time changed
            p.mem.put("show", show);
            boolean day = hour >= 6 && hour < 19;
            plate.text(Component.text(show, day ? NamedTextColor.GOLD : NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        Integer last = (Integer) p.mem.get("hour");
        p.mem.put("hour", hour);
        if (last == null || last == hour) return;              // first pass after loading: no strike
        if (plugin.realClock() && minute > 0) return;          // real mode: nobody near at the stroke = no late bell
        if (!plugin.bellEnabled()) return;                     // config sounds.clock-bell: the tower stays quiet
        if (plugin.nightRest() && (hour >= 23 || hour < 5)) return;   // config: no bell while the town sleeps

        p.world.spawnParticle(Particle.CLOUD, p.center().add(0, 2.0, 0), 5, 0.15, 0.06, 0.15, 0.01);
        ring(p, 0.8f + (hour % 12) * 0.03f);
        if (hour % 12 == 0) Bukkit.getScheduler().runTaskLater(d.plugin, () -> ring(p, 0.72f), 12L);
        for (Player near : p.world.getNearbyPlayers(p.center(), 16)) plugin.adv().grant(near, "heard");
    }

    /** int[]{hour, minute}: from the real wall clock (clock.time-source: real) or the world clock. */
    private int[] time(org.bukkit.World w) {
        if (plugin.realClock()) {
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(plugin.zone());
            return new int[]{now.getHour(), now.getMinute()};
        }
        long t = w.getTime();
        return new int[]{(int) ((t / 1000 + 6) % 24), (int) ((t % 1000) * 60 / 1000)};
    }

    /** Right-click the clock: action bar with the exact time, the next strike and the sky. */
    private boolean clockUse(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (!hand.getType().isAir()) return false;             // hands full of prop items still place/join
        int[] hm = time(p.world);
        int next = hm[1] == 0 ? 0 : 60 - hm[1];
        String sky = p.world.hasStorm() ? "stormy" : p.world.isClearWeather() ? "clear" : "cloudy";
        pl.sendActionBar(Component.text(String.format("%02d:%02d", hm[0], hm[1]), NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("  ·  next strike " + (next == 0 ? "now" : "in " + next + " min") + "  ·  sky: " + sky, NamedTextColor.GRAY)));
        return true;
    }

    private void ring(Decor.Prop p, float pitch) {
        p.world.playSound(p.center(), Sound.BLOCK_BELL_USE, 1.4f, pitch);
        p.world.playSound(p.center(), Sound.BLOCK_BELL_RESONATE, 0.7f, pitch * 0.85f);
    }
}
