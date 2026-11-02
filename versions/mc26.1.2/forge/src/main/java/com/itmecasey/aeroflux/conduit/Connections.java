package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Works out which of a bundle's faces connect to something, by looking at the neighbors.
 *
 * <p>Each pipe joins the pipe of the same kind in a neighboring bundle on its own, whatever else
 * either bundle holds, unless either side has that face blocked; a machine or other block
 * attaches when it offers the capability the pipe carries. This only decides what is drawn, what
 * the wrench can target and where the transfer engine finds its endpoints; the moving itself
 * happens in the {@code transfer} package.
 *
 * <p>A redstone pipe has no capability to look for, so it attaches by what the block is: one that
 * gives off a signal ({@code isSignalSource}, such as a lever, button, repeater or redstone block) or
 * reports a comparator value ({@code hasAnalogOutputSignal}, such as a chest or a cauldron).
 * Blocks that only receive power (a lamp, a piston) attach only after the player sets that face
 * to EXPORT with the wrench, which is how a player says "power this one".
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

            BundleState neighbor = neighborState.getBlock() instanceof ConduitBundleBlock
                    && level.getBlockEntity(neighborPos) instanceof ConduitBundleBlockEntity entity ? entity.bundle() : null;

            for (PipeType type : bundle.set().types()) {
                int lane = type.lane();
                if (neighbor != null) {
                    // Pipe to pipe: the same kind on the other side, and neither side blocked.
                    boolean joins = neighbor.set().contains(type)
                            && neighbor.mode(lane, face.opposite()) != ConnectionMode.BLOCKED;
                    bundle.setConnection(lane, face, joins, false);
                } else {
                    boolean attaches = type.connectsToSides()
                            && attaches(level, neighborPos, direction.getOpposite(), type, bundle.mode(lane, face));
                    bundle.setConnection(lane, face, attaches, true);
                }
            }
        }
        return !before.equals(bundle);
    }

    /** Whether the block at {@code pos} offers what this kind of pipe carries on the given side. */
    private static boolean attaches(Level level, BlockPos pos, Direction side, PipeType type, ConnectionMode mode) {
        if (type == PipeType.REDSTONE) {
            return attachesToRedstone(level.getBlockState(pos), mode);
        }
        // Forge 1.21.1 has no level-wide capability lookup; capabilities live on block entities.
        BlockEntity neighbor = level.getBlockEntity(pos);
        if (neighbor == null) {
            return false;
        }
        return switch (type) {
            case FLUID -> neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent();
            case ITEM -> neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent();
            case ENERGY -> neighbor.getCapability(ForgeCapabilities.ENERGY, side).isPresent();
            // ME pipes attach to anything with an AE2 grid node on that side (cables, drives, interfaces).
            case ME, DENSE_ME -> Ae2Compat.attaches(level, pos, side);
            // Gas has no capability to look for until Mekanism is out for this version.
            default -> false;
        };
    }

    /**
     * Whether a redstone pipe attaches to this block. Other conduits never count: bundles pass
     * signals through the network, not through each other, and counting them would let a pipe read
     * its own output back.
     */
    private static boolean attachesToRedstone(BlockState state, ConnectionMode mode) {
        if (state.getBlock() instanceof ConduitBundleBlock) {
            return false;
        }
        return state.isSignalSource() || state.hasAnalogOutputSignal() || mode == ConnectionMode.EXPORT;
    }
}
