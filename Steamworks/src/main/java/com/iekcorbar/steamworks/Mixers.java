package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kettle + mixer. The kettle holds up to 9 ingredient stacks, water (4 units
 * per bucket, max 3 buckets) and up to 9 result stacks; fire, a lit campfire,
 * lava or magma under it heats it. A mixer right above it, when turning, works
 * on the first recipe whose ingredients are all in the kettle. Results go into
 * a container under or beside the kettle, or are taken by hand.
 */
final class Mixers {
    static final int MAX_STACKS = 9, WATER_PER_BUCKET = 4, MAX_WATER = 12;

    record Ingredient(Material material, Tag<Material> tag, MaterialType sw, String kitchen, int count) {
        boolean matches(ItemStack i, PartItems items) {
            if (i == null || i.isEmpty() || items.typeOf(i) != null) return false;
            String k = KitchenBridge.idOf(i);
            if (kitchen != null || k != null) return kitchen != null && kitchen.equals(k);
            MaterialType held = items.materialOf(i);
            if (sw != null) return sw == held;
            if (held != null) return false;
            return tag != null ? tag.isTagged(i.getType()) : i.getType() == material;
        }
    }

    record MixRecipe(List<Ingredient> inputs, int water, boolean heat, int time, List<MachineRecipes.Output> outputs) {}

    private final SteamworksPlugin plugin;
    private final List<MixRecipe> recipes = new ArrayList<>();

    Mixers(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    List<MixRecipe> recipes() {
        return recipes;
    }

    void load(File file) {
        recipes.clear();
        for (Map<?, ?> m : MachineRecipes.section(plugin, file, "mixer")) {
            try {
                List<Ingredient> ins = new ArrayList<>();
                Object in = m.get("inputs");
                if (in instanceof List<?> l) for (Object e : l) ins.add(parseIngredient(String.valueOf(e)));
                else if (in != null) ins.add(parseIngredient(String.valueOf(in)));
                if (ins.isEmpty()) throw new IllegalArgumentException("no inputs");
                List<MachineRecipes.Output> outs = new ArrayList<>();
                Object o = m.get("output");
                if (o instanceof List<?> l) for (Object e : l) outs.add(MachineRecipes.parseOutput(String.valueOf(e)));
                else if (o != null) outs.add(MachineRecipes.parseOutput(String.valueOf(o)));
                if (outs.isEmpty()) throw new IllegalArgumentException("no output");
                int water = m.get("water") instanceof Number n ? n.intValue() : 0;
                boolean heat = Boolean.TRUE.equals(m.get("heat"));
                int time = m.get("time") instanceof Number n ? n.intValue() : 200;
                recipes.add(new MixRecipe(ins, Math.max(0, water), heat, Math.max(1, time), outs));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("machines.yml: skipping mixer recipe " + m + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " mixer recipe(s).");
    }

    private static Ingredient parseIngredient(String s) {
        String[] p = s.trim().split("\\s+");
        String low = p[0].toLowerCase(Locale.ROOT);
        int count = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        if (low.startsWith("tag:")) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(low.substring(4)), Material.class);
            if (tag == null) throw new IllegalArgumentException("unknown item tag " + p[0]);
            return new Ingredient(null, tag, null, null, count);
        }
        if (low.startsWith("sw:")) {
            MaterialType sw = MaterialType.byId(low.substring(3));
            if (sw == null) throw new IllegalArgumentException("unknown Steamworks material " + p[0]);
            return new Ingredient(null, null, sw, null, count);
        }
        if (low.startsWith("kitchen:")) {
            String id = low.substring(8);
            if (KitchenBridge.create(id, 1) == null) throw new IllegalArgumentException("Kitchen plugin missing or unknown item " + p[0]);
            return new Ingredient(null, null, null, id, count);
        }
        Material mat = Material.matchMaterial(p[0]);
        if (mat == null) throw new IllegalArgumentException("unknown material " + p[0]);
        return new Ingredient(mat, null, null, null, count);
    }

    /** True if some recipe uses this item (only those go into a kettle). */
    boolean accepts(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        for (MixRecipe r : recipes) for (Ingredient i : r.inputs()) if (i.matches(item, plugin.items())) return true;
        return false;
    }

    static boolean heated(KNode kettle) {
        Block b = kettle.block().getRelative(BlockFace.DOWN);
        Material m = b.getType();
        if (m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.LAVA || m == Material.MAGMA_BLOCK) return true;
        if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) return b.getBlockData() instanceof Lightable l && l.isLit();
        return false;
    }

