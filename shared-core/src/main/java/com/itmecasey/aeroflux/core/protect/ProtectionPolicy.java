package com.itmecasey.aeroflux.core.protect;

/**
 * Decides whether an action goes ahead, from the answers of the protection sources.
 *
 * <p>The rules, in one place: with protection switched off nothing is asked and everything is
 * allowed; a machine with no known owner (placed by a pipe, a dispenser, a command, or before
 * ownership was recorded) acts as it always did and nothing is asked either; otherwise any source
 * that denies wins, and when all of them allow or abstain the action goes ahead.
 */
public final class ProtectionPolicy {
    private ProtectionPolicy() {
    }

    /**
     * Whether the sources need to be asked at all for a machine.
     *
     * @param enabled    the config switch
     * @param ownerKnown whether the machine recorded a placing player
     */
    public static boolean shouldAsk(boolean enabled, boolean ownerKnown) {
        return enabled && ownerKnown;
    }

    /**
     * The decision for a machine's action.
     *
     * @param answers what each source said; ignored when {@link #shouldAsk} is false
     */
    public static boolean machineMayAct(boolean enabled, boolean ownerKnown, Iterable<Verdict> answers) {
        return !shouldAsk(enabled, ownerKnown) || noneDenies(answers);
    }

    /**
     * The decision for a tool a player is using. The player is always known, so only the switch matters.
     */
    public static boolean playerMayAct(boolean enabled, Iterable<Verdict> answers) {
        return !enabled || noneDenies(answers);
    }

    /** Whether no answer is a denial. */
    public static boolean noneDenies(Iterable<Verdict> answers) {
        for (Verdict answer : answers) {
            if (answer == Verdict.DENY) {
                return false;
            }
        }
        return true;
    }
}
