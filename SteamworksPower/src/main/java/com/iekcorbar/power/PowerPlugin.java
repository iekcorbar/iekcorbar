package com.iekcorbar.power;

import com.iekcorbar.steamworks.SteamworksPlugin;
import com.iekcorbar.steamworks.api.CustomPartRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Steamworks Power - the machines-and-muscle add-on. A Water Turbine that runs on river current,
 * a Mechanical Press that compacts what the furnace can't, and a Stock Ticker that counts your
 * chests. All three join the Steamworks rotation network through its public CustomPart API.
 */
public final class PowerPlugin extends JavaPlugin {
    private SteamworksPlugin sw;
    WaterTurbine turbine;
    MechanicalPress press;
    StockTicker ticker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        sw = (SteamworksPlugin) getServer().getPluginManager().getPlugin("Steamworks");
        CustomPartRegistry reg = sw.customParts();
        turbine = new WaterTurbine(this);
        press = new MechanicalPress(this);
        ticker = new StockTicker(this);
        reg.register(turbine);
        reg.register(press);
        reg.register(ticker);
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        PowerCommand cmd = new PowerCommand(this);
        getCommand("swpower").setExecutor(cmd);
        getCommand("swpower").setTabCompleter(cmd);
        // recipes reference the machines' models, so wait until FreeMinecraftModels knows them
        sw.whenModelsReady(this::registerRecipes);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                for (NamespacedKey k : recipeKeys) e.getPlayer().discoverRecipe(k);
            }
        }, this);
        getLogger().info("Steamworks Power enabled - the river can mind the factory now.");
    }

    @Override
    public void onDisable() {
        if (sw != null) sw.customParts().unregister(this);
    }

    CustomPartRegistry parts() {
        return sw.customParts();
    }

    // ------------------------------------------------------------ workbench recipes

    private final java.util.List<NamespacedKey> recipeKeys = new java.util.ArrayList<>();

    private void registerRecipes() {
        CustomPartRegistry reg = parts();
        ItemStack turbineItem = reg.item(turbine, 1);
        // Water turbine: a propeller in a brass-cased drum on an iron frame.
        shaped("water_turbine", turbineItem, new String[]{"I I", "CPC", "I I"},
                'I', new ItemStack(Material.IRON_INGOT), 'C', swItem("material/brass_casing"), 'P', swItem("material/propeller"));
        // Mechanical press: a screw ram between plate columns over a casing frame.
        ItemStack pressItem = reg.item(press, 1);
        shaped("mechanical_press", pressItem, new String[]{" P ", "PCP", "IBI"},
                'P', swItem("material/iron_plate"), 'C', swItem("material/andesite_casing"), 'B', new ItemStack(Material.PISTON), 'I', new ItemStack(Material.IRON_INGOT));
        // Stock ticker: a comparator reading the blocks around it, dials behind glass.
        ItemStack tickerItem = reg.item(ticker, 1);
        shaped("stock_ticker", tickerItem, new String[]{" G ", "GCG", " B "},
                'G', new ItemStack(Material.GLASS_PANE), 'C', new ItemStack(Material.COMPARATOR), 'B', swItem("material/brass_casing"));
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers())
            for (NamespacedKey k : recipeKeys) p.discoverRecipe(k);
        getLogger().info("Registered 3 workbench recipes for the add-on machines.");
    }

    private void shaped(String id, ItemStack result, String[] shape, Object... pairs) {
        NamespacedKey key = new NamespacedKey(this, id);
        ShapedRecipe r = new ShapedRecipe(key, result).shape(shape);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            char c = (Character) pairs[i];
            Object ing = pairs[i + 1];
            r.setIngredient(c, ing instanceof ItemStack s
                    ? new RecipeChoice.ExactChoice(s) : new RecipeChoice.MaterialChoice(((Material) ing)));
        }
        Bukkit.removeRecipe(key, false);
        Bukkit.addRecipe(r);
        recipeKeys.add(key);
    }

    /** A Steamworks item (the base's own material/part), or null if the base is older than this. */
    ItemStack swItem(String id) {
        ItemStack it = parts().steamworksItem(id);
        return it != null ? it : new ItemStack(Material.PAPER); // placeholder only if the base predates steamworksItem()
    }
}
