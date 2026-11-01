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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick.
 */
public class FluxTankBlockEntity extends BlockEntity {
    private final FluidStacksResourceHandler tank;
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        int capacity = AeroFluxConfig.tankCapacity(((FluxTankBlock) state.getBlock()).tier());
        // A single slot, so the tank can only ever hold one kind of flux.
        this.tank = new FluidStacksResourceHandler(1, capacity) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return ModFluids.isFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
                syncIfPictureChanged();
            }
        };
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    /** Tells clients about the tank, but only when what they would draw has changed. */
    private void syncIfPictureChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        FluidResource held = tank.getResource(0);
        FluxType type = held.getFluid().isSame(ModFluids.MOON_FLUX.get()) ? FluxType.MOON : FluxType.AERO;
        int key = FillLevel.syncKey(tank.getAmountAsLong(0), tank.getCapacityAsLong(0, held), type);
        if (key != syncedKey) {
            syncedKey = key;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank").ifPresent(tank::deserialize);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
