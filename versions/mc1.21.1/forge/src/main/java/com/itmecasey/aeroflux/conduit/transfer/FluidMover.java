package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

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
            IFluidHandler handler = Neighbors.find(level, endpoint.target(), endpoint.machineSide(), ForgeCapabilities.FLUID_HANDLER);
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

        FluidStack fluid = pickFluid(sources, limit);
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

    /** The fluid of the first source that has any, as a one-off stack describing its type. */
    private static FluidStack pickFluid(List<Held> sources, int limit) {
        for (Held source : sources) {
            try {
                FluidStack offered = source.handler.drain(limit, IFluidHandler.FluidAction.SIMULATE);
                if (!offered.isEmpty()) {
                    return new FluidStack(offered, 1);
                }
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
        return FluidStack.EMPTY;
    }

    private record Held(NetworkEndpoint endpoint, IFluidHandler handler) {
    }

    /** A machine's fluid handler, restricted to the one fluid this step moves. */
    private record FluidPort(Held held, FluidStack fluid) implements AmountMover.Port {
        @Override
        public BlockPos target() {
            return held.endpoint.target();
        }

        @Override
        public long available(long limit) {
            return held.handler.drain(new FluidStack(fluid, (int) limit), IFluidHandler.FluidAction.SIMULATE).getAmount();
        }

        @Override
        public long accepting(long limit) {
            return held.handler.fill(new FluidStack(fluid, (int) limit), IFluidHandler.FluidAction.SIMULATE);
        }

        @Override
        public long extract(long amount) {
            return held.handler.drain(new FluidStack(fluid, (int) amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        public long insert(long amount) {
            return held.handler.fill(new FluidStack(fluid, (int) amount), IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
