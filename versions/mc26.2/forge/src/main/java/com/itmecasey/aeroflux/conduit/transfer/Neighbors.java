package com.itmecasey.aeroflux.conduit.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Looks up the capabilities of the machines next to a pipe. Forge has no level-wide lookup, so the
 * question goes to the neighbor's block entity, and a block without one offers nothing.
 */
final class Neighbors {
    private Neighbors() {
    }

    /**
     * The capability the machine at {@code pos} offers on {@code side}, or {@code null} if it has none.
     * The result is used within the same tick, so the {@code LazyOptional} is resolved right away.
     */
    static <T> T find(ServerLevel level, BlockPos pos, Direction side, Capability<T> capability) {
        BlockEntity machine = level.getBlockEntity(pos);
        return machine == null ? null : machine.getCapability(capability, side).orElse(null);
    }
}
