package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick. Its
 * screen gets the exact numbers, and the in and out rates, through {@link TankMenu} instead.
 */
public class FluxTankBlockEntity extends BlockEntity implements MenuProvider, TankView {
    private final Tier tier;
    private final FluidTank tank;
    /** Counts what goes in and out, for the in and out rates on the tank's screen. */
    private final RateMeter meter = new RateMeter();
    private final LazyOptional<IFluidHandler> handler;
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        this.tier = ((FluxTankBlock) state.getBlock()).tier();
        int capacity = StorageSizes.tank(tier);
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

            /** The amount the meter last saw; Forge's tank does not say what a change was. */
            private int recorded;

            @Override
            protected void onContentsChanged() {
                setChanged();
                if (level != null && !level.isClientSide) {
                    meter.record(getFluidAmount() - recorded, level.getGameTime());
                }
                recorded = getFluidAmount();
                syncIfPictureChanged();
            }

            @Override
            public void setFluid(FluidStack stack) {
                // Loading or replacing the contents is no flow, so the meter only moves its baseline.
                super.setFluid(stack);
                recorded = getFluidAmount();
            }
        };
        this.handler = LazyOptional.of(() -> tank);
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    @Override
    public FluidTank tank() {
        return tank;
    }

    public Tier tier() {
        return tier;
    }

    /** Flux in the tank, in mB. */
    public int amount() {
        return tank.getFluidAmount();
    }

    /** The tank's size, in mB. */
    public int capacity() {
        return tank.getCapacity();
    }

    /** The flux in the tank, or {@code null} if it is empty. */
    @Nullable
    public FluxType storedFlux() {
        return tank.isEmpty() ? null : ModFluids.fluxTypeOf(tank.getFluid().getFluid());
    }

    /** mB that went in during the last finished second. */
    public long inLastWindow() {
        return level == null ? 0 : meter.lastIn(level.getGameTime());
    }

    /** mB that came out during the last finished second. */
    public long outLastWindow() {
        return level == null ? 0 : meter.lastOut(level.getGameTime());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TankMenu(containerId, playerInventory, this);
    }

    /** What the client needs to build its copy of the menu. */
    public void writeOpenData(FriendlyByteBuf buf) {
        TankMenu.writeOpenData(buf, worldPosition, tier);
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
