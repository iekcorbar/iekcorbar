package com.iekcorbar.steamworks;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
            boolean end = next == null || !(next.type == PartType.BELT || next.type == PartType.CHUTE || next.type == PartType.FUNNEL);
            if (n.type == PartType.FUNNEL) {
                // the mouth: items falling past the rim (y above the funnel's lid) drop into the flow
                BoundingBox mouth = new BoundingBox(n.x, n.y + 1.5, n.z, n.x + 1, n.y + 2.6, n.z + 1);
                for (Entity e : n.world.getNearbyEntities(mouth))
                    if (e instanceof Item)
                        e.teleport(new org.bukkit.Location(n.world, n.x + 0.5, n.y + 1.2, n.z + 0.5));
            }
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
        if (next != null && next.type == PartType.CUSTOM && next.ext != null) {
            // an add-on machine (a sieve funnel) may take the item over: null = not its business
            ItemStack rest = next.ext.handleItem(ExtParts.view(next), stack);
            if (rest != null) return finish(item, stack, rest.getAmount());
        }
        if (next != null && next.type == PartType.KETTLE && plugin.mixers().accepts(stack)) {
            int left = Mixers.addTo(next.kettleItems, stack.clone());
            plugin.visuals().updateKettle(next);
            plugin.saveSoon(next);
            return finish(item, stack, left);
        }
        if (next != null && next.type == PartType.MILLSTONE && next.millInput == null && plugin.millstones().find(stack) != null) {
            next.millInput = stack.clone();   // the millstone's own tick shows and grinds it
            plugin.saveSoon(next);
            item.remove();
            return true;
        }
        if (next != null && next.type == PartType.DEPOT) {
            // the depot is a shelf: the item waits there for the ejector it looks at, or for a hand
            item.teleport(new org.bukkit.Location(next.world, next.x + 0.5, next.y + 1.1, next.z + 0.5));
            item.setVelocity(new Vector());
            next.world.playSound(next.center(), "block.stone_pressure_plate.use", 0.5f, 1.4f);
            return false;
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

    /** Weighted ejectors: powered, they fling whatever rests on top of them (or on the depot they look at). */
    void eject() {
        for (KNode n : plugin.kinetics().ejectors) {
            if (n.speed() == 0 || plugin.kinetics().tick < n.ejectUntil) continue;
            BlockFace f = n.facing;
            // collect items on top of the ejector OR on the depot it looks at
            java.util.List<Item> candidates = new java.util.ArrayList<>();
            for (Entity e : n.world.getNearbyEntities(new BoundingBox(n.x, n.y + 0.6, n.z, n.x + 1, n.y + 2.4, n.z + 1)))
                if (e instanceof Item i) candidates.add(i);
            org.bukkit.Location from = n.center().add(0, 0.8, 0);
            KNode dep = plugin.kinetics().rel(n, f.getModX(), 0, f.getModZ());
            if (candidates.isEmpty() && dep != null && dep.type == PartType.DEPOT) {
                for (Entity e : dep.world.getNearbyEntities(new BoundingBox(dep.x, dep.y + 0.6, dep.z, dep.x + 1, dep.y + 2.0, dep.z + 1)))
                    if (e instanceof Item i) candidates.add(i);
                from = dep.center().add(0, 0.8, 0);
            }
            if (candidates.isEmpty()) continue;
            // fling up to 3 items at once (weighted by speed)
            int max = n.speed() >= 32 ? 3 : (n.speed() >= 16 ? 2 : 1);
            int flung = 0;
            for (Item item : candidates) {
                if (flung >= max) break;
                n.ejectUntil = plugin.kinetics().tick + 16;
                org.bukkit.Location launch = from.clone().add(f.getModX() * 0.45, 0, f.getModZ() * 0.45);
                item.teleport(launch);
                // weighted: heavier items (more stack) fly shorter
                double weight = Math.min(1.0, item.getItemStack().getAmount() / 16.0);
                double speed = 0.55 + (1 - weight) * 0.2;
                item.setVelocity(new Vector(f.getModX(), 0, f.getModZ()).multiply(speed).setY(0.38 + (1 - weight) * 0.1));
                n.world.playSound(n.center(), "block.tripwire.attach", 0.7f, 1.15f);
                n.world.spawnParticle(org.bukkit.Particle.CRIT, launch, 5, 0.1, 0.1, 0.1, 0.05);
                flung++;
            }
        }
    }

    private static Item firstItem(KNode n, BoundingBox box) {
        for (Entity e : n.world.getNearbyEntities(box)) if (e instanceof Item i) return i;
        return null;
    }
}
