package com.itmecasey.aeroflux.core.conduit;

import com.itmecasey.aeroflux.core.Tier;
import java.util.Locale;

/**
 * The sixteen channels of a redstone pipe, named and colored after the dyes. A pipe offers the
 * first {@link PipeTiers#redstoneChannels} of them, by tier; every face of a redstone pipe is
 * tuned to one, and a signal only travels between faces on the same channel.
 */
public enum RedstoneChannels {
    WHITE(0xF9FFFE),
    ORANGE(0xF9801D),
    MAGENTA(0xC74EBD),
    LIGHT_BLUE(0x3AB3DA),
    YELLOW(0xFED83D),
    LIME(0x80C71F),
    PINK(0xF38BAA),
    GRAY(0x474F52),
    LIGHT_GRAY(0x9D9D97),
    CYAN(0x169C9C),
    PURPLE(0x8932B8),
    BLUE(0x3C44AA),
    BROWN(0x835432),
    GREEN(0x5E7C16),
    RED(0xB02E26),
    BLACK(0x1D1D21);

    private final int color;

    RedstoneChannels(int color) {
        this.color = color;
    }

    /** RGB color of the channel's dye. */
    public int color() {
        return color;
    }

    /** The lowercase name used in translation keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The channel with this number; numbers wrap, so any int names one. */
    public static RedstoneChannels of(int channel) {
        return values()[Math.floorMod(channel, values().length)];
    }

    /** How many channels a redstone pipe of this tier offers. */
    public static int count(Tier tier) {
        return PipeTiers.redstoneChannels(tier);
    }

    /** The channel after {@code channel}, wrapping at the tier's count; a face above the count starts over at 0. */
    public static int next(int channel, Tier tier) {
        int count = count(tier);
        return channel + 1 >= count ? 0 : channel + 1;
    }
}
