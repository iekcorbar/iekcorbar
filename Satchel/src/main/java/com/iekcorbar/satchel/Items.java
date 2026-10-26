package com.iekcorbar.satchel;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates and recognises Satchel items, holds a backpack's contents in the item itself and registers the recipes. */
final class Items implements Listener {
    /** One workbench recipe: a shape (or null = shapeless) with Material / SItem ingredients. */
    record Spec(String id, SItem result, int amount, String[] shape, Map<Character, Object> ing, List<Object> loose) {
        List<SItem> required() {
            List<SItem> out = new ArrayList<>();
            if (shape != null) {
                for (String row : shape) for (char c : row.toCharArray()) if (ing.get(c) instanceof SItem s) out.add(s);
            } else {
                for (Object o : loose) if (o instanceof SItem s) out.add(s);
            }
            return out;
        }
    }

    private final SatchelPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey bagKey;
    private final NamespacedKey storeKey;
    private final NamespacedKey upgKey;
    private final NamespacedKey upgListKey;
    final List<Spec> specs = new ArrayList<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    Items(SatchelPlugin plugin) {
        this.plugin = plugin;
        idKey = new NamespacedKey(plugin, "item");
        bagKey = new NamespacedKey(plugin, "bag");
        storeKey = new NamespacedKey(plugin, "store");
        upgKey = new NamespacedKey(plugin, "upgrades");
        upgListKey = new NamespacedKey(plugin, "upg");
    }

