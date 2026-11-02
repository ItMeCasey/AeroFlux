package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.fluid.TankContents;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModDataComponents;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick.
 */
public class FluxTankBlockEntity extends BlockEntity {
    private final FluidTank tank;
    private final LazyOptional<IFluidHandler> handler;
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        int capacity = StorageSizes.tank(((FluxTankBlock) state.getBlock()).tier());
        this.tank = new FluxFluidTank(capacity, stack -> ModFluids.isAnyFlux(stack.getFluid())) {
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
        this.handler = LazyOptional.of(() -> tank);
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

    /** The comparator signal: how full the tank is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getFluidAmount(), tank.getCapacity());
    }

    /** A broken tank keeps its flux on the item (the loot table copies this component). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!tank.isEmpty()) {
            components.set(ModDataComponents.TANK_FLUID.get(), TankContents.of(tank.getFluid()));
        }
    }

    /** Placing a tank item that carries flux fills the new tank with it. */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        TankContents carried = components.get(ModDataComponents.TANK_FLUID.get());
        if (carried != null && ModFluids.isAnyFlux(carried.fluid())) {
            tank.setFluid(carried.toStack());
        }
    }

    /** The flux lives in the component on items, so it is not saved a second time in the block data. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        // Forge's tank writes its fluid straight into the block's own tag, under these keys.
        tag.remove("FluidName");
        tag.remove("Amount");
        tag.remove("Tag");
    }

    /** Tanks accept and give fluid on every side. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(tag);
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
