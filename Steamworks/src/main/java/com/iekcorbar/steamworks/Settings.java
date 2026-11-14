package com.iekcorbar.steamworks;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml values. */
final class Settings {
    final double maxRpm;
    final int maxNetwork;
    final double mediumSpeed, fastSpeed;
    final int breakHits;
    final double crankRpm, crankCapacity, crankExhaustion;
    final int crankTicks;
    final double wheelRpmPerSide, wheelMaxRpm, wheelCapacity;
    final int wheelInterval;
    final double millImpact, millDivisor, millMinRate, millMaxRate;
    final double hammerImpact, hammerCycle, hammerDivisor, hammerMinRate, hammerMaxRate;
    final double fanImpact, fanRpmPerBlock, fanPush;
    final int fanMinRange, fanMaxRange, fanProcessTicks;
    final boolean fanPushPlayers;
    final double mixerImpact, mixerDivisor, mixerMinRate, mixerMaxRate;
    final double trainMaxSpeed, trainAccel, trainBrake, trainReverseMax, trainFriction, trainMaxFuel, trainAutoSpeed;
    final int trainStationWait, trainSectionLength;
    final double trainSwitchLookahead;
    final double pumpjackImpact, pumpjackRate, oilChance, oilMin, oilMax, refineryFuelPart;
    final int tankCapacity, refineryBatch, canisterFuelTicks, pollutionDecay;
    final double drillImpact, drillHardnessFactor;
    final int sawMaxLogs;
    final double beltImpact, beltSpeedPerRpm;
    final double armImpact, armMoveTicksAt16;
    final int armRange;
    final String docsUrl;
    final float machineVolume;
    final double windCapacity, windRpmLow, windRpmMid, windRpmHigh, windRainBonus, windStormBonus;
    final int windRadius;
    final double engineRpm, engineCapacity, crusherImpact, crusherDivisor;
    final int fuelCanisterBoilerTicks;
    final java.util.Map<org.bukkit.Material, Integer> trainFuel = new java.util.EnumMap<>(org.bukkit.Material.class);
    final float viewRange;

