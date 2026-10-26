package com.iekcorbar.steamworks;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Conveyor belts: a belt piece is a full block whose top is the belt surface.
 * Which way it runs follows its rotation like a real roller (surface moves
 * along axis x up for positive speed), so a gearshift or a reversed crank turns
 * the belt round. Items, mobs and players on top get carried; at the end of a
 * line items go into a container or kettle in front, otherwise they fall off.
 * Items a running steam hammer above can press wait under it.
 */
final class Belts {
    private final SteamworksPlugin plugin;

    Belts(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Unit direction the top surface moves, or null when standing still. */
    static Vector direction(KNode n) {
        double s = n.speed();
        if (s == 0) return null;
        Vector axis = Kinetics.vec(Kinetics.positive(n.axis));
        Vector d = axis.crossProduct(new Vector(0, 1, 0));
        return s > 0 ? d : d.multiply(-1);
    }

    void tick() {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().belts) {
            Vector d = direction(n);
            if (d == null) continue;
            double v = Math.abs(n.speed()) * cfg.beltSpeedPerRpm;
            BoundingBox box = new BoundingBox(n.x, n.y + 0.95, n.z, n.x + 1, n.y + 1.6, n.z + 1);
            Block ahead = n.world.getBlockAt(n.x + d.getBlockX(), n.y, n.z + d.getBlockZ());
            KNode next = plugin.kinetics().at(ahead);
            boolean end = next == null || next.type != PartType.BELT;
            for (Entity e : n.world.getNearbyEntities(box)) {
                if (!(e instanceof Item) && !(e instanceof LivingEntity)) continue;
                if (e instanceof Player p && (p.isFlying() || p.getGameMode() == GameMode.SPECTATOR || p.isSneaking())) continue;
                if (e.getVehicle() != null) continue;
                if (e instanceof Item item && heldByHammer(n, item)) {
                    centre(n, e, new Vector());
                    continue;
                }
                if (end && e instanceof Item item && pastHalf(n, e, d) && deliver(item, ahead, next)) continue;
                centre(n, e, d.clone().multiply(v));
            }
        }
    }

    /** Sets the horizontal velocity to the belt speed and pulls the entity towards the belt's middle line. */
    private static void centre(KNode n, Entity e, Vector along) {
        Vector pos = e.getLocation().toVector();
        Vector mid = new Vector(n.x + 0.5, pos.getY(), n.z + 0.5);
        Vector off = mid.subtract(pos);
        // only correct sideways (across the belt), not along it
        if (along.lengthSquared() > 0) {
            Vector u = along.clone().normalize();
            off.subtract(u.multiply(off.dot(u)));
        }
        Vector vel = along.clone().add(off.multiply(0.15));
        vel.setY(e.getVelocity().getY());
        e.setVelocity(vel);
    }

    private static boolean pastHalf(KNode n, Entity e, Vector d) {
        Vector rel = e.getLocation().toVector().subtract(new Vector(n.x + 0.5, 0, n.z + 0.5));
        return rel.getX() * d.getX() + rel.getZ() * d.getZ() > 0.3;
    }

    /** Into a container or kettle at the end of the line. True if the item entity is gone. */
    private boolean deliver(Item item, Block ahead, KNode next) {
        ItemStack stack = item.getItemStack();
        if (next != null && next.type == PartType.KETTLE && plugin.mixers().accepts(stack)) {
            int left = Mixers.addTo(next.kettleItems, stack.clone());
            plugin.visuals().updateKettle(next);
            plugin.saveSoon(next);
            return finish(item, stack, left);
        }
        if (next == null && ahead.getState(false) instanceof Container c) {
            Map<Integer, ItemStack> rest = c.getInventory().addItem(stack.clone());
            int left = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            return finish(item, stack, left);
        }
        return false;
    }

    private static boolean finish(Item item, ItemStack stack, int left) {
        if (left <= 0) {
            item.remove();
            return true;
        }
        if (left < stack.getAmount()) item.setItemStack(stack.asQuantity(left));
        return false;
    }

    /** A running hammer two blocks up presses items on this belt: let them wait for the strike. */
    private boolean heldByHammer(KNode belt, Item item) {
        KNode h = plugin.kinetics().rel(belt, 0, 2, 0);
        return h != null && h.type == PartType.STEAM_HAMMER && h.speed() != 0 && plugin.hammers().recipes.find(item.getItemStack()) != null;
    }
}
