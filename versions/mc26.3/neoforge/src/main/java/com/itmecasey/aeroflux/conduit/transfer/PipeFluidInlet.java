package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * What another mod's pipe, pump or machine sees on the face of a fluid pipe: a way in. AeroFlux
 * pipes hold no fluid of their own (each tick a network moves fluid straight from its sources to
 * its sinks), so whatever is pushed in here is handed on at once to the network's sinks, the same
 * way the network's own step would: by priority, through their filters, and never back to the
 * block it came from. Together with the network's own step it stays within the pipe's rate per
 * tick (see {@link LaneState#remaining}).
 *
 * <p>Only faces that take fluid in (Open and Import) offer one, and nothing can be drained
 * through it; the network pulls from other mods' blocks itself.
 */
public final class PipeFluidInlet implements ResourceHandler<FluidResource> {
    private final ConduitBundleBlockEntity bundle;
    private final Face face;

    private PipeFluidInlet(ConduitBundleBlockEntity bundle, Face face) {
        this.bundle = bundle;
        this.face = face;
    }

    /**
     * The inlet on one side of a bundle, or {@code null} when that side takes no fluid in: no fluid
     * pipe, not joined to a block there, or a face that only exports or is off. Decided from the
     * bundle's synced state alone, so the client sees the same faces as the server.
     */
    public static @Nullable ResourceHandler<FluidResource> forSide(ConduitBundleBlockEntity bundle, @Nullable Direction side) {
        if (side == null) {
            return null;
        }
        BundleState state = bundle.bundle();
        int lane = PipeType.FLUID.lane();
        Face face = Face.byIndex(side.get3DDataValue());
        if (!state.set().contains(PipeType.FLUID) || !state.isConnected(lane, face) || !state.isExternal(lane, face)) {
            return null;
        }
        ConnectionMode mode = state.mode(lane, face);
        return mode.pullsFromNeighbor() ? new PipeFluidInlet(bundle, face) : null;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    /** As much as the network may still move this tick, so callers that size their push by it ask for the right amount. */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        if (!(bundle.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        LaneNetwork network = TransferNetworks.networkFor(level, bundle.getBlockPos(), PipeType.FLUID);
        return network == null ? 0 : network.amountPerTick();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return true;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (index != 0 || resource.isEmpty() || amount <= 0 || !(bundle.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        BlockPos pos = bundle.getBlockPos();
        LaneNetwork network = TransferNetworks.networkFor(level, pos, PipeType.FLUID);
        if (network == null) {
            return 0;
        }
        NetworkEndpoint entry = entry(network, pos);
        String id = PipeConfigActions.fluidId(resource.getFluid());
        if (entry == null || !entry.allows(id)) {
            return 0;
        }
        LaneState state = TransferNetworks.stateFor(level, network.leader(), PipeType.FLUID.lane());
        long gameTime = level.getGameTime();
        int budget = (int) Math.min(Math.min(amount, state.remaining(gameTime, network.amountPerTick())), Integer.MAX_VALUE);
        int placed = 0;
        for (ResourceHandler<FluidResource> sink : sinks(level, network, entry, id)) {
            if (placed >= budget) {
                break;
            }
            placed += sink.insert(resource, budget - placed, transaction);
        }
        if (placed > 0) {
            state.spend(gameTime, placed, transaction);
        }
        return placed;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    /** The network's endpoint for this face, or {@code null} if the network does not know it yet. */
    private @Nullable NetworkEndpoint entry(LaneNetwork network, BlockPos pos) {
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.bundle().equals(pos) && endpoint.face() == face) {
                return endpoint;
            }
        }
        return null;
    }

    /** The handlers the network pushes into that let this fluid in, highest priority first, leaving out the block it came from. */
    private static List<ResourceHandler<FluidResource>> sinks(ServerLevel level, LaneNetwork network, NetworkEndpoint entry, String id) {
        List<NetworkEndpoint> targets = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.pushes() && endpoint.allows(id) && !endpoint.target().equals(entry.target())
                    && level.isLoaded(endpoint.target())) {
                targets.add(endpoint);
            }
        }
        // A stable sort, so equal priorities keep endpoint order.
        targets.sort(Comparator.comparingInt(NetworkEndpoint::priority).reversed());
        List<ResourceHandler<FluidResource>> handlers = new ArrayList<>();
        for (NetworkEndpoint endpoint : targets) {
            ResourceHandler<FluidResource> handler =
                    level.getCapability(Capabilities.Fluid.BLOCK, endpoint.target(), endpoint.machineSide());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }
}
