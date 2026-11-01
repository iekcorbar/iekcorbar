package com.iekcorbar.wildlife;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** What the recipe viewer (Zukan plugin) asks for: the Wildlife items. The Field Journal recipe is a normal Bukkit recipe. */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final WildlifePlugin plugin;

    ZukanData(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        all.add(plugin.items().journal());
        all.add(plugin.items().crabClaw());
        all.add(plugin.items().lizardTail());
        all.add(plugin.items().whistle());
        all.add(plugin.pets().egg("wl_dragon", null));
        all.addAll(plugin.drops().all());
        for (Species s : Species.values()) {   // every species, the wraith included, plus the rare and extra-look eggs
            all.add(plugin.items().egg(s));
            for (String v : plugin.items().eggVariants(s)) all.add(plugin.items().egg(s, v));
        }
        all.addAll(plugin.zoo().allItems());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        List<Map<String, Object>> recipes = new ArrayList<>();
        Map<String, Object> w = new LinkedHashMap<>();                       // the whistle is a shapeless workbench recipe
        w.put("category", "Wildlife");
        w.put("icon", plugin.items().whistle());
        w.put("station", "Crafting table");
        w.put("shapeless", true);
        w.put("inputs", List.of(List.of(new ItemStack(org.bukkit.Material.COPPER_INGOT)), List.of(new ItemStack(org.bukkit.Material.IRON_NUGGET)), List.of(new ItemStack(org.bukkit.Material.COAL))));
        w.put("outputs", List.of(List.of(plugin.items().whistle())));
        w.put("info", List.of("Right-click: every tamed dragon of yours flies to you.", "Dragons: tame with coal, /wildlife pet, dye to repaint, chest for saddlebags.", "Two fed dragons lay an egg: keep it in your inventory or in a hay-block nest."));
        recipes.add(w);
        out.put("recipes", recipes);
        return out;
    }
}
