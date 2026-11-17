package com.iekcorbar.kura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Creates and recognises Kura items, keeps filter settings in them and registers the recipes. */
final class Items {
    private final KuraPlugin plugin;
    private final NamespacedKey idKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    Items(KuraPlugin plugin) {
        this.plugin = plugin;
        idKey = new NamespacedKey(plugin, "item");
    }

    ItemStack create(KItem t, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kura", t.id()));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, t.maxStack);
        if (t == KItem.PAINT_KIT) {
            it.setData(DataComponentTypes.MAX_DAMAGE, 64);
            it.setData(DataComponentTypes.DAMAGE, 0);
        }
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(t.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        relore(it);
        return it;
    }

    KItem typeOf(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return null;
        return KItem.byId(it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------ data kept in items

    private NamespacedKey key(String k) {
        return new NamespacedKey(plugin, k);
    }

    String string(ItemStack it, String k) {
        return it.getItemMeta().getPersistentDataContainer().get(key(k), PersistentDataType.STRING);
    }

    void setString(ItemStack it, String k, String v) {
        ItemMeta m = it.getItemMeta();
        if (v == null) m.getPersistentDataContainer().remove(key(k));
        else m.getPersistentDataContainer().set(key(k), PersistentDataType.STRING, v);
        it.setItemMeta(m);
        relore(it);
    }

    boolean flag(ItemStack it, String k) {
        Byte b = it.getItemMeta().getPersistentDataContainer().get(key(k), PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    void setFlag(ItemStack it, String k, boolean v) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(key(k), PersistentDataType.BYTE, (byte) (v ? 1 : 0));
        it.setItemMeta(m);
        relore(it);
    }

    /** Item Filter / Tag Filter sample items (Tag Filter: one per tag line). */
    List<ItemStack> filterItems(ItemStack it) {
        String s = string(it, "filter_items");
        return s == null || s.isEmpty() ? new ArrayList<>() : Part.decodeList(s);
    }

    void setFilterItems(ItemStack it, List<ItemStack> list) {
        setString(it, "filter_items", list.isEmpty() ? null : Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(list)));
    }

    List<String> filterTags(ItemStack it) {
        String s = string(it, "filter_tags");
        List<String> out = new ArrayList<>();
        if (s != null) for (String t : s.split(",")) if (!t.isBlank()) out.add(t);
        return out;
    }

    void setFilterTags(ItemStack it, List<String> tags) {
        setString(it, "filter_tags", tags.isEmpty() ? null : String.join(",", tags));
    }

    /** Lore = the item's description plus what is set in it. */
    void relore(ItemStack it) {
        KItem t = typeOf(it);
        if (t == null) return;
        List<Component> lore = new ArrayList<>();
        for (String l : t.lore) {
            l = l.replace("%range%", String.valueOf(plugin.getConfig().getInt("wireless.range", 16)))
                    .replace("%adv%", String.valueOf(plugin.getConfig().getInt("wireless.advanced-range", 64)));
            lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        switch (t) {
            case ITEM_FILTER -> {
                List<ItemStack> items = filterItems(it);
                lore.add(line((flag(it, "filter_deny") ? "Blocks" : "Allows") + " " + items.size() + " item" + (items.size() == 1 ? "" : "s")
                        + (flag(it, "filter_nbt") ? " (exact match)" : ""), NamedTextColor.AQUA));
            }
            case TAG_FILTER -> {
                List<String> tags = filterTags(it);
                lore.add(line((flag(it, "filter_deny") ? "Blocks: " : "Allows: ") + (tags.isEmpty() ? "nothing yet" : ""), NamedTextColor.AQUA));
                for (String tag : tags) lore.add(line(" #" + tag.replace("minecraft:", ""), NamedTextColor.DARK_AQUA));
            }
            case ADV_WIRELESS_TERMINAL -> {
                String b = string(it, "bound");
                if (b != null) {
                    String[] f = b.split(",");
                    lore.add(line("Bound to " + f[1] + ", " + f[2] + ", " + f[3] + " in " + f[0], NamedTextColor.AQUA));
                }
            }
            case PAINT_KIT -> {
                String paint = string(it, "paint");
                if (paint != null) lore.add(line("Paint: " + pretty(paint), NamedTextColor.AQUA));
            }
            case FILING_CABINET -> {
                String s = string(it, "contents");
                if (s != null) {
                    int n = 0;
                    for (ItemStack c : Part.decodeList(s)) n += c.getAmount();
                    lore.add(line("Contains " + n + " items", NamedTextColor.AQUA));
                }
            }
            default -> { }
        }
        ItemMeta m = it.getItemMeta();
        m.lore(lore);
        it.setItemMeta(m);
    }

    static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    static String pretty(String materialName) {
        String s = materialName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------ recipes

    void registerRecipes() {
        unregisterRecipes();
        // 0.1.11 "cables are enough": the Inventory Connector, Inventory Trim and Cable Connector lost
        // their recipes - a cable now joins whatever it touches, so one part does what those three did.
        // The items themselves stay in the plugin: parts already placed on the server still load, look
        // right and carry the network (see PartType.wire()).
        shaped(KItem.STORAGE_TERMINAL, 1, new String[]{"CGC", "RQR", "CCC"},
                Map.of('C', Material.COPPER_INGOT, 'G', Material.GLASS_PANE, 'R', Material.REDSTONE, 'Q', Material.COMPARATOR));
        shapeless(KItem.CRAFTING_TERMINAL, 1, KItem.STORAGE_TERMINAL, Material.CRAFTING_TABLE, Material.GOLD_INGOT);
        shaped(KItem.INVENTORY_CABLE, 6, new String[]{"CRC"}, Map.of('C', Material.COPPER_INGOT, 'R', Material.REDSTONE));
        shapeless(KItem.FRAMED_CABLE, 1, KItem.INVENTORY_CABLE, Material.STICK, Material.STICK);
        shapeless(KItem.INVENTORY_HOPPER, 1, Material.HOPPER, Material.COPPER_INGOT, Material.REDSTONE);
        shapeless(KItem.LEVEL_EMITTER, 1, Material.COMPARATOR, Material.REDSTONE_TORCH, Material.COPPER_INGOT);
        shapeless(KItem.INVENTORY_INTERFACE, 1, Material.BARREL, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.REDSTONE);
        shapeless(KItem.OPEN_CRATE, 1, Material.BARREL, Material.IRON_BARS);
        shaped(KItem.FILING_CABINET, 1, new String[]{"IPI", "PCP", "IPI"}, Map.of('I', Material.IRON_INGOT, 'P', Material.PAPER, 'C', Material.CHEST));
        shapeless(KItem.WIRELESS_TERMINAL, 1, KItem.STORAGE_TERMINAL, Material.ENDER_PEARL, Material.GOLD_INGOT, Material.REDSTONE);
        shapeless(KItem.ADV_WIRELESS_TERMINAL, 1, KItem.WIRELESS_TERMINAL, Material.ENDER_EYE, Material.DIAMOND, Material.GOLD_INGOT);
        shapeless(KItem.ITEM_FILTER, 2, Material.PAPER, Material.PAPER, Material.COPPER_INGOT);
        shapeless(KItem.TAG_FILTER, 1, KItem.ITEM_FILTER, Material.BOOK);
        shapeless(KItem.POLY_FILTER, 1, KItem.ITEM_FILTER, Material.CHEST);
        shapeless(KItem.CONFIGURATOR, 1, Material.STICK, Material.COPPER_INGOT, Material.REDSTONE, Material.GLASS_PANE);
        shapeless(KItem.PAINT_KIT, 1, Material.BRUSH, Material.RED_DYE, Material.GREEN_DYE, Material.BLUE_DYE);
    }

    private void shaped(KItem result, int n, String[] shape, Map<Character, Material> ing) {
        ShapedRecipe r = new ShapedRecipe(key(result.id()), create(result, n));
        r.shape(shape);
        for (Map.Entry<Character, Material> e : ing.entrySet()) r.setIngredient(e.getKey(), e.getValue());
        add(r);
    }

    /** Ingredients: Material or KItem (matched exactly). */
    private void shapeless(KItem result, int n, Object... ing) {
        ShapelessRecipe r = new ShapelessRecipe(key(result.id()), create(result, n));
        for (Object o : ing) {
            if (o instanceof Material m) r.addIngredient(m);
            else if (o instanceof KItem k) r.addIngredient(new RecipeChoice.ExactChoice(create(k, 1)));
        }
        add(r);
    }

    private void add(org.bukkit.inventory.Recipe r) {
        NamespacedKey k = ((org.bukkit.Keyed) r).getKey();
        Bukkit.removeRecipe(k, false);
        Bukkit.addRecipe(r);
        recipeKeys.add(k);
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
    }

    List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }
}
