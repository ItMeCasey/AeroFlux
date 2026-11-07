package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointFilter;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.NetworkFinder;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Finds the network of one lane around a bundle and works out what it can do.
 *
 * <p>The nodes of the search are block positions. A bundle is a node for a lane when it holds
 * the same pipe type there, whatever else it holds. Two bundles are neighbors when the face
 * between them is a pipe-to-pipe connection on both sides. Unloaded chunks end the network: nothing is read from them.
 */
final class NetworkScanner {
    /**
     * The order that decides who leads and how endpoints are numbered: lowest y first, then lowest
     * z, then lowest x. It is a total order on positions, so every member of a network agrees on
     * the same leader without talking to each other.
     */
    static final Comparator<BlockPos> LEADER_ORDER = Comparator
            // Lambdas, because BlockPos also has static getY(long) methods that confuse method references.
            .<BlockPos>comparingInt(pos -> pos.getY())
            .thenComparingInt(pos -> pos.getZ())
            .thenComparingInt(pos -> pos.getX());

    private NetworkScanner() {
    }

    /**
     * Searches the lane of the bundle at {@code start}.
     *
     * @param maxNodes size limit for the search
     * @param version  the topology version to stamp on the result
     * @return the network, or {@code null} if there is no such pipe at {@code start}
     */
    static LaneNetwork scan(ServerLevel level, BlockPos start, PipeType type, int maxNodes, int version) {
        int lane = type.lane();
        if (bundleAt(level, start, type) == null) {
            return null;
        }
        NetworkFinder.Result<BlockPos> found = NetworkFinder.find(start, node -> neighbors(level, node, type), maxNodes);

        List<BlockPos> members = new ArrayList<>();
        for (BlockPos pos : found.nodes()) {
            if (bundleAt(level, pos, type) != null) {
                members.add(pos);
            }
        }
        members.sort(LEADER_ORDER);

        List<NetworkEndpoint> endpoints = new ArrayList<>();
        long amount = Long.MAX_VALUE;
        int ticks = 1;
        // The start is always a member, so these minimums and maximums are set by the first pass.
        int items = Integer.MAX_VALUE;
        for (BlockPos pos : members) {
            ConduitBundleBlockEntity entity = bundleAt(level, pos, type);
            BundleState bundle = entity.bundle();
            collectEndpoints(pos, bundle, entity.endpoints(), lane, endpoints);
            int speed = bundle.speedUpgrades(lane);
            int capacity = bundle.capacityUpgrades(lane);
            amount = Math.min(amount, PipeUpgrades.amountPerTick(type, bundle.tier(lane), speed, capacity));
            ticks = Math.max(ticks, PipeUpgrades.ticksPerOperation(bundle.tier(lane), speed));
            items = Math.min(items, PipeUpgrades.itemsPerOperation(bundle.tier(lane), capacity));
        }
        return new LaneNetwork(type, members.get(0), List.copyOf(members), List.copyOf(endpoints), amount, ticks,
                items, found.truncated(), version, level.getGameTime());
    }

    /**
     * Adds every face of this bundle that ends at a machine and does something. Faces come in
     * index order. Each endpoint gets its own copy of the face's filter, so the snapshot does not
     * change under the movers if the player edits the filter; the edit rebuilds the network anyway.
     */
    private static void collectEndpoints(BlockPos pos, BundleState bundle, EndpointSettings settings, int lane,
                                         List<NetworkEndpoint> into) {
        for (Face face : Face.values()) {
            if (!bundle.isConnected(lane, face) || !bundle.isExternal(lane, face)) {
                continue;
            }
            ConnectionMode mode = bundle.mode(lane, face);
            if (mode == ConnectionMode.OFF || mode == ConnectionMode.BLOCKED) {
                continue;
            }
            BlockPos target = pos.relative(Direction.from3DDataValue(face.index()));
            EndpointFilter filter = settings.filter(lane, face);
            into.add(new NetworkEndpoint(pos, face, target, mode, bundle.distribution(lane),
                    filter == null ? null : filter.copy(), settings.priority(lane, face)));
        }
    }

    /** The positions joined to {@code node} in the lane of the given pipe type. */
    private static List<BlockPos> neighbors(ServerLevel level, BlockPos node, PipeType type) {
        List<BlockPos> result = new ArrayList<>(6);
        int lane = type.lane();
        ConduitBundleBlockEntity self = bundleAt(level, node, type);
        if (self == null) {
            return result;
        }
        for (Direction direction : Direction.values()) {
            // A bundle only reaches out through its own pipe-to-pipe connections.
            Face face = Face.byIndex(direction.get3DDataValue());
            if (!self.bundle().isConnected(lane, face) || self.bundle().isExternal(lane, face)) {
                continue;
            }
            BlockPos next = node.relative(direction);
            if (connectsBack(level, next, direction.getOpposite(), type)) {
                result.add(next);
            }
        }
        return result;
    }

    /** Whether the bundle at {@code pos} holds the pipe and is joined to a pipe on its given side. */
    private static boolean connectsBack(ServerLevel level, BlockPos pos, Direction side, PipeType type) {
        ConduitBundleBlockEntity other = bundleAt(level, pos, type);
        if (other == null) {
            return false;
        }
        Face face = Face.byIndex(side.get3DDataValue());
        return other.bundle().isConnected(type.lane(), face) && !other.bundle().isExternal(type.lane(), face);
    }

    /** The bundle at {@code pos} if it is loaded and holds exactly this pipe type, otherwise {@code null}. */
    private static ConduitBundleBlockEntity bundleAt(ServerLevel level, BlockPos pos, PipeType type) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
                && bundle.bundle().set().contains(type)) {
            return bundle;
        }
        return null;
    }
}
