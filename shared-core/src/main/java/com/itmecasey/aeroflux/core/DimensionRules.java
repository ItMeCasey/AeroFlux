package com.itmecasey.aeroflux.core;

/**
 * What a dimension allows: which flux can be collected, how strongly, and which clock to
 * follow. These are loaded from data packs (one JSON per dimension) and merged with config
 * overrides by the loader modules; this class only holds the values.
 *
 * @param aeroEnabled        whether Aero Flux can be collected
 * @param moonEnabled        whether Moon Flux can be collected
 * @param aeroMultiplier     scales Aero Flux output; 1.0 leaves it unchanged
 * @param moonMultiplier     scales Moon Flux output; 1.0 leaves it unchanged
 * @param timeSource         which clock decides day and night
 * @param requiresSky        whether a collector needs open sky above it
 * @param ignoreWeather      whether rain and thunder are ignored
 * @param chunkLoaderAllowed whether Flux Chunk Loaders may be placed here
 */
public record DimensionRules(
        boolean aeroEnabled,
        boolean moonEnabled,
        double aeroMultiplier,
        double moonMultiplier,
        TimeSource timeSource,
        boolean requiresSky,
        boolean ignoreWeather,
        boolean chunkLoaderAllowed) {

    /** Which clock a dimension uses to decide whether it is day or night. */
    public enum TimeSource {
        /** Use the dimension's own day/night cycle. */
        DIMENSION_TIME,
        /** Follow the Overworld's clock, whatever this dimension does. */
        OVERWORLD_TIME,
        /** Always daytime: only Aero Flux is made. */
        ALWAYS_DAY,
        /** Always nighttime: only Moon Flux is made. */
        ALWAYS_NIGHT,
        /** Both fluids are made at the same time, for dimensions with fixed light. */
        ALWAYS_BOTH
    }

    /** The default for the Overworld: both fluids, normal output, the dimension's own clock. */
    public static final DimensionRules OVERWORLD = new DimensionRules(
            true, true, 1.0, 1.0, TimeSource.DIMENSION_TIME, true, false, true);

    /** The default for every other dimension: nothing is collected unless a rule says so. */
    public static final DimensionRules DISABLED = new DimensionRules(
            false, false, 1.0, 1.0, TimeSource.DIMENSION_TIME, true, false, false);
}
