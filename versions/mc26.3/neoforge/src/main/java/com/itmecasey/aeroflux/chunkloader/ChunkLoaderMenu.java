package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.chunkloader.LoaderStatus;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The menu behind the chunk loader screen. It has no slots: it only carries the few numbers the
 * screen draws (the Flux, its type, the change carried over and the status). Everything else,
 * such as the area and the cost, follows from the tier through {@link ChunkLoaderMath}.
 */
public class ChunkLoaderMenu extends AbstractContainerMenu {
    private static final int DATA_FLUX = 0;      // two slots
    private static final int DATA_FLUX_TYPE = 2;
    private static final int DATA_CREDIT = 3;    // always under 1,000, so one slot is enough
    private static final int DATA_STATUS = 4;
    private static final int DATA_COUNT = 5;

    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final BlockPos pos;
    private final Tier tier;
    private final ContainerData data;
    private final @Nullable FluxChunkLoaderBlockEntity entity;

    /** Server side: a menu reading live from a loader. */
    public ChunkLoaderMenu(int containerId, Inventory inventory, FluxChunkLoaderBlockEntity entity) {
        this(containerId, entity.getBlockPos(), entity.tier(), new LoaderData(entity), entity);
    }

    private ChunkLoaderMenu(int containerId, BlockPos pos, Tier tier, ContainerData data,
                            @Nullable FluxChunkLoaderBlockEntity entity) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        this.pos = pos;
        this.tier = tier;
        this.data = data;
        this.entity = entity;
        addDataSlots(data);
    }

    /** What {@link #fromNetwork} reads back, in the same order. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, Tier tier) {
        buf.writeBlockPos(pos);
        buf.writeEnum(tier);
    }

    /** Builds the client's copy from what {@link FluxChunkLoaderBlockEntity#writeOpenData} wrote. */
    public static ChunkLoaderMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ChunkLoaderMenu(containerId, buf.readBlockPos(), buf.readEnum(Tier.class),
                new SimpleContainerData(DATA_COUNT), null);
    }

    // --- What the screen reads ---------------------------------------------------------------

    public BlockPos pos() {
        return pos;
    }

    /** The chunk the loader stands in: the center of its area. */
    public ChunkPos centerChunk() {
        return ChunkPos.containing(pos);
    }

    public Tier tier() {
        return tier;
    }

    public int fluxAmount() {
        return SplitInt.join(data.get(DATA_FLUX), data.get(DATA_FLUX + 1));
    }

    public int capacity() {
        return StorageSizes.chunkLoader(tier);
    }

    public @Nullable FluxType fluxType() {
        int stored = data.get(DATA_FLUX_TYPE);
        return stored <= 0 || stored > FluxType.values().length ? null : FluxType.values()[stored - 1];
    }

    public LoaderStatus status() {
        return LoaderStatus.byIndex(data.get(DATA_STATUS));
    }

    /** The draw for the whole area, in milli-mB per tick. */
    public int costPerTickMilli() {
        return ChunkLoaderMath.costPerTickMilli(tier);
    }

    /** Ticks the Flux on hand lasts at the current draw. */
    public long runtimeTicks() {
        return ChunkLoaderMath.runtimeTicks(tier, fluxAmount(), data.get(DATA_CREDIT));
    }

    // --- Menu plumbing -----------------------------------------------------------------------

    /** There are no slots, so there is nothing to move. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof FluxChunkLoaderBlockEntity) || found.isRemoved() || (entity != null && found != entity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    /** The synced numbers, read from the loader each time the server checks for changes. */
    private record LoaderData(FluxChunkLoaderBlockEntity loader) implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FLUX -> SplitInt.low(loader.fluxAmount());
                case DATA_FLUX + 1 -> SplitInt.high(loader.fluxAmount());
                case DATA_FLUX_TYPE -> loader.storedFlux() == null ? 0 : loader.storedFlux().ordinal() + 1;
                case DATA_CREDIT -> (int) loader.creditMilli();
                case DATA_STATUS -> loader.status().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read only.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
