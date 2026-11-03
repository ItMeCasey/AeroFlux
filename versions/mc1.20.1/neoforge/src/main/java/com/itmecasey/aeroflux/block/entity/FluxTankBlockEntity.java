package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Flux or any other fluid, one kind at a time.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick. Its
 * screen gets the exact numbers, and the in and out rates, through {@link TankMenu} instead.
 *
 * <p>A broken tank keeps its fluid: the loot table copies the saved fluid into the item's
 * {@code BlockEntityTag}, and vanilla's {@code BlockItem} loads it back into the tank it places.
 */
public class FluxTankBlockEntity extends BlockEntity implements MenuProvider, TankView {
    private final Tier tier;
    private final FluidTank tank;
    /** Counts what goes in and out, for the in and out rates on the tank's screen. */
    private final RateMeter meter = new RateMeter();
    /** The amount as the meter last saw it; Forge's tank does not say what it held before a change. */
    private int metered;
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;
    private final LazyOptional<IFluidHandler> handler;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        this.tier = ((FluxTankBlock) state.getBlock()).tier();
        int capacity = StorageSizes.tank(tier);
        this.tank = new FluxFluidTank(capacity, stack -> !stack.isEmpty()) {
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
                if (level != null && !level.isClientSide) {
                    meter.record(getFluidAmount() - metered, level.getGameTime());
                }
                metered = getFluidAmount();
                syncIfPictureChanged();
            }
        };
        this.handler = LazyOptional.of(() -> tank);
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    @Override
    public FluidTank tank() {
        return tank;
    }

    /** The comparator signal: how full the tank is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getFluidAmount(), tank.getCapacity());
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

    /** The flux in the tank, or {@code null} if it is empty or holds some other fluid. */
    @Nullable
    public FluxType storedFlux() {
        return tank.isEmpty() ? null : ModFluids.fluxTypeOf(tank.getFluid().getFluid());
    }

    /** The fluid in the tank, whatever it is, or {@code null} if the tank is empty. */
    @Nullable
    public Fluid storedFluid() {
        return tank.isEmpty() ? null : tank.getFluid().getFluid();
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
        int fluidId = tank.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid());
        int key = FillLevel.syncKey(tank.getFluidAmount(), tank.getCapacity(), fluidId);
        if (key != syncedKey) {
            syncedKey = key;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        // Tanks accept and give fluid on every side.
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // An empty tank saves no fluid at all, so its loot table copies nothing and it drops a
        // plain tank that stacks with new ones. Loading a tag without fluid empties the tank.
        if (!tank.isEmpty()) {
            tank.writeToNBT(tag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag);
        metered = tank.getFluidAmount();
    }

    @Override
    public CompoundTag getUpdateTag() {
        // Clients always get the fluid, even an empty one: a packet with an empty tag is never
        // loaded, so a tank that was just drained would keep drawing its old level.
        CompoundTag tag = saveWithoutMetadata();
        tank.writeToNBT(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
