package com.iekcorbar.steamworks.api;

/**
 * One floating model of a custom part: the FMM model name (lowercase, as its json is called) and
 * whether it turns with the network (SPIN) or stands still (STATIC). Steamworks spawns and rotates
 * these for you - see {@link CustomPart#visuals(CustomNode)}.
 */
public record CustomVisual(String model, boolean spin) {
    public static CustomVisual spin(String model) {
        return new CustomVisual(model, true);
    }

    public static CustomVisual statik(String model) {
        return new CustomVisual(model, false);
    }
}
