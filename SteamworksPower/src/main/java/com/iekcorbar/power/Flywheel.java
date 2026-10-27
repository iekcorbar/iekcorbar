package com.iekcorbar.power;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;

/**
 * Brass Flywheel: a buffer on the line. While the network runs it winds itself up; when the
 * power falls away it takes over, keeping the machines turning at a slowly falling speed until
 * its charge runs out. It charges from the network's own RPM - it does not care which source
 * brought it. Strong power coming back simply hands the wheel the line over again.
 */
final class Flywheel implements CustomPart {
    private final PowerPlugin plugin;

    Flywheel(PowerPlugin plugin) {
        this.plugin = plugin;
    }

    /** Per-machine state; lives in the node's data slot, survives chunk saves as a small blob. */
    static final class State {
        boolean deposit;      // true while running down and feeding the network
        double spin;          // the speed to hold on to (signed, at this node)
        double charge;        // 0 .. buffer
    }

    private State state(CustomNode n) {
        if (n.data() instanceof State s) return s;
        State s = new State();
        n.data(s);
        return s;
    }

    @Override public String id() { return "pow_brass_flywheel"; }
    @Override public String displayName() { return "Brass Flywheel"; }
    @Override public String[] lore() {
        return new String[]{"A heavy brass wheel on the line.", "Winds up while the power runs and coasts the machines through a dip."};
    }
    @Override public String ownerPlugin() { return "SteamworksPower"; }
    @Override public Material particleBlock() { return Material.GOLD_BLOCK; }

    @Override public double capacity() { return plugin.getConfig().getDouble("flywheel.capacity", 32); }
    @Override public double impact() { return plugin.getConfig().getDouble("flywheel.impact", 2); }

    @Override
    public double generatedSpeed(CustomNode n) {
        State s = state(n);
        double speed = n.speed(), v = Math.abs(speed);
        double buffer = plugin.getConfig().getDouble("flywheel.buffer", 120);
        double minRpm = plugin.getConfig().getDouble("flywheel.min-rpm", 3);
        if (!s.deposit) {
            if (v >= minRpm) {
                s.spin = speed;
                s.charge = Math.min(buffer, s.charge + plugin.getConfig().getDouble("flywheel.charge-per-second", 12));
            } else if (v == 0 && s.charge >= 1 && s.spin != 0) {
                s.deposit = true;                    // the power fell away: take over the line
            }
            return 0;
        }
        if (s.charge <= 0) {
            s.deposit = false;
            s.spin = 0;
            return 0;
        }
        if (v > Math.abs(s.spin) + 0.5) {            // real power is back and stronger: give over
            s.deposit = false;
            return 0;
        }
        if (v > 0.2) s.charge = Math.max(0, s.charge - plugin.getConfig().getDouble("flywheel.drain-per-second", 6));
        double hold = Math.max(0.15, s.charge / buffer);   // the last stretch sputters
        return s.spin * hold;
    }

    @Override
    public boolean interact(CustomNode n, Player p, ItemStack hand) {
        State s = state(n);
        double buffer = plugin.getConfig().getDouble("flywheel.buffer", 120);
        int pct = (int) Math.round(100 * s.charge / buffer);
        String mode = s.deposit ? "running down" : s.charge >= buffer ? "full" : "winding up";
        p.sendActionBar(Component.text("Flywheel: " + pct + "% charged (" + mode + ")", NamedTextColor.GOLD));
        return true;
    }

    @Override
    public byte[] saveState(CustomNode n) {
        State s = state(n);
        return ByteBuffer.allocate(17).put((byte) (s.deposit ? 1 : 0)).putDouble(s.spin).putDouble(s.charge).array();
    }

    @Override
    public void loadState(CustomNode n, byte[] data) {
        if (data == null || data.length < 17) return;
        ByteBuffer b = ByteBuffer.wrap(data);
        State s = new State();
        s.deposit = b.get() != 0;
        s.spin = b.getDouble();
        s.charge = b.getDouble();
        n.data(s);
    }
}
