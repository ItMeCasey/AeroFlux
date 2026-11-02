package com.itmecasey.aeroflux.block.menu;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxDimensions;
import com.itmecasey.aeroflux.core.CollectionMath;
import com.itmecasey.aeroflux.core.CollectorForecast;
import com.itmecasey.aeroflux.core.DimensionRules;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.RateMeter;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.SplitInt;
import com.itmecasey.aeroflux.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The menu behind a collector's screen. It has no slots, only the numbers the gauge and the sky
 * panel draw: the buffer, what the sky offers and at what rate, the weather, the moon and what
 * blocks the sky. The server reads them from the {@link FluxCollectorBlockEntity} and its level;
 * the client only shows what arrives. Numbers that can pass 16 bits travel in two slots
 * ({@link SplitInt}).
 */
public class CollectorMenu extends AbstractContainerMenu {
    private static final int DATA_AMOUNT = 0;      // two slots
    private static final int DATA_CAPACITY = 2;    // two slots
    private static final int DATA_STORED = 4;
    private static final int DATA_OFFERED = 5;
    /** Two slots: last tick's output in thousandths of a mB, since rain makes fractional rates. */
    private static final int DATA_RATE = 6;
    /** Two slots: mB pushed out during the last finished second. */
    private static final int DATA_OUT = 8;
    private static final int DATA_FLAGS = 10;
    private static final int DATA_MOON_PHASE = 11;
    private static final int DATA_TIME = 12;
    private static final int DATA_TIME_SOURCE = 13;
    /** Two slots: the blocking block's registry number plus one, so 0 means none. */
    private static final int DATA_BLOCKER = 14;
    /** Two slots: the height of the blocking block; it is always straight above the collector. */
    private static final int DATA_BLOCKER_Y = 16;
    private static final int DATA_COUNT = 18;

    private static final int SKY = 1;
    private static final int RAIN = 2;
    private static final int THUNDER = 4;
    private static final int NEEDS_SKY = 8;
    private static final int IGNORES_WEATHER = 16;
    private static final int LOCKED = 32;

    /** Thousandths of a mB per mB, for the rate. */
    private static final double MILLI = 1_000.0;
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final BlockPos pos;
    private final Tier tier;
    private final ContainerData data;
    private final @Nullable FluxCollectorBlockEntity entity;

    public CollectorMenu(int containerId, Inventory inventory, FluxCollectorBlockEntity entity) {
        this(containerId, entity.getBlockPos(), entity.tier(), new LiveData(entity), entity);
    }

    private CollectorMenu(int containerId, BlockPos pos, Tier tier, ContainerData data, @Nullable FluxCollectorBlockEntity entity) {
        super(ModMenus.COLLECTOR.get(), containerId);
        this.pos = pos;
        this.tier = tier;
        this.data = data;
        this.entity = entity;
        addDataSlots(data);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, Tier tier) {
        buf.writeBlockPos(pos);
        buf.writeEnum(tier);
    }