    ItemStack create(SItem t, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("satchel", t.id()));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, t.backpack() ? 1 : 64);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(t.displayName, t.backpack() ? NamedTextColor.GOLD : NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        if (t == SItem.NETHERITE_BACKPACK) fireproof(it);
        relore(it);
        return it;
    }

    private static void fireproof(ItemStack it) {
        try {
            it.setData(DataComponentTypes.DAMAGE_RESISTANT, io.papermc.paper.datacomponent.item.DamageResistant
                    .damageResistant(io.papermc.paper.registry.RegistryAccess.registryAccess()
                            .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE).getTag(io.papermc.paper.registry.keys.tags.DamageTypeTagKeys.IS_FIRE)));
        } catch (RuntimeException | LinkageError ignored) {
            // older API: the item simply burns like any other
        }
    }

    SItem typeOf(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return null;
        return SItem.byId(it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    /** A plain paper named like a backpack with the last lore line "Satchel" and no data of ours: what the WanderingTrades files hand out. */
    SItem fromTrader(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta() || typeOf(it) != null) return null;
        ItemMeta m = it.getItemMeta();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        if (!m.hasDisplayName()) return null;
        String name = plain.serialize(m.displayName());
        List<Component> lore = m.lore();
        if (lore == null || lore.isEmpty() || !plain.serialize(lore.get(lore.size() - 1)).equals("Satchel")) return null;
        for (SItem t : SItem.values()) if (t.backpack() && t.displayName.equals(name)) return t;
        return null;
    }

    SItem bagType(ItemStack it) {
        SItem t = typeOf(it);
        return t != null && t.backpack() ? t : null;
    }

    // ------------------------------------------------------------ contents

    /** The id a backpack gets the first time it is opened, so the open menu can find it again. */
    UUID bagId(ItemStack bag) {
        String s = bag.getItemMeta().getPersistentDataContainer().get(bagKey, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    UUID ensureBagId(ItemStack bag) {
        UUID id = bagId(bag);
        if (id != null) return id;
        id = UUID.randomUUID();
        ItemMeta m = bag.getItemMeta();
        m.getPersistentDataContainer().set(bagKey, PersistentDataType.STRING, id.toString());
        bag.setItemMeta(m);
        return id;
    }

    ItemStack[] storage(ItemStack bag, SItem t) {
        return read(bag, storeKey, t.storage());
    }

    ItemStack[] upgrades(ItemStack bag, SItem t) {
        return read(bag, upgKey, t.upgradeSlots);
    }

    private ItemStack[] read(ItemStack bag, NamespacedKey key, int size) {
        ItemStack[] out = new ItemStack[size];
        byte[] data = bag.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (data != null && data.length > 0) {
            try {
                ItemStack[] stored = ItemStack.deserializeItemsFromBytes(data);
                System.arraycopy(stored, 0, out, 0, Math.min(stored.length, size));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("A backpack's contents could not be read: " + e);
            }
        }
        for (int i = 0; i < out.length; i++) if (out[i] != null && out[i].isEmpty()) out[i] = null;
        return out;
    }

    /** Writes the contents into the item (storage and upgrade slots), keeps the lore up to date. */
    void write(ItemStack bag, ItemStack[] store, ItemStack[] upg) {
        ItemMeta m = bag.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        if (isEmpty(store)) pdc.remove(storeKey);
        else pdc.set(storeKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(store));
        if (isEmpty(upg)) {
            pdc.remove(upgKey);
            pdc.remove(upgListKey);
        } else {
            pdc.set(upgKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(upg));
            StringBuilder sb = new StringBuilder();
            for (ItemStack u : upg) {
                SItem t = typeOf(u);
                if (t != null && t.upgrade()) sb.append(sb.length() == 0 ? "" : ",").append(t.id());
            }
            pdc.set(upgListKey, PersistentDataType.STRING, sb.toString());
        }
        bag.setItemMeta(m);
        relore(bag);
    }

    private static boolean isEmpty(ItemStack[] a) {
        for (ItemStack s : a) if (s != null && !s.isEmpty()) return false;
        return true;
    }

    /** Which upgrades are installed (cheap: a text kept in the item, no item decoding). */
    List<SItem> installed(ItemStack bag) {
        List<SItem> out = new ArrayList<>();
        String s = bag.getItemMeta().getPersistentDataContainer().get(upgListKey, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) {
            SItem t = SItem.byId(p);
            if (t != null) out.add(t);
        }
        return out;
    }

    boolean has(ItemStack bag, SItem upgrade) {
        String s = bag.getItemMeta().getPersistentDataContainer().get(upgListKey, PersistentDataType.STRING);
        return s != null && s.contains(upgrade.id());
    }

    /** Puts a stack into the array (merging first, then empty slots); returns what did not fit (null = all fitted). */
    static ItemStack insert(ItemStack[] store, ItemStack stack) {
        ItemStack rest = stack.clone();
        for (int i = 0; i < store.length && !rest.isEmpty(); i++) {
            ItemStack s = store[i];
            if (s == null || !s.isSimilar(rest)) continue;
            int room = s.getMaxStackSize() - s.getAmount();
            if (room <= 0) continue;
            int n = Math.min(room, rest.getAmount());
            s.setAmount(s.getAmount() + n);
            rest.setAmount(rest.getAmount() - n);
        }
        for (int i = 0; i < store.length && !rest.isEmpty(); i++) {
            if (store[i] != null) continue;
            int n = Math.min(rest.getMaxStackSize(), rest.getAmount());
            ItemStack put = rest.clone();
            put.setAmount(n);
            store[i] = put;
            rest.setAmount(rest.getAmount() - n);
        }
        return rest.isEmpty() ? null : rest;
    }

    static boolean contains(ItemStack[] store, ItemStack stack) {
        for (ItemStack s : store) if (s != null && s.isSimilar(stack)) return true;
        return false;
    }

    // ------------------------------------------------------------ lore

    void relore(ItemStack it) {
        SItem t = typeOf(it);
        if (t == null) return;
        List<Component> lore = new ArrayList<>();
        for (String l : t.lore) lore.add(line(l, NamedTextColor.GRAY));
        if (t.backpack()) {
            ItemStack[] st = storage(it, t);
            int used = 0, count = 0;
            for (ItemStack s : st) if (s != null) {
                used++;
                count += s.getAmount();
            }
            lore.add(line(used + " / " + t.storage() + " slots used (" + count + " items)", NamedTextColor.AQUA));
            List<SItem> up = installed(it);
            lore.add(line("Upgrades " + up.size() + " / " + t.upgradeSlots + (up.isEmpty() ? "" : ":"), NamedTextColor.DARK_AQUA));
            for (SItem u : up) lore.add(line(" " + u.displayName.replace(" Upgrade", ""), NamedTextColor.DARK_AQUA));
        }
        ItemMeta m = it.getItemMeta();
        m.lore(lore);
        it.setItemMeta(m);
    }

    static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------ recipes

    void registerRecipes() {
        unregisterRecipes();
        specs.clear();
        shaped(SItem.UPGRADE_BASE, 1, new String[]{"NLN", "LSL", "NLN"}, Map.of('N', Material.IRON_NUGGET, 'L', Material.LEATHER, 'S', Material.STRING));
        // backpacks are normally bought (wandering traders, shop NPC); crafting them is off unless the config says so
        boolean craft = plugin.getConfig().getBoolean("crafting.backpacks", false);
        if (craft) registerBackpackRecipes();
        registerUpgradeRecipes();
    }

    private void registerBackpackRecipes() {
        shaped(SItem.CANVAS_BACKPACK, 1, new String[]{"SLS", "LCL", "LLL"}, Map.of('S', Material.STRING, 'L', Material.LEATHER, 'C', Material.CHEST));
        shaped(SItem.COPPER_BACKPACK, 1, new String[]{"XXX", "XBX", "XXX"}, Map.of('X', Material.COPPER_INGOT, 'B', SItem.CANVAS_BACKPACK));
        shaped(SItem.IRON_BACKPACK, 1, new String[]{"XXX", "XBX", "XXX"}, Map.of('X', Material.IRON_INGOT, 'B', SItem.COPPER_BACKPACK));
        shaped(SItem.GOLD_BACKPACK, 1, new String[]{"XXX", "XBX", "XXX"}, Map.of('X', Material.GOLD_INGOT, 'B', SItem.IRON_BACKPACK));
        shaped(SItem.DIAMOND_BACKPACK, 1, new String[]{" X ", "XBX", " X "}, Map.of('X', Material.DIAMOND, 'B', SItem.GOLD_BACKPACK));
        shaped(SItem.NETHERITE_BACKPACK, 1, new String[]{" X ", "XBX", " X "}, Map.of('X', Material.NETHERITE_INGOT, 'B', SItem.DIAMOND_BACKPACK));
    }

    private void registerUpgradeRecipes() {
        shapeless(SItem.PICKUP_UPGRADE, SItem.UPGRADE_BASE, Material.HOPPER);
        shapeless(SItem.MAGNET_UPGRADE, SItem.UPGRADE_BASE, Material.ENDER_PEARL, Material.IRON_INGOT);
        shapeless(SItem.FEEDING_UPGRADE, SItem.UPGRADE_BASE, Material.GOLDEN_CARROT, Material.BREAD);
        shapeless(SItem.EVERLASTING_UPGRADE, SItem.UPGRADE_BASE, Material.NETHER_STAR, Material.OBSIDIAN);
        shapeless(SItem.DEPOSIT_UPGRADE, SItem.UPGRADE_BASE, Material.CHEST, Material.PISTON);
        shapeless(SItem.RESTOCK_UPGRADE, SItem.UPGRADE_BASE, Material.CHEST, Material.STICKY_PISTON);
        shapeless(SItem.REFILL_UPGRADE, SItem.UPGRADE_BASE, Material.COMPARATOR, Material.HOPPER);
    }

    private NamespacedKey key(String k) {
        return new NamespacedKey(plugin, k);
    }

    private RecipeChoice choice(Object o) {
        return o instanceof Material m ? new RecipeChoice.MaterialChoice(m) : new RecipeChoice.MaterialChoice(Material.PAPER);
    }

    private void shaped(SItem result, int n, String[] shape, Map<Character, Object> ing) {
        Spec spec = new Spec(result.id(), result, n, shape, ing, null);
        specs.add(spec);
        ShapedRecipe r = new ShapedRecipe(key(result.id()), create(result, n));
        r.shape(shape);
        for (Map.Entry<Character, Object> e : ing.entrySet()) r.setIngredient(e.getKey(), choice(e.getValue()));
        add(r);
    }

    private void shapeless(SItem result, Object... ing) {
        List<Object> list = new ArrayList<>(Arrays.asList(ing));
        specs.add(new Spec(result.id(), result, 1, null, null, list));
        ShapelessRecipe r = new ShapelessRecipe(key(result.id()), create(result, 1));
        for (Object o : list) r.addIngredient(choice(o));
        add(r);
    }

    private void add(Recipe r) {
        NamespacedKey k = ((org.bukkit.Keyed) r).getKey();
        Bukkit.removeRecipe(k, false);
        Bukkit.addRecipe(r, false);
        recipeKeys.add(k);
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    /**
     * Our recipes use generic paper slots, so check here that every paper in the grid is the right Satchel item.
     * A backpack upgrade also carries the contents and upgrades of the old backpack into the new one.
     */
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof org.bukkit.Keyed kd)) return;
        NamespacedKey k = kd.getKey();
        if (!k.getNamespace().equals(plugin.getName().toLowerCase())) return;
        Spec spec = null;
        for (Spec s : specs) if (s.id().equals(k.getKey())) spec = s;
        if (spec == null) return;
        List<SItem> need = new ArrayList<>(spec.required());
        ItemStack source = null;
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (it == null || it.getType() != Material.PAPER) continue;
            SItem t = typeOf(it);
            if (t == null || !need.remove(t)) {
                e.getInventory().setResult(null);
                return;
            }
            if (t.backpack()) source = it;
        }
        if (!need.isEmpty()) {
            e.getInventory().setResult(null);
            return;
        }
        if (source != null && spec.result().backpack()) {
            ItemStack out = create(spec.result(), 1);
            write(out, storage(source, spec.result()), upgrades(source, spec.result()));
            e.getInventory().setResult(out);
        }
    }

    // ------------------------------------------------------------ for the recipe viewer

    List<Map<String, Object>> viewerRecipes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Spec s : specs) {
            List<Object> cells = new ArrayList<>();
            if (s.shape() != null) {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        Object o = null;
                        if (r < s.shape().length && c < s.shape()[r].length()) o = s.ing().get(s.shape()[r].charAt(c));
                        cells.add(o == null ? List.of() : cellOf(o));
                    }
                }
            } else {
                for (Object o : s.loose()) cells.add(cellOf(o));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Crafting");
            m.put("icon", new ItemStack(Material.CRAFTING_TABLE));
            m.put("inputs", cells);
            m.put("outputs", List.of(create(s.result(), s.amount())));
            m.put("grid", true);
            m.put("shapeless", s.shape() == null);
            if (s.result().backpack() && s.result().previous() != null) m.put("info", List.of("The old backpack keeps its contents and upgrades."));
            out.add(m);
        }
        return out;
    }

    private Object cellOf(Object o) {
        if (o instanceof Material m) return new ItemStack(m);
        return create((SItem) o, 1);
    }
}
