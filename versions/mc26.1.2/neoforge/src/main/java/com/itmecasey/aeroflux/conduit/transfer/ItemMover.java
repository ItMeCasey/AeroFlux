package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.PriorityPlanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Moves items. Every few ticks (the slowest pipe sets the pace) each source gives one batch:
 * it takes up to the batch size from its first filled slot and offers it to the sinks in the
 * order its pipe's distribution mode says.
 *
 * <p>Filter upgrades skip what a face may not handle: a source only gives items its own filter
 * lets out, and a sink whose filter rejects the item is passed over. Sinks with a higher
 * priority (Priority upgrade) are offered the batch first; sinks of equal priority keep the
 * distribution mode's order.
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
        int slot = firstFilledSlot(source, sinks, batchSize);
        if (slot < 0) {
            return;
        }
        ItemResource item = source.handler.getResource(slot);
        String id = idOf(item);
        int batch = HandlerAccess.extractFromSlot(source.handler, slot, item, batchSize, true);
        int[] order = PriorityPlanner.byPriority(
                source.endpoint.distribution().order(sinks.size(), state.rotation(), state.random()), priorities(sinks));
        if (!anyAccepts(source, sinks, order, item, id, batch)) {
            return;
        }

        int remaining = HandlerAccess.extractFromSlot(source.handler, slot, item, batch, false);
        for (int index : order) {
            if (remaining <= 0) {
                break;
            }
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink) || !sink.endpoint.allows(id)) {
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

    /**
     * The first slot that gives something when asked for a batch, holding an item the source's
     * filter lets out and some sink's filter lets in; or -1. Skipping items no filter would pass
     * keeps one unwanted stack in the first slot from blocking everything behind it.
     */
    private static int firstFilledSlot(Held source, List<Held> sinks, int batchSize) {
        ResourceHandler<ItemResource> handler = source.handler;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            String id = idOf(resource);
            if (source.endpoint.allows(id) && anySinkAllows(source, sinks, id)
                    && HandlerAccess.extractFromSlot(handler, slot, resource, batchSize, true) > 0) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean anySinkAllows(Held source, List<Held> sinks, String id) {
        for (Held sink : sinks) {
            if (!isSameMachine(source, sink) && sink.endpoint.allows(id)) {
                return true;
            }
        }
        return false;
    }

    /** The registry id of an item, the form item filters store. */
    private static String idOf(ItemResource item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    /** The priority of every sink, indexed like {@code sinks}. */
    private static int[] priorities(List<Held> sinks) {
        int[] priorities = new int[sinks.size()];
        for (int j = 0; j < priorities.length; j++) {
            priorities[j] = sinks.get(j).endpoint.priority();
        }
        return priorities;
    }

    /** Whether at least one sink other than the source's own machine would take part of the stack. */
    private static boolean anyAccepts(Held source, List<Held> sinks, int[] order, ItemResource item, String id,
                                      int count) {
        for (int index : order) {
            Held sink = sinks.get(index);
            if (isSameMachine(source, sink) || !sink.endpoint.allows(id)) {
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
