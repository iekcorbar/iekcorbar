package com.iekcorbar.kitchen;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Creates Kitchen items, registers their crafting recipes and applies food effects. */
final class Items implements Listener {
    private final KitchenPlugin plugin;
    final NamespacedKey itemKey;
    /** Steamworks' material tag (its flour can replace wheat in noodles) - read without depending on Steamworks. */
    private final NamespacedKey steamworksMaterial = new NamespacedKey("steamworks", "material");
    final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<NamespacedKey, Predicate<ItemStack[]>> extraChecks = new HashMap<>();

    Items(KitchenPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "item");
    }

    // ------------------------------------------------------------ items

    ItemStack create(KItem t, int amount) {
        ItemStack item = new ItemStack(base(t), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, t.kind == KItem.Kind.MEAL ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : t.lore) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Kitchen", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, t.id());
        if (t.knife()) {
            double damage = switch (t) {
                case FLINT_KNIFE -> 3.5;
                case IRON_KNIFE -> 4.5;
                case DIAMOND_KNIFE -> 5.5;
                default -> 6.5;
            };
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey(plugin, "knife_damage"),
                    damage - 1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(new NamespacedKey(plugin, "knife_speed"),
                    -2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        item.setItemMeta(meta);
        plugin.spoilage().stamp(item);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("kitchen", t.id()));
        if (!t.knife()) item.setData(DataComponentTypes.MAX_STACK_SIZE, t.maxStack);
        if (t.edible()) {
            boolean drink = t.kind == KItem.Kind.DRINK;
            item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(t.nutrition).saturation(t.saturation)
                    .canAlwaysEat(drink).build());
            item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .consumeSeconds(t.remainder == KItem.Rem.BOWL ? 2.0f : drink ? 1.6f : t.nutrition <= 2 ? 0.9f : 1.6f)
                    .animation(drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                    .sound(Key.key(drink ? "entity.generic.drink" : "entity.generic.eat"))
                    .hasConsumeParticles(!drink).build());
            if (t.remainder == KItem.Rem.BOWL) item.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(new ItemStack(Material.BOWL)));
            if (t.remainder == KItem.Rem.BOTTLE) item.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(new ItemStack(Material.GLASS_BOTTLE)));
        }
        return item;
    }

    private static Material base(KItem t) {
        return switch (t) {
            case FLINT_KNIFE -> Material.STONE_SWORD;
            case IRON_KNIFE -> Material.IRON_SWORD;
            case DIAMOND_KNIFE -> Material.DIAMOND_SWORD;
            case NETHERITE_KNIFE -> Material.NETHERITE_SWORD;
            default -> Material.PAPER;
        };
    }

    KItem typeOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        return KItem.byId(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    boolean is(ItemStack item, KItem t) {
        return typeOf(item) == t;
    }

    /** Steamworks material id of an item (e.g. "flour"), or null. */
    String steamworksMaterial(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(steamworksMaterial, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ eating

    private final java.util.Map<java.util.UUID, java.util.ArrayDeque<Long>> feesten = new java.util.HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onPipeUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        KItem t = typeOf(e.getItem());
        if (t != KItem.HASH_PIPE) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        // find hash in inventory
        ItemStack hash = null;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && typeOf(i) == KItem.HASH) { hash = i; break; }
        }
        if (hash == null) {
            p.sendActionBar(Component.text("No hash to smoke.", NamedTextColor.GRAY));
            return;
        }
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hash.setAmount(hash.getAmount() - 1);
        applyEffects(p, KItem.HASH);
        p.getWorld().playSound(p.getLocation(), "entity.generic.drink", 0.6f, 0.8f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        KItem t = typeOf(e.getItem());
        if (t != null) applyEffects(e.getPlayer(), t);
        // joke: ten meals in two minutes — you are not a furnace
        Player p = e.getPlayer();
        boolean fed = (t != null && (t.kind == KItem.Kind.FOOD || t.kind == KItem.Kind.DRINK))
                || e.getItem().getType().name().contains("COOKED")
                || e.getItem().getType().name().contains("BAKED");
        if (!fed) return;
        long now = System.currentTimeMillis();
        java.util.ArrayDeque<Long> q = feesten.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 120_000) q.pollFirst();
        if (q.size() < 10) return;
        q.clear();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 0));
        p.sendActionBar(Component.text("You are not a furnace.", NamedTextColor.GOLD));
        plugin.adv().grant(p, "furnace");
    }

    /** Extra effects of a dish (also used by meals eaten bite by bite). */
    void applyEffects(Player p, KItem t) {
        switch (t) {
            case MISO_SOUP, ODEN, NABE -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0));
            case RAMEN, GYUDON, OYAKODON -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 0));
            }
            case GREEN_TEA -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 0));
                p.removePotionEffect(PotionEffectType.POISON);
                p.removePotionEffect(PotionEffectType.HUNGER);
                p.removePotionEffect(PotionEffectType.NAUSEA);
            }
            case FUGU_SASHIMI -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                    p.sendActionBar(Component.text("That fugu was not cut right...", NamedTextColor.DARK_GREEN));
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 600, 0));
                }
            }
            case JOINT -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0));
                p.sendActionBar(Component.text("You feel relaxed...", NamedTextColor.GREEN));
            }
            case HASH -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 800, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
                p.sendActionBar(Component.text("Strong stuff...", NamedTextColor.DARK_GREEN));
            }
            default -> { }
        }
        // Sakagura sake-pairing: if the player is drunk, food gives a bonus
        try {
            org.bukkit.plugin.Plugin sak = plugin.getServer().getPluginManager().getPlugin("Sakagura");
            if (sak != null && sak.isEnabled()) {
                org.bukkit.NamespacedKey drunkKey = new org.bukkit.NamespacedKey(sak, "drunk");
                Integer drunkLevel = p.getPersistentDataContainer().get(drunkKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                if (drunkLevel != null && drunkLevel >= 30) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 120, 1));
                    p.sendActionBar(Component.text("Sake pairing! The food tastes even better.", NamedTextColor.GOLD));
                    plugin.adv().grant(p, "sake_pairing");
                }
            }
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ recipes

    private RecipeChoice of(KItem... types) {
        List<ItemStack> list = new ArrayList<>();
        for (KItem t : types) list.add(create(t, 1));
        return RecipeChoice.exactChoice(list);
    }

    private NamespacedKey key(String id) {
        NamespacedKey k = new NamespacedKey(plugin, id);
        Bukkit.removeRecipe(k, false);
        recipeKeys.add(k);
        return k;
    }

    /** {shape..., char, ingredient, char, ingredient...}; ingredient = Material, Tag, KItem or RecipeChoice. */
    private void shaped(String id, ItemStack result, String[] shape, Object... ing) {
        ShapedRecipe r = new ShapedRecipe(key(id), result);
        r.shape(shape);
        for (int i = 0; i < ing.length; i += 2) r.setIngredient((char) ing[i], choice(ing[i + 1]));
        Bukkit.addRecipe(r);
    }

    private void shapeless(String id, ItemStack result, Object... ing) {
        ShapelessRecipe r = new ShapelessRecipe(key(id), result);
        for (Object o : ing) r.addIngredient(choice(o));
        Bukkit.addRecipe(r);
    }

    @SuppressWarnings("unchecked")
    private RecipeChoice choice(Object o) {
        if (o instanceof RecipeChoice c) return c;
        if (o instanceof KItem k) return of(k);
        if (o instanceof Material m) return new RecipeChoice.MaterialChoice(m);
        if (o instanceof Tag<?> t) return new RecipeChoice.MaterialChoice((Tag<Material>) t);
        throw new IllegalArgumentException("bad ingredient " + o);
    }

    void registerRecipes() {
        unregisterRecipes();
        // tools
        shaped("flint_knife", create(KItem.FLINT_KNIFE, 1), new String[]{" F", "S "}, 'F', Material.FLINT, 'S', Material.STICK);
        shaped("iron_knife", create(KItem.IRON_KNIFE, 1), new String[]{" I", "S "}, 'I', Material.IRON_INGOT, 'S', Material.STICK);
        shaped("diamond_knife", create(KItem.DIAMOND_KNIFE, 1), new String[]{" D", "S "}, 'D', Material.DIAMOND, 'S', Material.STICK);
        SmithingTransformRecipe nk = new SmithingTransformRecipe(key("netherite_knife"), create(KItem.NETHERITE_KNIFE, 1),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), of(KItem.DIAMOND_KNIFE),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT), false);
        Bukkit.addRecipe(nk);
        shaped("plate", create(KItem.PLATE, 2), new String[]{"CCC"}, 'C', Material.CLAY_BALL);

        // stations + props
        shaped("cutting_board", create(KItem.CUTTING_BOARD, 1), new String[]{"SPP"}, 'S', Material.STICK, 'P', Tag.PLANKS);
        shaped("cooking_pot", create(KItem.COOKING_POT, 1), new String[]{"ISI", "B B", "BBB"},
                'I', Material.IRON_INGOT, 'S', Material.STICK, 'B', Material.BRICK);
        shaped("kamado_stove", create(KItem.KAMADO_STOVE, 1), new String[]{"BBB", "BFB", "CCC"},
                'B', Material.BRICK, 'F', Material.CAMPFIRE, 'C', Material.COBBLESTONE);
        shaped("tansu_cabinet", create(KItem.TANSU_CABINET, 1), new String[]{"SSS", "T T", "SSS"},
                'S', Tag.WOODEN_SLABS, 'T', Tag.WOODEN_TRAPDOORS);
        shaped("bamboo_basket", create(KItem.BAMBOO_BASKET, 1), new String[]{"B B", "B B", "BBB"}, 'B', Material.BAMBOO);
        crate("rice_sack", KItem.RICE_SACK, of(KItem.RICE), create(KItem.RICE, 9));
        crate("soybean_crate", KItem.SOYBEAN_CRATE, of(KItem.SOYBEANS), create(KItem.SOYBEANS, 9));
        crate("napa_cabbage_crate", KItem.NAPA_CABBAGE_CRATE, of(KItem.NAPA_CABBAGE), create(KItem.NAPA_CABBAGE, 9));
        crate("daikon_crate", KItem.DAIKON_CRATE, of(KItem.DAIKON), create(KItem.DAIKON, 9));
        crate("green_onion_crate", KItem.GREEN_ONION_CRATE, of(KItem.GREEN_ONION), create(KItem.GREEN_ONION, 9));
        crate("tea_crate", KItem.TEA_CRATE, of(KItem.TEA_LEAVES), create(KItem.TEA_LEAVES, 9));
        crate("carrot_crate", KItem.CARROT_CRATE, new RecipeChoice.MaterialChoice(Material.CARROT), new ItemStack(Material.CARROT, 9));
        crate("potato_crate", KItem.POTATO_CRATE, new RecipeChoice.MaterialChoice(Material.POTATO), new ItemStack(Material.POTATO, 9));
        crate("beetroot_crate", KItem.BEETROOT_CRATE, new RecipeChoice.MaterialChoice(Material.BEETROOT), new ItemStack(Material.BEETROOT, 9));
        crate("straw_bale", KItem.STRAW_BALE, of(KItem.STRAW), create(KItem.STRAW, 9));
        shapeless("organic_compost", create(KItem.ORGANIC_COMPOST, 1), Material.DIRT, KItem.STRAW, KItem.STRAW,
                KItem.TREE_BARK, KItem.TREE_BARK, Material.BONE_MEAL);
        shaped("tatami_mat", create(KItem.TATAMI_MAT, 2), new String[]{"SSS"}, 'S', KItem.STRAW);
        shaped("zabuton", create(KItem.ZABUTON, 1), new String[]{"W", "S"}, 'W', Tag.WOOL, 'S', KItem.STRAW);

        // food assembled by hand
        shapeless("raw_noodles", create(KItem.RAW_NOODLES, 2), Material.WHEAT, Material.WHEAT, Material.EGG);
        NamespacedKey flourNoodles = new NamespacedKey(plugin, "raw_noodles_flour");
        shapeless("raw_noodles_flour", create(KItem.RAW_NOODLES, 3), Material.PAPER, Material.PAPER, Material.EGG);
        extraChecks.put(flourNoodles, m -> {
            for (ItemStack i : m) if (i != null && i.getType() == Material.PAPER && !"flour".equals(steamworksMaterial(i))) return false;
            return true;
        });
        shapeless("onigiri", create(KItem.ONIGIRI, 2), KItem.STEAMED_RICE, KItem.STEAMED_RICE, KItem.NORI);
        shapeless("salmon_nigiri", create(KItem.SALMON_NIGIRI, 2), KItem.STEAMED_RICE, KItem.SALMON_SASHIMI);
        shapeless("tuna_nigiri", create(KItem.TUNA_NIGIRI, 2), KItem.STEAMED_RICE, KItem.TUNA_SASHIMI);
        shapeless("maki_roll", create(KItem.MAKI_ROLL, 3), KItem.NORI, KItem.STEAMED_RICE,
                of(KItem.COD_SLICE, KItem.DAIKON_SLICES, KItem.TUNA_SASHIMI, KItem.SALMON_SASHIMI));
        shapeless("dango", create(KItem.DANGO, 1), KItem.MOCHI, KItem.MOCHI, KItem.MOCHI, Material.STICK);
        shapeless("sushi_platter", create(KItem.SUSHI_PLATTER, 1), KItem.PLATE, KItem.SALMON_NIGIRI, KItem.SALMON_NIGIRI,
                KItem.TUNA_NIGIRI, KItem.TUNA_NIGIRI, KItem.MAKI_ROLL, KItem.MAKI_ROLL);
        shapeless("dango_plate", create(KItem.DANGO_PLATE, 1), KItem.PLATE, KItem.DANGO, KItem.DANGO, KItem.DANGO);
        shapeless("bento_box", create(KItem.BENTO_BOX, 1), Tag.WOODEN_SLABS, KItem.STEAMED_RICE, KItem.TAMAGOYAKI,
                KItem.TEMPURA, KItem.TAKUAN, KItem.SALMON_SASHIMI);
        shapeless("tea_set", create(KItem.TEA_SET, 1), KItem.PLATE, KItem.GREEN_TEA, KItem.GREEN_TEA, KItem.GREEN_TEA, KItem.GREEN_TEA);
        // cannabis & hemp
        shapeless("hemp_fiber", create(KItem.HEMP_FIBER, 2), KItem.HEMP_LEAVES, KItem.HEMP_LEAVES, KItem.HEMP_LEAVES, KItem.HEMP_LEAVES);
        shapeless("hemp_string", create(KItem.HEMP_STRING, 1), KItem.HEMP_FIBER, KItem.HEMP_FIBER);
        shapeless("hemp_rope", create(KItem.HEMP_ROPE, 1), KItem.HEMP_STRING, KItem.HEMP_STRING, KItem.HEMP_STRING);
        shapeless("hemp_cloth", create(KItem.HEMP_CLOTH, 1), KItem.HEMP_FIBER, KItem.HEMP_FIBER, KItem.HEMP_FIBER, KItem.HEMP_FIBER);
        shapeless("dried_cannabis", create(KItem.DRIED_CANNABIS, 1), KItem.CANNABIS_BUDS);
        shapeless("hash", create(KItem.HASH, 1), KItem.DRIED_CANNABIS, KItem.DRIED_CANNABIS, KItem.DRIED_CANNABIS, KItem.DRIED_CANNABIS);
        shapeless("joint", create(KItem.JOINT, 1), Material.PAPER, KItem.DRIED_CANNABIS);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipes(recipeKeys);
    }

    private void crate(String id, KItem crate, RecipeChoice content, ItemStack nine) {
        RecipeChoice[] in = new RecipeChoice[9];
        java.util.Arrays.fill(in, content);
        ShapelessRecipe r = new ShapelessRecipe(key(id), create(crate, 1));
        for (RecipeChoice c : in) r.addIngredient(c);
        Bukkit.addRecipe(r);
        shapeless(id + "_unpack", nine, crate);
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
        extraChecks.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    /** Kitchen items only go into Kitchen recipes; recipes with an extra rule (flour) are checked here. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null) return;
        NamespacedKey key = r instanceof org.bukkit.Keyed k ? k.getKey() : null;
        ItemStack[] matrix = e.getInventory().getMatrix();
        if (key != null && key.getNamespace().equals(plugin.namespace())) {
            Predicate<ItemStack[]> check = extraChecks.get(key);
            if (check != null && !check.test(matrix)) e.getInventory().setResult(null);
            return;
        }
        for (ItemStack i : matrix) {
            if (typeOf(i) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmith(PrepareSmithingEvent e) {
        ItemStack base = e.getInventory().getInputEquipment();
        KItem t = typeOf(base);
        // only our own knife upgrade (vanilla's diamond->netherite sword recipe would also take the knife)
        if (t != null && typeOf(e.getResult()) != KItem.NETHERITE_KNIFE) e.setResult(null);
    }
}
