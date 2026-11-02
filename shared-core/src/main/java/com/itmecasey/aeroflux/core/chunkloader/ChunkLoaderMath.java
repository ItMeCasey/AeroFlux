package com.itmecasey.aeroflux.core.chunkloader;

import com.itmecasey.aeroflux.core.Tier;
import java.util.Locale;

/**
 * The numbers behind the Flux Chunk Loader: how many chunks each tier keeps loaded, what that
 * costs in Flux, how much the loader holds and how long that lasts.
 *
 * <p>A loader's area is a square of chunks centered on its own chunk: 1x1 at copper, then two
 * more chunks across for every tier up to 11x11 at fluxium. The cost is charged per chunk per
 * tick and gets cheaper per chunk at higher tiers, so a big loader is better value than several
 * small ones. Costs are fractions of a millibucket, so they are kept in thousandths of a mB
 * ("milli-mB") and a loader pays for its Flux in whole mB, carrying the change over to the next
 * tick (see {@link #mbToBuy}).
 *
 * <p>Built-in defaults, like {@link Tier}; meant to be rebalanced in the balance pass.
 */
public final class ChunkLoaderMath {
    /** Milli-mB in one mB. */
    public static final int MILLI = 1_000;

    private static final int TICKS_PER_SECOND = 20;

    private ChunkLoaderMath() {
    }

    /** Chunks from the center to the edge of the area: 0 at copper, 5 at fluxium. */
    public static int radius(Tier tier) {
        return tier.ordinal();
    }

    /** Chunks along one side of the area: 1, 3, 5, 7, 9 or 11. */
    public static int side(Tier tier) {
        return radius(tier) * 2 + 1;
    }

    /** Chunks kept loaded in total: 1, 9, 25, 49, 81 or 121. */
    public static int chunkCount(Tier tier) {
        int side = side(tier);
        return side * side;
    }

    /** Flux charged per loaded chunk per tick, in milli-mB. */
    public static int costPerChunkMilli(Tier tier) {
        return switch (tier) {
            case COPPER -> 2_000;
            case IRON -> 1_600;
            case GOLD -> 1_250;
            case DIAMOND -> 1_000;
            case NETHERITE -> 800;
            case FLUXIUM -> 600;
        };
    }

    /** Flux charged per tick for the whole area, in milli-mB (2.0 mB/t at copper, 72.6 mB/t at fluxium). */
    public static int costPerTickMilli(Tier tier) {
        return chunkCount(tier) * costPerChunkMilli(tier);
    }

    /** The loader's Flux tank, in mB: 16 buckets at copper, doubling each tier up to 512. */
    public static int capacity(Tier tier) {
        return 16_000 << tier.ordinal();
    }

    /** Whether a chunk lies inside the area of a loader whose own chunk is at the given center. */
    public static boolean covers(Tier tier, int centerX, int centerZ, int chunkX, int chunkZ) {
        int radius = radius(tier);
        return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
    }

    /**
     * How many whole mB a loader must take from its tank to pay for one tick.
     *
     * @param creditMilli milli-mB already paid for and not yet used
     * @param costMilli   what this tick costs, in milli-mB
     * @return 0 if the credit covers the tick, otherwise the mB that bring it up to the cost
     */
    public static int mbToBuy(long creditMilli, long costMilli) {
        long missing = costMilli - creditMilli;
        if (missing <= 0) {
            return 0;
        }
        // Round up: a part of a mB still has to be bought as a whole one.
        return (int) ((missing + MILLI - 1) / MILLI);
    }

    /**
     * How long the Flux in the tank keeps a loader running, in ticks.
     *
     * @param amountMb    Flux in the tank, in mB
     * @param creditMilli change carried over from the last purchase, in milli-mB
     */
    public static long runtimeTicks(Tier tier, int amountMb, long creditMilli) {
        long available = (long) Math.max(0, amountMb) * MILLI + Math.max(0, creditMilli);
        return available / costPerTickMilli(tier);
    }

    /** A tick count as a clock: {@code 0:45}, {@code 12:05} or {@code 3:07:09}. */
    public static String formatTicks(long ticks) {
        long seconds = Math.max(0, ticks) / TICKS_PER_SECOND;
        long hours = seconds / 3_600;
        long minutes = seconds / 60 % 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /** A milli-mB amount as mB with at most one decimal, such as {@code 2} or {@code 72.6}. */
    public static String formatMilli(long milli) {
        long tenths = Math.round(milli / 100.0);
        return tenths % 10 == 0
                ? Long.toString(tenths / 10)
                : String.format(Locale.ROOT, "%d.%d", tenths / 10, tenths % 10);
    }
}
