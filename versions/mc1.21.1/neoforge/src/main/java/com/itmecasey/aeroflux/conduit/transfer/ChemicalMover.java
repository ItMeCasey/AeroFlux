package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.compat.mekanism.MekanismChemicals;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Moves Mekanism chemicals (gases, infusion types, pigments, slurries) through a gas network, the
 * same way {@link FluidMover} moves fluid: one chemical at a time, chosen from the first source that
 * holds one some sink will take, then shared out by {@link AmountMover}. Filter upgrades work on
 * chemical ids and Priority upgrades decide which sinks fill first.
 *
 * <p>This class names Mekanism classes, so {@link GasMover} only calls it when Mekanism is installed.
 */
final class ChemicalMover {
    private ChemicalMover() {
    }

    static void run(ServerLevel level, LaneNetwork network, LaneState state) {
        long limit = network.amountPerTick();

        List<Held> sources = new ArrayList<>();
        List<Held> sinks = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (!level.isLoaded(endpoint.target())) {
                continue;
            }
            IChemicalHandler handler = MekanismChemicals.handler(level, endpoint.target(), endpoint.machineSide());
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
        ChemicalStack chemical = pickChemical(sources, sinks, limit);
        if (chemical.isEmpty()) {
            return;
        }
        String id = MekanismChemicals.id(chemical);

        List<AmountMover.Port> sourcePorts = new ArrayList<>();
        List<DistributionMode> modes = new ArrayList<>();
        for (Held source : sources) {
            sourcePorts.add(new ChemicalPort(source, chemical, source.endpoint.allows(id)));
            modes.add(source.endpoint.distribution());
        }
        List<AmountMover.Port> sinkPorts = new ArrayList<>();
        for (Held sink : sinks) {
            sinkPorts.add(new ChemicalPort(sink, chemical, sink.endpoint.allows(id)));
        }
        AmountMover.move(sourcePorts, sinkPorts, modes.toArray(new DistributionMode[0]), limit, state);
    }

    /**
     * The chemical of the first source that can really give some, that its own filter lets out and
     * that at least one sink's filter lets in, as a one-off stack describing its type; or empty.
     */
    private static ChemicalStack pickChemical(List<Held> sources, List<Held> sinks, long limit) {
        for (Held source : sources) {
            try {
                IChemicalHandler handler = source.handler;
                for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
                    ChemicalStack held = handler.getChemicalInTank(tank);
                    if (held.isEmpty()) {
                        continue;
                    }
                    String id = MekanismChemicals.id(held);
                    if (source.endpoint.allows(id) && anySinkAllows(sinks, id)
                            && !handler.extractChemical(held.copyWithAmount(limit), Action.SIMULATE).isEmpty()) {
                        return held.copyWithAmount(1);
                    }
                }
            } catch (RuntimeException e) {
                TransferFaults.report(source.endpoint.target(), e);
            }
        }
        return ChemicalStack.EMPTY;
    }

    private static boolean anySinkAllows(List<Held> sinks, String id) {
        for (Held sink : sinks) {
            if (sink.endpoint.allows(id)) {
                return true;
            }
        }
        return false;
    }

    private record Held(NetworkEndpoint endpoint, IChemicalHandler handler) {
    }

    /**
     * A block's chemical handler, restricted to the one chemical this step moves. A port whose
     * filter rejects that chemical reports nothing to give and no room, so the planner leaves it out.
     * Mekanism's insert answers with what did not fit, so the amounts taken are worked out from that.
     */
    private record ChemicalPort(Held held, ChemicalStack chemical, boolean allowed) implements AmountMover.Port {
        @Override
        public BlockPos target() {
            return held.endpoint.target();
        }

        @Override
        public long available(long limit) {
            return allowed ? held.handler.extractChemical(chemical.copyWithAmount(limit), Action.SIMULATE).getAmount() : 0;
        }

        @Override
        public long accepting(long limit) {
            return allowed ? limit - held.handler.insertChemical(chemical.copyWithAmount(limit), Action.SIMULATE).getAmount() : 0;
        }

        @Override
        public long extract(long amount) {
            return allowed ? held.handler.extractChemical(chemical.copyWithAmount(amount), Action.EXECUTE).getAmount() : 0;
        }

        /**
         * Puts the chemical in. This is also how a source gets back what the sinks did not take,
         * and a source only gave a chemical its filter allows, so the filter never blocks a return.
         */
        @Override
        public long insert(long amount) {
            return amount - held.handler.insertChemical(chemical.copyWithAmount(amount), Action.EXECUTE).getAmount();
        }

        @Override
        public int priority() {
            return held.endpoint.priority();
        }
    }
}
