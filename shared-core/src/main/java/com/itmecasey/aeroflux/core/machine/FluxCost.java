package com.itmecasey.aeroflux.core.machine;

/**
 * What a machine's job costs in Flux, for the cost bar on the machine screen. A job burns the
 * running cost every tick it works and, on its last tick, any extra Flux the job asks for (the
 * Enchanter's per-level cost, say). The machine pays a tick only when the tank holds that tick's
 * whole cost, so "can the tank cover the next tick" is the question that decides whether it stalls.
 *
 * <p>The machine works these numbers out on the server and the screen only draws them, so both
 * sides read the same rules from here.
 */
public final class FluxCost {
    private FluxCost() {
    }

    /**
     * Flux one whole job takes, in mB: the running cost for every tick plus the job's extra Flux.
     *
     * @param perTick    the running cost, in mB per tick
     * @param totalTicks how long the job takes, in ticks; 0 or less when there is no job
     * @param extraFlux  the Flux paid on top on the last tick, in mB
     * @return the cost, or 0 when there is no job; never more than {@link Integer#MAX_VALUE}
     */
    public static int perOperation(int perTick, int totalTicks, int extraFlux) {
        if (totalTicks <= 0) {
            return 0;
        }
        long cost = (long) Math.max(0, perTick) * totalTicks + Math.max(0, extraFlux);
        return (int) Math.min(Integer.MAX_VALUE, cost);
    }

    /**
     * Flux the next tick of the job will take, in mB. It is the running cost, plus the extra Flux
     * when the next tick is the last one, which is the same test the machine makes when it pays.
     *
     * @param progress ticks of the job already done
     * @param total    how long the job takes, in ticks; 0 or less when there is no job
     * @return the cost, or 0 when there is no job
     */
    public static int nextTick(int perTick, int progress, int total, int extraFlux) {
        if (total <= 0) {
            return 0;
        }
        boolean lastTick = progress + 1 >= total;
        long cost = (long) Math.max(0, perTick) + (lastTick ? Math.max(0, extraFlux) : 0);
        return (int) Math.min(Integer.MAX_VALUE, cost);
    }

    /** Whether the tank can pay for the next tick; with nothing to pay, it always can. */
    public static boolean coversNextTick(int amount, int nextTickCost) {
        return nextTickCost <= 0 || amount >= nextTickCost;
    }

    /**
     * How much of one job the Flux in the tank pays for, from 0 to 1: the fill of the cost bar.
     * A full bar means the tank holds at least one whole job's worth.
     */
    public static double coverFraction(int amount, int perOperation) {
        if (perOperation <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, amount / (double) perOperation));
    }

    /** How many whole jobs the Flux in the tank pays for; 0 when there is no job. */
    public static int operationsCovered(int amount, int perOperation) {
        return perOperation <= 0 ? 0 : Math.max(0, amount) / perOperation;
    }
}
