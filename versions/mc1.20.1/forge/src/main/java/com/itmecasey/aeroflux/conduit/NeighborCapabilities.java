package com.itmecasey.aeroflux.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Looks up the capabilities a neighboring block offers. In 1.20.1 capabilities live on block
 * entities, so a plain block never offers any; the newer versions ask the level instead.
 */
public final class NeighborCapabilities {
    private NeighborCapabilities() {
    }

    /**
     * The capability the block at {@code pos} offers on {@code side}.
     *
     * @return the capability, or {@code null} if there is no block entity or it offers none there
     */
    public static <T> T find(Level level, BlockPos pos, Capability<T> capability, Direction side) {
        BlockEntity neighbor = level.getBlockEntity(pos);
        return neighbor == null ? null : neighbor.getCapability(capability, side).orElse(null);
    }

    /** Whether the block at {@code pos} offers the capability on {@code side}. */
    public static boolean offers(Level level, BlockPos pos, Capability<?> capability, Direction side) {
        BlockEntity neighbor = level.getBlockEntity(pos);
        return neighbor != null && neighbor.getCapability(capability, side).isPresent();
    }
}
