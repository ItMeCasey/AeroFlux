package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.chunkloader.LoaderStatus;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.fluid.InputOnlyFluidHandler;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * A Flux Chunk Loader: burns Aero or Moon Flux to keep a square of chunks around itself loaded
 * and ticking.
 *
 * <p>Each server tick it asks {@link LoaderStatus#decide} what it may do, given the server switch,
 * the dimension's {@code chunk_loader_allowed} rule, the per-player and per-dimension caps, and
 * whether it could pay for the tick. It pays in whole mB and keeps the change as credit (see
 * {@link ChunkLoaderMath#mbToBuy}). Its tickets go through {@link ChunkLoaderTickets} and are only
 * touched when the answer changes between "holds tickets" and "does not", so a steady loader
 * costs nothing beyond the drain. When it runs dry it keeps its chunks for the configured grace
 * period before letting them go.
 *
 * <p>Pipes and buckets can only fill it; nothing can pump the Flux back out.
 */
public class FluxChunkLoaderBlockEntity extends BlockEntity implements MenuProvider {
    private static final int SCHEMA_VERSION = 1;
    private static final String TAG_SCHEMA = "schema_version";
    private static final String TAG_FLUX = "flux";
    private static final String TAG_CREDIT = "credit";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_HOLDING = "holding";
    private static final String TAG_GRACE = "grace";

    private final Tier tier;
    private final FluidTank tank;
    private final IFluidHandler inputView;

    @Nullable
    private UUID owner;
    /** Milli-mB already paid for and not used yet; always under one mB. */
    private long creditMilli;
    /** Whether the tickets are held, as far as this loader knows. Saved, because Forge saves the tickets too. */
    private boolean holding;
    private int graceLeft;
    private LoaderStatus status = LoaderStatus.NO_FLUX;
    /** False until the first tick after placing or loading, which makes the world match {@link #holding}. */
    private boolean settled;

    public FluxChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER.get(), pos, state);
        this.tier = ((FluxChunkLoaderBlock) state.getBlock()).tier();
        // One slot, so the loader holds one kind of Flux at a time; either kind will do.
        this.tank = new FluxFluidTank(StorageSizes.chunkLoader(tier), stack -> ModFluids.isFlux(stack.getFluid())) {
            /**
             * Never less than what the tank holds. A tank can end up overfull (its block shrank in an
             * update); it still takes nothing more, and probes such as Jade, which refuse a fill
             * above 100%, see a full tank instead of crashing.
             */
            @Override
            public int getCapacity() {
                return Math.max(super.getCapacity(), getFluidAmount());
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.inputView = new InputOnlyFluidHandler(tank);
    }

    // --- Parts -------------------------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    /** The tank itself; the menu and the self-test use it. The outside world gets {@link #fluidHandler}. */
    public FluidTank tank() {
        return tank;
    }

    /** The comparator signal: how full the Flux tank is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getFluidAmount(), tank.getCapacity());
    }

    /** What pipes and buckets see on every side: a tank that only fills. */
    public IFluidHandler fluidHandler() {
        return inputView;
    }

    public int fluxAmount() {
        return tank.getFluidAmount();
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    @Nullable
    public FluxType storedFlux() {
        return tank.isEmpty() ? null : ModFluids.fluxTypeOf(tank.getFluid().getFluid());
    }

    public LoaderStatus status() {
        return status;
    }

    /** Whether the loader holds its chunk tickets right now. */
    public boolean holdingTickets() {
        return holding;
    }

    public long creditMilli() {
        return creditMilli;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    /** Called once by the block when a player places it. */
    void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            register(serverLevel);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChunkLoaderMenu(containerId, playerInventory, this);
    }

    /** What the client needs to build its copy of the menu. */
    public void writeOpenData(FriendlyByteBuf buf) {
        ChunkLoaderMenu.writeOpenData(buf, worldPosition, tier);
    }

    // --- Ticking -----------------------------------------------------------------------------

    /** Ticker entry point; see {@link FluxChunkLoaderBlock#getTicker}. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FluxChunkLoaderBlockEntity loader) {
        if (level instanceof ServerLevel serverLevel) {
            loader.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        if (!settled) {
            // First tick since placing or loading: make sure the registry knows this loader.
            register(level);
        }

        boolean enabled = AeroFluxConfig.chunkLoadersEnabled();
        boolean allowed = FluxDimensions.rulesFor(level).chunkLoaderAllowed();
        boolean underCap = holding || underCaps(level);
        // Only a loader that is allowed to run pays; a capped or forbidden one keeps its Flux.
        boolean paid = enabled && allowed && underCap && pay();
        if (paid) {
            graceLeft = AeroFluxConfig.chunkLoaderGraceTicks();
        } else if (holding && graceLeft > 0) {
            graceLeft--;
        }
        status = LoaderStatus.decide(enabled, allowed, holding, underCap, paid, graceLeft);

        boolean wanted = status.holdsTickets();
        if (wanted != holding || !settled) {
            // Forge may have brought the tickets back from the save, or the save may say they
            // were held when they were not; either way, this puts the world in line with "wanted".
            setHolding(level, wanted);
        }
        settled = true;
    }

    /** Whether the owner and the dimension both have room for one more running loader. */
    private boolean underCaps(ServerLevel level) {
        int perPlayer = AeroFluxConfig.chunkLoadersPerPlayer();
        if (perPlayer > 0 && ChunkLoaderRegistry.activeOwnedBy(level.getServer(), owner(), level, worldPosition) >= perPlayer) {
            return false;
        }
        int perDimension = AeroFluxConfig.chunkLoadersPerDimension();
        return perDimension <= 0 || ChunkLoaderRegistry.of(level).activeCount(worldPosition) < perDimension;
    }

    /** Pays for one tick out of the credit and the tank; takes nothing unless the whole tick is covered. */
    private boolean pay() {
        long cost = ChunkLoaderMath.costPerTickMilli(tier);
        int buy = ChunkLoaderMath.mbToBuy(creditMilli, cost);
        if (buy > 0) {
            if (tank.getFluidAmount() < buy) {
                return false;
            }
            tank.drain(buy, IFluidHandler.FluidAction.EXECUTE);
        }
        creditMilli += (long) buy * ChunkLoaderMath.MILLI - cost;
        setChanged();
        return true;
    }

    private void setHolding(ServerLevel level, boolean hold) {
        if (hold) {
            ChunkLoaderTickets.acquire(level, worldPosition, tier);
        } else {
            ChunkLoaderTickets.release(level, worldPosition, tier);
            graceLeft = 0;
        }
        holding = hold;
        ChunkLoaderRegistry.of(level).setActive(worldPosition, hold);
        setChanged();
    }

    /** Adds this loader to its dimension's registry, or refreshes its owner there. */
    private void register(ServerLevel level) {
        ChunkLoaderRegistry.of(level).put(new ChunkLoaderRegistry.Entry(worldPosition, owner(), tier, holding));
    }

    /**
     * Breaking the block lets its chunks go and takes it off the registry. The Flux in it is lost.
     * The block calls this when it is replaced by something else.
     */
    void broken(ServerLevel level, BlockPos pos) {
        ChunkLoaderTickets.release(level, pos, tier);
        ChunkLoaderRegistry.of(level).remove(pos);
        holding = false;
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_SCHEMA, SCHEMA_VERSION);
        tag.put(TAG_FLUX, tank.writeToNBT(new CompoundTag()));
        tag.putLong(TAG_CREDIT, creditMilli);
        tag.putBoolean(TAG_HOLDING, holding);
        tag.putInt(TAG_GRACE, graceLeft);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Version 1 is the only layout so far; later versions will branch on TAG_SCHEMA here.
        tank.readFromNBT(tag.getCompound(TAG_FLUX));
        creditMilli = tag.getLong(TAG_CREDIT);
        holding = tag.getBoolean(TAG_HOLDING);
        graceLeft = tag.getInt(TAG_GRACE);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        settled = false;
    }

    // --- Capabilities ------------------------------------------------------------------------

    /** Forge's view of the tank, the same on every side. */
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(this::fluidHandler);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }
}
