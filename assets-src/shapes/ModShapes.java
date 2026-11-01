package com.itmecasey.aeroflux.registry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hit and collision bounds for the blocks that are not plain cubes, taken from the block models
 * in assets-src. Boxes are in 0-16 space and written for a block facing north; use
 * {@link #rotated(VoxelShape, Direction)} for blocks with a facing.
 */
public final class ModShapes {
    public static final VoxelShape COLLECTOR = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 9, 11),
            Block.box(1, 9, 1, 15, 12, 15));

    public static final VoxelShape TANK = Block.box(0, 0, 0, 16, 16, 16);

    public static final VoxelShape FLUX_FURNACE = Block.box(0, 0, 0, 16, 16, 16);

    public static final VoxelShape FLUX_GRINDER = Block.box(0, 0, 0, 16, 16, 16);

    public static final VoxelShape FLUX_SAWMILL = Shapes.or(
            Block.box(0, 0, 0, 16, 12, 16),
            Block.box(3, 12, 7, 13, 15, 9));

    public static final VoxelShape ALLOY_SMELTER = Block.box(0, 0, 0, 16, 16, 16);

    public static final VoxelShape FLUX_INFUSER = Shapes.or(
            Block.box(1, 0, 1, 15, 8, 15),
            Block.box(6, 8, 6, 10, 13, 10));

    public static final VoxelShape FLUX_ENCHANTER = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 10, 11),
            Block.box(2, 10, 2, 14, 12, 14));

    public static final VoxelShape FLUX_DISENCHANTER = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 10, 11),
            Block.box(2, 10, 2, 14, 12, 14));

    public static final VoxelShape PLANT_GROWER = Block.box(0, 0, 0, 16, 10, 16);

    public static final VoxelShape CHUNK_LOADER = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(5, 2, 5, 11, 13, 11),
            Block.box(2, 9, 2, 14, 11, 4),
            Block.box(2, 9, 12, 14, 11, 14),
            Block.box(2, 9, 4, 4, 11, 12),
            Block.box(12, 9, 4, 14, 11, 12));

    public static final VoxelShape PIPE_NODE = Block.box(0, 0, 0, 16, 16, 16);

    private ModShapes() {
    }

    /** Turns a north-facing shape to face the given horizontal direction. */
    public static VoxelShape rotated(VoxelShape north, Direction facing) {
        int quarterTurns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape result = north;
        for (int i = 0; i < quarterTurns; i++) {
            VoxelShape turned = Shapes.empty();
            for (var box : result.toAabbs()) {
                turned = Shapes.or(turned, Shapes.box(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX));
            }
            result = turned;
        }
        return result;
    }
}
