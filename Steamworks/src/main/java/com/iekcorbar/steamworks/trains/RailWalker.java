package com.iekcorbar.steamworks.trains;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.util.Vector;

/**
 * A point on vanilla rails: the rail block, the edge it came in through
 * ("from"), the edge it heads to ("to") and how far along that piece it is.
 * Straight and sloped pieces are straight lines between the edge middles,
 * curves are quarter circles (radius 0.5) round the block corner. Moving on
 * crosses into the next rail block the way a minecart would, including up
 * and down slopes. Every wheel of a train is one walker; they all move the
 * same distance, so they stay on the same path and keep their spacing.
 */
public final class RailWalker {
    /** Rail shape at a block, or null (no rail / chunk not loaded). */
    public interface Rails {
        Shape shape(int x, int y, int z);
    }

    public int x, y, z;
    public Shape shape;
    public BlockFace from, to;
    public double t;

    public RailWalker(int x, int y, int z, Shape shape, BlockFace from, BlockFace to, double t) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.shape = shape;
        this.from = from;
        this.to = to;
        this.t = t;
    }

    public RailWalker copy() {
        return new RailWalker(x, y, z, shape, from, to, t);
    }

    private void set(RailWalker o) {
        x = o.x; y = o.y; z = o.z; shape = o.shape; from = o.from; to = o.to; t = o.t;
    }

    // ------------------------------------------------------------ shapes

    /** The two edges a rail shape connects. */
    public static BlockFace[] ends(Shape s) {
        return switch (s) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case SOUTH_EAST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST};
            case SOUTH_WEST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST};
            case NORTH_WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.WEST};
            case NORTH_EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.EAST};
        };
    }

    static boolean connects(Shape s, BlockFace edge) {
        BlockFace[] e = ends(s);
        return e[0] == edge || e[1] == edge;
    }

    static BlockFace other(Shape s, BlockFace edge) {
        BlockFace[] e = ends(s);
        return e[0] == edge ? e[1] : e[0];
    }

    /** Height of an edge above the block's bottom: 1 for the high end of a slope. */
    static int height(Shape s, BlockFace edge) {
        return switch (s) {
            case ASCENDING_EAST -> edge == BlockFace.EAST ? 1 : 0;
            case ASCENDING_WEST -> edge == BlockFace.WEST ? 1 : 0;
            case ASCENDING_NORTH -> edge == BlockFace.NORTH ? 1 : 0;
            case ASCENDING_SOUTH -> edge == BlockFace.SOUTH ? 1 : 0;
            default -> 0;
        };
    }

    static boolean curve(Shape s) {
        return s == Shape.SOUTH_EAST || s == Shape.SOUTH_WEST || s == Shape.NORTH_WEST || s == Shape.NORTH_EAST;
    }

    static boolean sloped(Shape s) {
        return s.name().startsWith("ASCENDING");
    }

    public double length() {
        return curve(shape) ? Math.PI / 4 : sloped(shape) ? Math.sqrt(2) : 1;
    }

    // ------------------------------------------------------------ position

    private Vector edge(BlockFace e) {
        return new Vector(x + 0.5 + 0.5 * e.getModX(), y + height(shape, e), z + 0.5 + 0.5 * e.getModZ());
    }

    /** Point at distance tt along this piece (rail level = block bottom + height). */
    public Vector point(double tt) {
        double f = Math.max(0, Math.min(1, tt / length()));
        Vector a = edge(from), b = edge(to);
        if (!curve(shape)) return a.multiply(1 - f).add(b.multiply(f));
        double cx = x + 0.5 + 0.5 * (from.getModX() + to.getModX());
        double cz = z + 0.5 + 0.5 * (from.getModZ() + to.getModZ());
        double a0 = Math.atan2(a.getZ() - cz, a.getX() - cx), a1 = Math.atan2(b.getZ() - cz, b.getX() - cx);
        double d = a1 - a0;
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        double ang = a0 + d * f;
        return new Vector(cx + 0.5 * Math.cos(ang), y, cz + 0.5 * Math.sin(ang));
    }

    public Vector pos() {
        return point(t);
    }

    // ------------------------------------------------------------ moving

    /** Moves ds blocks along the track (negative = backwards). False (and unchanged) if the track ends. */
    public boolean move(double ds, Rails rails) {
        if (ds >= 0) return forward(ds, rails);
        reverse();
        boolean ok = forward(-ds, rails);
        reverse();
        return ok;
    }

    public void reverse() {
        BlockFace f = from;
        from = to;
        to = f;
        t = length() - t;
    }

    private boolean forward(double ds, Rails rails) {
        RailWalker w = copy();
        w.t += ds;
        int guard = 0;
        while (w.t > w.length()) {
            if (++guard > 64) return false;
            double left = w.t - w.length();
            int exitY = w.y + height(w.shape, w.to);
            int nx = w.x + w.to.getModX(), nz = w.z + w.to.getModZ();
            BlockFace entry = w.to.getOppositeFace();
            Shape s = rails.shape(nx, exitY, nz);
            int ny = exitY;
            if (s == null || !connects(s, entry) || height(s, entry) != 0) {
                s = rails.shape(nx, exitY - 1, nz); // down onto a slope whose high end faces us
                ny = exitY - 1;
                if (s == null || !connects(s, entry) || height(s, entry) != 1) return false;
            }
            w.x = nx;
            w.y = ny;
            w.z = nz;
            w.shape = s;
            w.from = entry;
            w.to = other(s, entry);
            w.t = left;
        }
        set(w);
        return true;
    }

    /** "x,y,z,SHAPE,FROM,TO,t" for trains.yml. */
    public String save() {
        return x + "," + y + "," + z + "," + shape.name() + "," + from.name() + "," + to.name() + "," + t;
    }

    public static RailWalker load(String s) {
        String[] p = s.split(",");
        return new RailWalker(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Shape.valueOf(p[3]),
                BlockFace.valueOf(p[4]), BlockFace.valueOf(p[5]), Double.parseDouble(p[6]));
    }
}
