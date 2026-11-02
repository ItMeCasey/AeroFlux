package com.itmecasey.aeroflux.conduit.transfer;

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

        FluidResource fluid = pickFluid(sources, limit);
        if (fluid.isEmpty() || sinks.isEmpty()) {
            return;
        }

        List<AmountMover.Port> sourcePorts = new ArrayList<>();
        List<DistributionMode> modes = new ArrayList<>();
        for (Held source : sources) {
            sourcePorts.add(new FluidPort(source, fluid));
            modes.add(source.endpoint.distribution());
        }
        List<AmountMover.Port> sinkPorts = new ArrayList<>();
        for (Held sink : sinks) {
            sinkPorts.add(new FluidPort(sink, fluid));
        }
        AmountMover.move(sourcePorts, sinkPorts, modes.toArray(new DistributionMode[0]), limit, state);
    }

    /** The fluid of the first source that can really give some, or the empty resource. */
    private static FluidResource pickFluid(List<Held> sources, int limit) {
        for (Held source : sources) {
            try {
                ResourceHandler<FluidResource> handler = source.handler;
                for (int slot = 0; slot < handler.size(); slot++) {
                    FluidResource candidate = handler.getResource(slot);
                    // A slot that lists a fluid may still refuse to give it, so ask before choosing.
                    if (!candidate.isEmpty() && HandlerAccess.extract(handler, candidate, limit, true) > 0) {
                        return candidate;
                    }
                }
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
        return FluidResource.EMPTY;
    }

    private record Held(NetworkEndpoint endpoint, ResourceHandler<FluidResource> handler) {
    }

    /** A machine's fluid handler, restricted to the one fluid this step moves. */
    private record FluidPort(Held held, FluidResource fluid) implements AmountMover.Port {
        @Override
        public BlockPos target() {
            return held.endpoint.target();
        }

        @Override
        public long available(long limit) {
            return HandlerAccess.extract(held.handler, fluid, (int) limit, true);
        }

        @Override
        public long accepting(long limit) {
            return HandlerAccess.insert(held.handler, fluid, (int) limit, true);
        }

        @Override
        public long extract(long amount) {
            return HandlerAccess.extract(held.handler, fluid, (int) amount, false);
        }

        @Override
        public long insert(long amount) {
            return HandlerAccess.insert(held.handler, fluid, (int) amount, false);
        }
    }
}
