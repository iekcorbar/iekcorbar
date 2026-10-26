package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lets FreeMinecraftModels props be placed in every direction (props.yml).
 *
 * FMM itself always places a prop upright on the clicked face, only turned
 * towards the player. A "family" here is one item (the base model, the one
 * with the crafting recipe) with a model variant per mounting: floor, wall,
 * ceiling, each optionally with a different variant while sneaking (e.g. lying
 * flat). This listener picks the variant from the clicked face and spawns it
 * through FMM's own PropEntity.spawnPropEntity, called by reflection so the
 * plugin doesn't need FMM to compile or run.
 *
 * To stop FMM's own placement listener from also placing the base model, the
 * hand item's freeminecraftmodels:model_id tag is taken off at LOWEST and put
 * back at MONITOR (FMM's listener runs at LOWEST too, after ours because
 * plugin.yml has loadbefore: FreeMinecraftModels). Picking a variant up again
 * (FMM drops an item of the variant model) is turned back into the base item.
 */
public class PropPlacementManager implements Listener {

    private static final NamespacedKey FMM_MODEL_ID = NamespacedKey.fromString("freeminecraftmodels:model_id");

    record Family(String base, String floor, String floorSneak, String wall, String wallSneak,
                  String ceiling, String ceilingSneak, float yawSnap, float yawOffset, float wallYawOffset,
                  int light, PropRecipe recipe) {}

    /** Our own crafting recipe for a family's base item (props.yml "recipe:"), e.g. 8 pipes per craft. */
    record PropRecipe(List<String> shape, Map<Character, Material> ingredients, int amount) {}

    private final IekcorbarSiteSync plugin;
    private final File file;
    private final Map<String, Family> byModel = new HashMap<>(); // base AND variant ids -> family
    private final Map<String, List<String>> wrenchCycles = new HashMap<>();
    private final List<String> bases = new ArrayList<>(); // family base ids, in props.yml order (/props menu)
    private final Map<UUID, String> strippedTag = new HashMap<>();
    private final Map<NamespacedKey, Family> recipeKeys = new HashMap<>();
    private final PropLights lights;
    private Method spawnProp, createItem;
    private boolean enabled = true;
    private boolean started;

