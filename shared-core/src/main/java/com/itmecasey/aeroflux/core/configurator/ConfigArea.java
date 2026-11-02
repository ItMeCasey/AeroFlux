package com.itmecasey.aeroflux.core.configurator;

import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import java.util.ArrayList;
import java.util.List;

/**
 * The box an area paste covers: every block between two corners the player clicked, both
 * included. Corners can be clicked in any order.
 *
 * <p>The box is limited to {@link #MAX_SIDE} blocks along each axis, which keeps the search for
 * machines inside it cheap; how many machines a paste may change is a separate limit the server
 * sets (see {@link #checkCount}).
 */
public record ConfigArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** The longest side an area may have, in blocks. */
    public static final int MAX_SIDE = 32;
    /** The default for how many machines one area paste may change; servers can set their own. */
    public static final int DEFAULT_LIMIT = 64;

    /** The box between two corners. */
    public static ConfigArea between(RunPlanner.Pos a, RunPlanner.Pos b) {
        return new ConfigArea(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** How many blocks the box holds. */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** Whether any side is longer than {@link #MAX_SIDE}. */
    public boolean tooLarge() {
        return sizeX() > MAX_SIDE || sizeY() > MAX_SIDE || sizeZ() > MAX_SIDE;
    }

    public boolean contains(RunPlanner.Pos pos) {
        return pos.x() >= minX && pos.x() <= maxX && pos.y() >= minY && pos.y() <= maxY
                && pos.z() >= minZ && pos.z() <= maxZ;
    }

    /** Every position in the box, x fastest, then z, then y (one layer at a time from the bottom). */
    public List<RunPlanner.Pos> positions() {
        List<RunPlanner.Pos> all = new ArrayList<>((int) Math.min(volume(), Integer.MAX_VALUE));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    all.add(new RunPlanner.Pos(x, y, z));
                }
            }
        }
        return all;
    }

    /** Whether an area paste over {@code machines} machines is allowed under a limit; 0 or less means no machines at all. */
    public static boolean checkCount(int machines, int limit) {
        return machines > 0 && machines <= limit;
    }

    /** A point in the world, in blocks, for drawing outlines. */
    public record Point(double x, double y, double z) {
    }

    /**
     * Points along the twelve edges of the box's outer surface, about {@code spacing} blocks apart
     * and always including the eight corners, for a particle outline.
     */
    public List<Point> outline(double spacing) {
        return outline(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0, spacing);
    }

    /** Points along the twelve edges of any box, given by its two opposite corners. */
    public static List<Point> outline(double x0, double y0, double z0, double x1, double y1, double z1, double spacing) {
        List<Point> points = new ArrayList<>();
        double[] xs = {x0, x1};
        double[] ys = {y0, y1};
        double[] zs = {z0, z1};
        // Four edges along each axis: one per pair of the other two axes' ends.
        for (double y : ys) {
            for (double z : zs) {
                line(points, x0, y, z, x1, y, z, spacing);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                line(points, x, y0, z, x, y1, z, spacing);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                line(points, x, y, z0, x, y, z1, spacing);
            }
        }
        return points;
    }

    private static void line(List<Point> into, double x0, double y0, double z0, double x1, double y1, double z1,
                             double spacing) {
        double length = Math.abs(x1 - x0) + Math.abs(y1 - y0) + Math.abs(z1 - z0);
        int steps = Math.max(1, (int) Math.ceil(length / Math.max(0.05, spacing)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            into.add(new Point(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t));
        }
    }
}
