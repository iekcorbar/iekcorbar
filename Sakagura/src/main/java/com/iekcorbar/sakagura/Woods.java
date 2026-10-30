package com.iekcorbar.sakagura;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/** Cask woods (plus the steampunk copper cask) and which ones taste alike. */
enum Woods {
    OAK(Material.OAK_PLANKS, 0.8, 0.3, 0.2, 0.5),
    SPRUCE(Material.SPRUCE_PLANKS, 0.6, 0.4, 0.3, 0.6),
    BIRCH(Material.BIRCH_PLANKS, 0.4, 0.7, 0.1, 0.4),
    JUNGLE(Material.JUNGLE_PLANKS, 0.5, 0.5, 0.6, 0.7),
    ACACIA(Material.ACACIA_PLANKS, 0.3, 0.2, 0.9, 0.8),
    DARK_OAK(Material.DARK_OAK_PLANKS, 0.9, 0.2, 0.3, 0.7),
    CHERRY(Material.CHERRY_PLANKS, 0.3, 0.9, 0.1, 0.5),
    MANGROVE(Material.MANGROVE_PLANKS, 0.7, 0.3, 0.4, 0.9),
    BAMBOO(Material.BAMBOO_PLANKS, 0.4, 0.6, 0.5, 0.6),
    COPPER(Material.COPPER_BLOCK, 0.2, 0.1, 0.1, 1.0);

    final Material planks;
    /** Robustness: how much the wood adds to alcohol content (0-1). */
    final double robustness;
    /** Sweetness: how much the wood adds to effect duration (0-1). */
    final double sweetness;
    /** Spiciness: chance of adding extra effects (0-1). */
    final double spiciness;
    /** Intensity: overall potency multiplier (0-1). */
    final double intensity;

    private static final Set<Set<String>> SIMILAR = Set.of(Set.of("oak", "dark_oak"), Set.of("oak", "birch"), Set.of("spruce", "dark_oak"),
            Set.of("jungle", "bamboo"), Set.of("acacia", "mangrove"), Set.of("cherry", "birch"));

    Woods(Material planks, double robustness, double sweetness, double spiciness, double intensity) {
        this.planks = planks;
        this.robustness = robustness;
        this.sweetness = sweetness;
        this.spiciness = spiciness;
        this.intensity = intensity;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    String label() {
        String s = id().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Flavor profile description for this wood. */
    String flavorProfile() {
        StringBuilder sb = new StringBuilder();
        if (robustness >= 0.7) sb.append("Robust, ");
        if (sweetness >= 0.7) sb.append("Sweet, ");
        if (spiciness >= 0.7) sb.append("Spicy, ");
        if (intensity >= 0.8) sb.append("Intense, ");
        if (sb.length() == 0) sb.append("Mild, ");
        return sb.toString().replaceAll(", $", "");
    }

    /** Get a bonus multiplier for aging in this wood (0.8-1.2). */
    double agingBonus() {
        return 0.8 + intensity * 0.4;
    }

    static boolean similar(String a, String b) {
        return SIMILAR.contains(Set.of(a, b));
    }

    static Woods byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
