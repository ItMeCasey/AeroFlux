package com.itmecasey.aeroflux.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

/**
 * How AeroFlux reads another block's capabilities on Forge, where they hang off the block entity.
 * Its own blocks hand theirs out through {@code getCapability} on each block entity.
 */
public final class ModCapabilities {
    private ModCapabilities() {
    }

    /** The capability the block at {@code pos} offers on {@code side}, or {@code null}. */
    @Nullable
    public static <T> T find(Level level, Capability<T> capability, BlockPos pos, @Nullable Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(capability, side).orElse(null);
    }
}
