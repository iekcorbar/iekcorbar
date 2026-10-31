package com.iekcorbar.power;

import com.iekcorbar.steamworks.SteamworksPlugin;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mechanical Press: stands on the ground, shaft plugged in from below. Items lying on top are
 * compacted one at a time - what vanilla can't smelt, the press squeezes (see config "press:").
 */
public final class MechanicalPress implements CustomPart {
    private final SteamworksPlugin plugin;
    private final List<Recipe> recipes = new ArrayList<>();

    record Recipe(ItemStack input, int count, List<Out> outs, int time) {}
    record Out(ItemStack item, int count, double chance) {}

    /** Per-node work state (lost when the chunk unloads - waiting items are returned on the plate). */
    static public final class Working {
        ItemStack waiting;
        double progress;
    }

    public MechanicalPress(SteamworksPlugin plugin) {
        this.plugin = plugin;
        loadRecipes();
    }

    void loadRecipes() {
        recipes.clear();
        int time = Math.max(10, plugin.getConfig().getInt("press.time", 60));
        for (var cfg : plugin.getConfig().getMapList("press.recipes")) {
            Object in = cfg.get("input");
            Object out = cfg.get("output");
            if (in == null || !(out instanceof List<?> outs)) continue;
            Recipe r = parse(in.toString(), outs, time);
            if (r != null) recipes.add(r);
        }
    }

    private Recipe parse(String input, List<?> outs, int time) {
        String[] p = input.trim().split("\\s+");
        ItemStack in = resolve(p[0]);
        int count = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        if (in == null) return null;
        List<Out> result = new ArrayList<>();
        for (Object o : outs) {
            String[] q = String.valueOf(o).trim().split("\\s+");
            ItemStack item = resolve(q[0]);
            if (item == null) continue;
            int n = q.length > 1 ? Integer.parseInt(q[1]) : 1;
            double chance = q.length > 2 ? Integer.parseInt(q[2].replace("%", "")) / 100.0 : 1;
            result.add(new Out(item, n, chance));
        }
        return result.isEmpty() ? null : new Recipe(in, count, result, time);
    }

    /** "IRON_ORE", "sw:zinc_nugget" -> a stack to match/produce, or null. */
    private ItemStack resolve(String token) {
        if (token.startsWith("sw:")) return plugin.customParts().steamworksItem("material/" + token.substring(3));
        if (token.startsWith("kitchen:")) {
            try {
                Class<?> kb = Class.forName("com.iekcorbar.steamworks.KitchenBridge");
                java.lang.reflect.Method create = kb.getMethod("create", String.class, int.class);
                Object result = create.invoke(null, token.substring(8), 1);
                if (result instanceof ItemStack) return (ItemStack) result;
            } catch (Exception ignored) {
            }
            return null;
        }
        Material m = Material.matchMaterial(token);
        return m == null || !m.isItem() ? null : new ItemStack(m);
    }

    List<Recipe> recipes() {
        return recipes;
    }

    @Override public String id() { return "pow_mechanical_press"; }
    @Override public String displayName() { return "Mechanical Press"; }
    @Override public String[] lore() {
        return new String[]{"Compacts what lies on its plate:", "raw into dense. Needs rotation from below."};
    }
    @Override public String ownerPlugin() { return "SteamworksPower"; }
    @Override public Material particleBlock() { return Material.IRON_BLOCK; }

    @Override public boolean rotatable() { return false; }
    @Override public double impact() { return plugin.getConfig().getDouble("press.impact", 8); }
    @Override public java.util.List<com.iekcorbar.steamworks.api.CustomVisual> visuals(CustomNode n) {
        return java.util.List.of(com.iekcorbar.steamworks.api.CustomVisual.statik("pow_mechanical_press"));
    }
    @Override public java.util.EnumSet<org.bukkit.block.BlockFace> ports(org.bukkit.Axis axis) {
        return java.util.EnumSet.of(org.bukkit.block.BlockFace.DOWN);    // the shaft turns it from below, like the millstone
    }
    @Override public org.bukkit.Axis initialAxis(org.bukkit.block.BlockFace face, org.bukkit.entity.Player p) {
        return org.bukkit.Axis.Y;
    }

    @Override
    public void tick(CustomNode n, long tick) {
        Working w = work(n);
        double speed = Math.abs(n.speed());
        if (w.waiting == null) {
            if (speed <= 0 || tick % 5 != 0) return;
            ItemStack on = takeFromPlate(n);
            if (on != null) { w.waiting = on; w.progress = 0; }
            return;
        }
        Recipe r = find(w.waiting);
        if (r == null) {                                            // not pressable: give it back
            n.world().dropItemNaturally(n.center().add(0, 0.6, 0), w.waiting);
            w.waiting = null;
            return;
        }
        w.progress += speed / 20;                                   // time work-units: speed / 20 per tick
        if (w.progress < r.time()) return;
        w.progress -= r.time();
        w.waiting.setAmount(w.waiting.getAmount() - r.count());
        if (w.waiting.getAmount() <= 0) w.waiting = null;
        Location out = n.center().add(0, 0.6, 0);
        for (Out o : r.outs()) {
            if (o.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= o.chance()) continue;
            ItemStack made = o.item().clone();
            made.setAmount(o.count());
            n.world().dropItem(out, made);
        }
        n.world().playSound(n.center(), Sound.BLOCK_ANVIL_USE, 0.5f, 0.8f);
        n.world().spawnParticle(Particle.CLOUD, out, 6, 0.3, 0.08, 0.3, 0.02);
    }

    private Working work(CustomNode n) {
        Object d = n.data();
        if (d instanceof Working w) return w;
        Working w = new Working();
        n.data(w);
        return w;
    }

    /** The first item entity lying on the press's top face. */
    private ItemStack takeFromPlate(CustomNode n) {
        for (org.bukkit.entity.Entity e : n.center().add(0, 1.0, 0).getNearbyEntities(0.45, 0.5, 0.45)) {
            if (!(e instanceof Item it)) continue;
            ItemStack s = it.getItemStack();
            if (s.isEmpty()) continue;
            ItemStack one = s.asOne();
            s.setAmount(s.getAmount() - 1);
            if (s.getAmount() <= 0) it.remove();
            return one;
        }
        return null;
    }

    private Recipe find(ItemStack item) {
        for (Recipe r : recipes) if (item.isSimilar(r.input())) return r;
        return null;
    }

    @Override
    public boolean interact(CustomNode n, org.bukkit.entity.Player p, ItemStack hand) {
        if (hand != null && !hand.getType().isAir()) {
            Recipe r = find(hand.asOne());
            if (r != null) {                                      // put items on the plate by hand
                ItemStack one = hand.asOne();
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                Item drop = n.world().spawn(n.center().add(0, 1.05, 0), Item.class, it -> {
                    it.setItemStack(one);
                    it.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                });
                drop.setPickupDelay(40);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRemove(CustomNode n) {
        Object d = n.data();
        if (d instanceof Working w && w.waiting != null) {
            n.world().dropItemNaturally(n.center().add(0, 0.6, 0), w.waiting);
            w.waiting = null;
        }
    }
}
