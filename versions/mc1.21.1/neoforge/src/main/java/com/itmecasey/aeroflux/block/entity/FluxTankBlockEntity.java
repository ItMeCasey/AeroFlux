package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick.
 */
public class FluxTankBlockEntity extends BlockEntity {
    private final FluidTank tank;
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        int capacity = AeroFluxConfig.tankCapacity(((FluxTankBlock) state.getBlock()).tier());
        this.tank = new FluidTank(capacity, stack -> ModFluids.isAnyFlux(stack.getFluid())) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update); it still takes nothing more, and probes such as Jade, which refuse a fill above
             * 100%, see a full tank instead of crashing.
             */
            @Override
            public int getCapacity() {
                return Math.max(super.getCapacity(), getFluidAmount());
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
                syncIfPictureChanged();
            }
        };
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    public FluidTank tank() {
        return tank;
    }

    /** Tells clients about the tank, but only when what they would draw has changed. */
    private void syncIfPictureChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        FluxType found = ModFluids.fluxTypeOf(tank.getFluid().getFluid());
        FluxType type = found == null ? FluxType.AERO : found;
        int key = FillLevel.syncKey(tank.getFluidAmount(), tank.getCapacity(), type);
        if (key != syncedKey) {
            syncedKey = key;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
