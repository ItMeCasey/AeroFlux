package com.itmecasey.aeroflux.core.machine;

/** The dice of machine recipes: a byproduct such as sawdust drops only some of the time. */
public final class OutputChance {
    private OutputChance() {
    }

    /**
     * Whether a result with this chance drops.
     *
     * @param chance the chance from 0.0 (never) to 1.0 (always); values outside are clamped
     * @param roll   a random number in [0, 1)
     */
    public static boolean drops(float chance, double roll) {
        if (chance >= 1.0F) {
            return true;
        }
        return chance > 0.0F && roll < chance;
    }
}
