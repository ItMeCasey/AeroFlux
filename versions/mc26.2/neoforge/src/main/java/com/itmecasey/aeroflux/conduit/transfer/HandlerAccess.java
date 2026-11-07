package com.itmecasey.aeroflux.conduit.transfer;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Small wrappers around NeoForge's transaction-based transfer API. The movers ask a question
 * first ("how much would fit?") and act afterwards, which in this API means the same call made
 * in a transaction that is thrown away or kept.
 */
final class HandlerAccess {
    private HandlerAccess() {
    }

    /**
     * Takes up to {@code amount} of a resource out of a handler.
     *
     * @param simulate true to only find out how much would come out, leaving the handler unchanged
     * @return how much was (or would be) taken
     */
    static <T extends Resource> int extract(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.extract(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return taken;
        }
    }

    /**
     * Like {@link #extract}, but only from one slot, so that "the first filled slot" stays the
     * slot that is emptied.
     */
    static <T extends Resource> int extractFromSlot(ResourceHandler<T> handler, int slot, T resource, int amount,
                                                    boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.extract(slot, resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return taken;
        }
    }

    /**
     * Puts up to {@code amount} of a resource into a handler.
     *
     * @param simulate true to only find out how much would fit, leaving the handler unchanged
     * @return how much was (or would be) accepted
     */
    static <T extends Resource> int insert(ResourceHandler<T> handler, T resource, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = handler.insert(resource, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return accepted;
        }
    }

    /** Takes up to {@code amount} energy out of a handler; see {@link #extract(ResourceHandler, Resource, int, boolean)}. */
    static int extractEnergy(EnergyHandler handler, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.extract(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return taken;
        }
    }

    /** Puts up to {@code amount} energy into a handler; see {@link #insert(ResourceHandler, Resource, int, boolean)}. */
    static int insertEnergy(EnergyHandler handler, int amount, boolean simulate) {
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = handler.insert(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return accepted;
        }
    }
}
