package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The chunk tickets of the Flux Chunk Loaders.
 *
 * <p>Forge on 26.1 has no ticket manager of its own, so the loaders use a vanilla ticket type of
 * their own. It is not saved with the world: when the server starts, {@link #restore} walks the
 * {@link ChunkLoaderRegistry} and puts the tickets of running loaders back, before any of them has
 * had a chance to tick. Loaders that were switched off by the server config are stopped there.
 *
 * <p>Vanilla keeps one ticket per chunk and type, however many loaders add it, so a chunk is only
 * let go once no other running loader in the dimension still covers it.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class ChunkLoaderTickets {
    /** Ticket level for entity ticking, the same strength as /forceload. */
    private static final int TICKET_RADIUS = 2;

    private static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, AeroFlux.MODID);

    /** Loads and simulates the chunk and keeps the dimension awake; never times out, never saved. */
    public static final RegistryObject<TicketType> CHUNK_LOADER = TICKET_TYPES.register("chunk_loader",
            () -> new TicketType(TicketType.NO_TIMEOUT,
                    TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private ChunkLoaderTickets() {
    }

    /** Hooks the ticket type into the mod event bus; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        TICKET_TYPES.register(modBusGroup);
    }

    /** Forces every chunk in a loader's area to stay loaded and ticking. */
    public static void acquire(ServerLevel level, BlockPos loader, Tier tier) {
        ChunkPos center = ChunkPos.containing(loader);
        int radius = ChunkLoaderMath.radius(tier);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.getChunkSource().addTicketWithRadius(CHUNK_LOADER.get(),
                        new ChunkPos(center.x() + dx, center.z() + dz), TICKET_RADIUS);
            }
        }
    }

    /**
     * Lets go of every chunk in a loader's area that no other running loader still needs.
     * Releasing chunks it never held is harmless.
     */
    public static void release(ServerLevel level, BlockPos loader, Tier tier) {
        ChunkPos center = ChunkPos.containing(loader);
        int radius = ChunkLoaderMath.radius(tier);
        ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunk = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!coveredByOther(registry, loader, chunk)) {
                    level.getChunkSource().removeTicketWithRadius(CHUNK_LOADER.get(), chunk, TICKET_RADIUS);
                }
            }
        }
    }

    private static boolean coveredByOther(ChunkLoaderRegistry registry, BlockPos except, ChunkPos chunk) {
        for (ChunkLoaderRegistry.Entry entry : registry.entries()) {
            if (!entry.active() || entry.pos().equals(except)) {
                continue;
            }
            ChunkPos center = ChunkPos.containing(entry.pos());
            int radius = ChunkLoaderMath.radius(entry.tier());
            if (Math.abs(chunk.x() - center.x()) <= radius && Math.abs(chunk.z() - center.z()) <= radius) {
                return true;
            }
        }
        return false;
    }

    /** Puts back the tickets of every running loader, or stops them all if the server turned them off. */
    @SubscribeEvent
    public static void restore(ServerStartedEvent event) {
        boolean enabled = AeroFluxConfig.chunkLoadersEnabled();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
            int restored = 0;
            int stopped = 0;
            for (ChunkLoaderRegistry.Entry entry : List.copyOf(registry.entries())) {
                if (!entry.active()) {
                    continue;
                }
                if (enabled) {
                    acquire(level, entry.pos(), entry.tier());
                    restored++;
                } else {
                    registry.setActive(entry.pos(), false);
                    stopped++;
                }
            }
            if (restored > 0) {
                AeroFlux.LOGGER.info("Restored the chunk tickets of {} Flux Chunk Loader(s) in {}", restored, level.dimension().identifier());
            }
            if (stopped > 0) {
                AeroFlux.LOGGER.info("Stopped {} Flux Chunk Loader(s) in {}: chunk loaders are off", stopped, level.dimension().identifier());
            }
        }
    }
}
