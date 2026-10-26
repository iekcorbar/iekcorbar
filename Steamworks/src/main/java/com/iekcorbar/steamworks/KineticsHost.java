package com.iekcorbar.steamworks;

/** What the kinetic graph needs from the plugin (kept small so the maths can be tested without a server). */
interface KineticsHost {
    Settings settings();

    /** A network was (re)built: update the models. */
    void rebuilt(Network net);

    /** A network spins on: send the next model angles. */
    void spin(Network net, int period);

    /** A part turned too fast and must drop. */
    void snapBreakerPin(KNode n);
}
