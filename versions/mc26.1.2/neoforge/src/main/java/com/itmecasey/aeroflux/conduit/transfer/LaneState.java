package com.itmecasey.aeroflux.conduit.transfer;

import java.util.Random;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The small amount of memory a lane's leader keeps between ticks: a rotation counter for
 * round-robin sharing, the item cooldown, a source of randomness, and how much of this tick's
 * throughput is already spent. It lives apart from {@link LaneNetwork} so rebuilding the network
 * does not reset it.
 */
public final class LaneState {
    private final Random random;
    private int rotation;
    private int cooldown;
    /** The game tick {@link #spent} counts for. */
    private long budgetTick = Long.MIN_VALUE;
    private long spent;
    /** Keeps the spent amount in step with transactions, so a simulated push counts for nothing. */
    private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {
        @Override
        protected Long createSnapshot() {
            return spent;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            spent = snapshot;
        }
    };

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

    /**
     * How much of {@code limit} is still free in game tick {@code gameTime}. The network's own
     * step and other mods pushing into its faces (see {@link PipeFluidInlet}) share the one limit,
     * so a pipe never carries more per tick than its tier allows, whoever does the pushing.
     */
    long remaining(long gameTime, long limit) {
        startTick(gameTime);
        return Math.max(0, limit - spent);
    }

    /** Counts {@code amount} against the budget of tick {@code gameTime}. */
    void spend(long gameTime, long amount) {
        startTick(gameTime);
        spent += Math.max(0, amount);
    }

    /** The same inside a transaction: the amount only counts if the transaction is committed. */
    void spend(long gameTime, long amount, TransactionContext transaction) {
        startTick(gameTime);
        journal.updateSnapshots(transaction);
        spent += Math.max(0, amount);
    }

    /** A new tick starts with nothing spent. */
    private void startTick(long gameTime) {
        if (gameTime != budgetTick) {
            budgetTick = gameTime;
            spent = 0;
        }
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
