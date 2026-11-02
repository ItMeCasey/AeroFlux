package com.itmecasey.aeroflux.core.conduit;

import java.util.List;

/**
 * The shape rules for pipes in a bundle, in pixels.
 *
 * <p>Every pipe is a 4 pixel square. A bundle of up to six pipes has to run straight along any of
 * the three axes and also turn corners, so each pipe gets a fixed position in all three
 * dimensions, not a flat slot. A pipe is a core cube at that position plus an arm from the core
 * to each connected face; an arm keeps the core's other two coordinates.
 *
 * <p>Positions are chosen so that, for every pair of pipes, at least two of the three coordinates
 * differ by 5 pixels or more. Looking along any axis, no two pipes then touch and each pair keeps
 * a pixel of clear space, so straight runs are clean in every direction, and an arm never runs
 * through another pipe's core. Where two pipes also differ on the third coordinate, their arms
 * cannot cross even at a corner. Up to three pipes get that everywhere; with four to six pipes
 * the lattice runs out of room and some corner combinations of different pipes still cross.
 */
public final class PipeGeometry {
    /** Width, height and depth of a pipe, in pixels. */
    public static final double PIPE_SIZE = 4.0;
    /** How deep a machine connector reaches into the block, in pixels. */
    public static final double CONNECTOR_DEPTH = 2.0;

    private static final double HALF = PIPE_SIZE / 2.0;

    /**
     * Core center positions by pipe count, as x, y, z in pixels. Each set is centered as a group
     * (its extent is symmetric around the block center), and pipes take their positions in lane
     * order. The search that found these is described in the class comment.
     */
    private static final double[][][] LAYOUTS = {
        {},
        {{8, 8, 8}},
        {{5.5, 5.5, 5.5}, {10.5, 10.5, 10.5}},
        {{3, 3, 3}, {8, 8, 8}, {13, 13, 13}},
        {{3, 3, 3}, {3, 8, 8}, {8, 3, 8}, {13, 13, 13}},
        {{3, 3, 3}, {3, 8, 8}, {8, 3, 8}, {8, 8, 3}, {13, 13, 13}},
        {{3, 3, 3}, {3, 8, 8}, {8, 3, 8}, {8, 13, 13}, {13, 8, 13}, {13, 13, 3}},
    };

    private PipeGeometry() {
    }

    /**
     * Where a pipe's core is centered.
     *
     * @param x pixels from the west edge of the block
     * @param y pixels from the bottom
     * @param z pixels from the north edge
     */
    public record Slot(double x, double y, double z) {
    }

    /** The positions for a bundle of {@code count} pipes, in the order pipes take them. */
    public static List<Slot> layout(int count) {
        if (count < 0 || count >= LAYOUTS.length) {
            throw new IllegalArgumentException("A bundle holds at most 6 pipes, not " + count);
        }
        return java.util.Arrays.stream(LAYOUTS[count]).map(p -> new Slot(p[0], p[1], p[2])).toList();
    }

    /** The core cube of a pipe at the given slot. */
    public static Box core(Slot slot) {
        return new Box(slot.x() - HALF, slot.y() - HALF, slot.z() - HALF,
                slot.x() + HALF, slot.y() + HALF, slot.z() + HALF);
    }

    /**
     * The arm of a pipe toward one face.
     *
     * @param slot     where the pipe sits
     * @param face     the direction the arm points
     * @param external whether the arm ends at a machine connector instead of the block edge
     */
    public static Box arm(Slot slot, Face face, boolean external) {
        Box core = core(slot);
        double low = external ? CONNECTOR_DEPTH : 0.0;
        double high = 16.0 - low;
        return switch (face) {
            case NORTH -> new Box(core.minX(), core.minY(), low, core.maxX(), core.maxY(), core.minZ());
            case SOUTH -> new Box(core.minX(), core.minY(), core.maxZ(), core.maxX(), core.maxY(), high);
            case WEST -> new Box(low, core.minY(), core.minZ(), core.minX(), core.maxY(), core.maxZ());
            case EAST -> new Box(core.maxX(), core.minY(), core.minZ(), high, core.maxY(), core.maxZ());
            case DOWN -> new Box(core.minX(), low, core.minZ(), core.maxX(), core.minY(), core.maxZ());
            case UP -> new Box(core.minX(), core.maxY(), core.minZ(), core.maxX(), high, core.maxZ());
        };
    }

    /**
     * The flat connector placed where a bundle meets a machine: {@value #CONNECTOR_DEPTH} pixels
     * deep from the block face, and just big enough to cover every pipe's cross-section there.
     */
    public static Box connector(int pipeCount, Face face) {
        List<Slot> slots = layout(pipeCount);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Slot slot : slots) {
            Box core = core(slot);
            minX = Math.min(minX, core.minX());
            maxX = Math.max(maxX, core.maxX());
            minY = Math.min(minY, core.minY());
            maxY = Math.max(maxY, core.maxY());
            minZ = Math.min(minZ, core.minZ());
            maxZ = Math.max(maxZ, core.maxZ());
        }
        double near = CONNECTOR_DEPTH;
        double far = 16.0 - CONNECTOR_DEPTH;
        return switch (face) {
            case NORTH -> new Box(minX, minY, 0, maxX, maxY, near);
            case SOUTH -> new Box(minX, minY, far, maxX, maxY, 16);
            case WEST -> new Box(0, minY, minZ, near, maxY, maxZ);
            case EAST -> new Box(far, minY, minZ, 16, maxY, maxZ);
            case DOWN -> new Box(minX, 0, minZ, maxX, near, maxZ);
            case UP -> new Box(minX, far, minZ, maxX, 16, maxZ);
        };
    }
}
