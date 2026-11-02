package com.itmecasey.aeroflux.conduit.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up what a machine offers a pipe. Forge capabilities hang off block entities, so a block
 * without one simply offers nothing.
 */
final class MachineCapabilities {
    private MachineCapabilities() {
    }

    /**
     * The capability of the block entity at {@code pos} as seen from {@code side}.
     *
     * @return the handler, or {@code null} if there is no block entity or it does not offer it
     */
    @Nullable
    static <T> T find(ServerLevel level, BlockPos pos, Direction side, Capability<T> capability) {
        BlockEntity machine = level.getBlockEntity(pos);
        return machine == null ? null : machine.getCapability(capability, side).resolve().orElse(null);
    }
}