    public PropPlacementManager(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "props.yml");
        this.lights = new PropLights(plugin);
    }

    public void enable() {
        if (!file.exists()) plugin.saveResource("props.yml", false);
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        lights.enable(id -> {
            Family f = id == null ? null : byModel.get(id.toLowerCase(Locale.ROOT));
            return f != null && f.light > 0;
        });
        // FreeMinecraftModels has to be enabled (and its models loaded) before its items can be made.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            started = true;
            registerRecipes();
        }, 40L);
    }

    public String reload() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("enabled", true);
        byModel.clear();
        wrenchCycles.clear();
        bases.clear();
        ConfigurationSection fams = cfg.getConfigurationSection("families");
        if (fams != null) {
            for (String base : fams.getKeys(false)) {
                ConfigurationSection f = fams.getConfigurationSection(base);
                if (f == null) continue;
                bases.add(base);
                String floor = f.getString("floor", base);
                String wall = f.getString("wall", floor);
                String ceiling = f.getString("ceiling", floor);
                Family family = new Family(base, floor, f.getString("floor-sneak", floor),
                        wall, f.getString("wall-sneak", wall), ceiling, f.getString("ceiling-sneak", ceiling),
                        (float) f.getDouble("yaw-snap", 22.5), (float) f.getDouble("yaw-offset", 0),
                        (float) f.getDouble("wall-yaw-offset", 0), f.getInt("light", 0),
                        readRecipe(base, f.getConfigurationSection("recipe")));
                for (String id : new String[]{base, family.floor, family.floorSneak, family.wall,
                        family.wallSneak, family.ceiling, family.ceilingSneak}) {
                    byModel.put(id.toLowerCase(Locale.ROOT), family);
                }
                // Steam wrench: each list is one tilt cycle (right-click = next, sneak = previous).
                for (Object raw : f.getList("wrench-cycles", List.of())) {
                    if (!(raw instanceof List<?> ids) || ids.size() < 2) continue;
                    List<String> cycle = ids.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).toList();
                    for (String id : cycle) {
                        wrenchCycles.put(id, cycle);
                        byModel.putIfAbsent(id, family);
                    }
                }
            }
        }
        if (started) registerRecipes();
        String msg = "Loaded " + (fams == null ? 0 : fams.getKeys(false).size()) + " prop family(ies).";
        plugin.getLogger().info("[Props] " + msg);
        return msg;
    }

    private PropRecipe readRecipe(String base, ConfigurationSection r) {
        if (r == null) return null;
        List<String> shape = r.getStringList("shape");
        Map<Character, Material> ingredients = new HashMap<>();
        ConfigurationSection ing = r.getConfigurationSection("ingredients");
        if (ing != null) {
            for (String k : ing.getKeys(false)) {
                Material mat = Material.matchMaterial(ing.getString(k, ""));
                if (k.length() != 1 || mat == null) {
                    plugin.getLogger().warning("[Props] " + base + ": bad recipe ingredient " + k + ": " + ing.getString(k));
                    return null;
                }
                ingredients.put(k.charAt(0), mat);
            }
        }
        if (shape.isEmpty() || shape.size() > 3 || ingredients.isEmpty()) {
            plugin.getLogger().warning("[Props] " + base + ": recipe needs 1-3 shape rows and ingredients.");
            return null;
        }
        return new PropRecipe(shape, ingredients, Math.max(1, Math.min(64, r.getInt("amount", 1))));
    }

    // -------------------------------------------------------------- recipes

    private void registerRecipes() {
        for (NamespacedKey key : recipeKeys.keySet()) Bukkit.removeRecipe(key);
        recipeKeys.clear();
        if (!resolveFmm()) return;
        for (String base : bases) {
            Family family = byModel.get(base.toLowerCase(Locale.ROOT));
            if (family == null || family.recipe == null) continue;
            ItemStack result = baseItem(base, family.recipe.amount);
            if (result == null) continue;
            NamespacedKey key = new NamespacedKey(plugin, "prop_" + base.toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            try {
                recipe.shape(family.recipe.shape.toArray(new String[0]));
                family.recipe.ingredients.forEach((c, mat) -> {
                    if (family.recipe.shape.stream().anyMatch(row -> row.indexOf(c) >= 0)) recipe.setIngredient(c, mat);
                });
                Bukkit.addRecipe(recipe);
                recipeKeys.put(key, family);
            } catch (IllegalArgumentException | IllegalStateException e) {
                plugin.getLogger().warning("[Props] Bad recipe for " + base + ": " + e.getMessage());
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipes(recipeKeys.keySet());
        plugin.getLogger().info("[Props] Registered " + recipeKeys.size() + " prop recipe(s).");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!recipeKeys.isEmpty()) event.getPlayer().discoverRecipes(recipeKeys.keySet());
    }

    // Always hand out a freshly made item, so it gets its model look even if FMM
    // wasn't fully loaded yet when the recipe was registered.
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof ShapedRecipe shaped)) return;
        Family family = recipeKeys.get(shaped.getKey());
        if (family == null) return;
        ItemStack fresh = baseItem(family.base, family.recipe.amount);
        if (fresh != null) event.getInventory().setResult(fresh);
    }

    /** Loads a FreeMinecraftModels class through FMM's own class loader (no "not a depend" warning). */
    static Class<?> fmmClass(String name) throws ClassNotFoundException {
        org.bukkit.plugin.Plugin fmm = org.bukkit.Bukkit.getPluginManager().getPlugin("FreeMinecraftModels");
        if (fmm == null) throw new ClassNotFoundException("FreeMinecraftModels is not installed");
        return Class.forName(name, true, fmm.getClass().getClassLoader());
    }

    private boolean resolveFmm() {
        if (spawnProp != null) return true;
        try {
            spawnProp = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.customentity.PropEntity")
                    .getMethod("spawnPropEntity", String.class, Location.class);
            createItem = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.utils.ModelItemFactory")
                    .getMethod("createModelItem", String.class, Material.class);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Props] FreeMinecraftModels not usable: " + e);
            return false;
        }
    }

    /** The next (or previous) model in this model's wrench tilt cycle, or null if it has none. */
    String nextTilt(String modelId, boolean backwards) {
        List<String> cycle = modelId == null ? null : wrenchCycles.get(modelId.toLowerCase(Locale.ROOT));
        if (cycle == null) return null;
        int i = cycle.indexOf(modelId.toLowerCase(Locale.ROOT));
        return cycle.get(Math.floorMod(i + (backwards ? -1 : 1), cycle.size()));
    }

    /** Spawns a prop through FMM; null if FMM refused (e.g. already one on that block) or isn't there. */
    Object spawn(String modelId, Location loc) {
        if (!resolveFmm()) return null;
        try {
            return spawnProp.invoke(null, modelId, loc);
        } catch (Exception e) {
            plugin.getLogger().warning("[Props] Could not spawn '" + modelId + "': " + e);
            return null;
        }
    }

    /** Asks claim/protection plugins whether this player may build at that block. */
    boolean mayBuild(Player player, Block target, Block against, ItemStack item) {
        BlockPlaceEvent probe = new BlockPlaceEvent(target, target.getState(), against, item, player, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(probe);
        return !probe.isCancelled() && probe.canBuild();
    }

    private static String modelIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(FMM_MODEL_ID, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------- placing

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(PlayerInteractEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = event.getItem();
        String modelId = modelIdOf(item);
        Family family = modelId == null ? null : byModel.get(modelId.toLowerCase(Locale.ROOT));
        if (family == null || !resolveFmm()) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (event.getAction() == Action.RIGHT_CLICK_AIR) { // aimed a bit too far for a block click, like FMM
            RayTraceResult hit = player.rayTraceBlocks(5.0);
            clicked = hit == null ? null : hit.getHitBlock();
            face = hit == null ? null : hit.getHitBlockFace();
        }
        // Hide the tag from FMM's own listener for the rest of this event (restored at MONITOR).
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(FMM_MODEL_ID);
        item.setItemMeta(meta);
        strippedTag.put(player.getUniqueId(), family.base);
        event.setCancelled(true);
        if (clicked == null || face == null) return;

        boolean sneak = player.isSneaking();
        String variant;
        Location loc = clicked.getLocation();
        float yaw;
        switch (face) {
            case UP -> {
                variant = sneak ? family.floorSneak : family.floor;
                loc.add(0.5, 1.0, 0.5);
                yaw = normalize(yawTowards(player, loc, family.yawSnap) + family.yawOffset);
            }
            case DOWN -> {
                variant = sneak ? family.ceilingSneak : family.ceiling;
                loc.add(0.5, 0.0, 0.5);
                yaw = normalize(yawTowards(player, loc, family.yawSnap) + family.yawOffset);
            }
            default -> {
                variant = sneak ? family.wallSneak : family.wall;
                loc.add(0.5 + face.getModX() * 0.5, 0.5, 0.5 + face.getModZ() * 0.5);
                // Facing straight out of the wall (same yaw formula FMM uses for "towards the player").
                yaw = normalize((float) Math.toDegrees(Math.atan2(-face.getModX(), face.getModZ())) + family.wallYawOffset);
            }
        }
        loc.setYaw(yaw);
        loc.setPitch(0);

        // Let claim/protection plugins decide, as if a block were placed in front of the clicked face.
        if (!mayBuild(player, clicked.getRelative(face), clicked, item)) {
            player.sendActionBar(AdvancementManager.text("&cYou can't place that here."));
            return;
        }
        if (spawn(variant, loc) == null) {
            player.sendActionBar(AdvancementManager.text("&cCan't place it here (already one on this block?)."));
            return;
        }
        if (family.light > 0) lights.place(clicked.getRelative(face), family.light);
        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceDone(PlayerInteractEvent event) {
        String base = strippedTag.remove(event.getPlayer().getUniqueId());
        if (base == null) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) return;
        // The tag goes back as the BASE id, so variant items in hand become the base item.
        ItemStack fresh = baseItem(base, item.getAmount());
        if (fresh != null) {
            event.getPlayer().getInventory().setItemInMainHand(fresh);
        } else {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(FMM_MODEL_ID, PersistentDataType.STRING, base);
            item.setItemMeta(meta);
        }
    }

    // ------------------------------------------------------------ picking up

    // FMM's pickup drops an item of the placed model - make it the family's base item again.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        String modelId = modelIdOf(entity.getItemStack());
        if (modelId == null) return;
        Family family = byModel.get(modelId.toLowerCase(Locale.ROOT));
        if (family == null) return;
        if (family.light > 0) { // a lamp was picked up - take its light away once the prop is gone
            Location at = entity.getLocation();
            Bukkit.getScheduler().runTaskLater(plugin, () -> lights.checkNear(at), 2L);
        }
        if (family.base.equalsIgnoreCase(modelId) || !resolveFmm()) return;
        ItemStack fresh = baseItem(family.base, entity.getItemStack().getAmount());
        if (fresh != null) entity.setItemStack(fresh);
    }

    /** The base item of every family, in props.yml order. */
    List<String> bases() {
        return List.copyOf(bases);
    }

    /** A fresh FMM placement item for this model, or null if FMM isn't usable. */
    ItemStack baseItem(String base, int amount) {
        if (!resolveFmm()) return null;
        try {
            ItemStack fresh = (ItemStack) createItem.invoke(null, base, Material.PAPER);
            fresh.setAmount(amount);
            return fresh;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- utils

    private static float yawTowards(Player player, Location from, float snap) {
        double dx = player.getLocation().getX() - from.getX();
        double dz = player.getLocation().getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (snap > 0) yaw = Math.round(yaw / snap) * snap;
        return normalize(yaw);
    }

    private static float normalize(float yaw) {
        yaw %= 360;
        return yaw < 0 ? yaw + 360 : yaw;
    }
}
