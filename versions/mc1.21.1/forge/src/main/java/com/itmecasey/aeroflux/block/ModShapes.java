package com.itmecasey.aeroflux.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hit and collision bounds for blocks that are not full cubes. The boxes follow the block
 * models and are written in the usual 0-16 pixel space.
 */
public final class ModShapes {
    /** Base plate, stem and the wide rim under the dish. */
    public static final VoxelShape COLLECTOR = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 9, 11),
            Block.box(1, 9, 1, 15, 12, 15));

    /** The tank frame and glass fill the whole block. */
    public static final VoxelShape TANK = Block.box(0, 0, 0, 16, 16, 16);

    private ModShapes() {
    }
}
