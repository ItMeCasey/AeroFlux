package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * The chunk tickets of the Flux Chunk Loaders, through Forge's {@link ForgeChunkManager}.
 *
 * <p>Each ticket is owned by the loader's block position. Forge saves the tickets with the world
 * and puts them back when the server starts, before any loader has ticked; at that moment
 * {@link #validate} drops the tickets of loaders that no longer exist (they are missing from the
 * {@link ChunkLoaderRegistry}), that were not running, or that the server has switched off.
 */
public final class ChunkLoaderTickets {
    private ChunkLoaderTickets() {
    }

    /** Hands Forge the check it runs on the saved tickets; call once while the mod sets up. */
    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(AeroFlux.MODID, ChunkLoaderTickets::validate);
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
        ChunkPos center = new ChunkPos(loader);
        int radius = ChunkLoaderMath.radius(tier);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ForgeChunkManager.forceChunk(level, AeroFlux.MODID, loader, center.x + dx, center.z + dz, add, false);
            }
        }
    }

    /** Called by Forge as the saved tickets are about to come back; see the class comment. */
    private static void validate(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
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
            AeroFlux.LOGGER.info("Dropped the chunk tickets of {} Flux Chunk Loader(s) in {}", dropped, level.dimension().location());
        }
    }
}
