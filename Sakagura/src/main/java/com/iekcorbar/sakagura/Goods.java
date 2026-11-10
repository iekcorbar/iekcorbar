package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sakagura items: garden produce and seeds, casks, and the brew bottles (built from their Brew state). */
final class Goods implements Listener {
    private final Sakagura plugin;
    final NamespacedKey itemKey, brewKey, woodKey, stillKey;
    final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private static final Map<String, Integer> FOOD = Map.ofEntries(Map.entry("grape", 1), Map.entry("peach", 3), Map.entry("cherry", 2), Map.entry("strawberry", 2),
            Map.entry("blueberry", 2), Map.entry("yuzu", 1), Map.entry("ume", 1), Map.entry("apple", 4), Map.entry("pear", 3),
            Map.entry("apricot", 2), Map.entry("mikan", 2), Map.entry("kiwi", 2),
            // 0.5.0: nieuwe gewassen (pittig/kruidachtige dingen blijven on-eetbaar)
            Map.entry("mango", 3), Map.entry("papaya", 2), Map.entry("guava", 2), Map.entry("mangosteen", 2),
            Map.entry("dragonfruit", 1), Map.entry("rambutan", 1), Map.entry("longan", 1), Map.entry("sudachi", 1));

    Goods(Sakagura plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item");
        brewKey = new NamespacedKey(plugin, "brew");
        woodKey = new NamespacedKey(plugin, "cask");
        stillKey = new NamespacedKey(plugin, "still");
        Ingredients.ours = itemKey;
        Ingredients.brewKey = brewKey;
    }

    // ------------------------------------------------------------ garden items

