package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.AeroFlux;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Keeps a misbehaving neighbor from taking the server down. Another mod's handler that throws
 * is logged once per position, with the stack trace, and then ignored for that call.
 */
final class TransferFaults {
    /** Forget old reports past this many, so a long session cannot grow the set forever. */
    private static final int MAX_REMEMBERED = 1024;

    private static final Set<Long> REPORTED = new HashSet<>();

    private TransferFaults() {
    }

    /** Logs the failure the first time it is seen for this position. */
    static void report(BlockPos pos, RuntimeException error) {
        if (REPORTED.size() >= MAX_REMEMBERED) {
            REPORTED.clear();
        }
        if (REPORTED.add(pos.asLong())) {
            AeroFlux.LOGGER.warn("A pipe transfer at {} failed and was skipped; this is logged once per position", pos, error);
        }
    }
}
