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
import com.itmecasey.aeroflux.registry.ModCapabilities;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

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
public final class PipeFluidInlet implements IFluidHandler {
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
    @Nullable
    public static IFluidHandler forSide(ConduitBundleBlockEntity bundle, @Nullable Direction side) {
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
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    /** As much as the network may still move this tick, so callers that size their push by it ask for the right amount. */
    @Override
    public int getTankCapacity(int tank) {
        if (!(bundle.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        LaneNetwork network = TransferNetworks.networkFor(level, bundle.getBlockPos(), PipeType.FLUID);
        return network == null ? 0 : (int) Math.min(Integer.MAX_VALUE, network.amountPerTick());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !(bundle.getLevel() instanceof ServerLevel level)) {
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
        int budget = (int) Math.min(Math.min(resource.getAmount(), state.remaining(gameTime, network.amountPerTick())), Integer.MAX_VALUE);
        int placed = 0;
        for (IFluidHandler sink : sinks(level, network, entry, id)) {
            if (placed >= budget) {
                break;
            }
            placed += sink.fill(new FluidStack(resource, budget - placed), action);
        }
        if (action.execute()) {
            state.spend(gameTime, placed);
        }
        return placed;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    /** The network's endpoint for this face, or {@code null} if the network does not know it yet. */
    @Nullable
    private NetworkEndpoint entry(LaneNetwork network, BlockPos pos) {
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.bundle().equals(pos) && endpoint.face() == face) {
                return endpoint;
            }
        }
        return null;
    }

    /** The handlers the network pushes into that let this fluid in, highest priority first, leaving out the block it came from. */
    private static List<IFluidHandler> sinks(ServerLevel level, LaneNetwork network, NetworkEndpoint entry, String id) {
        List<NetworkEndpoint> targets = new ArrayList<>();
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.pushes() && endpoint.allows(id) && !endpoint.target().equals(entry.target())
                    && level.isLoaded(endpoint.target())) {
                targets.add(endpoint);
            }
        }
        // A stable sort, so equal priorities keep endpoint order.
        targets.sort(Comparator.comparingInt(NetworkEndpoint::priority).reversed());
        List<IFluidHandler> handlers = new ArrayList<>();
        for (NetworkEndpoint endpoint : targets) {
            IFluidHandler handler = ModCapabilities.find(level, ForgeCapabilities.FLUID_HANDLER, endpoint.target(), endpoint.machineSide());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }
}
