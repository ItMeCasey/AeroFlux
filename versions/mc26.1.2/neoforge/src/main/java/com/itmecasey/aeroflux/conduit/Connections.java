package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Works out which of a bundle's faces connect to something, by looking at the neighbors.
 *
 * <p>Two pipe blocks join only when they hold the same set of pipes; a pipe node joins
 * anything; and a machine or other block attaches when it offers the capability the pipe
 * carries. Nothing is transferred yet: this only decides what is drawn and what the wrench
 * can target.
 */
public final class Connections {
    private Connections() {
    }

    /**
     * Recomputes every connection of a bundle from its surroundings.
     *
     * @return true if anything changed
     */
    public static boolean refresh(Level level, BlockPos pos, BundleState bundle) {
        BundleState before = bundle.copy();
        for (Face face : Face.values()) {
            Direction direction = Direction.from3DDataValue(face.index());
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

            boolean sameSetBundle = neighborState.getBlock() instanceof ConduitBundleBlock
                    && level.getBlockEntity(neighborPos) instanceof ConduitBundleBlockEntity neighbor
                    && neighbor.bundle().set().equals(bundle.set());
            boolean node = neighborState.getBlock() instanceof PipeNodeBlock;

            for (PipeType type : bundle.set().types()) {
                int lane = type.lane();
                if (sameSetBundle || node) {
                    bundle.setConnection(lane, face, true, false);
                } else {
                    boolean attaches = type.connectsToSides() && attaches(level, neighborPos, direction.getOpposite(), type);
                    bundle.setConnection(lane, face, attaches, true);
                }
            }
        }
        return !before.equals(bundle);
    }

    /** Whether the block at {@code pos} offers what this kind of pipe carries on the given side. */
    private static boolean attaches(Level level, BlockPos pos, Direction side, PipeType type) {
        return switch (type) {
            case FLUID -> level.getCapability(Capabilities.Fluid.BLOCK, pos, side) != null;
            case ITEM -> level.getCapability(Capabilities.Item.BLOCK, pos, side) != null;
            case ENERGY -> level.getCapability(Capabilities.Energy.BLOCK, pos, side) != null;
            // Redstone, gas and ME have nothing to attach to until their support is built.
            default -> false;
        };
    }
}
