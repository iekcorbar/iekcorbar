package com.iekcorbar.steamworks;

import org.bukkit.Axis;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Offline checks of the kinetic maths (no server). */
public class KineticsTest {
    static int fails = 0;
    static final UUID ID = UUID.randomUUID();
    static final World W = (World) Proxy.newProxyInstance(KineticsTest.class.getClassLoader(), new Class[]{World.class},
            (p, m, a) -> m.getName().equals("getUID") ? ID : m.getName().equals("hashCode") ? 1 : m.getName().equals("equals") ? p == a[0] : null);

    static Settings cfg;
    static List<KNode> popped = new ArrayList<>();

    static Kinetics fresh() {
        popped.clear();
        Kinetics[] k = new Kinetics[1];
        k[0] = new Kinetics(new KineticsHost() {
            public Settings settings() { return cfg; }
            public void rebuilt(Network net) { }
            public void spin(Network net, int period) { }
            public void snapBreakerPin(KNode n) { popped.add(n); k[0].remove(n); }
        });
        return k[0];
    }

    static KNode put(Kinetics k, int x, int y, int z, PartType t, Axis a, BlockFace f) {
        KNode n = new KNode(W, x, y, z, t, a, f);
        k.add(n);
        return n;
    }

    static KNode put(Kinetics k, int x, int y, int z, PartType t, Axis a) {
        return put(k, x, y, z, t, a, BlockFace.UP);
    }

    static void check(String what, double got, double want) {
        boolean ok = Math.abs(got - want) < 1e-6;
        if (!ok) fails++;
        System.out.printf("%s %-55s got %8.3f want %8.3f%n", ok ? "OK  " : "FAIL", what, got, want);
    }

    static void check(String what, boolean got) {
        if (!got) fails++;
        System.out.printf("%s %s%n", got ? "OK  " : "FAIL", what);
    }

