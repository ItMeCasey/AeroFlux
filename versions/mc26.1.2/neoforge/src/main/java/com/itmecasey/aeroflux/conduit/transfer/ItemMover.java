package com.itmecasey.aeroflux.conduit.transfer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
            ResourceHandler<ItemResource> handler =
                    level.getCapability(Capabilities.Item.BLOCK, endpoint.target(), endpoint.machineSide());
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
        ItemResource item = source.handler.getResource(slot);
        int batch = HandlerAccess.extractFromSlot(source.handler, slot, item, batchSize, true);
        int[] order = source.endpoint.distribution().order(sinks.size(), state.rotation(), state.random());
        if (!anyAccepts(source, sinks, order, item, batch)) {
            return;
        }

        int remaining = HandlerAccess.extractFromSlot(source.handler, slot, item, batch, false);
        for (int index : order) {
            if (remaining <= 0) {
                break;
            }
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink)) {
                continue;
            }
            try {
                int fits = HandlerAccess.insert(sink.handler, item, remaining, true);
                if (fits > 0) {
                    remaining -= HandlerAccess.insert(sink.handler, item, remaining, false);
                }
            } catch (RuntimeException e) {
                TransferFaults.report(sink.endpoint.target(), e);
            }
        }
        if (remaining > 0) {
            // Nobody took the rest after all; return it, and drop it as a last resort so nothing vanishes.
            remaining -= HandlerAccess.insert(source.handler, item, remaining, false);
            if (remaining > 0) {
                Block.popResource(level, source.endpoint.target(), item.toStack(remaining));
            }
        }
    }

    /** The first slot that gives something when asked for a batch, or -1. */
    private static int firstFilledSlot(ResourceHandler<ItemResource> handler, int batchSize) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && HandlerAccess.extractFromSlot(handler, slot, resource, batchSize, true) > 0) {
                return slot;
            }
        }
        return -1;
    }

    /** Whether at least one sink other than the source's own machine would take part of the stack. */
    private static boolean anyAccepts(Held source, List<Held> sinks, int[] order, ItemResource item, int count) {
        for (int index : order) {
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink)) {
                continue;
            }
            try {
                if (HandlerAccess.insert(sink.handler, item, count, true) > 0) {
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

    private record Held(NetworkEndpoint endpoint, ResourceHandler<ItemResource> handler) {
    }
}
