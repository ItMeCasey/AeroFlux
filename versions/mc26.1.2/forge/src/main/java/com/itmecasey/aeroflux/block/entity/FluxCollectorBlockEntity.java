package com.itmecasey.aeroflux.block.entity;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.menu.CollectorMenu;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxAccumulator;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.fluid.OutputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.save.SaveVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The working part of a {@link FluxCollectorBlock}.
 *
 * <p>Each tick on the server it works out how much flux the sky gives right now, adds it to an
 * internal buffer, and every few ticks pushes the buffer into neighboring tanks and machines.
 * The buffer holds one kind of flux at a time, so if night falls while Aero Flux is still in
 * it, Moon Flux waits until the buffer has been emptied.
 *
 * <p>It also keeps what its screen's sky panel shows: the rate it made last tick, which flux the
 * sky offers, whether the buffer is holding that flux up, and what blocks the sky when something
 * does. The screen reads these through {@link CollectorMenu}.
 */
public class FluxCollectorBlockEntity extends BlockEntity implements MenuProvider {
    /** How often the open-sky check runs; scanning the column above is cheap but not free. */
    private static final int SKY_CHECK_INTERVAL = 20;
    /** How often the buffer is pushed to neighbors, in ticks. */
    private static final int PUSH_INTERVAL = 4;
    /** The most fluid moved to neighbors in one push, in mB. */
    private static final int PUSH_LIMIT = 4_000;
    private static final String TAG_COLLECTING = "collecting";

    private final Tier tier;
    private final FluidTank buffer;
    private final FluxAccumulator accumulator = new FluxAccumulator();
    /** What outsiders get to see: the buffer with filling switched off. */
    private final LazyOptional<IFluidHandler> outputHandler;
    /** Counts what goes into and out of the buffer, for the screen's "full in" time. */
    private final RateMeter meter = new RateMeter();
    private boolean skyVisible;
    /** The first block above that keeps the sky out, or {@code null} with open sky (or none found). */
    private @Nullable BlockPos blocker;
    /** What the collector made last tick, in mB per tick; 0 when it made nothing. */
    private double lastRate;
    /** The flux the sky offers right now, or {@code null} at dusk and dawn. */
    private @Nullable FluxType offered;
    /** True while the buffer still holds the other flux, so the offered one has to wait. */
    private boolean bufferLocked;
    /**
     * The flux it is collecting, as clients were last told, for the motes over its dish; null
     * while it collects nothing. It only changes at dawn, dusk or when the sky or buffer changes,
     * so it costs a block update now and then, never every tick.
     */
    private @Nullable FluxType collecting;

