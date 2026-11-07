package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.chunkloader.LoaderStatus;
import com.itmecasey.aeroflux.core.protect.ProtectionAction;
import com.itmecasey.aeroflux.core.protect.ProtectionCache;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.fluid.InputOnlyFluidHandler;
import com.itmecasey.aeroflux.protect.Protection;
import com.itmecasey.aeroflux.registry.ModBlockEntities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.save.SaveVersion;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

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
    /** How long a claim answer is trusted, in ticks; a claim changes rarely and this keeps the check cheap. */
    private static final int PROTECTION_TTL = 40;
    private static final String TAG_FLUX = "flux";
    private static final String TAG_CREDIT = "credit";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_HOLDING = "holding";
    private static final String TAG_GRACE = "grace";

    private final Tier tier;
    private final FluidStacksResourceHandler tank;
    private final ResourceHandler<FluidResource> inputView;

    private @Nullable UUID owner;
    /** Milli-mB already paid for and not used yet; always under one mB. */
    private long creditMilli;
    /** Whether the tickets are held, as far as this loader knows. Saved, because NeoForge saves the tickets too. */
    private boolean holding;
    private int graceLeft;
    private LoaderStatus status = LoaderStatus.NO_FLUX;
    private final ProtectionCache protection = new ProtectionCache(PROTECTION_TTL);
    /** False until the first tick after placing or loading, which makes the world match {@link #holding}. */
    private boolean settled;

    public FluxChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER.get(), pos, state);
        this.tier = ((FluxChunkLoaderBlock) state.getBlock()).tier();
        // One slot, so the loader holds one kind of Flux at a time; either kind will do.
        this.tank = new FluidStacksResourceHandler(1, StorageSizes.chunkLoader(tier)) {
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
            }
        };
        this.inputView = new InputOnlyFluidHandler(tank);
    }

    // --- Parts -------------------------------------------------------------------------------

    public Tier tier() {
        return tier;
    }

    /** The tank itself; the menu and the self-test use it. The outside world gets {@link #fluidHandler}. */
    public FluidStacksResourceHandler tank() {
        return tank;
    }

    /** The comparator signal: how full the Flux tank is, from 0 to 15. */
    public int comparatorSignal() {
        return FillLevel.comparatorSignal(tank.getAmountAsLong(0), tank.getCapacityAsLong(0, tank.getResource(0)));
    }

    /** What pipes and buckets see on every side: a tank that only fills. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return inputView;
    }

    public int fluxAmount() {
        return tank.getAmountAsInt(0);
    }

    /** The flux type in the tank, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        FluidResource held = tank.getResource(0);
        return held.isEmpty() ? null : ModFluids.fluxTypeOf(held);
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
        protection.clear();
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
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
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
        // A claim that no longer lets the owner act here counts like a dimension that forbids loaders.
        boolean allowed = FluxDimensions.rulesFor(level).chunkLoaderAllowed() && protectionAllows(level);
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
            // NeoForge may have brought the tickets back from the save, or the save may say they
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

    /** Whether the owner's claims still let this loader work where it stands. An unowned loader is never asked. */
    private boolean protectionAllows(ServerLevel level) {
        return owner == null || protection.allowed(0L, level.getGameTime(),
                () -> Protection.mayAct(level, owner, worldPosition, ProtectionAction.USE_BLOCK));
    }

    /** Pays for one tick out of the credit and the tank; takes nothing unless the whole tick is covered. */
    private boolean pay() {
        long cost = ChunkLoaderMath.costPerTickMilli(tier);
        int buy = ChunkLoaderMath.mbToBuy(creditMilli, cost);
        if (buy > 0) {
            FluidResource held = tank.getResource(0);
            if (held.isEmpty() || tank.getAmountAsInt(0) < buy) {
                return false;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                tank.extract(0, held, buy, transaction);
                transaction.commit();
            }
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

    /** Breaking the block lets its chunks go and takes it off the registry. The Flux in it is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderTickets.release(serverLevel, pos, tier);
            ChunkLoaderRegistry.of(serverLevel).remove(pos);
            holding = false;
        }
    }

    // --- Saving ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        SaveVersion.stamp(output, SaveSchema.Kind.CHUNK_LOADER);
        tank.serialize(output.child(TAG_FLUX));
        output.putLong(TAG_CREDIT, creditMilli);
        output.putBoolean(TAG_HOLDING, holding);
        output.putInt(TAG_GRACE, graceLeft);
        if (owner != null) {
            output.store(TAG_OWNER, UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Version 1 is the only layout so far; reading it reports a save from a newer build.
        SaveVersion.read(SaveSchema.Kind.CHUNK_LOADER, input);
        input.child(TAG_FLUX).ifPresent(tank::deserialize);
        creditMilli = input.getLongOr(TAG_CREDIT, 0L);
        holding = input.getBooleanOr(TAG_HOLDING, false);
        graceLeft = input.getIntOr(TAG_GRACE, 0);
        owner = input.read(TAG_OWNER, UUIDUtil.CODEC).orElse(null);
        settled = false;
    }
}
