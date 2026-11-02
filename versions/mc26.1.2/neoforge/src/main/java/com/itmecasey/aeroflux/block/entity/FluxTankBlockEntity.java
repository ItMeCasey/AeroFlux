package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModDataComponents;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Holds the fluid of a {@link FluxTankBlock}: Aero Flux or Moon Flux, never both at once.
 *
 * <p>Clients draw the fluid level inside the glass, so they are sent the tank whenever its
 * picture changes (see {@link FillLevel}); a tank being filled does not resend every tick. Its
 * screen gets the exact numbers, and the in and out rates, through {@link TankMenu} instead.
 */
public class FluxTankBlockEntity extends BlockEntity implements MenuProvider, TankView {
    private final Tier tier;
    private final FluidStacksResourceHandler tank;
    /** Counts what goes in and out, for the in and out rates on the tank's screen. */
    private final RateMeter meter = new RateMeter();
    /** The {@link FillLevel#syncKey} last sent to clients. */
    private int syncedKey;

    public FluxTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
        this.tier = ((FluxTankBlock) state.getBlock()).tier();
        int capacity = StorageSizes.tank(tier);
        // A single slot, so the tank can only ever hold one kind of flux.
        this.tank = new FluidStacksResourceHandler(1, capacity) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update, or a Capacity upgrade came out); it still takes nothing more, and probes such as
             * Jade, which refuse a fill above 100%, see a full tank instead of crashing.
             */
            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return Math.max(super.getCapacityAsLong(index, resource), getAmountAsLong(index));
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return ModFluids.isAnyFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    meter.record(getAmountAsLong(index) - previousContents.getAmount(), level.getGameTime());
                }
                syncIfPictureChanged();
            }
        };
    }

    /** The tank itself, exposed to pipes and containers through the fluid capability. */
    @Override
    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    public Tier tier() {
        return tier;
    }

    /** Flux in the tank, in mB. */
    public int amount() {
        return tank.getAmountAsInt(0);
    }

    /** The tank's size, in mB. */
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, tank.getCapacityAsLong(0, tank.getResource(0)));
    }

    /** The flux in the tank, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        FluidResource held = tank.getResource(0);
        return held.isEmpty() ? null : ModFluids.fluxTypeOf(held);
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
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        TankMenu.writeOpenData(buf, worldPosition, tier);
    }

    /** Tells clients about the tank, but only when what they would draw has changed. */
    private void syncIfPictureChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        FluidResource held = tank.getResource(0);
        FluxType found = ModFluids.fluxTypeOf(held);
        FluxType type = found == null ? FluxType.AERO : found;
        int key = FillLevel.syncKey(tank.getAmountAsLong(0), tank.getCapacityAsLong(0, held), type);
        if (key != syncedKey) {
            syncedKey = key;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The comparator signal: how full the tank is, from 0 to 15. */
    public int comparatorSignal() {
        FluidResource held = tank.getResource(0);
        return FillLevel.comparatorSignal(tank.getAmountAsLong(0), tank.getCapacityAsLong(0, held));
    }

    /** A broken tank keeps its flux on the item (the loot table copies this component). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        FluidStack held = tank.copyToList().get(0);
        if (!held.isEmpty()) {
            components.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(held));
        }
    }

    /** Placing a tank item that carries flux fills the new tank with it. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack carried = components.getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY).copy();
        if (!carried.isEmpty() && ModFluids.isAnyFlux(FluidResource.of(carried))) {
            tank.set(0, FluidResource.of(carried), carried.getAmount());
        }
    }

    /** The flux lives in the component on items, so it is not saved a second time in the block data. */
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("tank");
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
