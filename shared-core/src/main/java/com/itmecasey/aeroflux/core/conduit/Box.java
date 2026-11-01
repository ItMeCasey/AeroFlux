package com.itmecasey.aeroflux.core.conduit;

/**
 * An axis-aligned box in block pixel space, where one block is 16 by 16 by 16 pixels.
 *
 * @param minX west edge
 * @param minY bottom edge
 * @param minZ north edge
 * @param maxX east edge
 * @param maxY top edge
 * @param maxZ south edge
 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /** Straight-line distance from a point to the nearest point of the box; 0 if inside. */
    public double distanceTo(double x, double y, double z) {
        double dx = Math.max(Math.max(minX - x, 0.0), x - maxX);
        double dy = Math.max(Math.max(minY - y, 0.0), y - maxY);
        double dz = Math.max(Math.max(minZ - z, 0.0), z - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Whether the two boxes share volume; boxes that only touch do not count. */
    public boolean overlaps(Box other) {
        return minX < other.maxX && maxX > other.minX
                && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
    }
}