    public FluxCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLECTOR.get(), pos, state);
        this.tier = ((FluxCollectorBlock) state.getBlock()).tier();
        this.buffer = new FluxFluidTank(StorageSizes.collector(tier), stack -> ModFluids.isFlux(stack.getFluid())) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update); it still takes nothing more, and probes see a full tank rather than more than 100%.
             */
            @Override
            public int getCapacity() {
                return Math.max(super.getCapacity(), getFluidAmount());
            }

            /** The amount the meter last saw, so each change can be counted as the difference. */
            private int metered;

            @Override
            public void setFluid(FluidStack stack) {
                // Loading or placing sets the contents outright; that is not flow, so only move the baseline.
                super.setFluid(stack);
                metered = getFluidAmount();
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
                int now = getFluidAmount();
                if (level != null) {
                    meter.record(now - metered, level.getGameTime());
                }
                metered = now;
            }
        };
        this.outputHandler = LazyOptional.of(() -> new OutputOnlyFluidHandler(buffer));
    }

    /** The internal buffer; neighbors may only drain it, never fill it. */
    public FluidTank buffer() {
        return buffer;
    }

    /** The comparator signal: how full the buffer is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(buffer.getFluidAmount(), buffer.getCapacity());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxCollectorBlockEntity collector) {
        collector.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        long time = level.getGameTime();
        if (time % SKY_CHECK_INTERVAL == 0) {
            skyVisible = level.canSeeSky(pos.above());
            blocker = skyVisible ? null : findBlocker(level, pos);
        }

        produce(level, pos);
        FluxType nowCollecting = lastRate > 0.0 ? offered : null;
        if (nowCollecting != collecting) {
            collecting = nowCollecting;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        if (time % PUSH_INTERVAL == 0 && !buffer.isEmpty()) {
            pushToNeighbors(level, pos);
        }
    }

    /**
     * The first block above the collector that sky light cannot pass, for the screen to name. Sky
     * light is what {@link Level#canSeeSky} goes by, so glass is never blamed and leaves are.
     */
    @Nullable
    private static BlockPos findBlocker(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int y = pos.getY() + 1; y <= level.getMaxY(); y++) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && !state.propagatesSkylightDown()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Adds this tick's flux, if the sky is open and the time of day suits the flux in the buffer. */
    private void produce(Level level, BlockPos pos) {
        lastRate = 0.0;
        offered = null;
        bufferLocked = false;
        DimensionRules rules = FluxDimensions.rulesFor(level);

        // 26.1 replaced the single day time with world clocks: each dimension names a default
        // clock, and the Overworld clock is the one the rules fall back to elsewhere.
        long dimensionTime = level.getDefaultClockTime();
        long overworldTime = level.getOverworldClockTime();
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        int moonPhase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();

        for (FluxType type : FluxType.values()) {
            if (!type.collected()) {
                continue;
            }
            double perTick = CollectionMath.outputPerTick(AeroFluxConfig.collectorRate(tier), type, rules,
                    dimensionTime, overworldTime, raining, thundering, moonPhase);
            if (perTick <= 0.0) {
                continue;
            }
            Fluid fluid = ModFluids.fluidOf(type);
            if (!buffer.isEmpty() && !buffer.getFluid().getFluid().isSame(fluid)) {
                // The sky offers this flux but the buffer still holds the other one. Where both are
                // made at once the other may yet be on offer, so keep looking.
                if (offered == null) {
                    offered = type;
                    bufferLocked = true;
                }
                continue;
            }
            offered = type;
            bufferLocked = false;
            if (rules.requiresSky() && !skyVisible) {
                return;
            }
            lastRate = perTick;
            int whole = accumulator.add(perTick);
            if (whole > 0) {
                int stored = buffer.fill(new FluidStack(fluid, whole), IFluidHandler.FluidAction.EXECUTE);
                if (stored < whole) {
                    // The buffer is full, so the leftover is lost; do not stockpile a fraction.
                    accumulator.reset();
                }
            }
            return;
        }
    }

    private void pushToNeighbors(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            IFluidHandler neighbor = FluidUtil.getFluidHandler(level, pos.relative(direction), direction.getOpposite())
                    .resolve().orElse(null);
            if (neighbor == null) {
                continue;
            }
            FluidUtil.tryFluidTransfer(neighbor, buffer, Math.min(buffer.getFluidAmount(), PUSH_LIMIT), true);
            if (buffer.isEmpty()) {
                return;
            }
        }
    }

    // --- What the screen shows ---------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    /** Flux in the buffer, in mB. */
    public int amount() {
        return buffer.getFluidAmount();
    }

    /** The buffer's size, in mB. */
    public int capacity() {
        return buffer.getCapacity();
    }

    /** The flux in the buffer, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        return buffer.isEmpty() ? null : ModFluids.fluxTypeOf(buffer.getFluid().getFluid());
    }

    /** What the collector made last tick, in mB per tick. */
    public double lastRate() {
        return lastRate;
    }

    /** The flux the sky offers right now, or {@code null} when it offers none. */
    public @Nullable FluxType offered() {
        return offered;
    }

    /** Whether the buffer still holds the other flux, so the offered one cannot go in. */
    public boolean bufferLocked() {
        return bufferLocked;
    }

    /** Whether the collector saw open sky at its last check. */
    public boolean skyVisible() {
        return skyVisible;
    }

    /** What keeps the sky out, as of the last check; {@code null} with open sky or when nothing was found. */
    public @Nullable BlockPos blocker() {
        return blocker;
    }

    /** mB pushed out of the buffer during the last finished second. */
    public long outLastWindow() {
        return level == null ? 0 : meter.lastOut(level.getGameTime());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CollectorMenu(containerId, playerInventory, this);
    }

    /** What the client needs to build its copy of the menu. */
    public void writeOpenData(FriendlyByteBuf buf) {
        CollectorMenu.writeOpenData(buf, worldPosition, tier);
    }

    /** The flux it is collecting right now, or {@code null}; on a client, as the server last said. */
    public @Nullable FluxType collecting() {
        return collecting;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        // Collectors can be drained from any side but never filled.
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return outputHandler.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        outputHandler.invalidate();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        SaveVersion.stamp(output, SaveSchema.Kind.COLLECTOR);
        buffer.writeTo(output);
        output.putDouble("carry", accumulator.carry());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.COLLECTOR, input);
        buffer.readFrom(input);
        accumulator.setCarry(input.getDoubleOr("carry", 0.0));
        collecting = fluxById(input.getStringOr(TAG_COLLECTING, ""));
    }

    /** Clients only need to know what is being collected; the buffer reaches the screen through the menu. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (collecting != null) {
            tag.putString(TAG_COLLECTING, collecting.id());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    private static FluxType fluxById(String id) {
        for (FluxType type : FluxType.values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