    public static void main(String[] args) {
        cfg = new Settings(YamlConfiguration.loadConfiguration(new File(args[0])));

        // 1. crank on top of a shaft, crank turns +16
        Kinetics k = fresh();
        KNode shaft = put(k, 0, 0, 0, PartType.SHAFT, Axis.Y);
        KNode crank = put(k, 0, 1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.UP);
        k.crank(crank, 1);
        k.processDirty();
        check("crank speed", crank.speed(), 16);
        check("shaft under crank", shaft.speed(), 16);
        check("crank network capacity 8x16", shaft.network.capacity, 128);

        // 2. gearbox axis Z (ports X and Y), input shaft from below
        k = fresh();
        KNode in = put(k, 0, -1, 0, PartType.SHAFT, Axis.Y);
        KNode c2 = put(k, 0, -2, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        KNode gb = put(k, 0, 0, 0, PartType.GEARBOX, Axis.Z);
        KNode east = put(k, 1, 0, 0, PartType.SHAFT, Axis.X);
        KNode west = put(k, -1, 0, 0, PartType.SHAFT, Axis.X);
        KNode up = put(k, 0, 1, 0, PartType.SHAFT, Axis.Y);
        KNode south = put(k, 0, 0, 1, PartType.SHAFT, Axis.Z); // not a port: must stay unconnected
        k.crank(c2, 1);
        k.processDirty();
        check("crank facing down turns -Y side: speed about +Y", c2.speed(), 16);
        check("input shaft", in.speed(), 16);
        check("gearbox east output (Create: +s)", east.speed(), 16);
        check("gearbox west output (Create: -s)", west.speed(), -16);
        check("gearbox straight through flips (Create: -s)", up.speed(), -16);
        check("closed gearbox side not connected", south.network != gb.network);

        // 3. cogs: side by side -1, large diagonal -0.5
        k = fresh();
        KNode cr = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        KNode a = put(k, 0, 0, 0, PartType.COGWHEEL, Axis.Y);
        KNode b = put(k, 1, 0, 0, PartType.COGWHEEL, Axis.Y);
        KNode big = put(k, 1, 0, 1, PartType.LARGE_COGWHEEL, Axis.Y);
        KNode cOther = put(k, 0, 0, 1, PartType.COGWHEEL, Axis.X); // other axis: no mesh
        k.crank(cr, 1);
        k.processDirty();
        check("cog A", a.speed(), 16);
        check("cog B beside A", b.speed(), -16);
        check("large cog diagonal to A", big.speed(), -8);
        check("cog with another axis is separate", cOther.network != a.network);
        check("cogs square loop not locked", !a.network.jammed);

        // 4. large-large at right angles: (axis Y) and (axis X) offset (1,1,0)
        k = fresh();
        KNode c4 = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        KNode l1 = put(k, 0, 0, 0, PartType.LARGE_COGWHEEL, Axis.Y);
        KNode l2 = put(k, 1, 1, 0, PartType.LARGE_COGWHEEL, Axis.X);
        KNode l3 = put(k, -1, 1, 0, PartType.LARGE_COGWHEEL, Axis.X);
        k.crank(c4, 1);
        k.processDirty();
        check("large-large (+1,+1): same sign", l2.speed(), 16);
        check("large-large (-1,+1): flipped", l3.speed(), -16);

        // 5. overstress: 2 millstones fine (128/128), 3 jam
        k = fresh();
        KNode c5 = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        put(k, 0, 0, 0, PartType.COGWHEEL, Axis.Y);
        put(k, 1, 0, 0, PartType.COGWHEEL, Axis.Y);
        KNode m1 = put(k, 0, 1, 0, PartType.MILLSTONE, Axis.Y);
        KNode m2 = put(k, 1, 1, 0, PartType.MILLSTONE, Axis.Y);
        k.crank(c5, 1);
        k.processDirty();
        check("2 millstones load", m1.network.load, 128);
        check("2 millstones running", !m1.network.jammed && Math.abs(m2.speed()) == 16);
        put(k, 2, 0, 0, PartType.COGWHEEL, Axis.Y);
        KNode m3 = put(k, 2, 1, 0, PartType.MILLSTONE, Axis.Y);
        k.processDirty();
        check("3 millstones overstressed", m3.network.jammed && m3.network.reason.startsWith("Overstressed"));
        check("jammed = speed 0", m1.speed(), 0);

        // 6. two cranks against each other on one shaft line
        k = fresh();
        KNode ca = put(k, 0, 1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.UP);
        put(k, 0, 0, 0, PartType.SHAFT, Axis.Y);
        KNode cb = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        k.crank(ca, 1);
        k.crank(cb, -1);
        k.processDirty();
        check("opposed sources jam", ca.network.jammed && ca.network.reason.contains("against"));
        k.crank(cb, 1);
        k.processDirty();
        check("same direction: runs", !ca.network.jammed && ca.speed() == 16);
        check("same direction: capacity adds up", ca.network.capacity, 256);

        // 7. speed-up chain until a breaker pin snaps: large drives small (x2) repeatedly
        k = fresh();
        KNode c7 = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        put(k, 0, 0, 0, PartType.LARGE_COGWHEEL, Axis.Y);
        List<KNode> smalls = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            smalls.add(put(k, s + 1, 2 * s, s + 1, PartType.COGWHEEL, Axis.Y)); // diagonal to large (s, 2s, s)
            put(k, s + 1, 2 * s + 1, s + 1, PartType.SHAFT, Axis.Y);
            put(k, s + 1, 2 * s + 2, s + 1, PartType.LARGE_COGWHEEL, Axis.Y);
        }
        k.crank(c7, 1);
        k.processDirty();
        check("stage speeds: first small x2", smalls.get(0).speed(), -32);
        check("something snapped from overspeed", !popped.isEmpty());
        check("the snapped part is the 4th small cog (256 RPM)", !popped.isEmpty() && popped.get(0) == smalls.get(3));
        check("after the snap the rest runs again", smalls.get(2).speed(), -128);

        // 8. steam hammer: driven from the side, passes rotation on, one crank = one hammer
        k = fresh();
        KNode c8 = put(k, 2, 0, 0, PartType.HAND_CRANK, Axis.X, BlockFace.EAST);
        put(k, 1, 0, 0, PartType.SHAFT, Axis.X);
        KNode h1 = put(k, 0, 0, 0, PartType.STEAM_HAMMER, Axis.X);
        KNode past = put(k, -1, 0, 0, PartType.SHAFT, Axis.X);
        KNode side = put(k, 0, 0, 1, PartType.SHAFT, Axis.Z); // not on the hammer's axis: unconnected
        k.crank(c8, 1);
        k.processDirty();
        check("hammer turns with the crank", Math.abs(h1.speed()), 16);
        check("hammer load 8x16 = crank capacity", h1.network.load, 128);
        check("one crank drives one hammer", !h1.network.jammed);
        check("shaft behind the hammer turns too", Math.abs(past.speed()), 16);
        check("hammer side without port not connected", side.network != h1.network);
        put(k, -2, 0, 0, PartType.STEAM_HAMMER, Axis.X);
        k.processDirty();
        check("second hammer on one crank overstresses", h1.network.jammed && h1.network.reason.startsWith("Overstressed"));

        // 9. clutch: passes rotation, a signal splits the line
        k = fresh();
        KNode c9 = put(k, 0, -1, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.DOWN);
        put(k, 0, 0, 0, PartType.SHAFT, Axis.Y);
        KNode clutch = put(k, 0, 1, 0, PartType.CLUTCH, Axis.Y);
        KNode beyond = put(k, 0, 2, 0, PartType.SHAFT, Axis.Y);
        k.crank(c9, 1);
        k.processDirty();
        check("clutch off: far side turns", beyond.speed(), 16);
        k.setPowered(clutch, true);
        k.processDirty();
        check("clutch on: far side stops", beyond.speed(), 0);
        check("clutch on: far side is its own network", beyond.network != c9.network);
        check("clutch on: source side still turns", c9.speed(), 16);
        k.setPowered(clutch, false);
        k.processDirty();
        check("clutch off again: far side turns", beyond.speed(), 16);

        // 10. gearshift: a signal reverses the far (negative) side
        k = fresh();
        KNode c10 = put(k, 0, 2, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.UP);
        KNode top = put(k, 0, 1, 0, PartType.SHAFT, Axis.Y);
        KNode gs = put(k, 0, 0, 0, PartType.GEARSHIFT, Axis.Y);
        KNode bottom = put(k, 0, -1, 0, PartType.SHAFT, Axis.Y);
        k.crank(c10, 1);
        k.processDirty();
        check("gearshift off: same direction", bottom.speed(), 16);
        k.setPowered(gs, true);
        k.processDirty();
        check("gearshift on: far side reversed", bottom.speed(), -16);
        check("gearshift on: near side unchanged", top.speed(), 16);
        check("gearshift on: not jammed", !gs.network.jammed);

        // 11. bellows fan: powered only from behind, load 4 x speed
        k = fresh();
        KNode c11 = put(k, -2, 0, 0, PartType.HAND_CRANK, Axis.X, BlockFace.WEST);
        put(k, -1, 0, 0, PartType.SHAFT, Axis.X);
        KNode fan = put(k, 0, 0, 0, PartType.BELLOWS_FAN, Axis.X, BlockFace.EAST); // blows east, back = west
        KNode front = put(k, 1, 0, 0, PartType.SHAFT, Axis.X); // in front: no port
        k.crank(c11, 1);
        k.processDirty();
        check("fan turns from behind", Math.abs(fan.speed()), 16);
        check("fan load 4x16", fan.network.load, 64);
        check("shaft in front of the fan not connected", front.network != fan.network);

        // 12. mixer powered from above, kettle under it is not kinetic
        k = fresh();
        KNode c12 = put(k, 0, 3, 0, PartType.HAND_CRANK, Axis.Y, BlockFace.UP);
        put(k, 0, 2, 0, PartType.SHAFT, Axis.Y);
        KNode mixer = put(k, 0, 1, 0, PartType.MIXER, Axis.Y);
        KNode kettle = put(k, 0, 0, 0, PartType.KETTLE, Axis.Y);
        k.crank(c12, 1);
        k.processDirty();
        check("mixer turns from above", Math.abs(mixer.speed()), 16);
        check("mixer load 4x16", mixer.network.load, 64);
        check("kettle not part of the network", kettle.network != mixer.network && kettle.speed() == 0);

        // 13. pumpjack: shaft through the side, load 8 x speed; pipes/tanks are not kinetic
        k = fresh();
        KNode c13 = put(k, 2, 0, 0, PartType.HAND_CRANK, Axis.X, BlockFace.EAST);
        put(k, 1, 0, 0, PartType.SHAFT, Axis.X);
        KNode pj = put(k, 0, 0, 0, PartType.PUMPJACK, Axis.X);
        KNode pipe = put(k, -1, 0, 0, PartType.PIPE, Axis.Y);
        k.crank(c13, 1);
        k.processDirty();
        check("pumpjack turns", Math.abs(pj.speed()), 16);
        check("pumpjack load 8x16", pj.network.load, 128);
        check("pipe not in the kinetic network", pipe.network != pj.network);

        // 14. belt line: three pieces in a row run together, powered by a shaft at the side of one
        k = fresh();
        KNode c14 = put(k, 0, 0, 2, PartType.HAND_CRANK, Axis.Z, BlockFace.SOUTH);
        put(k, 0, 0, 1, PartType.SHAFT, Axis.Z);
        KNode b0 = put(k, 0, 0, 0, PartType.BELT, Axis.Z, BlockFace.EAST);
        KNode b1 = put(k, 1, 0, 0, PartType.BELT, Axis.Z, BlockFace.EAST);
        KNode b2 = put(k, 2, 0, 0, PartType.BELT, Axis.Z, BlockFace.EAST);
        KNode cross = put(k, 0, 0, -1, PartType.BELT, Axis.X, BlockFace.NORTH); // other axis: not part of the line
        k.crank(c14, 1);
        k.processDirty();
        check("belt piece with the shaft", Math.abs(b0.speed()), 16);
        check("belt line runs together", b2.speed() == b0.speed() && b1.speed() == b0.speed());
        check("crossing belt not in the line", cross.network != b0.network);

        // 15. chain drive: two sprockets 6 blocks apart turn together, unlinked = separate
        k = fresh();
        KNode c15 = put(k, 0, 0, 1, PartType.HAND_CRANK, Axis.Z, BlockFace.SOUTH);
        KNode s1 = put(k, 0, 0, 0, PartType.SPROCKET, Axis.Z);
        KNode s2 = put(k, 6, 2, 0, PartType.SPROCKET, Axis.Z);
        KNode out = put(k, 6, 2, -1, PartType.SHAFT, Axis.Z);
        s1.chain = new int[]{6, 2, 0};
        s2.chain = new int[]{-6, -2, 0};
        k.crank(c15, 1);
        k.processDirty();
        check("chained sprocket turns the same", s2.speed(), s1.speed());
        check("shaft behind the far sprocket turns", Math.abs(out.speed()), 16);
        s1.chain = null;
        s2.chain = null;
        k.relink(s1);
        k.relink(s2);
        k.processDirty();
        check("unchained: far side stops", out.speed(), 0);

        // 16. windmill and steam engine as sources (generated set by PowerPlants), crushing wheel pair
        k = fresh();
        KNode wm = put(k, 0, 0, 0, PartType.WINDMILL, Axis.Z, BlockFace.SOUTH); // sails south, shaft north
        KNode wShaft = put(k, 0, 0, -1, PartType.SHAFT, Axis.Z);
        k.setGenerated(wm, 8);
        k.processDirty();
        check("windmill drives the shaft behind it", Math.abs(wShaft.speed()), 8);
        check("windmill capacity 64 x 8", wm.network.capacity, 512);

        KNode eng = put(k, 5, 0, 0, PartType.STEAM_ENGINE, Axis.X);
        KNode cw1 = put(k, 6, 0, 0, PartType.SHAFT, Axis.X);
        k.setGenerated(eng, 24);
        k.processDirty();
        check("engine output", Math.abs(cw1.speed()), 24);
        check("engine capacity 64 x 24", eng.network.capacity, 1536);

        KNode w1 = put(k, 7, 0, 0, PartType.CRUSHING_WHEEL, Axis.X);
        KNode w2 = put(k, 7, 0, 1, PartType.CRUSHING_WHEEL, Axis.X);
        k.processDirty();
        check("crushing pair turns against each other", w1.speed() == -w2.speed() && w1.speed() != 0);
        check("crushing wheels load 8 x 24 each", eng.network.load, 2 * 8 * 24);

        // 17. cog collision rules = Create's CogWheelBlock.isValidCogwheelPosition
        k = fresh();
        KNode bigY = put(k, 0, 0, 0, PartType.LARGE_COGWHEEL, Axis.Y);
        check("large cog in free air fits", k.cogsFit(bigY));
        KNode nearSmall = new KNode(W, 1, 0, 0, PartType.COGWHEEL, Axis.Y, BlockFace.UP);
        check("small cog beside a large cog collides", !k.cogsFit(nearSmall));
        KNode bigX = put(k, 5, 0, 0, PartType.LARGE_COGWHEEL, Axis.X);   // its rim turns towards x=6
        KNode faceJoint = new KNode(W, 6, 0, 0, PartType.COGWHEEL, Axis.Y, BlockFace.UP);
        check("cog facing into the joint fits", k.cogsFit(faceJoint));
        KNode stackShaft = new KNode(W, 0, 1, 0, PartType.LARGE_COGWHEEL, Axis.Y, BlockFace.UP);
        check("large cog stacked on the same shaft fits", k.cogsFit(stackShaft));

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
