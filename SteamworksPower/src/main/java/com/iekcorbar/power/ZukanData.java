package com.iekcorbar.power;

import com.iekcorbar.steamworks.api.CustomPart;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: the three machines as items, and the press's
 * workbench list from config (the workbench recipes themselves are normal Bukkit recipes).
 * Zukan sees the depend in plugin.yml and lists everything under @steamworks as add-on content.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final PowerPlugin plugin;

    ZukanData(PowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        for (CustomPart p : plugin.parts().parts()) all.add(plugin.parts().item(p, 1));

        List<Map<String, Object>> recipes = new ArrayList<>();
        ItemStack station = plugin.parts().item(plugin.press, 1);
        for (MechanicalPress.Recipe r : plugin.press.recipes()) {
            List<ItemStack> ins = new ArrayList<>();
            ItemStack in = r.input().clone();
            in.setAmount(r.count());
            ins.add(in);
            List<ItemStack> outs = new ArrayList<>();
            List<String> info = new ArrayList<>();
            for (MechanicalPress.Out o : r.outs()) {
                ItemStack made = o.item().clone();
                made.setAmount(o.count());
                outs.add(made);
                if (o.chance() < 1) info.add(made.getType().name() + ": " + Math.round(o.chance() * 100) + "% chance");
            }
            info.add("Work: " + r.time() + " (" + String.format(java.util.Locale.ROOT, "%.1f", r.time() / 20.0) + " s at 12 RPM)");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Mechanical Press");
            m.put("icon", new ItemStack(Material.PISTON));
            m.put("station", station);
            m.put("grid", false);
            m.put("shapeless", false);
            m.put("inputs", List.of(ins));
            m.put("outputs", outs);
            m.put("info", info);
            recipes.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", recipes);
        return out;
    }
}
