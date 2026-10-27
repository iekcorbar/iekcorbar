package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.KNode.DisplayPart;
import com.iekcorbar.steamworks.KNode.DisplayPart.Role;
import org.bukkit.Axis;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The part models: ItemDisplay entities (not saved with the world - respawned
 * when a chunk loads) showing the FreeMinecraftModels display models. Turning is
 * done by the client: every few ticks the target angle is sent with an
 * interpolation time, so the server sends ~1-10 updates per second per part.
 */
final class Visuals {
    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f ONE = new Vector3f(1, 1, 1);

    private final SteamworksPlugin plugin;
    final NamespacedKey tag;

    Visuals(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "display");
    }

    void spawn(KNode n) {
        remove(n);
        if (n.type == PartType.CUSTOM && n.ext != null) {          // add-on machines draw what they ask for
            BlockFace cup = Kinetics.positive(n.axis);
            BlockFace cfront = n.axis == Axis.Y ? BlockFace.SOUTH : BlockFace.UP;
            Quaternionf corient = orientation(cup, cfront);
            float csign = Kinetics.sign(cup);
            for (com.iekcorbar.steamworks.api.CustomVisual v : n.ext.visuals(ExtParts.view(n)))
                add(n, v.model(), v.spin() ? Role.SPIN : Role.STATIC, corient, v.spin() ? csign : 1f);
            n.snap = true;
            return;
        }
        BlockFace up, front;
        switch (n.type) {
            case HAND_CRANK, BELLOWS_FAN, DRILL, SAW, WINDMILL, EJECTOR -> {
                up = n.facing;
                front = Kinetics.axisOf(up) == Axis.Y ? BlockFace.SOUTH : BlockFace.UP;
            }
            case STATION, SIGNAL -> {
                up = BlockFace.UP;
                front = n.facing;
            }
            default -> {
                up = Kinetics.positive(n.axis);
                front = n.type.isGauge() ? dialFace(n) : (n.axis == Axis.Y ? BlockFace.SOUTH : BlockFace.UP);
            }
        }
        Quaternionf orient = orientation(up, front);
        float sign = Kinetics.sign(up);
        switch (n.type) {
            case SPEED_GAUGE, STRESS_GAUGE -> {
                add(n, "sw_shaft", Role.SPIN, orient, sign);
                add(n, n.type.model() + "_body", Role.STATIC, orient, sign);
                add(n, "sw_gauge_needle", Role.NEEDLE, orient, sign);
            }
            case MILLSTONE -> {
                // the stone wheel stands upright in its frame and the drive shaft plugs into the
                // axle on either side: the frame stays world-upright (model X along the axle, like
                // the steam hammer frame), the runner turns with the axle (model Y convention).
                Quaternionf frame = orientation(BlockFace.UP, n.axis == Axis.X ? BlockFace.SOUTH : BlockFace.EAST);
                add(n, "sw_millstone_base", Role.STATIC, frame, 1);
                add(n, "sw_millstone_runner", Role.SPIN, orient, sign);
            }
            case STEAM_HAMMER -> {
                // frame and head stand upright with model X along the shaft; the cam shaft spins like a shaft
                Quaternionf frame = orientation(BlockFace.UP, n.axis == Axis.X ? BlockFace.SOUTH : BlockFace.EAST);
                add(n, "sw_steam_hammer_frame", Role.STATIC, frame, 1);
                add(n, "sw_steam_hammer_head", Role.HEAD, frame, 1);
                add(n, "sw_steam_hammer_cam", Role.SPIN, orient, sign);
            }
            case CLUTCH, GEARSHIFT -> {
                add(n, casingModel(n), Role.CASING, orient, sign);
                add(n, "sw_shaft_half", Role.SPIN, orient, sign);
                add(n, "sw_shaft_half", Role.SPIN_B, orientation(up.getOppositeFace(), front), -sign);
            }
            case KETTLE -> {
                add(n, "sw_kettle", Role.STATIC, orient, sign);
                add(n, "sw_kettle_water", Role.LIQUID, orient, sign);
                updateKettle(n);
            }
            case MIXER -> {
                add(n, "sw_mixer_housing", Role.STATIC, orient, sign);
                add(n, "sw_mixer_whisk", Role.SPIN, orient, sign);
            }
            case PUMPJACK -> {
                Quaternionf frame = orientation(BlockFace.UP, n.axis == Axis.X ? BlockFace.SOUTH : BlockFace.EAST);
                add(n, "sw_pumpjack_base", Role.STATIC, frame, 1);
                add(n, "sw_pumpjack_beam", Role.BEAM, frame, 1);
                add(n, "sw_shaft", Role.SPIN, orient, sign);
                beam(n, 0, 0);
            }
            case PIPE -> {
                add(n, "sw_pipe_center", Role.STATIC, orient, sign);
                updatePipe(n);
            }
            case TANK -> {
                add(n, "sw_tank", Role.STATIC, orient, sign);
                add(n, "sw_fluid_crude_oil", Role.LIQUID, orient, sign);
                updateTank(n);
            }
            case REFINERY -> add(n, "sw_refinery", Role.STATIC, orient, sign);
            case ARM -> {
                add(n, "sw_arm_base", Role.STATIC, orient, sign);
                add(n, "sw_arm_turret", Role.TURRET, orient, sign);
                if (n.tool != null) add(n, "sw_" + n.tool.id().replace("arm_", "arm_tool_"), Role.TURRET, orient, sign);
                add(n, "sw_arm_turret", Role.HELD, orient, sign);
                armTurn(n, (float) n.beamPhase, 0);
            }
            case BELT -> {
                Quaternionf frame = orientation(BlockFace.UP, n.facing);
                add(n, "sw_belt_frame", Role.STATIC, frame, 1);
                add(n, "sw_belt_rollers", Role.SPIN, orient, sign);
            }
            case DRILL, SAW -> {
                add(n, n.type.model() + "_casing", Role.STATIC, orient, sign);
                add(n, n.type.model() + "_head", Role.SPIN, orient, sign);
            }
            case STATION -> {
                add(n, "sw_station", Role.STATIC, orient, sign);
                stationText(n);
            }
            case SIGNAL -> add(n, signalModel(n), Role.CASING, orient, sign);
            case BELLOWS_FAN -> {
                add(n, "sw_bellows_fan_casing", Role.STATIC, orient, sign);
                add(n, "sw_bellows_fan_rotor", Role.SPIN, orient, sign);
            }
            case SPROCKET -> {
                add(n, "sw_sprocket", Role.SPIN, orient, sign);
                chainSpans(n);
            }
            case WINDMILL -> {
                add(n, "sw_windmill_bearing", Role.STATIC, orient, sign);
                add(n, "sw_windmill_sails", Role.SPIN, orient, sign, 3.5f);
            }
            case STEAM_BOILER -> add(n, boilerModel(n), Role.CASING, orient, sign);
            case STEAM_ENGINE -> {
                Quaternionf frame = orientation(BlockFace.UP, n.axis == Axis.X ? BlockFace.SOUTH : BlockFace.EAST);
                add(n, "sw_steam_engine", Role.STATIC, frame, 1);
                add(n, "sw_steam_engine_flywheel", Role.SPIN, orient, sign);
            }
            case CRUSHING_WHEEL -> add(n, "sw_crushing_wheel", Role.SPIN, orient, sign);
            case CHUTE, FUNNEL -> {
                Quaternionf frame = orientation(BlockFace.UP, n.facing);   // laid out like a belt, items slide on top
                add(n, n.type.model(), Role.STATIC, frame, 1);
            }
            case DEPOT -> add(n, "sw_depot", Role.STATIC, orientation(BlockFace.UP, n.facing), 1);
            case EJECTOR -> add(n, "sw_ejector", Role.STATIC, orient, sign);
            case PUMP -> {
                add(n, "sw_pump", Role.STATIC, orient, sign);
                add(n, "sw_shaft_half", Role.SPIN, orient, sign);          // the impeller axle you can see through the glass
            }
            case VALVE -> add(n, "sw_valve", Role.STATIC, orient, sign);
            case WHISTLE -> add(n, "sw_whistle", Role.STATIC, orient, sign);
            case GEARBOX -> add(n, n.type.model(), Role.STATIC, orient, sign);
            default -> add(n, n.type.model(), Role.SPIN, orient, sign);
        }
        n.snap = true;
    }

    /** Gauges: dials face sideways - on a vertical shaft the way the player looked when placing. */
    static BlockFace dialFace(KNode n) {
        if (Kinetics.axisOf(n.facing) != n.axis) return n.facing;
        return switch (n.axis) {
            case X -> BlockFace.SOUTH;
            case Z -> BlockFace.EAST;
            case Y -> Kinetics.axisOf(n.facing) == Axis.Y ? BlockFace.SOUTH : n.facing;
        };
    }

    private void add(KNode n, String model, Role role, Quaternionf orient, float sign) {
        add(n, model, role, orient, sign, 1f);
    }

    private void add(KNode n, String model, Role role, Quaternionf orient, float sign, float scale) {
        ItemStack item = plugin.items().displayItem(model);
        Vector3f size = new Vector3f(scale, scale, scale);
        ItemDisplay d = n.world.spawn(n.center(), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setViewRange(plugin.settings().viewRange * Math.max(1f, scale));
            e.setInterpolationDuration(0);
            e.setTransformation(new Transformation(ZERO, orient, size, new Quaternionf()));
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        n.displays.add(new DisplayPart(d, role, orient, sign, scale));
    }

    void remove(KNode n) {
        for (DisplayPart p : n.displays) p.entity.remove();
        n.displays.clear();
        for (org.bukkit.entity.TextDisplay t : n.labels) t.remove();
        n.labels.clear();
    }

    /** Model Y -> up, model Z -> front (model X = up x front). */
    static Quaternionf orientation(BlockFace up, BlockFace front) {
        Vector3f u = new Vector3f(up.getModX(), up.getModY(), up.getModZ());
        Vector3f f = new Vector3f(front.getModX(), front.getModY(), front.getModZ());
        Vector3f x = new Vector3f(u).cross(f);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, u, f));
    }

    /** Half-tooth offset so neighbouring cogs mesh instead of overlapping. */
    private static double toothOffset(KNode n) {
        int s = switch (n.axis) {
            case X -> n.y + n.z;
            case Y -> n.x + n.z;
            case Z -> n.x + n.y;
        };
        if ((s & 1) == 0) return 0;
        return n.type == PartType.COGWHEEL ? 22.5 : n.type == PartType.LARGE_COGWHEEL ? 11.25 : 0;
    }

    private static double angle(KNode n, Network net) {
        return net.phi * n.ratio + toothOffset(n);
    }

    /** After a network rebuild: put new/changed parts at their angle at once, refresh gauges. */
    void rebuilt(Network net) {
        for (KNode n : net.nodes) {
            if (n.snap) {
                n.snap = false;
                apply(n, net, 0);
            }
            needle(n, net);
        }
    }

    /** Sends the next target angle for every turning part of a network. */
    void spin(Network net, int period) {
        for (KNode n : net.nodes) apply(n, net, period);
    }

    private static String boilerModel(KNode n) {
        return n.burnTicks > 0 ? "sw_steam_boiler_lit" : "sw_steam_boiler";
    }

    /** Boiler: the firebox door glows while it burns. */
    void updateBoiler(KNode n) {
        for (DisplayPart p : n.displays) {
            if (p.role == Role.CASING && p.entity.isValid()) p.entity.setItemStack(plugin.items().displayItem(boilerModel(n)));
        }
    }

    private static String casingModel(KNode n) {
        return n.type.model() + "_casing" + (n.powered ? "_on" : "");
    }

    private static String signalModel(KNode n) {
        return n.signalRed ? "sw_signal_red" : "sw_signal_green";
    }

    void updateSignal(KNode n) {
        for (DisplayPart p : n.displays) {
            if (p.role == Role.CASING && p.entity.isValid()) p.entity.setItemStack(plugin.items().displayItem(signalModel(n)));
        }
    }

    /** Karakuri arm: the turret (and its tool head) swings round to face a point. */
    void armTurn(KNode n, float radians, int duration) {
        Quaternionf yaw = new Quaternionf().rotateY(radians);
        for (DisplayPart p : n.displays) {
            if (p.role != Role.TURRET || !p.entity.isValid()) continue;
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(duration);
            p.entity.setTransformation(new Transformation(ZERO, p.orient, ONE, yaw));
        }
        armHeld(n, duration);
    }

    void armHeld(KNode n) {
        armHeld(n, 0);
    }

    /** The item in the claw, hanging under the tool head at the end of the boom. */
    private void armHeld(KNode n, int duration) {
        float a = (float) n.beamPhase;
        for (DisplayPart p : n.displays) {
            if (p.role != Role.HELD || !p.entity.isValid()) continue;
            boolean has = n.held != null && !n.held.isEmpty();
            p.entity.setItemStack(has ? n.held.asOne() : new ItemStack(org.bukkit.Material.AIR));
            p.entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(duration);
            Vector3f at = new Vector3f((float) Math.sin(a) * 0.78f, 0.12f, (float) Math.cos(a) * 0.78f);
            p.entity.setTransformation(new Transformation(at, new Quaternionf().rotateY(a), new Vector3f(0.35f, 0.35f, 0.35f), new Quaternionf()));
        }
    }

    /**
     * Chain between two sprockets: two straight runs (over the top and under the
     * bottom of the sprockets), drawn by the end with the lower key so it is not
     * drawn twice. The span model is 1 block long along model +Z, stretched here.
     */
    void chainSpans(KNode n) {
        n.displays.removeIf(p -> {
            if (p.role != Role.CHAIN) return false;
            p.entity.remove();
            return true;
        });
        if (n.chain == null) return;
        long other = KNode.key(n.x + n.chain[0], n.y + n.chain[1], n.z + n.chain[2]);
        if (other < n.key) return;
        Vector3f dir = new Vector3f(n.chain[0], n.chain[1], n.chain[2]);
        float len = dir.length();
        if (len < 0.5f) return;
        dir.div(len);
        BlockFace up = Kinetics.positive(n.axis);
        Vector3f axis = new Vector3f(up.getModX(), up.getModY(), up.getModZ());
        Vector3f side = new Vector3f(axis).cross(dir).normalize(); // across the run, in the sprocket's plane
        Quaternionf orient = new Quaternionf().setFromNormalized(new Matrix3f(new Vector3f(axis).cross(dir), axis, dir));
        for (float s : new float[]{1, -1}) {
            ItemStack item = plugin.items().displayItem("sw_chain_span");
            Vector3f shift = new Vector3f(side).mul(s * 5.5f / 16f);
            ItemDisplay d = n.world.spawn(n.center(), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(item);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setViewRange(plugin.settings().viewRange);
                e.setTransformation(new Transformation(shift, orient, new Vector3f(1, 1, len), new Quaternionf()));
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
            n.displays.add(new DisplayPart(d, Role.CHAIN, orient, 1));
        }
    }

    /** Pumpjack walking beam: rocks round its pivot (model X = shaft axis), pivot 5 px above the block centre. */
    void beam(KNode n, float degrees, int duration) {
        Quaternionf rock = new Quaternionf().rotateX((float) Math.toRadians(degrees));
        for (DisplayPart p : n.displays) {
            if (p.role != Role.BEAM || !p.entity.isValid()) continue;
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(duration);
            p.entity.setTransformation(new Transformation(new Vector3f(0, 5 / 16f, 0), p.orient, ONE, rock));
        }
    }

    /** Pipe: one arm towards every neighbouring pipe / tank / pumpjack / refinery. */
    void updatePipe(KNode n) {
        n.displays.removeIf(p -> {
            if (p.role != Role.ARM) return false;
            p.entity.remove();
            return true;
        });
        for (BlockFace f : Kinetics.FACES) {
            KNode m = plugin.kinetics().rel(n, f.getModX(), f.getModY(), f.getModZ());
            if (!Oil.fluidPart(m)) continue;
            BlockFace front = Kinetics.axisOf(f) == Axis.Y ? BlockFace.SOUTH : BlockFace.UP;
            add(n, "sw_pipe_arm", Role.ARM, orientation(f, front), 1);
        }
    }

    /** Tank: liquid column of the fluid's colour, scaled to the fill level (bottom stays put). */
    void updateTank(KNode n) {
        float f = n.fluid == null ? 0 : Math.min(1f, (float) n.fluidAmount / plugin.settings().tankCapacity);
        for (DisplayPart p : n.displays) {
            if (p.role != Role.LIQUID || !p.entity.isValid()) continue;
            if (f <= 0.001f) {
                p.entity.setItemStack(new ItemStack(org.bukkit.Material.AIR));
                continue;
            }
            p.entity.setItemStack(plugin.items().displayItem(n.fluid.model()));
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(10);
            // the column model spans -6.5..6.5 px; scale it and keep its bottom at -6.5 px
            p.entity.setTransformation(new Transformation(new Vector3f(0, -6.5f / 16f * (1 - f), 0), p.orient, new Vector3f(1, f, 1), new Quaternionf()));
        }
    }

    /** Station name on both faces of the board. */
    private void stationText(KNode n) {
        String name = n.label == null ? "Station" : n.label;
        for (int side = 0; side < 2; side++) {
            BlockFace f = side == 0 ? n.facing : n.facing.getOppositeFace();
            org.bukkit.Location l = n.center().add(f.getModX() * 0.07, 0.28, f.getModZ() * 0.07);
            l.setYaw(switch (f) { case SOUTH -> 0f; case WEST -> 90f; case NORTH -> 180f; default -> 270f; });
            org.bukkit.entity.TextDisplay t = n.world.spawn(l, org.bukkit.entity.TextDisplay.class, d -> {
                d.setPersistent(false);
                d.text(net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.BLACK));
                d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setLineWidth(120);
                float scale = name.length() > 14 ? 0.35f : name.length() > 9 ? 0.45f : 0.55f;
                d.setTransformation(new Transformation(ZERO, new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
                d.setViewRange(plugin.settings().viewRange);
                d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
            n.labels.add(t);
        }
    }

    /** Clutch/gearshift: lamp on the casing shows the redstone state. */
    void updateCasing(KNode n) {
        for (DisplayPart p : n.displays) {
            if (p.role == Role.CASING && p.entity.isValid()) p.entity.setItemStack(plugin.items().displayItem(casingModel(n)));
        }
    }

    private void apply(KNode n, Network net, int duration) {
        double a = angle(n, net);
        Vector3f shift = n.whiskDrop == 0 ? ZERO : new Vector3f(0, -n.whiskDrop, 0); // mixer whisk down in the kettle
        for (DisplayPart p : n.displays) {
            if ((p.role != Role.SPIN && p.role != Role.SPIN_B) || !p.entity.isValid()) continue;
            // the far half of a gearshift turns at speed x its port factor (reversed when powered)
            double half = p.role == Role.SPIN_B && n.type == PartType.GEARSHIFT && n.powered ? -a : a;
            Quaternionf spin = new Quaternionf().rotateY((float) Math.toRadians((half % 360) * p.spinSign));
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(duration);
            p.entity.setTransformation(new Transformation(shift, p.orient, new Vector3f(p.scale, p.scale, p.scale), spin));
        }
    }

    /** Re-sends the turning parts now (e.g. the mixer whisk moved up/down). */
    void refreshSpin(KNode n, int duration) {
        if (n.network != null) apply(n, n.network, duration);
    }

    /** Kettle: liquid surface height follows the water (hidden when empty). */
    void updateKettle(KNode n) {
        for (DisplayPart p : n.displays) {
            if (p.role != Role.LIQUID || !p.entity.isValid()) continue;
            if (n.kettleWater <= 0) {
                p.entity.setItemStack(new ItemStack(org.bukkit.Material.AIR));
                continue;
            }
            p.entity.setItemStack(plugin.items().displayItem("sw_kettle_water"));
            float level = (float) n.kettleWater / Mixers.MAX_WATER; // surface model sits at the bottom of the pot
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(10);
            p.entity.setTransformation(new Transformation(new Vector3f(0, level * 0.6f, 0), p.orient, ONE, new Quaternionf()));
        }
    }

    /** Hammer blow: the head drops fast, then rises again before the next strike. */
    void strike(KNode n, float depth, int interval) {
        int rise = Math.max(2, Math.min(12, interval - 5));
        for (DisplayPart p : n.displays) {
            if (p.role != Role.HEAD || !p.entity.isValid()) continue;
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(2);
            p.entity.setTransformation(new Transformation(new Vector3f(0, -depth, 0), p.orient, ONE, new Quaternionf()));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.entity.isValid()) return;
                p.entity.setInterpolationDelay(0);
                p.entity.setInterpolationDuration(rise);
                p.entity.setTransformation(new Transformation(ZERO, p.orient, ONE, new Quaternionf()));
            }, 3L);
        }
    }

    private void needle(KNode n, Network net) {
        if (!n.type.isGauge()) return;
        Settings cfg = plugin.settings();
        double f;
        if (n.type == PartType.SPEED_GAUGE) {
            f = Math.sqrt(Math.min(1, Math.abs(n.speed()) / cfg.maxRpm));
        } else if (net.jammed && net.reason.startsWith("Overstressed")) {
            f = 1;
        } else {
            f = net.capacity > 0 ? Math.min(1, net.load / net.capacity) : 0;
        }
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.toRadians(225 - 270 * f));
        for (DisplayPart p : n.displays) {
            if (p.role != Role.NEEDLE || !p.entity.isValid()) continue;
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(0); // a smooth sweep would take the short way round, through the dial gap
            p.entity.setTransformation(new Transformation(ZERO, p.orient, ONE, rot));
        }
    }
}
