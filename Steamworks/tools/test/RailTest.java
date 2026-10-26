package com.iekcorbar.steamworks.trains;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/** Offline checks of RailWalker (no server). */
public class RailTest {
    static int fails = 0;
    static final Map<String, Shape> track = new HashMap<>();

    static void rail(int x, int y, int z, Shape s) {
        track.put(x + "," + y + "," + z, s);
    }

    static final RailWalker.Rails RAILS = (x, y, z) -> track.get(x + "," + y + "," + z);

    static void check(String what, boolean ok) {
        if (!ok) fails++;
        System.out.printf("%s %s%n", ok ? "OK  " : "FAIL", what);
    }

    static boolean near(Vector v, double x, double y, double z) {
        return Math.abs(v.getX() - x) < 1e-6 && Math.abs(v.getY() - y) < 1e-6 && Math.abs(v.getZ() - z) < 1e-6;
    }

    public static void main(String[] args) {
        // straight east 0..3 at y=0, curve at (4,0,0) east->south, straight south (4,0,1..2),
        // then a slope up south at (4,0,3) (high end SOUTH), flat at (4,1,4), slope down at (4,0,5) (high end NORTH)
        for (int x = 0; x <= 3; x++) rail(x, 0, 0, Shape.EAST_WEST);
        rail(4, 0, 0, Shape.SOUTH_WEST);
        rail(4, 0, 1, Shape.NORTH_SOUTH);
        rail(4, 0, 2, Shape.NORTH_SOUTH);
        rail(4, 0, 3, Shape.ASCENDING_SOUTH);
        rail(4, 1, 4, Shape.NORTH_SOUTH);
        rail(4, 0, 5, Shape.ASCENDING_NORTH);
        rail(4, 0, 6, Shape.NORTH_SOUTH);

        RailWalker w = new RailWalker(0, 0, 0, Shape.EAST_WEST, BlockFace.WEST, BlockFace.EAST, 0.5);
        check("start in block middle", near(w.pos(), 0.5, 0, 0.5));
        check("move 3 east", w.move(3, RAILS) && near(w.pos(), 3.5, 0, 0.5));
        check("into the curve", w.move(0.5 + Math.PI / 8, RAILS) && w.x == 4 && w.shape == Shape.SOUTH_WEST);
        Vector mid = w.pos();
        double r = Math.hypot(mid.getX() - 4.0, mid.getZ() - 1.0);
        check("curve point on radius 0.5 round corner (4,1)", Math.abs(r - 0.5) < 1e-6);
        check("out of the curve heading south", w.move(Math.PI / 8 + 0.5, RAILS) && w.z == 1 && w.to == BlockFace.SOUTH
                && near(w.pos(), 4.5, 0, 1.5));
        check("up the slope", w.move(1.5 + Math.sqrt(2) / 2, RAILS) && w.z == 3 && Math.abs(w.pos().getY() - 0.5) < 1e-6);
        check("onto the raised flat piece", w.move(Math.sqrt(2) / 2 + 0.5, RAILS) && w.y == 1 && w.z == 4
                && near(w.pos(), 4.5, 1, 4.5));
        check("down the slope", w.move(0.5 + Math.sqrt(2) / 2, RAILS) && w.y == 0 && w.z == 5
                && Math.abs(w.pos().getY() - 0.5) < 1e-6);
        check("end of track stops", !w.move(5, RAILS) && w.z == 5);

        // backwards retraces the same path
        RailWalker back = w.copy();
        double total = Math.sqrt(2) / 2 + 1 + Math.sqrt(2) + 2 + Math.PI / 4 + 3.5;
        check("drive back through slopes and curve to x=0", back.move(-total, RAILS) && near(back.pos(), 0.5, 0, 0.5));
        RailWalker a = new RailWalker(1, 0, 0, Shape.EAST_WEST, BlockFace.WEST, BlockFace.EAST, 0.5);
        RailWalker b = a.copy();
        a.move(4, RAILS);
        a.move(-4, RAILS);
        check("forward 4 then back 4 = same point", near(a.pos(), b.pos().getX(), b.pos().getY(), b.pos().getZ()));

        // two wheels 1.2 apart keep their distance (along the track) through the curve
        RailWalker f = new RailWalker(2, 0, 0, Shape.EAST_WEST, BlockFace.WEST, BlockFace.EAST, 0.5);
        RailWalker rr = f.copy();
        rr.move(-1.2, RAILS);
        boolean ok = true;
        for (int i = 0; i < 40; i++) {
            if (!f.move(0.1, RAILS) || !rr.move(0.1, RAILS)) break;
            double d = f.pos().distance(rr.pos());
            if (d > 1.2 + 1e-6 || d < 0.9) ok = false;
        }
        check("wheel distance stays sane through the curve", ok);

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
