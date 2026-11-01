package com.itmecasey.aeroflux.core.conduit;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape rules for pipes in a bundle, in pixels.
 *
 * <p>Every pipe is 4 by 4 pixels. The pipes of a block are centered as a group and the layout
 * depends only on how many there are, so equal sets always line up from block to block. A
 * pipe's core sits at its slot in the plane facing north and south; its arms run straight from
 * the core to the block face (or to a machine connector) in each connected direction.
 *
 * <p>Arms toward the sides and up and down keep the core's other coordinates, so a bundle that
 * only runs north to south has parallel, evenly spaced pipes. Where a bundle bends or branches,
 * arms of different pipes can overlap; resolving that is left to a later pass.
 */
public final class PipeGeometry {
    /** Width and height of a pipe, in pixels. */
    public static final double PIPE_SIZE = 4.0;
    /** How deep a machine connector reaches into the block, in pixels. */
    public static final double CONNECTOR_DEPTH = 2.0;

    private static final double CENTER = 8.0;
    private static final double HALF = PIPE_SIZE / 2.0;
    /**
     * Distance from the block center to a pipe center in the five and six pipe rings. Pipes are
     * squares, not circles, so neighbors only clear each other by the required pixel once the
     * ring is a little wider than the plan's rough 5 pixels (about 5.3 and 5.8).
     */
    private static final double RING_RADIUS_FIVE = 5.3;
    private static final double RING_RADIUS_SIX = 5.8;

    private PipeGeometry() {
    }

    /**
     * A pipe's position relative to the block center.
     *
     * @param u pixels to the east (right when looking north)
     * @param v pixels up
     */
    public record Slot(double u, double v) {
    }

    /**
     * The slots for a bundle of {@code count} pipes, in the order pipes fill them: for two,
     * left then right; otherwise clockwise starting at the top (or top left for four).
     */
    public static List<Slot> layout(int count) {
        return switch (count) {
            case 0 -> List.of();
            case 1 -> List.of(new Slot(0, 0));
            case 2 -> List.of(new Slot(-3, 0), new Slot(3, 0));
            case 3 -> List.of(new Slot(0, 3), new Slot(3, -3), new Slot(-3, -3));
            case 4 -> List.of(new Slot(-3, 3), new Slot(3, 3), new Slot(3, -3), new Slot(-3, -3));
            case 5, 6 -> ring(count);
            default -> throw new IllegalArgumentException("A bundle holds at most 6 pipes, not " + count);
        };
    }

    private static List<Slot> ring(int count) {
        double radius = count == 5 ? RING_RADIUS_FIVE : RING_RADIUS_SIX;
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            slots.add(new Slot(radius * Math.sin(angle), radius * Math.cos(angle)));
        }
        return slots;
    }

    /** The core cube of a pipe at the given slot. */
    public static Box core(Slot slot) {
        double x = CENTER + slot.u();
        double y = CENTER + slot.v();
        return new Box(x - HALF, y - HALF, CENTER - HALF, x + HALF, y + HALF, CENTER + HALF);
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
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Slot slot : slots) {
            Box core = core(slot);
            minX = Math.min(minX, core.minX());
            maxX = Math.max(maxX, core.maxX());
            minY = Math.min(minY, core.minY());
            maxY = Math.max(maxY, core.maxY());
        }
        double minZ = CENTER - HALF;
        double maxZ = CENTER + HALF;
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
