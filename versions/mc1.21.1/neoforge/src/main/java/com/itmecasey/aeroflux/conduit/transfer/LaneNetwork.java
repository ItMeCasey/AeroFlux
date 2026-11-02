package com.itmecasey.aeroflux.conduit.transfer;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A snapshot of one lane's network: which bundles belong to it, which of them leads, where it
 * touches machines, and how fast it may move things. It is rebuilt now and then, never edited.
 *
 * @param type             the pipe type shared by every member
 * @param leader           the member that does the network's work, see {@link NetworkScanner#LEADER_ORDER}
 * @param members          every bundle in the network, leader first, in leader order
 * @param endpoints        every machine face, in a fixed order (bundle position, then face)
 * @param amountPerTick    the most a fluid, energy or gas network may move per tick (slowest pipe)
 * @param ticksPerOperation ticks between two item operations (slowest pipe)
 * @param itemsPerOperation items moved by one source in one operation (smallest batch)
 * @param truncated        true if the search hit the size limit, so the network is only partly known
 * @param version          the topology version this snapshot was built for
 * @param builtAt          the game time when it was built
 */
public record LaneNetwork(PipeType type, BlockPos leader, List<BlockPos> members, List<NetworkEndpoint> endpoints,
                          long amountPerTick, int ticksPerOperation, int itemsPerOperation, boolean truncated,
                          int version, long builtAt) {
}
