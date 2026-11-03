package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.NeighborCapabilities;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.itmecasey.aeroflux.conduit.NeighborCapabilities;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

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
        // Whatever other mods already pushed into the network this tick comes off the limit.
        long gameTime = level.getGameTime();
        int limit = (int) Math.min(state.remaining(gameTime, network.amountPerTick()), Integer.MAX_VALUE);
        if (limit <= 0) {
            return;
        }

        List<Held> sources = new ArrayList<>();
        List<Held> sinks = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (!level.isLoaded(endpoint.target())) {
                continue;
            }
            IFluidHandler handler = NeighborCapabilities.find(level, endpoint.target(),
                    ForgeCapabilities.FLUID_HANDLER, endpoint.machineSide());
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
        FluidStack fluid = pickFluid(sources, sinks, limit);
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
        state.spend(gameTime, AmountMover.move(sourcePorts, sinkPorts, modes.toArray(new DistributionMode[0]), limit, state));
    }

    /**
     * The fluid of the first source that can really give some, that its own filter lets out and
     * that at least one sink's filter lets in, as a one-off stack describing its type; or empty.
     * Without the filter check a filtered network could pick a fluid nobody may take and stall on
     * it forever.
     */
    private static FluidStack pickFluid(List<Held> sources, List<Held> sinks, int limit) {
        for (Held source : sources) {
            try {
                IFluidHandler handler = source.handler;
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack held = handler.getFluidInTank(tank);
                    if (held.isEmpty()) {
                        continue;
                    }
                    String id = PipeConfigActions.fluidId(held.getFluid());
                    if (source.endpoint.allows(id) && anySinkAllows(sinks, id)
                            && !handler.drain(withAmount(held, limit), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                        return withAmount(held, 1);
                    }
                }
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
        return FluidStack.EMPTY;
    }

    private static boolean anySinkAllows(List<Held> sinks, String id) {
        for (Held sink : sinks) {
            if (sink.endpoint.allows(id)) {
                return true;
            }
        }
        return false;
    }

    private record Held(NetworkEndpoint endpoint, IFluidHandler handler) {
    }

    /**
     * A machine's fluid handler, restricted to the one fluid this step moves. A port whose filter
     * rejects that fluid reports nothing to give and no room, so the planner leaves it out.
     */
    private record FluidPort(Held held, FluidStack fluid, boolean allowed) implements AmountMover.Port {
        @Override
        public BlockPos target() {
            return held.endpoint.target();
        }

        @Override
        public long available(long limit) {
            return allowed ? held.handler.drain(withAmount(fluid, (int) limit), IFluidHandler.FluidAction.SIMULATE).getAmount() : 0;
        }

        @Override
        public long accepting(long limit) {
            return allowed ? held.handler.fill(withAmount(fluid, (int) limit), IFluidHandler.FluidAction.SIMULATE) : 0;
        }

        @Override
        public long extract(long amount) {
            return allowed ? held.handler.drain(withAmount(fluid, (int) amount), IFluidHandler.FluidAction.EXECUTE).getAmount() : 0;
        }

        /**
         * Puts fluid in. This is also how a source gets back what the sinks did not take, and a
         * source only gave fluid its filter allows, so the filter never blocks a return.
         */
        @Override
        public long insert(long amount) {
            return held.handler.fill(withAmount(fluid, (int) amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int priority() {
            return held.endpoint.priority();
        }
    }

    /** A copy of a fluid stack holding a different amount. */
    private static FluidStack withAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
