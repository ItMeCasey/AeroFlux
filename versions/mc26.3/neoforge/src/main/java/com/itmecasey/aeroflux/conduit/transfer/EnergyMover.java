package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** Moves energy (FE) through an energy network, sharing it out the way {@link AmountMover} plans. */
final class EnergyMover implements ResourceMover {
    @Override
    public void run(ServerLevel level, LaneNetwork network, LaneState state) {
        long limit = Math.min(network.amountPerTick(), Integer.MAX_VALUE);

        List<AmountMover.Port> sources = new ArrayList<>();
        List<DistributionMode> modes = new ArrayList<>();
        List<AmountMover.Port> sinks = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (!level.isLoaded(endpoint.target())) {
                continue;
            }
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, endpoint.target(), endpoint.machineSide());
            if (handler == null) {
                continue;
            }
            // The handler has no "can extract" flag; a machine that cannot give simply reports nothing available.
            EnergyPort port = new EnergyPort(endpoint.target(), handler, endpoint.priority());
            if (endpoint.pulls()) {
                sources.add(port);
                modes.add(endpoint.distribution());
            }
            if (endpoint.pushes()) {
                sinks.add(port);
            }
        }
        AmountMover.move(sources, sinks, modes.toArray(new DistributionMode[0]), limit, state);
    }

    /** A machine's energy handler. Energy has one kind, so no filtering is needed; only priority applies. */
    private record EnergyPort(BlockPos target, EnergyHandler handler, int priority) implements AmountMover.Port {
        @Override
        public long available(long limit) {
            return HandlerAccess.extractEnergy(handler, (int) limit, true);
        }

        @Override
        public long accepting(long limit) {
            return HandlerAccess.insertEnergy(handler, (int) limit, true);
        }

        @Override
        public long extract(long amount) {
            return HandlerAccess.extractEnergy(handler, (int) amount, false);
        }

        @Override
        public long insert(long amount) {
            return HandlerAccess.insertEnergy(handler, (int) amount, false);
        }
    }
}
