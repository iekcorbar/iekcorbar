package com.iekcorbar.bouw;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the props from families.json (written by tools/make_bouw_pack.py): doors open on right-click, fences join. */
final class Bouw {
    final Decor decor;
    private final BouwPlugin plugin;
    /** family id + label, in file order (tab completer). */
    final List<String[]> families = new ArrayList<>();
    /** piece id -> family id, for the creative menu filter. */
    final java.util.Map<String, String> familyOf = new java.util.HashMap<>();

    Bouw(BouwPlugin plugin) {
        this.plugin = plugin;
        decor = new Decor(plugin, "bouw", defs(), plugin.getConfig().getInt("props.break-hits", 2));
    }

    private static Material planks(String wood) {
        Material m = Material.matchMaterial(wood.toUpperCase(Locale.ROOT) + "_PLANKS");
        return m == null ? Material.OAK_PLANKS : m;
    }

    private static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String p : s.split("_")) b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return b.toString().trim();
    }

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        JsonArray fam;
        try (var in = plugin.getResource("families.json")) {
            fam = new Gson().fromJson(new InputStreamReader(in), JsonArray.class);
        } catch (Exception e) {
            plugin.getLogger().severe("families.json missing or broken: " + e);
            return l;
        }
        for (JsonElement fe : fam) {
            JsonObject f = fe.getAsJsonObject();
            boolean door = f.get("kind").getAsString().equals("door");
            String label = f.get("label").getAsString();
            families.add(new String[]{f.get("family").getAsString(), label});
            for (JsonElement ie : f.getAsJsonArray("items")) {
                JsonObject it = ie.getAsJsonObject();
                String id = it.get("id").getAsString(), wood = it.get("wood").getAsString();
                String name = pretty(wood) + " " + label;
                Material pl = planks(wood);
                familyOf.put(id, f.get("family").getAsString());
                if (door) {
                    l.add(new DecorDef(id, name, "A " + label.toLowerCase(Locale.ROOT) + " in " + pretty(wood).toLowerCase(Locale.ROOT) + ". Right-click to open or close.")
                            .height(2).openable().use(this::toggle)
                            .recipe(1, pl, pl, pl, pl, Material.STICK, Material.PAPER, Material.PAPER)
                            .sound("minecraft:block.wood.place"));
                } else {
                    l.add(new DecorDef(id, name, "A " + label.toLowerCase(Locale.ROOT) + " in " + pretty(wood).toLowerCase(Locale.ROOT) + ". Joins its neighbours.")
                            .connect(id)
                            .recipe(3, pl, pl, Material.STICK, Material.STICK, Material.STICK, Material.STICK)
                            .sound("minecraft:block.wood.place"));
                }
            }
        }
        return l;
    }

    private boolean toggle(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (!hand.getType().isAir()) return false;          // prop items in hand still place / join
        if (p.open) d.close(p); else d.open(p, 12000);
        plugin.adv().grant(pl, "door");
        return true;
    }
}
