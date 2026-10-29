package com.iekcorbar.steamworks.trains;

import java.util.Locale;

/** Train cars. Seat offsets: {forward, up, sideways} from the car centre at rail level. */
public enum CarType {
    LOCOMOTIVE("Steam Locomotive", new double[][]{{-0.6, 0.35, 0}},
            "Drives the train. Sit in the cab: W = steam on,", "S = brake / reverse, jump = whistle. Feed it coal.",
            "Click it with paper to lay out its line."),
    PASSENGER_WAGON("Passenger Wagon", new double[][]{{0.45, 0.35, 0.2}, {0.45, 0.35, -0.2}, {-0.45, 0.35, 0.2}, {-0.45, 0.35, -0.2}},
            "Four seats. Place it on rails right behind", "a locomotive or another wagon to couple it."),
    CARGO_WAGON("Cargo Wagon", new double[][]{},
            "Carries a chest's worth of goods.", "Right-click it to open, place behind a train to couple.");

    public final String displayName;
    public final double[][] seats;
    public final String[] lore;

    CarType(String displayName, double[][] seats, String... lore) {
        this.displayName = displayName;
        this.seats = seats;
        this.lore = lore;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String model() {
        return "sw_" + id();
    }

    public static CarType byId(String id) {
        for (CarType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