    Settings(FileConfiguration c) {
        maxRpm = c.getDouble("max-rpm", 192);
        maxNetwork = c.getInt("max-network-size", 512);
        mediumSpeed = c.getDouble("speed-levels.medium", 24);
        fastSpeed = c.getDouble("speed-levels.fast", 80);
        breakHits = Math.max(1, c.getInt("survival-break-hits", 2));
        crankRpm = c.getDouble("parts.hand-crank.rpm", 16);
        crankCapacity = c.getDouble("parts.hand-crank.capacity", 8);
        crankTicks = Math.max(1, c.getInt("parts.hand-crank.ticks-per-turn", 20));
        crankExhaustion = c.getDouble("parts.hand-crank.exhaustion", 0.25);
        wheelRpmPerSide = c.getDouble("parts.water-wheel.rpm-per-side", 4);
        wheelMaxRpm = c.getDouble("parts.water-wheel.max-rpm", 8);
        wheelCapacity = c.getDouble("parts.water-wheel.capacity", 32);
        wheelInterval = Math.max(5, c.getInt("parts.water-wheel.check-interval-ticks", 40));
        millImpact = c.getDouble("parts.millstone.impact", 4);
        millDivisor = Math.max(0.1, c.getDouble("parts.millstone.speed-divisor", 12));
        millMinRate = c.getDouble("parts.millstone.min-rate", 0.5);
        millMaxRate = c.getDouble("parts.millstone.max-rate", 16);
        hammerImpact = c.getDouble("parts.steam-hammer.impact", 8);
        hammerCycle = Math.max(1, c.getDouble("parts.steam-hammer.cycle", 100));
        hammerDivisor = Math.max(0.1, c.getDouble("parts.steam-hammer.speed-divisor", 8));
        hammerMinRate = Math.max(0.01, c.getDouble("parts.steam-hammer.min-rate", 0.25));
        hammerMaxRate = c.getDouble("parts.steam-hammer.max-rate", 25);
        fanImpact = c.getDouble("parts.bellows-fan.impact", 4);
        fanRpmPerBlock = Math.max(0.1, c.getDouble("parts.bellows-fan.rpm-per-block", 4));
        fanMinRange = Math.max(1, c.getInt("parts.bellows-fan.min-range", 3));
        fanMaxRange = Math.max(fanMinRange, c.getInt("parts.bellows-fan.max-range", 12));
        fanPush = c.getDouble("parts.bellows-fan.push", 0.05);
        fanPushPlayers = c.getBoolean("parts.bellows-fan.push-players", true);
        fanProcessTicks = Math.max(5, c.getInt("parts.bellows-fan.process-ticks", 100));
        mixerImpact = c.getDouble("parts.mixer.impact", 4);
        mixerDivisor = Math.max(0.1, c.getDouble("parts.mixer.speed-divisor", 12));
        mixerMinRate = c.getDouble("parts.mixer.min-rate", 0.5);
        mixerMaxRate = c.getDouble("parts.mixer.max-rate", 16);
        trainMaxSpeed = c.getDouble("trains.max-speed", 0.5);
        trainAccel = c.getDouble("trains.acceleration", 0.005);
        trainBrake = c.getDouble("trains.brake", 0.02);
        trainReverseMax = c.getDouble("trains.reverse-max-speed", 0.15);
        trainFriction = c.getDouble("trains.friction", 0.0015);
        trainMaxFuel = c.getDouble("trains.max-fuel-ticks", 72000);
        trainAutoSpeed = c.getDouble("trains.autopilot-speed", 0.3);
        trainStationWait = Math.max(1, c.getInt("trains.station-wait-seconds", 20)) * 20;
        trainSectionLength = Math.max(8, c.getInt("trains.signal-section-max-blocks", 128));
        trainSwitchLookahead = Math.max(4, c.getDouble("trains.switch-lookahead-blocks", 14));
        pumpjackImpact = c.getDouble("oil.pumpjack-impact", 8);
        pumpjackRate = c.getDouble("oil.pumpjack-mb-per-rpm-second", 2);
        oilChance = c.getDouble("oil.field-chance", 0.08);
        oilMin = c.getDouble("oil.field-min-mb", 20000);
        oilMax = c.getDouble("oil.field-max-mb", 120000);
        tankCapacity = Math.max(1000, c.getInt("oil.tank-capacity-mb", 16000));
        refineryBatch = Math.max(10, c.getInt("oil.refinery-mb-per-second", 100));
        refineryFuelPart = Math.max(0, Math.min(1, c.getDouble("oil.refinery-fuel-part", 0.6)));
        canisterFuelTicks = c.getInt("oil.fuel-canister-train-ticks", 6000);
        pollutionDecay = Math.max(0, c.getInt("oil.pollution-decay-per-5s", 1));
        drillImpact = c.getDouble("parts.drill.impact", 4);
        drillHardnessFactor = Math.max(1, c.getDouble("parts.drill.ticks-per-hardness-at-1rpm", 400));
        sawMaxLogs = Math.max(1, c.getInt("parts.saw.max-tree-logs", 192));
        beltImpact = c.getDouble("parts.belt.impact", 0.5);
        beltSpeedPerRpm = c.getDouble("parts.belt.blocks-per-tick-per-rpm", 0.004);
        armImpact = c.getDouble("parts.arm.impact", 4);
        armMoveTicksAt16 = Math.max(2, c.getDouble("parts.arm.move-ticks-at-16-rpm", 12));
        armRange = Math.max(1, Math.min(8, c.getInt("parts.arm.range", 4)));
        docsUrl = c.getString("docs-url", "https://iekcorbar.com/steamworks");
        machineVolume = (float) Math.max(0, Math.min(2, c.getDouble("sounds.machine-volume", 1.0)));
        windCapacity = c.getDouble("parts.windmill.capacity", 64);
        windRpmLow = c.getDouble("parts.windmill.rpm-below-64", 4);
        windRpmMid = c.getDouble("parts.windmill.rpm-64-to-100", 6);
        windRpmHigh = c.getDouble("parts.windmill.rpm-above-100", 8);
        windRainBonus = c.getDouble("parts.windmill.rain-bonus", 2);
        windStormBonus = c.getDouble("parts.windmill.thunder-bonus", 4);
        windRadius = Math.max(2, Math.min(8, c.getInt("parts.windmill.free-radius", 5)));
        engineRpm = c.getDouble("parts.steam-engine.rpm", 24);
        engineCapacity = c.getDouble("parts.steam-engine.capacity", 64);
        crusherImpact = c.getDouble("parts.crushing-wheel.impact", 8);
        crusherDivisor = Math.max(0.1, c.getDouble("parts.crushing-wheel.speed-divisor", 8));
        fuelCanisterBoilerTicks = c.getInt("parts.steam-boiler.fuel-ticks-per-1000mb", 6000);
        var fuel = c.getConfigurationSection("trains.fuel");
        if (fuel == null) {
            trainFuel.put(org.bukkit.Material.COAL, 2400);
            trainFuel.put(org.bukkit.Material.CHARCOAL, 2400);
            trainFuel.put(org.bukkit.Material.COAL_BLOCK, 24000);
            trainFuel.put(org.bukkit.Material.BLAZE_ROD, 3600);
        } else {
            for (String k : fuel.getKeys(false)) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(k);
                if (m != null) trainFuel.put(m, fuel.getInt(k));
            }
        }
        viewRange = (float) c.getDouble("visuals.view-range", 0.6);
    }

    /** Torque per RPM a source adds to its network. */
    double capacity(PartType t) {
        return switch (t) {
            case HAND_CRANK -> crankCapacity;
            case WATER_WHEEL -> wheelCapacity;
            case WINDMILL -> windCapacity;
            case STEAM_ENGINE -> engineCapacity;
            default -> 0;
        };
    }

    /** Torque per RPM a machine takes from its network. */
    double impact(PartType t) {
        return switch (t) {
            case MILLSTONE -> millImpact;
            case STEAM_HAMMER -> hammerImpact;
            case BELLOWS_FAN -> fanImpact;
            case MIXER -> mixerImpact;
            case PUMPJACK -> pumpjackImpact;
            case DRILL, SAW -> drillImpact;
            case BELT -> beltImpact;
            case ARM -> armImpact;
            case CRUSHING_WHEEL -> crusherImpact;
            default -> 0;
        };
    }
}
