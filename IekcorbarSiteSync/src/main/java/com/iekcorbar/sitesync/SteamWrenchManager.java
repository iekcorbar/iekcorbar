package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Steam Wrench: right-click a placed FreeMinecraftModels prop to tilt it to
 * the next model in its wrench cycle (props.yml wrench-cycles), sneak +
 * right-click for the previous one. Craftable (I_I / _C_ / _C_), /iprop wrench.
 *
 * Listens to FMM's ModeledEntityRightClickEvent, registered by reflection one
 * tick after startup (this plugin loads before FMM, so its classes aren't there
 * yet in onEnable). The wrench is a stick (stack size 1) with our own tag and
 * FMM's display model "steam_wrench" as its look.
 */
public class SteamWrenchManager implements Listener, TabExecutor {

    private final IekcorbarSiteSync plugin;
    private final PropPlacementManager props;
    private final NamespacedKey toolKey;
    private final NamespacedKey recipeKey;
    private Class<?> propClass;
    private Method getPlayer, getEntity, getEntityId, getSpawnLocation, permanentlyRemove, applyDisplayModel;

    public SteamWrenchManager(IekcorbarSiteSync plugin, PropPlacementManager props) {
        this.plugin = plugin;
        this.props = props;
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.recipeKey = new NamespacedKey(plugin, "steam_wrench");
    }

    public void enable() {
        if (plugin.getCommand("iprop") != null) {
            plugin.getCommand("iprop").setExecutor(this);
            plugin.getCommand("iprop").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTask(plugin, this::hookFmm);
    }

    @SuppressWarnings("unchecked")
    private void hookFmm() {
        try {
            Class<? extends Event> rightClick = (Class<? extends Event>)
                    PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.api.ModeledEntityRightClickEvent");
            propClass = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.customentity.PropEntity");
            Class<?> modeled = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.customentity.ModeledEntity");
            getPlayer = rightClick.getMethod("getPlayer");
            getEntity = rightClick.getMethod("getEntity");
            getEntityId = modeled.getMethod("getEntityID");
            getSpawnLocation = modeled.getMethod("getSpawnLocation");
            permanentlyRemove = propClass.getMethod("permanentlyRemove");
            applyDisplayModel = PropPlacementManager.fmmClass("com.magmaguy.freeminecraftmodels.api.ModelItemAPI")
                    .getMethod("applyDisplayModel", ItemStack.class, String.class);
            Bukkit.getPluginManager().registerEvent(rightClick, this, EventPriority.LOWEST,
                    (listener, event) -> onPropRightClick(event), plugin, true);
        } catch (Exception e) {
            plugin.getLogger().warning("[Props] Steam Wrench disabled - FreeMinecraftModels not usable: " + e);
            return;
        }
        registerRecipe();
        plugin.getLogger().info("[Props] Steam Wrench ready.");
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, wrench());
        recipe.shape("I I", " C ", " C ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('C', Material.COPPER_INGOT);
        Bukkit.addRecipe(recipe);
    }

    ItemStack wrench() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(AdvancementManager.text("&6Steam Wrench").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                AdvancementManager.text("&7Right-click a placed prop to turn or tilt it."),
                AdvancementManager.text("&7Sneak + right-click to turn it back.")));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, "wrench");
        item.setItemMeta(meta);
        try {
            if (applyDisplayModel != null) applyDisplayModel.invoke(null, item, "steam_wrench");
        } catch (Exception ignored) {
            // no look - still a working wrench
        }
        return item;
    }

    private boolean isWrench(ItemStack item) {
        return item != null && item.hasItemMeta()
                && "wrench".equals(item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING));
    }

    private void onPropRightClick(Event event) {
        try {
            Player player = (Player) getPlayer.invoke(event);
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!isWrench(hand)) return;
            Object prop = getEntity.invoke(event);
            if (!propClass.isInstance(prop)) return;
            String id = (String) getEntityId.invoke(prop);
            String next = props.nextTilt(id, player.isSneaking());
            ((Cancellable) event).setCancelled(true); // no pickup/mount scripts while using the wrench
            if (next == null) {
                player.sendActionBar(AdvancementManager.text("&7This can't be tilted."));
                return;
            }
            Location loc = (Location) getSpawnLocation.invoke(prop);
            if (!props.mayBuild(player, loc.getBlock(), loc.getBlock(), hand)) {
                player.sendActionBar(AdvancementManager.text("&cYou can't change that here."));
                return;
            }
            permanentlyRemove.invoke(prop);
            if (props.spawn(next, loc.clone()) == null) {
                props.spawn(id, loc.clone()); // put the old one back
                player.sendActionBar(AdvancementManager.text("&cCouldn't tilt it."));
                return;
            }
            player.playSound(loc, org.bukkit.Sound.BLOCK_COPPER_BULB_TURN_ON, 0.8f, 1.2f);
        } catch (Exception e) {
            plugin.getLogger().warning("[Props] Steam Wrench failed: " + e);
        }
    }

    // -------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> sender.sendMessage(AdvancementManager.text("&a" + props.reload()));
            case "wrench" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(AdvancementManager.text("&cUsage: /iprop wrench <player>"));
                    return true;
                }
                target.getInventory().addItem(wrench()).values()
                        .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                sender.sendMessage(AdvancementManager.text("&aGave a Steam Wrench to " + target.getName() + "."));
            }
            default -> sender.sendMessage(AdvancementManager.text("&6/iprop wrench [player] &7| &6reload"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("wrench", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("wrench")) return null; // online players
        return List.of();
    }
}
