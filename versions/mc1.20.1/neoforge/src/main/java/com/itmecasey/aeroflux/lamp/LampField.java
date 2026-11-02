package com.itmecasey.aeroflux.lamp;

import com.itmecasey.aeroflux.AeroFlux;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the lit lamps are, per dimension, and the spawn check that uses it: a monster may not spawn
 * naturally inside the cube of any lit lamp's radius. Spawners, eggs and commands are not affected.
 * Lamps add themselves each second they burn and take themselves out when they go dark or unload.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class LampField {
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> LIT = new HashMap<>();

    private LampField() {
    }

    static void light(Level level, BlockPos pos, int radius) {
        LIT.computeIfAbsent(level.dimension(), dimension -> new HashMap<>()).put(pos.immutable(), radius);
    }

    static void darken(Level level, BlockPos pos) {
        Map<BlockPos, Integer> lamps = LIT.get(level.dimension());
        if (lamps != null) {
            lamps.remove(pos);
        }
    }

    /** Whether a spot is inside the light of a lit lamp in that dimension. */
    public static boolean covers(ResourceKey<Level> dimension, BlockPos spot) {
        Map<BlockPos, Integer> lamps = LIT.get(dimension);
        if (lamps == null) {
            return false;
        }
        for (Map.Entry<BlockPos, Integer> lamp : lamps.entrySet()) {
            BlockPos at = lamp.getKey();
            int radius = lamp.getValue();
            if (Math.abs(spot.getX() - at.getX()) <= radius && Math.abs(spot.getY() - at.getY()) <= radius
                    && Math.abs(spot.getZ() - at.getZ()) <= radius) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        if (covers(event.getLevel().getLevel().dimension(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** A new world starts with no lamps. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LIT.clear();
    }
}
