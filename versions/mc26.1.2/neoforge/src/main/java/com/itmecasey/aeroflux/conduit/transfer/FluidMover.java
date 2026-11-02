package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Moves fluid through a fluid network. A network carries one kind of fluid at a time: the first
 * source (in endpoint order) that holds any decides it, and only sources holding that fluid and
 * sinks that accept it take part in the step.
 *
 * <p>Filter upgrades narrow this down: a face whose filter rejects the fluid neither gives nor
 * takes it, and a fluid that no sink's filter would let in is passed over when choosing. Priority
 * upgrades decide which sinks fill first.
 */
final class FluidMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        int limit = (int) Math.min(network.amountPerTick(), Integer.MAX_VALUE);

        List<Held> sources = new ArrayList<>();
        List<Held> sinks = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (!level.isLoaded(endpoint.target())) {
                continue;
            }
            ResourceHandler<FluidResource> handler =
                    level.getCapability(Capabilities.Fluid.BLOCK, endpoint.target(), endpoint.machineSide());
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

        if (sinks.isEmpty()) {
            return;
        }
        FluidResource fluid = pickFluid(sources, sinks, limit);
        if (fluid.isEmpty()) {
            return;
        }
        String id = PipeConfigActions.fluidId(fluid.getFluid());

        List<AmountMover.Port> sourcePorts = new ArrayList<>();
        List<DistributionMode> modes = new ArrayList<>();
        for (Held source : sources) {
            sourcePorts.add(new FluidPort(source, fluid, source.endpoint.allows(id)));
            modes.add(source.endpoint.distribution());
        }
        List<AmountMover.Port> sinkPorts = new ArrayList<>();
        for (Held sink : sinks) {
            sinkPorts.add(new FluidPort(sink, fluid, sink.endpoint.allows(id)));
        }
        AmountMover.move(sourcePorts, sinkPorts, modes.toArray(new DistributionMode[0]), limit, state);
    }

    /**
     * The fluid of the first source that can really give some, that its own filter lets out and
     * that at least one sink's filter lets in; or the empty resource. Without the filter check a
     * filtered network could pick a fluid nobody may take and stall on it forever.
     */
    private static FluidResource pickFluid(List<Held> sources, List<Held> sinks, int limit) {
        for (Held source : sources) {
            try {
                ResourceHandler<FluidResource> handler = source.handler;
                for (int slot = 0; slot < handler.size(); slot++) {
                    FluidResource candidate = handler.getResource(slot);
                    // A slot that lists a fluid may still refuse to give it, so ask before choosing.
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    String id = PipeConfigActions.fluidId(candidate.getFluid());
                    if (source.endpoint.allows(id) && anySinkAllows(sinks, id)
                            && HandlerAccess.extract(handler, candidate, limit, true) > 0) {
                        return candidate;
                    }
                }
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
        return FluidResource.EMPTY;
    }

    private static boolean anySinkAllows(List<Held> sinks, String id) {
        for (Held sink : sinks) {
            if (sink.endpoint.allows(id)) {
                return true;
            }
        }
        return false;
    }

    private record Held(NetworkEndpoint endpoint, ResourceHandler<FluidResource> handler) {
    }

    /**
     * A machine's fluid handler, restricted to the one fluid this step moves. A port whose filter
     * rejects that fluid reports nothing to give and no room, so the planner leaves it out.
     */
    private record FluidPort(Held held, FluidResource fluid, boolean allowed) implements AmountMover.Port {
        @Override
        public BlockPos target() {
            return held.endpoint.target();
        }

        @Override
        public long available(long limit) {
            return allowed ? HandlerAccess.extract(held.handler, fluid, (int) limit, true) : 0;
        }

        @Override
        public long accepting(long limit) {
            return allowed ? HandlerAccess.insert(held.handler, fluid, (int) limit, true) : 0;
        }

        @Override
        public long extract(long amount) {
            return allowed ? HandlerAccess.extract(held.handler, fluid, (int) amount, false) : 0;
        }

        /**
         * Puts fluid in. This is also how a source gets back what the sinks did not take, and a
         * source only gave fluid its filter allows, so the filter never blocks a return.
         */
        @Override
        public long insert(long amount) {
            return HandlerAccess.insert(held.handler, fluid, (int) amount, false);
        }

        @Override
        public int priority() {
            return held.endpoint.priority();
        }
    }
}
