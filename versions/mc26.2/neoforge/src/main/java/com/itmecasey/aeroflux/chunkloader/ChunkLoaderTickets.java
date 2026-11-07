package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * The chunk tickets of the Flux Chunk Loaders, through one NeoForge {@link TicketController}.
 *
 * <p>Each ticket is owned by the loader's block position. NeoForge saves the tickets with the
 * world and puts them back when the server starts, before any loader has ticked; at that moment
 * {@link #validate} drops the tickets of loaders that no longer exist (they are missing from the
 * {@link ChunkLoaderRegistry}), that were not running, or that the server has switched off.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class ChunkLoaderTickets {
    public static final TicketController CONTROLLER = new TicketController(Ids.mod("chunk_loader"), ChunkLoaderTickets::validate);

    private ChunkLoaderTickets() {
    }

    @SubscribeEvent
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Forces every chunk in a loader's area to stay loaded and ticking. */
    public static void acquire(ServerLevel level, BlockPos loader, Tier tier) {
        forceArea(level, loader, tier, true);
    }

    /** Lets go of every chunk in a loader's area. Releasing chunks it never held is harmless. */
    public static void release(ServerLevel level, BlockPos loader, Tier tier) {
        forceArea(level, loader, tier, false);
    }

    private static void forceArea(ServerLevel level, BlockPos loader, Tier tier, boolean add) {
        ChunkPos center = ChunkPos.containing(loader);
        int radius = ChunkLoaderMath.radius(tier);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                CONTROLLER.forceChunk(level, loader, center.x() + dx, center.z() + dz, add, false);
            }
        }
    }

    /** Called by NeoForge as the saved tickets are about to come back; see the class comment. */
    private static void validate(ServerLevel level, TicketHelper helper) {
        ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
        boolean enabled = AeroFluxConfig.chunkLoadersEnabled();
        int dropped = 0;
        for (BlockPos owner : helper.getBlockTickets().keySet()) {
            boolean running = registry.get(owner).map(ChunkLoaderRegistry.Entry::active).orElse(false);
            if (!enabled || !running) {
                helper.removeAllTickets(owner);
                registry.setActive(owner, false);
                dropped++;
            }
        }
        if (dropped > 0) {
            AeroFlux.LOGGER.info("Dropped the chunk tickets of {} Flux Chunk Loader(s) in {}", dropped, level.dimension().identifier());
        }
    }
}