    public static CollectorMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new CollectorMenu(containerId, buf.readBlockPos(), buf.readEnum(Tier.class), new SimpleContainerData(DATA_COUNT), null);
    }

    // --- What the screen reads ---------------------------------------------------------------

    public BlockPos pos() {
        return pos;
    }

    public Tier tier() {
        return tier;
    }

    /** Flux in the buffer, in mB. */
    public int amount() {
        return SplitInt.join(data.get(DATA_AMOUNT), data.get(DATA_AMOUNT + 1));
    }

    /** The buffer's size, in mB. */
    public int capacity() {
        return SplitInt.join(data.get(DATA_CAPACITY), data.get(DATA_CAPACITY + 1));
    }

    /** The flux in the buffer, or {@code null} if it is empty. */
    public @Nullable FluxType storedFlux() {
        return fluxAt(DATA_STORED);
    }

    /** The flux the sky offers right now, or {@code null} at dusk and dawn. */
    public @Nullable FluxType offered() {
        return fluxAt(DATA_OFFERED);
    }

    /** What the collector made last tick, in mB per tick. */
    public double ratePerTick() {
        return SplitInt.join(data.get(DATA_RATE), data.get(DATA_RATE + 1)) / MILLI;
    }

    /** What it pushed to its neighbors, in mB per tick over the last second. */
    public double outPerTick() {
        return SplitInt.join(data.get(DATA_OUT), data.get(DATA_OUT + 1)) / (double) RateMeter.WINDOW;
    }

    /** Ticks until the buffer is full at the current rates; 0 when full, -1 when it is not filling. */
    public long ticksToFull() {
        return CollectorForecast.ticksToFull(amount(), capacity(), ratePerTick() - outPerTick());
    }

    public boolean skyVisible() {
        return flag(SKY);
    }

    public boolean raining() {
        return flag(RAIN);
    }

    public boolean thundering() {
        return flag(THUNDER);
    }

    /** Whether this dimension needs open sky above a collector at all. */
    public boolean needsSky() {
        return flag(NEEDS_SKY);
    }

    /** Whether this dimension ignores the weather. */
    public boolean ignoresWeather() {
        return flag(IGNORES_WEATHER);
    }

    /** Whether the buffer still holds the other flux, so the one on offer has to wait. */
    public boolean bufferLocked() {
        return flag(LOCKED);
    }

    /** The moon phase, 0 (full) to 7. */
    public int moonPhase() {
        return data.get(DATA_MOON_PHASE);
    }

    /** The time of day on the clock this dimension follows, 0 to 23,999. */
    public int timeOfDay() {
        return data.get(DATA_TIME);
    }

    /** Which clock this dimension follows, and whether it has days at all. */
    public DimensionRules.TimeSource timeSource() {
        int index = data.get(DATA_TIME_SOURCE);
        DimensionRules.TimeSource[] all = DimensionRules.TimeSource.values();
        return index >= 0 && index < all.length ? all[index] : DimensionRules.TimeSource.DIMENSION_TIME;
    }

    /** What weather does to the flux on offer, as a factor; 1 when it does nothing. */
    public double weatherFactor() {
        FluxType type = offered();
        return ignoresWeather() || type == null ? 1.0 : CollectionMath.weatherFactor(type, raining(), thundering());
    }

    /** The block that keeps the sky out, or {@code null} when there is none or it is unknown. */
    public @Nullable Block blocker() {
        int id = SplitInt.join(data.get(DATA_BLOCKER), data.get(DATA_BLOCKER + 1)) - 1;
        return id < 0 ? null : BuiltInRegistries.BLOCK.byId(id);
    }

    /** Where that block is: straight above the collector. */
    public BlockPos blockerPos() {
        return new BlockPos(pos.getX(), SplitInt.join(data.get(DATA_BLOCKER_Y), data.get(DATA_BLOCKER_Y + 1)), pos.getZ());
    }

    private boolean flag(int bit) {
        return (data.get(DATA_FLAGS) & bit) != 0;
    }

    private @Nullable FluxType fluxAt(int index) {
        int stored = data.get(index);
        return stored <= 0 || stored > FluxType.values().length ? null : FluxType.values()[stored - 1];
    }

    // --- Menu plumbing -----------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity found = player.level().getBlockEntity(pos);
        if (!(found instanceof FluxCollectorBlockEntity) || found.isRemoved() || (entity != null && found != entity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }

    /** The synced numbers, read from the collector and its level each time the server checks for changes. */
    private record LiveData(FluxCollectorBlockEntity collector) implements ContainerData {
        @Override
        public int get(int index) {
            Level level = collector.getLevel();
            return switch (index) {
                case DATA_AMOUNT -> SplitInt.low(collector.amount());
                case DATA_AMOUNT + 1 -> SplitInt.high(collector.amount());
                case DATA_CAPACITY -> SplitInt.low(collector.capacity());
                case DATA_CAPACITY + 1 -> SplitInt.high(collector.capacity());
                case DATA_STORED -> ordinalOf(collector.storedFlux());
                case DATA_OFFERED -> ordinalOf(collector.offered());
                case DATA_RATE -> SplitInt.low(milli(collector.lastRate()));
                case DATA_RATE + 1 -> SplitInt.high(milli(collector.lastRate()));
                case DATA_OUT -> SplitInt.low(outLastWindow());
                case DATA_OUT + 1 -> SplitInt.high(outLastWindow());
                case DATA_FLAGS -> level == null ? 0 : flags(level);
                case DATA_MOON_PHASE -> level == null ? 0 : level.getMoonPhase();
                case DATA_TIME -> level == null ? 0 : (int) Math.floorMod(clockTime(level), CollectionMath.DAY_LENGTH);
                case DATA_TIME_SOURCE -> level == null ? 0 : FluxDimensions.rulesFor(level).timeSource().ordinal();
                case DATA_BLOCKER -> SplitInt.low(blockerId());
                case DATA_BLOCKER + 1 -> SplitInt.high(blockerId());
                case DATA_BLOCKER_Y -> SplitInt.low(blockerY());
                case DATA_BLOCKER_Y + 1 -> SplitInt.high(blockerY());
                default -> 0;
            };
        }

        private int flags(Level level) {
            DimensionRules rules = FluxDimensions.rulesFor(level);
            return (collector.skyVisible() ? SKY : 0)
                    | (level.isRaining() ? RAIN : 0)
                    | (level.isThundering() ? THUNDER : 0)
                    | (rules.requiresSky() ? NEEDS_SKY : 0)
                    | (rules.ignoreWeather() ? IGNORES_WEATHER : 0)
                    | (collector.bufferLocked() ? LOCKED : 0);
        }

        /** The clock the collector itself goes by, as {@link CollectionMath#isActive} picks it. */
        private static long clockTime(Level level) {
            boolean overworld = FluxDimensions.rulesFor(level).timeSource() == DimensionRules.TimeSource.OVERWORLD_TIME;
            return overworld && level.getServer() != null ? level.getServer().overworld().getDayTime() : level.getDayTime();
        }

        private int outLastWindow() {
            return (int) Math.min(Integer.MAX_VALUE, collector.outLastWindow());
        }

        private int blockerId() {
            BlockPos at = collector.blocker();
            Level level = collector.getLevel();
            if (at == null || level == null) {
                return 0;
            }
            return BuiltInRegistries.BLOCK.getId(level.getBlockState(at).getBlock()) + 1;
        }

        private int blockerY() {
            BlockPos at = collector.blocker();
            return at == null ? 0 : at.getY();
        }

        private static int milli(double perTick) {
            return (int) Math.min(Integer.MAX_VALUE, Math.round(perTick * MILLI));
        }

        private static int ordinalOf(@Nullable FluxType type) {
            return type == null ? 0 : type.ordinal() + 1;
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
