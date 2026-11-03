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
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.fluid.OutputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.save.SaveVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

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
    private final FluidStacksResourceHandler buffer;
    private final ResourceHandler<FluidResource> outputView;
    private final FluxAccumulator accumulator = new FluxAccumulator();
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
        // A single slot, so the buffer can only ever hold one kind of flux.
        this.buffer = new FluidStacksResourceHandler(1, StorageSizes.collector(tier)) {
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
                return ModFluids.isFlux(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                setChanged();
                if (level != null) {
                    meter.record(getAmountAsLong(index) - previousContents.getAmount(), level.getGameTime());
                }
            }
        };
        this.outputView = new OutputOnlyFluidHandler(buffer);
    }

    /** The buffer as other blocks see it: they may drain it, never fill it. */
    public ResourceHandler<FluidResource> outputView() {
        return outputView;
    }

    /** The comparator signal: how full the buffer is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(buffer.getAmountAsLong(0), buffer.getCapacityAsLong(0, buffer.getResource(0)));
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

        if (time % PUSH_INTERVAL == 0 && !ResourceHandlerUtil.isEmpty(buffer)) {
            pushToNeighbors(level, pos);
        }
    }

    /**
     * The first block above the collector that sky light cannot pass, for the screen to name. Sky
     * light is what {@link Level#canSeeSky} goes by, so glass is never blamed and leaves are.
     */
    private static @Nullable BlockPos findBlocker(Level level, BlockPos pos) {
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
        long dimensionTime = level.getDefaultClockTime();
        long overworldTime = level.getOverworldClockTime();
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        int moonPhase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos).index();

        FluidResource held = buffer.getResource(0);
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
            if (!held.isEmpty() && !held.getFluid().isSame(fluid)) {
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
                int stored;
                try (Transaction transaction = Transaction.openRoot()) {
                    stored = buffer.insert(FluidResource.of(fluid), whole, transaction);
                    transaction.commit();
                }
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
            ResourceHandler<FluidResource> neighbor = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbor == null) {
                continue;
            }
            ResourceHandlerUtil.move(buffer, neighbor, resource -> true, PUSH_LIMIT, null);
            if (ResourceHandlerUtil.isEmpty(buffer)) {
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
        return buffer.getAmountAsInt(0);
    }

    /** The buffer's size, in mB. */
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, buffer.getCapacityAsLong(0, buffer.getResource(0)));
    }

    /** The flux in the buffer, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        FluidResource held = buffer.getResource(0);
        return held.isEmpty() ? null : ModFluids.fluxTypeOf(held);
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
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        CollectorMenu.writeOpenData(buf, worldPosition, tier);
    }

    /** The flux it is collecting right now, or {@code null}; on a client, as the server last said. */
    public @Nullable FluxType collecting() {
        return collecting;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        SaveVersion.stamp(output, SaveSchema.Kind.COLLECTOR);
        buffer.serialize(output.child("buffer"));
        output.putDouble("carry", accumulator.carry());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.COLLECTOR, input);
        input.child("buffer").ifPresent(buffer::deserialize);
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
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static @Nullable FluxType fluxById(String id) {
        for (FluxType type : FluxType.values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
