package com.iekcorbar.additions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

    Additions(AdditionsPlugin plugin) {
        decor = new Decor(plugin, "additions", defs(), plugin.getConfig().getInt("props.break-hits", 2));
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        l.add(new DecorDef("steam_clock", "Steam Clock", "A brass tower clock. It strikes the hour - listen for the bell - and reads the time above the dial.")
                .height(2)
                .tick(2, this::clock)
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
     * Steam Clock, once per game hour: update the floating plate and ring the bell
     * (twice at the top of 12 and 0). The engine only calls this while players are near.
     */
    private void clock(Decor d, Decor.Prop p, long tick) {
        int hour = (int) ((p.world.getTime() / 1000 + 6) % 24);
        Object last = p.mem.get("hour");
        if (last != null && (Integer) last == hour) return;
        boolean running = last != null;                 // the first pass after loading only sets the plate
        p.mem.put("hour", hour);

        TextDisplay plate = (TextDisplay) p.mem.get("plate");
        if (plate == null || !plate.isValid()) {
            plate = p.world.spawn(p.center().add(0, 2.45, 0), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            p.mem.put("plate", plate);
            p.onRemove = plate::remove;
        }
        plate.text(Component.text(String.format("%02d:00", hour), NamedTextColor.GOLD, TextDecoration.BOLD));
        if (!running) return;

        p.world.spawnParticle(Particle.CLOUD, p.center().add(0, 2.0, 0), 5, 0.15, 0.06, 0.15, 0.01);
        ring(p, 0.8f + (hour % 12) * 0.03f);
        if (hour % 12 == 0) Bukkit.getScheduler().runTaskLater(d.plugin, () -> ring(p, 0.72f), 12L);
    }

    private void ring(Decor.Prop p, float pitch) {
        p.world.playSound(p.center(), Sound.BLOCK_BELL_USE, 1.4f, pitch);
        p.world.playSound(p.center(), Sound.BLOCK_BELL_RESONATE, 0.7f, pitch * 0.85f);
    }
}