    KNode kettleUnder(KNode mixer) {
        KNode k = plugin.kinetics().rel(mixer, 0, -1, 0);
        return k != null && k.type == PartType.KETTLE ? k : null;
    }

    private int available(KNode kettle, Ingredient ing) {
        int n = 0;
        for (ItemStack s : kettle.kettleItems) if (ing.matches(s, plugin.items())) n += s.getAmount();
        return n;
    }

    MixRecipe find(KNode kettle) {
        boolean hot = heated(kettle);
        for (MixRecipe r : recipes) {
            if (r.heat() && !hot) continue;
            if (kettle.kettleWater < r.water()) continue;
            boolean ok = true;
            for (Ingredient i : r.inputs()) {
                if (available(kettle, i) < i.count()) {
                    ok = false;
                    break;
                }
            }
            if (ok) return r;
        }
        return null;
    }

    // ------------------------------------------------------------ ticking (every 5 ticks)

    void tick(long tick) {
        Settings cfg = plugin.settings();
        for (KNode k : plugin.kinetics().kettles) {
            if (tick % 20 == 0) pushOut(k);
            if (tick % 10 == 0) pullFromHoppers(k);
        }
        for (KNode m : plugin.kinetics().mixers) {
            KNode k = kettleUnder(m);
            double speed = Math.abs(m.speed());
            MixRecipe r = k == null || speed <= 0 || k.millOutput.size() >= MAX_STACKS ? null : find(k);
            setWhisk(m, r != null);
            if (r == null) {
                m.mixProgress = 0;
                continue;
            }
            m.mixProgress += 5 * Math.max(cfg.mixerMinRate, Math.min(cfg.mixerMaxRate, speed / cfg.mixerDivisor));
            Location top = k.center().add(0, 0.4, 0);
            k.world.spawnParticle(r.heat() ? Particle.BUBBLE_POP : Particle.SPLASH, top, 4, 0.25, 0.05, 0.25, 0.01);
            if (tick % 20 == 0) k.world.playSound(top, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.25f, 1.2f);
            if (m.mixProgress < r.time()) continue;
            m.mixProgress = 0;
            finish(k, r);
        }
    }

    private void setWhisk(KNode mixer, boolean working) {
        float drop = working ? 0.5f : 0f;
        if (mixer.whiskDrop == drop) return;
        mixer.whiskDrop = drop;
        plugin.visuals().refreshSpin(mixer, 6);
    }

    private void finish(KNode k, MixRecipe r) {
        for (Ingredient i : r.inputs()) {
            int left = i.count();
            for (ItemStack s : new ArrayList<>(k.kettleItems)) {
                if (left <= 0) break;
                if (!i.matches(s, plugin.items())) continue;
                int take = Math.min(left, s.getAmount());
                s.setAmount(s.getAmount() - take);
                left -= take;
                if (s.getAmount() <= 0) k.kettleItems.remove(s);
            }
        }
        k.kettleWater -= r.water();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (MachineRecipes.Output o : r.outputs()) {
            if (o.chance() < 1 && rnd.nextDouble() >= o.chance()) continue;
            addTo(k.millOutput, o.create(plugin.items()));
        }
        Location top = k.center().add(0, 0.5, 0);
        k.world.playSound(top, Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 1.1f);
        k.world.spawnParticle(Particle.CLOUD, top, 5, 0.2, 0.1, 0.2, 0.02);
        plugin.visuals().updateKettle(k);
        plugin.saveSoon(k);
    }

    /** Merges an item into a list of stacks. Returns what did not fit (stack limit MAX_STACKS). */
    static int addTo(List<ItemStack> list, ItemStack item) {
        for (ItemStack s : list) {
            if (!s.isSimilar(item)) continue;
            int move = Math.min(s.getMaxStackSize() - s.getAmount(), item.getAmount());
            s.setAmount(s.getAmount() + move);
            item.setAmount(item.getAmount() - move);
            if (item.getAmount() <= 0) return 0;
        }
        if (list.size() >= MAX_STACKS) return item.getAmount();
        list.add(item);
        return 0;
    }

