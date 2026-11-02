package com.itmecasey.aeroflux.core.conduit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where everything in a conduit bundle is drawn, in pixels, worked out the way Ender IO lays out
 * its conduits.
 *
 * <p>Pipes are 2 pixel arms that meet in 3 pixel cores, and neighboring pipes sit 3 pixels apart.
 * A pipe has no fixed place in the bundle. Instead, for each face, the pipes connected through that
 * face are sorted (by lane) and spread over the face in a small pattern; a pipe's arm toward that
 * face sits at its spot in the pattern. Because the bundle on the other side sees the same pipes
 * connected through the shared face and sorts them the same way, the arms of two neighbors always
 * line up, whatever else either bundle holds.
 *
 * <p>A pipe that runs straight keeps the same spot on both ends and gets a single core there. A
 * pipe whose arms sit at different spots (it turns, or it changes position because the set of
 * pipes around it changes) cannot be joined by one core, so every such spot goes into one
 * junction box drawn around them. That box is the bundle's junction; it covers only the spots
 * involved, so a straight pipe beside it keeps its own core.
 *
 * <p>Where pipes meet a machine, the face gets one shared plate, and each pipe that attaches there
 * gets a small plug at its spot.
 */
public final class ConduitLayout {
    /** Width of a pipe arm. */
    public static final double PIPE_SIZE = 2.0;
    /** Width of a pipe core, and the size of one junction cell. */
    public static final double CORE_SIZE = 3.0;
    /** Distance between the centers of two neighboring pipes. */
    public static final double STEP = 3.0;
    /** Width of the plate where pipes meet a machine. */
    public static final double PLATE_SIZE = 11.0;
    /** Width of the inner raised part of that plate. */
    public static final double PLATE_INNER_SIZE = 7.0;
    /** How far each layer of the plate stands off the block face. */
    public static final double PLATE_LAYER = 0.5;
    /** Width of a pipe's plug on the plate. */
    public static final double PLUG_SIZE = 3.0;
    /** How deep a plug reaches into the block. */
    public static final double PLUG_DEPTH = 4.0;

    /** The spots on a face, by how many pipes share it; see {@link #spot(int, int)}. */
    private static final int[][] GRID = {
        {0, 0}, {0, -1}, {-1, 0}, {0, 1}, {1, 0}, {1, -1}, {-1, -1}, {-1, 1}, {1, 1},
    };

    /** What a piece of the bundle is, which decides how it is textured. */
    public enum Kind {
        /** The core of a pipe that has a single spot. */
        CORE,
        /** A pipe's arm toward one face. */
        ARM,
        /** The shared box around the spots of pipes that change position. */
        JUNCTION,
        /** A layer of the plate on a face that meets a machine. */
        PLATE,
        /** A pipe's plug on that plate. */
        PLUG,
    }

    /**
     * One box of the bundle.
     *
     * @param kind what it is
     * @param box  where it is
     * @param type the pipe it belongs to, or {@code null} for the junction and plates
     * @param face the face an arm, plate or plug points to, or {@code null}
     * @param axis the axis a pipe piece runs along (0 x, 1 y, 2 z), or -1 when it has none
     */
    public record Piece(Kind kind, Box box, PipeType type, Face face, int axis) {
    }

    /** A spot in the bundle, counted in steps from the center along x, y and z. */
    private record Cell(int x, int y, int z) {
        double centerX() {
            return 8.0 + STEP * x;
        }

        double centerY() {
            return 8.0 + STEP * y;
        }

        double centerZ() {
            return 8.0 + STEP * z;
        }
    }

    private final List<Piece> pieces;

    private ConduitLayout(List<Piece> pieces) {
        this.pieces = List.copyOf(pieces);
    }

    /** Every piece to draw, in a stable order. */
    public List<Piece> pieces() {
        return pieces;
    }

