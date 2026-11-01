package com.itmecasey.aeroflux.core.conduit;

/**
 * The six faces of a block, in the same order as Minecraft's {@code Direction} (down, up,
 * north, south, west, east), so {@link #index()} converts directly to and from a direction's
 * 3D data value.
 */
public enum Face {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Face(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** Position in the six-face order; matches {@code Direction#get3DDataValue}. */
    public int index() {
        return ordinal();
    }

    public int stepX() {
        return dx;
    }

    public int stepY() {
        return dy;
    }

    public int stepZ() {
        return dz;
    }

    /** The face on the other side of the block. */
    public Face opposite() {
        return values()[ordinal() ^ 1];
    }

    /** The face for a 3D data value (0 to 5). */
    public static Face byIndex(int index) {
        return values()[index];
    }
}