    /** Results into a container under the kettle or on one of its sides. */
    private void pushOut(KNode k) {
        if (k.millOutput.isEmpty()) return;
        for (BlockFace f : new BlockFace[]{BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block b = k.block().getRelative(f);
            if (!(b.getState(false) instanceof Container c)) continue;
            Inventory inv = c.getInventory();
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack s : k.millOutput) left.addAll(inv.addItem(s).values());
            k.millOutput.clear();
            k.millOutput.addAll(left);
            if (k.millOutput.isEmpty()) break;
        }
    }

    /** Hoppers beside the kettle pointing into it feed one ingredient at a time. */
    private void pullFromHoppers(KNode k) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block b = k.block().getRelative(f);
            if (b.getType() != Material.HOPPER) continue;
            if (!(b.getBlockData() instanceof Directional d) || d.getFacing() != f.getOppositeFace()) continue;
            if (!(b.getState(false) instanceof Hopper h)) continue;
            Inventory inv = h.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack s = inv.getItem(slot);
                if (s == null || s.isEmpty() || !accepts(s)) continue;
                if (addTo(k.kettleItems, s.asOne()) > 0) break;
                s.setAmount(s.getAmount() - 1);
                inv.setItem(slot, s.getAmount() <= 0 ? null : s);
                plugin.saveSoon(k);
                break;
            }
        }
    }

    // ------------------------------------------------------------ by hand

    /** Right-click on a kettle. Returns true if something happened. */
    boolean interact(KNode k, Player p, ItemStack hand) {
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (hand != null && hand.getType() == Material.WATER_BUCKET) {
            if (k.kettleWater + WATER_PER_BUCKET > MAX_WATER) return false;
            k.kettleWater += WATER_PER_BUCKET;
            if (!creative) p.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            p.playSound(k.center(), Sound.ITEM_BUCKET_EMPTY, 0.7f, 1f);
            changed(k);
            return true;
        }
        if (hand != null && hand.getType() == Material.BUCKET && k.kettleWater >= WATER_PER_BUCKET) {
            k.kettleWater -= WATER_PER_BUCKET;
            if (!creative) {
                hand.setAmount(hand.getAmount() - 1);
                for (ItemStack left : p.getInventory().addItem(new ItemStack(Material.WATER_BUCKET)).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            p.playSound(k.center(), Sound.ITEM_BUCKET_FILL, 0.7f, 1f);
            changed(k);
            return true;
        }
        if (accepts(hand)) {
            ItemStack in = hand.clone();
            int before = in.getAmount();
            int left = addTo(k.kettleItems, in);
            int moved = before - left;
            if (moved <= 0) return false;
            if (!creative) hand.setAmount(hand.getAmount() - moved);
            p.playSound(k.center(), Sound.ENTITY_GENERIC_SPLASH, 0.3f, 1.4f);
            changed(k);
            return true;
        }
        if (hand == null || hand.isEmpty()) {
            if (!k.millOutput.isEmpty()) {
                give(p, k.millOutput);
                k.millOutput.clear();
                changed(k);
                return true;
            }
            if (p.isSneaking() && !k.kettleItems.isEmpty()) {
                give(p, k.kettleItems);
                k.kettleItems.clear();
                changed(k);
                return true;
            }
        }
        return false;
    }

    private void changed(KNode k) {
        plugin.visuals().updateKettle(k);
        plugin.saveSoon(k);
    }

    private static void give(Player p, List<ItemStack> items) {
        for (ItemStack s : items) {
            for (ItemStack left : p.getInventory().addItem(s.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
    }

    Component info(KNode k) {
        Component c = Component.text("Kettle: ", NamedTextColor.GOLD);
        String water = k.kettleWater == 0 ? "no water" : String.format("water %.2f bucket(s)", k.kettleWater / (double) WATER_PER_BUCKET);
        c = c.append(Component.text(water + ", " + (heated(k) ? "heated" : "not heated") + ", "
                + k.kettleItems.size() + " ingredient stack(s)", NamedTextColor.GRAY));
        MixRecipe r = find(k);
        if (r != null) {
            MachineRecipes.Output o = r.outputs().get(0);
            String name = o.name();
            c = c.append(Component.text("  |  ready to mix: " + name, NamedTextColor.GREEN));
        }
        if (!k.millOutput.isEmpty()) c = c.append(Component.text("  |  results waiting", NamedTextColor.AQUA));
        return c;
    }

    static List<ItemStack> contents(KNode k) {
        List<ItemStack> out = new ArrayList<>(k.kettleItems);
        out.addAll(k.millOutput);
        return out;
    }
}