    /** Lays out a bundle from its pipes and connections. */
    public static ConduitLayout of(BundleState bundle) {
        List<PipeType> pipes = bundle.set().types();
        Map<PipeType, Map<Face, Cell>> arms = new EnumMap<>(PipeType.class);
        for (PipeType type : pipes) {
            arms.put(type, new EnumMap<>(Face.class));
        }
        // Spread the pipes connected through each face over that face.
        for (Face face : Face.values()) {
            List<PipeType> through = new ArrayList<>();
            for (PipeType type : pipes) {
                if (bundle.isConnected(type.lane(), face)) {
                    through.add(type);
                }
            }
            for (int i = 0; i < through.size(); i++) {
                arms.get(through.get(i)).put(face, onAxis(spot(i, through.size()), axisOf(face)));
            }
        }

        // Each pipe's spots: one per distinct arm position, or its idle spot when it has no arms.
        int mainAxis = mainAxis(bundle, pipes);
        Map<PipeType, Set<Cell>> spots = new EnumMap<>(PipeType.class);
        for (int i = 0; i < pipes.size(); i++) {
            PipeType type = pipes.get(i);
            Set<Cell> cells = new LinkedHashSet<>(arms.get(type).values());
            if (cells.isEmpty()) {
                cells.add(onAxis(spot(i, pipes.size()), mainAxis));
            }
            spots.put(type, cells);
        }

        // The junction covers every spot of a pipe that moves, plus any spot two still pipes share.
        List<Cell> junctionCells = new ArrayList<>();
        Map<Cell, PipeType> taken = new HashMap<>();
        for (PipeType type : pipes) {
            Set<Cell> cells = spots.get(type);
            if (cells.size() > 1) {
                junctionCells.addAll(cells);
            } else {
                Cell cell = cells.iterator().next();
                if (taken.putIfAbsent(cell, type) != null) {
                    junctionCells.add(cell);
                }
            }
        }
        Box junction = junctionCells.isEmpty() ? null : cellBounds(junctionCells);

        List<Piece> pieces = new ArrayList<>();
        if (junction != null) {
            pieces.add(new Piece(Kind.JUNCTION, junction, null, null, -1));
        }
        for (PipeType type : pipes) {
            Set<Cell> cells = spots.get(type);
            if (cells.size() == 1) {
                Box core = cube(cells.iterator().next(), CORE_SIZE);
                if (junction == null || !inside(core, junction)) {
                    pieces.add(new Piece(Kind.CORE, core, type, null, runningAxis(arms.get(type).keySet())));
                }
            }
            for (Map.Entry<Face, Cell> arm : arms.get(type).entrySet()) {
                pieces.add(new Piece(Kind.ARM, arm(arm.getValue(), arm.getKey()), type, arm.getKey(), axisOf(arm.getKey())));
            }
        }
        for (Face face : Face.values()) {
            boolean plated = false;
            for (PipeType type : pipes) {
                if (bundle.isConnected(type.lane(), face) && bundle.isExternal(type.lane(), face)) {
                    pieces.add(new Piece(Kind.PLUG, plug(arms.get(type).get(face), face), type, face, axisOf(face)));
                    plated = true;
                }
            }
            if (plated) {
                pieces.add(new Piece(Kind.PLATE, plate(face, PLATE_SIZE, 0.0), null, face, axisOf(face)));
                pieces.add(new Piece(Kind.PLATE, plate(face, PLATE_INNER_SIZE, PLATE_LAYER), null, face, axisOf(face)));
            }
        }
        return new ConduitLayout(pieces);
    }

