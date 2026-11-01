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

    /** The crafting material behind an item key: planks for woods, leaves for hedges, a stone block for walls. */
    private static Material material(String key, String recipe) {
        String k = key.toUpperCase(Locale.ROOT);
        String[] tries = switch (recipe) {
            case "leaves" -> new String[]{k + "_LEAVES"};
            case "stone" -> switch (k) {
                case "BRICK" -> new String[]{"BRICKS"};
                case "END_BRICK" -> new String[]{"END_STONE_BRICKS"};
                case "BLACKSTONE_BRICK" -> new String[]{"POLISHED_BLACKSTONE_BRICKS"};
                case "QUARTZ" -> new String[]{"QUARTZ_BLOCK"};
                case "STONE" -> new String[]{"STONE"};
                default -> new String[]{k + "S", "COBBLED_" + k, k, k + "_BLOCK"};
            };
            default -> new String[]{k + "_PLANKS"};
        };
        for (String t : tries) {
            Material m = Material.matchMaterial(t);
            if (m != null) return m;
        }
        return recipe.equals("stone") ? Material.STONE : recipe.equals("leaves") ? Material.OAK_LEAVES : Material.OAK_PLANKS;
    }

    /** One cheap extra ingredient per family, so no two families share a (shapeless) recipe. */
    private static final Material[] MARKERS = {Material.FLINT, Material.CLAY_BALL, Material.BRICK, Material.STRING, Material.FEATHER,
            Material.LEATHER, Material.BONE, Material.SUGAR, Material.WHEAT, Material.COAL, Material.SLIME_BALL, Material.GLOWSTONE_DUST,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.GUNPOWDER, Material.SPIDER_EYE, Material.EGG, Material.INK_SAC,
            Material.SNOWBALL, Material.KELP, Material.BAMBOO, Material.SUGAR_CANE, Material.VINE, Material.LILY_PAD, Material.COCOA_BEANS,
            Material.BEETROOT, Material.CARROT, Material.POTATO, Material.APPLE, Material.MELON_SLICE, Material.PUMPKIN_SEEDS,
            Material.WHEAT_SEEDS, Material.NETHER_WART, Material.AMETHYST_SHARD, Material.COPPER_INGOT, Material.GOLD_NUGGET,
            Material.EMERALD, Material.QUARTZ, Material.PRISMARINE_SHARD, Material.GLOW_BERRIES, Material.HONEYCOMB, Material.ECHO_SHARD};

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
            String recipe = f.get("recipe").getAsString();
            String family = f.get("family").getAsString();
            Material mk = MARKERS[families.size() % MARKERS.length];
            families.add(new String[]{family, label});
            for (JsonElement ie : f.getAsJsonArray("items")) {
                JsonObject it = ie.getAsJsonObject();
                String id = it.get("id").getAsString(), key = it.get("mat").getAsString();
                String name = key.isEmpty() ? label : pretty(key) + " " + label;
                String what = label.toLowerCase(Locale.ROOT);
                familyOf.put(id, family);
                DecorDef d;
                if (door) {
                    Material pl = material(key, "wood");
                    d = new DecorDef(id, name, "A " + what + ". Right-click to open or close.")
                            .height(2).openable().use(this::toggle)
                            .recipe(1, pl, pl, pl, pl, Material.STICK, Material.PAPER, Material.PAPER, mk);
                } else if (recipe.equals("metal")) {
                    d = new DecorDef(id, name, "A " + what + ". Joins its neighbours.").connect(id)
                            .recipe(4, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_NUGGET, Material.IRON_NUGGET, mk);
                } else {
                    Material m = material(key, recipe);
                    d = new DecorDef(id, name, "A " + what + ". Joins its neighbours.").connect(id);
                    if (recipe.equals("wood")) d.recipe(3, m, m, Material.STICK, Material.STICK, Material.STICK, Material.STICK, mk);
                    else d.recipe(4, m, m, m, m, m, m, mk);
                }
                d.sound(recipe.equals("stone") ? "minecraft:block.stone.place" : recipe.equals("metal") ? "minecraft:block.chain.place" : "minecraft:block.wood.place");
                l.add(d);
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
