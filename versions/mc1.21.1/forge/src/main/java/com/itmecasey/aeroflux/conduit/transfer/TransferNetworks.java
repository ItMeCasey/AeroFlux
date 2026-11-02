package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Remembers the networks found in each level, so a big network is searched once and not by every
 * bundle on every tick.
 *
 * <p>Each level has a topology version. Any change to a bundle bumps it, which makes every cached
 * network stale; the next bundle to ask rebuilds its own network and refreshes the cache entry of
 * every member, so one search serves the whole network. A network is also rebuilt every
 * {@value #REFRESH_TICKS} ticks, to pick up changes the version cannot see (a machine swapped for
 * another, for example). All of this runs on the server thread only.
 */
public final class TransferNetworks {
    /** Largest network that is searched; larger ones are cut off. A config option can replace this later. */
    public static final int MAX_NODES = 4096;
    /** The most ticks a cached network is trusted for. */
    static final int REFRESH_TICKS = 10;

    /** Weak keys, so an unloaded level (and everything cached for it) can be collected. */
    private static final Map<Level, LevelData> LEVELS = new WeakHashMap<>();

    private TransferNetworks() {
    }

    /** What is cached for one level. Holds positions only, never the level itself. */
    private static final class LevelData {
        int version;
        final Map<LaneKey, LaneNetwork> networks = new HashMap<>();
        final Map<LaneKey, LaneState> states = new HashMap<>();
    }

    private record LaneKey(BlockPos pos, int lane) {
    }

    private static LevelData data(Level level) {
        return LEVELS.computeIfAbsent(level, key -> new LevelData());
    }

    /**
     * The current network of the pipe in {@code lane} of the bundle at {@code pos}, from the cache
     * when it is fresh, otherwise searched again.
     *
     * @return the network, or {@code null} if that bundle holds no such pipe
     */
    public static LaneNetwork networkFor(ServerLevel level, BlockPos pos, PipeType type) {
        LevelData data = data(level);
        LaneKey key = new LaneKey(pos, type.lane());
        LaneNetwork cached = data.networks.get(key);
        if (cached != null && cached.type() == type && cached.version() == data.version
                && level.getGameTime() - cached.builtAt() < REFRESH_TICKS) {
            return cached;
        }
        LaneNetwork fresh = NetworkScanner.scan(level, pos, type, MAX_NODES, data.version);
        if (fresh == null) {
            data.networks.remove(key);
            return null;
        }
        for (BlockPos member : fresh.members()) {
            data.networks.put(new LaneKey(member, type.lane()), fresh);
        }
        return fresh;
    }

    /** The memory kept by the leader of a lane, created on first use. */
    static LaneState stateFor(ServerLevel level, BlockPos leader, int lane) {
        return data(level).states.computeIfAbsent(new LaneKey(leader, lane),
                key -> new LaneState(leader.asLong() * 31 + lane));
    }

    /** Marks every cached network of the level as stale. Call whenever a bundle changes or goes away. */
    public static void topologyChanged(Level level) {
        if (!level.isClientSide) {
            data(level).version++;
        }
    }

    /** Drops what is remembered for a bundle that was removed. */
    public static void forget(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        LevelData data = data(level);
        data.version++;
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            LaneKey key = new LaneKey(pos, lane);
            data.networks.remove(key);
            data.states.remove(key);
        }
    }
}
