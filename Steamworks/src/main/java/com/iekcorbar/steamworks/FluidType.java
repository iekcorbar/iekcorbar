package com.iekcorbar.steamworks;

import java.util.Locale;

/** Fluids in pipes and tanks, counted in mB (1000 = one bucket / one canister). */
public enum FluidType {
    CRUDE_OIL("Crude Oil", MaterialType.CRUDE_CANISTER),
    FUEL("Fuel", MaterialType.FUEL_CANISTER),
    CHEMICAL_WASTE("Chemical Waste", MaterialType.WASTE_CANISTER);

    public final String displayName;
    public final MaterialType canister;

    FluidType(String displayName, MaterialType canister) {
        this.displayName = displayName;
        this.canister = canister;
    }

    /** FMM model of the liquid column shown inside a tank. */
    public String model() {
        return "sw_fluid_" + name().toLowerCase(Locale.ROOT);
    }

    public static FluidType byCanister(MaterialType m) {
        for (FluidType f : values()) if (f.canister == m) return f;
        return null;
    }
}