    ItemStack item(String id, int amount) {
        // Sakagura apples are real vanilla apples under the hood, so they keep every vanilla
        // behaviour (golden apples, brewing, food) while still carrying our model and PDC id.
        Material base = "apple".equals(id) ? Material.APPLE : Material.PAPER;
        ItemStack it = new ItemStack(base, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        Fruit f = Fruit.byId(id);
        Fruit planted = Fruit.byPlantItem(id);
        Tree sapling = Tree.bySapling(id);
        String name = f != null ? f.displayName : sapling != null ? sapling.displayName + " Sapling" : label(id);
        m.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (planted != null) lore.add(Component.text("Plant on farmland.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (sapling != null) lore.add(Component.text("Right-click grass or dirt to plant an orchard tree.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Sakagura", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", id));
        // food spoilage: stamp fruits with a spoilage timer
        if (FOOD.containsKey(id)) plugin.spoilage().stamp(it, id);
        Integer food = FOOD.get(id);
        if (food != null) {
            it.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(food).saturation(food * 0.6f).build());
            it.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(0.8f).build());
        }
        return it;
    }

    static String label(String id) {
        String s = id.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }

    String idOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ casks

    ItemStack cask(Woods w, int amount) {
        ItemStack it = new ItemStack(Material.BARREL, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(w.label() + " Sake Cask", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Ages drinks put inside it, even while you're away.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Some recipes want a certain wood.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sakagura", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(woodKey, PersistentDataType.STRING, w.id());
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "cask_" + w.id()));
        return it;
    }

    Woods caskWood(ItemStack it) {
        if (it == null || it.getType() != Material.BARREL || !it.hasItemMeta()) return null;
        return Woods.byId(it.getItemMeta().getPersistentDataContainer().get(woodKey, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------ steam still

    ItemStack still(int amount) {
        ItemStack it = new ItemStack(Material.BARREL, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Steam Still", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Distills bottles put inside it.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Put bottles + a glowstone dust in it and heat it from below.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sakagura", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(stillKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "still"));
        return it;
    }

    boolean isStillItem(ItemStack it) {
        return it != null && it.getType() == Material.BARREL && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(stillKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ brew bottles

    Brew brewOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(brewKey, PersistentDataType.STRING);
        return s == null ? null : Brew.parse(s);
    }

    /** A bottle showing what this brew is now: finished drink, a drink on its way, or a murky failure. */
    ItemStack bottle(Brew b, int amount) {
        ItemStack it = new ItemStack(Material.POTION, Math.max(1, amount));
        PotionMeta m = (PotionMeta) it.getItemMeta();
        Recipes.Match match = plugin.recipes().match(b);
        List<Component> lore = new ArrayList<>();
        Component name;
        Color color;
        if (match == null || (match.finished() && match.quality() == 0)) {
            name = Component.text("Murky Brew", NamedTextColor.GRAY);
            color = Color.fromRGB(96, 80, 50);
            lore.add(line("<dark_gray>Something went wrong with this one."));
        } else if (match.finished()) {
            Recipe r = match.recipe();
            int q = match.quality();
            TextColor tc = q >= 8 ? NamedTextColor.GOLD : q >= 4 ? NamedTextColor.WHITE : NamedTextColor.GRAY;
            name = Component.text(r.name(q), tc);
            color = r.color;
            lore.add(Component.text(stars(q), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            for (String l : plugin.recipes().lore(r, q)) lore.add(line(l));
            if (r.alcohol > 0) lore.add(line("<gray>" + r.alcohol + "% alcohol"));
            for (PotionEffect e : plugin.recipes().effects(r, q)) m.addCustomEffect(e, true);
        } else {
            Recipe r = match.recipe();
            name = Component.text("Unfinished " + r.name(5), NamedTextColor.GRAY);
            color = r.color.mixColors(Color.fromRGB(120, 110, 90));
            lore.add(line("<dark_gray>Next: " + next(r, b)));
        }
        if (b.cook > 0 || b.mix > 0) lore.add(line("<dark_gray>" + (b.cook >= b.mix ? "Cooked " + mins(b.cook) : "Mixed " + mins(b.mix))));
        if (b.distills > 0) lore.add(line("<dark_gray>Distilled " + b.distills + "x"));
        if (b.years > 0.05) lore.add(line("<dark_gray>Aged " + String.format(Locale.ROOT, "%.1f", b.years) + " years" + (b.wood.isEmpty() ? "" : " (" + b.wood.replace('_', ' ') + ")")));
        if (b.since > 0) lore.add(line("<dark_aqua>Ageing in a cask..."));
        m.customName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.setColor(color);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.getPersistentDataContainer().set(brewKey, PersistentDataType.STRING, b.format());
        it.setItemMeta(m);
        // Per-drink model (if available)
        if (match != null && match.finished() && match.recipe() != null) {
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "drink_" + match.recipe().slug));
        } else if (match != null && match.recipe() != null) {
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "drink_" + match.recipe().slug));
        }
        return it;
    }

    private static String next(Recipe r, Brew b) {
        if (b.seq.length() >= r.sequence.length()) return "?";
        return switch (r.sequence.charAt(b.seq.length())) {
            case 'D' -> "distill it in a Steam Still (" + r.step('D').runs() + "x)";
            case 'A' -> {
                Recipe.Step a = r.step('A');
                yield "age it " + (int) Math.round(a.years()) + " years" + (a.wood().equals("any") ? "" : " in " + a.wood().replace('_', ' '));
            }
            default -> "?";
        };
    }

    private static String mins(double m) {
        return m < 1 ? "under a minute" : Math.round(m) + " min";
    }

    static String stars(int q) {
        int full = q / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < full ? "★" : (i == full && q % 2 == 1) ? "⯪" : "☆");
        return sb.toString();
    }

    private static Component line(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------ recipes (crafting)

    void registerRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
        for (Woods w : Woods.values()) {
            NamespacedKey k = new NamespacedKey(plugin, "cask_" + w.id());
            ShapedRecipe r = new ShapedRecipe(k, cask(w, 1));
            r.shape("PIP", "P P", "PIP");
            r.setIngredient('P', w == Woods.COPPER ? Material.COPPER_INGOT : w.planks);
            r.setIngredient('I', w == Woods.COPPER ? Material.IRON_INGOT : Material.IRON_NUGGET);
            Bukkit.addRecipe(r);
            recipeKeys.add(k);
        }
        // the steam still: a barrel, iron and the heat it runs on
        NamespacedKey sk = new NamespacedKey(plugin, "still");
        ShapelessRecipe sr = new ShapelessRecipe(sk, still(1));
        sr.addIngredient(Material.BARREL);
        sr.addIngredient(Material.IRON_INGOT, 2);
        sr.addIngredient(Material.BLAZE_POWDER);
        sr.addIngredient(Material.GLOWSTONE_DUST);
        Bukkit.addRecipe(sr);
        recipeKeys.add(sk);
        // seeds from the fruit (one fruit -> a few seeds)
        for (Fruit f : Fruit.values()) {
            if (f.seed == null) continue;
            NamespacedKey k = new NamespacedKey(plugin, f.seed);
            ShapelessRecipe r = new ShapelessRecipe(k, item(f.seed, 2));
            r.addIngredient(org.bukkit.inventory.RecipeChoice.exactChoice(item(f.produce, 1)));
            Bukkit.addRecipe(r);
            recipeKeys.add(k);
        }
        // 0.5.0 orchard saplings: fruit + sticks (maple: two red mushrooms - it bears none)
        for (Tree t : Tree.values()) {
            NamespacedKey k = new NamespacedKey(plugin, t.sapling());
            ShapelessRecipe r = new ShapelessRecipe(k, item(t.sapling(), 1));
            if (t.fruit() != null) r.addIngredient(org.bukkit.inventory.RecipeChoice.exactChoice(item(t.fruitId, 1)));
            else {
                r.addIngredient(Material.RED_MUSHROOM);
                r.addIngredient(Material.RED_MUSHROOM);
            }
            r.addIngredient(Material.STICK);
            r.addIngredient(Material.STICK);
            Bukkit.addRecipe(r);
            recipeKeys.add(k);
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipes(recipeKeys);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    // Disable vanilla apples from oak/dark-oak leaf drops so only Sakagura apples remain.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLeafDrop(BlockDropItemEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        org.bukkit.Material t = b.getType();
        if (t != org.bukkit.Material.OAK_LEAVES && t != org.bukkit.Material.DARK_OAK_LEAVES) return;
        e.getItems().removeIf(di -> {
            org.bukkit.inventory.ItemStack it = di.getItemStack();
            if (it.getType() != org.bukkit.Material.APPLE) return false;
            // keep our own tagged apples (they never drop from leaves, but be safe)
            if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, org.bukkit.persistence.PersistentDataType.STRING)) return false;
            return true;
        });
    }

    /** Our paper items only count in our own recipes. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        org.bukkit.inventory.Recipe r = e.getRecipe();
        if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.namespace())) return;
        for (ItemStack i : e.getInventory().getMatrix()) {
            String id = idOf(i);
            // apples are allowed in vanilla recipes (golden apples etc.) even though they carry our PDC
            if (id != null && "apple".equals(id)) continue;
            if (id != null || brewOf(i) != null) e.getInventory().setResult(null);
        }
    }
}