    /**
     * The boxes to use for hit testing and collision. Arms are widened to a core's width there, so a
     * 2 pixel pipe is not fiddly to aim at.
     */
    public List<Box> hitBoxes() {
        List<Box> boxes = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            boxes.add(piece.kind() == Kind.ARM ? widen(piece.box(), piece.axis()) : piece.box());
        }
        return boxes;
    }

    /** The arm of a pipe toward a face, or {@code null} if it has none there. */
    public Box armOf(PipeType type, Face face) {
        for (Piece piece : pieces) {
            if (piece.kind() == Kind.ARM && piece.type() == type && piece.face() == face) {
                return widen(piece.box(), piece.axis());
            }
        }
        return null;
    }

    /** The plate on a face, or {@code null} if no pipe meets a machine there. */
    public Box plateOf(Face face) {
        for (Piece piece : pieces) {
            if (piece.kind() == Kind.PLATE && piece.face() == face) {
                return piece.box();
            }
        }
        return null;
    }

    /**
     * The pipe nearest a point, for wrench and tool targeting; ties go to the lower lane.
     *
     * @return the nearest pipe, or {@code null} if the bundle is empty
     */
    public PipeType nearestPipe(double x, double y, double z) {
        PipeType best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Piece piece : pieces) {
            if (piece.type() == null) {
                continue;
            }
            Box box = piece.kind() == Kind.ARM ? widen(piece.box(), piece.axis()) : piece.box();
            double distance = box.distanceTo(x, y, z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = piece.type();
            }
        }
        return best;
    }

    /**
     * The spot of pipe {@code index} out of {@code count} sharing a face, as two offsets across the
     * face. One pipe sits in the middle, two side by side, three on a diagonal, and four to nine fill
     * a 3 by 3 grid, edges first.
     */
    static int[] spot(int index, int count) {
        if (count <= 1) {
            return GRID[0];
        }
        if (count == 2) {
            return index == 0 ? GRID[1] : GRID[3];
        }
        if (count == 3) {
            return new int[][] {{-1, -1}, {0, 0}, {1, 1}}[index];
        }
        // Up to eight leave the middle free; a ninth pipe takes it.
        return count < 9 ? GRID[index + 1] : GRID[index];
    }

    /** Places a face offset on the two axes across {@code axis}, as Ender IO does. */
    private static Cell onAxis(int[] offset, int axis) {
        return switch (axis) {
            case 0 -> new Cell(0, offset[1], offset[0]);
            case 1 -> new Cell(offset[0], 0, offset[1]);
            default -> new Cell(offset[0], offset[1], 0);
        };
    }

    /** 0 for x, 1 for y, 2 for z. */
    static int axisOf(Face face) {
        return switch (face) {
            case WEST, EAST -> 0;
            case DOWN, UP -> 1;
            case NORTH, SOUTH -> 2;
        };
    }

    /**
     * The axis that pipes with no connections line up along: the axis of the last connected face
     * in face order, so a horizontal run wins over a vertical one, or z for a bundle with nothing
     * connected.
     */
    private static int mainAxis(BundleState bundle, List<PipeType> pipes) {
        int axis = 2;
        for (Face face : Face.values()) {
            for (PipeType type : pipes) {
                if (bundle.isConnected(type.lane(), face)) {
                    axis = axisOf(face);
                    break;
                }
            }
        }
        return axis;
    }

    /** The axis all of a pipe's arms share, or -1 for a lone core, a corner or a branch. */
    private static int runningAxis(Set<Face> faces) {
        int axis = -1;
        for (Face face : faces) {
            int armAxis = axisOf(face);
            if (axis >= 0 && axis != armAxis) {
                return -1;
            }
            axis = armAxis;
        }
        return axis;
    }

    private static Box cube(Cell cell, double size) {
        double half = size / 2.0;
        return new Box(cell.centerX() - half, cell.centerY() - half, cell.centerZ() - half,
                cell.centerX() + half, cell.centerY() + half, cell.centerZ() + half);
    }

    /** The smallest box of whole cells around all the given spots. */
    private static Box cellBounds(List<Cell> cells) {
        Box box = null;
        for (Cell cell : cells) {
            Box cube = cube(cell, CORE_SIZE);
            box = box == null ? cube : new Box(Math.min(box.minX(), cube.minX()), Math.min(box.minY(), cube.minY()),
                    Math.min(box.minZ(), cube.minZ()), Math.max(box.maxX(), cube.maxX()),
                    Math.max(box.maxY(), cube.maxY()), Math.max(box.maxZ(), cube.maxZ()));
        }
        return box;
    }

    private static boolean inside(Box inner, Box outer) {
        return inner.minX() >= outer.minX() && inner.minY() >= outer.minY() && inner.minZ() >= outer.minZ()
                && inner.maxX() <= outer.maxX() && inner.maxY() <= outer.maxY() && inner.maxZ() <= outer.maxZ();
    }

    /** A 2 pixel arm from the block face to the edge of the middle core slice. */
    private static Box arm(Cell cell, Face face) {
        double half = PIPE_SIZE / 2.0;
        double coreEdge = CORE_SIZE / 2.0;
        double x0 = cell.centerX() - half;
        double x1 = cell.centerX() + half;
        double y0 = cell.centerY() - half;
        double y1 = cell.centerY() + half;
        double z0 = cell.centerZ() - half;
        double z1 = cell.centerZ() + half;
        return switch (face) {
            case DOWN -> new Box(x0, 0, z0, x1, 8 - coreEdge, z1);
            case UP -> new Box(x0, 8 + coreEdge, z0, x1, 16, z1);
            case NORTH -> new Box(x0, y0, 0, x1, y1, 8 - coreEdge);
            case SOUTH -> new Box(x0, y0, 8 + coreEdge, x1, y1, 16);
            case WEST -> new Box(0, y0, z0, 8 - coreEdge, y1, z1);
            case EAST -> new Box(8 + coreEdge, y0, z0, 16, y1, z1);
        };
    }

    /** A pipe's plug: a core-wide square reaching {@link #PLUG_DEPTH} in from the face. */
    private static Box plug(Cell cell, Face face) {
        double half = PLUG_SIZE / 2.0;
        return slab(face, cell.centerX() - half, cell.centerY() - half, cell.centerZ() - half,
                cell.centerX() + half, cell.centerY() + half, cell.centerZ() + half, 0.0, PLUG_DEPTH);
    }

    /** One layer of the plate: a centered square from {@code from} to one layer deeper. */
    private static Box plate(Face face, double size, double from) {
        double low = 8.0 - size / 2.0;
        double high = 8.0 + size / 2.0;
        return slab(face, low, low, low, high, high, high, from, from + PLATE_LAYER);
    }

    /**
     * A box that spans the given square across the face and reaches from {@code near} to
     * {@code far} pixels in from that face.
     */
    private static Box slab(Face face, double x0, double y0, double z0, double x1, double y1, double z1,
                            double near, double far) {
        return switch (face) {
            case DOWN -> new Box(x0, near, z0, x1, far, z1);
            case UP -> new Box(x0, 16 - far, z0, x1, 16 - near, z1);
            case NORTH -> new Box(x0, y0, near, x1, y1, far);
            case SOUTH -> new Box(x0, y0, 16 - far, x1, y1, 16 - near);
            case WEST -> new Box(near, y0, z0, far, y1, z1);
            case EAST -> new Box(16 - far, y0, z0, 16 - near, y1, z1);
        };
    }

    /** An arm widened to a core's width across its axis, for easier aiming. */
    private static Box widen(Box box, int axis) {
        double grow = (CORE_SIZE - PIPE_SIZE) / 2.0;
        return new Box(axis == 0 ? box.minX() : box.minX() - grow, axis == 1 ? box.minY() : box.minY() - grow,
                axis == 2 ? box.minZ() : box.minZ() - grow, axis == 0 ? box.maxX() : box.maxX() + grow,
                axis == 1 ? box.maxY() : box.maxY() + grow, axis == 2 ? box.maxZ() : box.maxZ() + grow);
    }
}
