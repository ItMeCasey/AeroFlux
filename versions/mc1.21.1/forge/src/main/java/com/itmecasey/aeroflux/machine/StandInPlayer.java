package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The player a Moon Spawner kills its mobs with. Forge on this version has no fake players, so
 * this is a real server player that never joins: it has no connection, is never added to the
 * world and never ticks. It only holds the spawner's weapon while a loot table is rolled, which
 * is what makes Looting and player-only drops work.
 *
 * <p>There is one per dimension, made the first time a spawner there needs it and dropped when
 * the dimension unloads. Its name and id never change, so the server keeps a single entry for it.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
final class StandInPlayer extends ServerPlayer {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("aeroflux:spawner".getBytes(StandardCharsets.UTF_8)), "[AeroFlux]");
    private static final Map<ResourceKey<Level>, StandInPlayer> BY_DIMENSION = new HashMap<>();

    private StandInPlayer(ServerLevel level) {
        super(level.getServer(), level, PROFILE, ClientInformation.createDefault());
    }

    /** The stand-in for a dimension. */
    static StandInPlayer of(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), dimension -> new StandInPlayer(level));
    }

    /** Lets go of a dimension's stand-in with the dimension, so an old world is never kept alive. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BY_DIMENSION.remove(level.dimension());
        }
    }
}
