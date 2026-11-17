package com.iekcorbar.hihou.bw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 0.5.0: join-time kit choice (BwStore hands out what you picked at every base spawn).
 * BedWars1058 loads kits from a kits.yml and gates the fancy ones behind VIPs; we bake
 * four honest ones in the code - every kit is a story you can read off the hotbar:
 * CLASSIC the duel, BUILDER the bridge, MINER the economy, ARCHER the poke. The choice
 * lives in the session (BwArena.kitChoice) so a restart just gives you the default again.
 */
final class BwKits {
    /** name -> display label; the apply switch below is the real definition. */
    private static final Map<String, String> KITS = new LinkedHashMap<>();

    static {
        KITS.put("classic", "Classic - a stone sword to start the war");
        KITS.put("builder", "Builder - wool on the hotbar, axe in the hand");
        KITS.put("miner", "Miner - an iron pick, and the gens will thank you");
        KITS.put("archer", "Archer - keep them honest from 20 blocks");
    }

    static boolean is(String name) {
        return name != null && KITS.containsKey(name.toLowerCase(Locale.ROOT));
    }

    static String names() {
        return String.join(", ", KITS.keySet());
    }

    /** @return null on success, otherwise the reason the choice could not be made. */
    static String select(Player p, BwArena a, String name) {
        String k = name == null ? null : name.toLowerCase(Locale.ROOT);
        if (!KITS.containsKey(k)) return "Kit: " + names() + ".";
        a.kitChoice.put(p.getUniqueId(), k);
        p.sendMessage(Component.text("Kit set to " + k + " - " + KITS.get(k) + ".", NamedTextColor.GREEN));
        return null;
    }

    /** Handed out fresh at every spawn/respawn; the kit is yours again, like the original. */
    static void apply(Player p, BwArena a) {
        String k = a.kitChoice.getOrDefault(p.getUniqueId(), "classic");
        switch (k) {
            case "builder" -> {
                p.getInventory().setItem(0, new ItemStack(Material.WOODEN_AXE));
                p.getInventory().setItem(1, new ItemStack(Material.WHITE_WOOL, 16));
            }
            case "miner" -> p.getInventory().setItem(0, new ItemStack(Material.IRON_PICKAXE));
            case "archer" -> {
                p.getInventory().setItem(0, new ItemStack(Material.BOW));
                p.getInventory().setItem(1, new ItemStack(Material.ARROW, 12));
            }
            default -> p.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        }
    }
}
