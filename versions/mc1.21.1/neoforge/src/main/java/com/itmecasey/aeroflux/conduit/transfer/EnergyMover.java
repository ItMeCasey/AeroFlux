package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

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
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, endpoint.target(), endpoint.machineSide());
            if (storage == null) {
                continue;
            }
            EnergyPort port = new EnergyPort(endpoint.target(), storage);
            if (endpoint.pulls() && storage.canExtract()) {
                sources.add(port);
                modes.add(endpoint.distribution());
            }
            if (endpoint.pushes() && storage.canReceive()) {
                sinks.add(port);
            }
        }
        AmountMover.move(sources, sinks, modes.toArray(new DistributionMode[0]), limit, state);
    }

    /** A machine's energy storage. Energy has one kind, so no filtering is needed. */
    private record EnergyPort(BlockPos target, IEnergyStorage storage) implements AmountMover.Port {
        @Override
        public long available(long limit) {
            return storage.extractEnergy((int) limit, true);
        }

        @Override
        public long accepting(long limit) {
            return storage.receiveEnergy((int) limit, true);
        }

        @Override
        public long extract(long amount) {
            return storage.extractEnergy((int) amount, false);
        }

        @Override
        public long insert(long amount) {
            return storage.receiveEnergy((int) amount, false);
        }
    }
}
