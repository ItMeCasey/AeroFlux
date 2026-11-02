package com.itmecasey.aeroflux.conduit.transfer;

import java.util.Random;

/**
 * The small amount of memory a lane's leader keeps between ticks: a rotation counter for
 * round-robin sharing, the item cooldown and a source of randomness. It lives apart from
 * {@link LaneNetwork} so rebuilding the network does not reset it.
 */
public final class LaneState {
    private final Random random;
    private int rotation;
    private int cooldown;

    LaneState(long seed) {
        this.random = new Random(seed);
    }

    /** A counter that grows by one each time it is asked; feeds round-robin choices. */
    int nextRotation() {
        return rotation++;
    }

    /** The current rotation without advancing it. */
    int rotation() {
        return rotation;
    }

    Random random() {
        return random;
    }

    /** Counts one tick of the item cooldown; true when an operation is due, and then restarts the wait. */
    boolean cooldownElapsed(int ticksBetweenOperations) {
        if (--cooldown > 0) {
            return false;
        }
        cooldown = ticksBetweenOperations;
        return true;
    }
}
