package com.itmecasey.aeroflux.core.machine;

import java.util.Locale;

/**
 * What a machine is doing right now, as the status lamp and status text show it. A redstone
 * "disabled" state will join these when machines get a redstone mode.
 */
public enum MachineStatus {
    /** Nothing to work on. */
    IDLE,
    /** Working: burning Flux and making progress. */
    RUNNING,
    /** Has a job but not enough Flux to pay for the next tick. */
    NO_FLUX,
    /** Has a finished product with no room to put it. */
    OUTPUT_FULL;

    /** Lowercase name used in translation keys, such as {@code "no_flux"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Works out the status from the three questions a machine asks each tick, in order of
     * importance: is there anything to do, is there room for the result, is there Flux to pay.
     */
    public static MachineStatus decide(boolean hasJob, boolean outputFits, boolean fluxCoversTick) {
        if (!hasJob) {
            return IDLE;
        }
        if (!outputFits) {
            return OUTPUT_FULL;
        }
        return fluxCoversTick ? RUNNING : NO_FLUX;
    }

    /** The status for a saved or sent index; anything out of range reads as {@link #IDLE}. */
    public static MachineStatus byIndex(int index) {
        MachineStatus[] all = values();
        return index >= 0 && index < all.length ? all[index] : IDLE;
    }
}
