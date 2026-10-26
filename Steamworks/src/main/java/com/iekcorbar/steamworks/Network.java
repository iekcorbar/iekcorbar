package com.iekcorbar.steamworks;

import java.util.List;

/** A connected group of parts that turn together. Rebuilt whenever something in it changes. */
final class Network {
    final List<KNode> nodes;
    final KNode root;
    /** Speed of the root part (RPM) as the strongest source drives it. */
    double ref;
    boolean jammed;
    String reason = "";
    double capacity, load;
    /** Accumulated turn of the root part in degrees (where the models are heading). */
    double phi;
    int period = 20;
    long nextUpdate;

    Network(List<KNode> nodes, KNode root) {
        this.nodes = nodes;
        this.root = root;
    }

    double speedOf(KNode n) {
        return jammed ? 0 : ref * n.ratio;
    }

    double effectiveRef() {
        return jammed ? 0 : ref;
    }

    double maxSpeed() {
        double max = 0;
        for (KNode n : nodes) max = Math.max(max, Math.abs(ref * n.ratio));
        return max;
    }
}
