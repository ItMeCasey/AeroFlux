package com.itmecasey.aeroflux.core.profile;

/**
 * How much server time AeroFlux may take, as the design states it: under {@value #REFERENCE_MS} ms per
 * tick on a base with {@value #REFERENCE_MACHINES} machines and {@value #REFERENCE_BUNDLES} conduit
 * bundles. The allowance is spread evenly over those two kinds of load and grows linearly with a bigger
 * base, with a small floor so that a nearly empty world is not held to a meaningless sliver.
 */
public final class ProfileBudget {
    /** The allowed time per tick for the reference base, in milliseconds. */
    public static final double REFERENCE_MS = 0.5;
    public static final int REFERENCE_MACHINES = 50;
    public static final int REFERENCE_BUNDLES = 1_000;
    /** The least the allowance ever shrinks to, in milliseconds. */
    public static final double FLOOR_MS = 0.1;

    private ProfileBudget() {
    }

    /**
     * The time per tick a base of this size may take.
     *
     * @param machineLike blocks other than conduit bundles: machines, collectors, loaders and so on
     * @param bundles     conduit bundles
     * @return milliseconds per tick
     */
    public static double allowedMs(int machineLike, int bundles) {
        double halfMs = REFERENCE_MS / 2.0;
        double machineUnits = Math.max(0, machineLike) / (double) REFERENCE_MACHINES;
        double bundleUnits = Math.max(0, bundles) / (double) REFERENCE_BUNDLES;
        return Math.max(FLOOR_MS, halfMs * (machineUnits + bundleUnits));
    }

    /** The allowance for what a report found loaded. */
    public static double allowedMs(ProfileReport report) {
        return allowedMs(report.loadedMachineLike(), report.loadedBundles());
    }

    /** Whether a measured time per tick is inside the allowance. */
    public static boolean within(double measuredMs, double allowedMs) {
        return measuredMs <= allowedMs;
    }

    /** The measured time as a share of the allowance, from 0 up; 0 when there is no allowance. */
    public static double fractionUsed(double measuredMs, double allowedMs) {
        return allowedMs <= 0 ? 0.0 : measuredMs / allowedMs;
    }
}
