package com.iekcorbar.power;

import com.iekcorbar.steamworks.SteamworksPlugin;
import com.iekcorbar.steamworks.api.CustomPart;
import com.iekcorbar.steamworks.api.CustomPartRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Workbench recipes of the five Power machines. They lived in the standalone SteamworksPower
 * plugin; after the integration into the base nobody registered them anymore, so the machines
 * could not be crafted. Registered once FreeMinecraftModels knows the models (the ingredients are
 * Steamworks items with a model), keys are steamworks:pow_&lt;machine&gt;.
 */
public final class PowerRecipes implements Listener {
    private final SteamworksPlugin sw;
    private final CustomPartRegistry reg;
    private final CustomPart turbine, press, ticker, flywheel, sieve;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public PowerRecipes(SteamworksPlugin sw, CustomPart turbine, CustomPart press, CustomPart ticker,
                        CustomPart flywheel, CustomPart sieve) {
        this.sw = sw;
        this.reg = sw.customParts();
        this.turbine = turbine;
        this.press = press;
        this.ticker = ticker;
        this.flywheel = flywheel;
        this.sieve = sieve;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, sw);
        sw.whenModelsReady(this::register);
    }

    // 13-11: the vanilla book cannot render our ExactChoice ingredients (26.2 strips PDC from the
    // wire -> kale paper icons + false "craftable"); recipes unlock the vanilla way, by crafting.
    // (kept: onJoin no longer force-reveals)
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
    }

    private void register() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k, false);
        keys.clear();
        // Water turbine: a propeller in a brass-cased drum on an iron frame.
        shaped("pow_water_turbine", reg.item(turbine, 1), new String[]{"I I", "CPC", "I I"},
                'I', new ItemStack(Material.IRON_INGOT), 'C', swItem("material/brass_casing"), 'P', swItem("material/propeller"));
        // Mechanical press: a screw ram between plate columns over a casing frame.
        shaped("pow_mechanical_press", reg.item(press, 1), new String[]{" P ", "PCP", "IBI"},
                'P', swItem("material/iron_plate"), 'C', swItem("material/andesite_casing"),
                'B', new ItemStack(Material.PISTON), 'I', new ItemStack(Material.IRON_INGOT));
        // Stock ticker: a comparator reading the blocks around it, dials behind glass.
        shaped("pow_stock_ticker", reg.item(ticker, 1), new String[]{" G ", "GCG", " B "},
                'G', new ItemStack(Material.GLASS_PANE), 'C', new ItemStack(Material.COMPARATOR), 'B', swItem("material/brass_casing"));
        // Brass flywheel: four plates bolted around a casing hub.
        shaped("pow_brass_flywheel", reg.item(flywheel, 1), new String[]{" P ", "PCP", " P "},
                'P', swItem("material/brass_plate"), 'C', swItem("material/brass_casing"));
        // Sieve funnel: the belt funnel under a mesh of plates and one good string.
        shaped("pow_sieve_funnel", reg.item(sieve, 1), new String[]{"PSP", " F "},
                'P', swItem("material/iron_plate"), 'S', new ItemStack(Material.STRING), 'F', swItem("part/funnel"));
        // 13-11: no forced reveal (see onJoin): vanilla unlocks by crafting, with real items.
        sw.getLogger().info("Registered " + keys.size() + " workbench recipes for the Power machines.");
    }

    private ItemStack swItem(String id) {
        ItemStack it = reg.steamworksItem(id);
        return it != null ? it : new ItemStack(Material.PAPER);
    }

    private void shaped(String id, ItemStack result, String[] shape, Object... pairs) {
        NamespacedKey key = new NamespacedKey(sw, id);
        ShapedRecipe r = new ShapedRecipe(key, result).shape(shape);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            char c = (Character) pairs[i];
            Object ing = pairs[i + 1];
            r.setIngredient(c, ing instanceof ItemStack s
                    ? new RecipeChoice.ExactChoice(s) : new RecipeChoice.MaterialChoice((Material) ing));
        }
        Bukkit.removeRecipe(key, false);
        Bukkit.addRecipe(r);
        keys.add(key);
    }
}
