package com.itmecasey.aeroflux.conduit.transfer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Moves items. Every few ticks (the slowest pipe sets the pace) each source gives one batch:
 * it takes up to the batch size from its first filled slot and offers it to the sinks in the
 * order its pipe's distribution mode says.
 */
final class ItemMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        if (!state.cooldownElapsed(network.ticksPerOperation())) {
            return;
        }
        List<Held> sources = new ArrayList<>();
        List<Held> sinks = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (!level.isLoaded(endpoint.target())) {
                continue;
            }
            IItemHandler handler = Neighbors.find(level, endpoint.target(), endpoint.machineSide(), ForgeCapabilities.ITEM_HANDLER);
            if (handler == null) {
                continue;
            }
            Held held = new Held(endpoint, handler);
            if (endpoint.pulls()) {
                sources.add(held);
            }
            if (endpoint.pushes()) {
                sinks.add(held);
            }
        }
        if (sources.isEmpty() || sinks.isEmpty()) {
            return;
        }

        int start = Math.floorMod(state.nextRotation(), sources.size());
        for (int step = 0; step < sources.size(); step++) {
            Held source = sources.get((start + step) % sources.size());
            try {
                moveBatch(level, source, sinks, network.itemsPerOperation(), state);
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
    }

    /** Takes one batch from the source and delivers it, or leaves everything alone if nobody can take any. */
    private static void moveBatch(ServerLevel level, Held source, List<Held> sinks, int batchSize, LaneState state) {
        int slot = firstFilledSlot(source.handler, batchSize);
        if (slot < 0) {
            return;
        }
        ItemStack preview = source.handler.extractItem(slot, batchSize, true);
        int[] order = source.endpoint.distribution().order(sinks.size(), state.rotation(), state.random());
        if (!anyAccepts(source, sinks, order, preview)) {
            return;
        }

        ItemStack remaining = source.handler.extractItem(slot, preview.getCount(), false);
        for (int index : order) {
            if (remaining.isEmpty()) {
                break;
            }
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink)) {
                continue;
            }
            try {
                ItemStack fits = ItemHandlerHelper.insertItem(sink.handler, remaining, true);
                if (fits.getCount() < remaining.getCount()) {
                    remaining = ItemHandlerHelper.insertItem(sink.handler, remaining, false);
                }
            } catch (RuntimeException e) {
                TransferFaults.report(sink.endpoint.target(), e);
            }
        }
        if (!remaining.isEmpty()) {
            // Nobody took the rest after all; return it, and drop it as a last resort so nothing vanishes.
            remaining = ItemHandlerHelper.insertItem(source.handler, remaining, false);
            if (!remaining.isEmpty()) {
                Block.popResource(level, source.endpoint.target(), remaining);
            }
        }
    }

    /** The first slot that gives something when asked for a batch, or -1. */
    private static int firstFilledSlot(IItemHandler handler, int batchSize) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.extractItem(slot, batchSize, true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Whether at least one sink other than the source's own machine would take part of the stack. */
    private static boolean anyAccepts(Held source, List<Held> sinks, int[] order, ItemStack stack) {
        for (int index : order) {
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink)) {
                continue;
            }
            try {
                if (ItemHandlerHelper.insertItem(sink.handler, stack, true).getCount() < stack.getCount()) {
                    return true;
                }
            } catch (RuntimeException e) {
                TransferFaults.report(sink.endpoint.target(), e);
            }
        }
        return false;
    }

    /** An item must never be passed from a machine to itself in one operation. */
    private static boolean isSameMachine(Held a, Held b) {
        return a.endpoint.target().equals(b.endpoint.target());
    }

    private record Held(NetworkEndpoint endpoint, IItemHandler handler) {
    }
}
