package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The player a Moon Spawner kills its mobs with. Forge on this version has no fake players, so
 * this is a real server player that never joins: it is never added to the world and never ticks.
 * It only holds the spawner's weapon while a loot table is rolled, which is what makes Looting and
 * player-only drops work.
 *
 * <p>Other mods' loot code sees it as the killer and may try to message it or send it something,
 * so it has a connection that quietly drops every packet and ignores every disconnect, the way
 * fake players on other loaders do.
 *
 * <p>There is one per dimension, made the first time a spawner there needs it and dropped when
 * the dimension unloads. Its name and id never change, so the server keeps a single entry for it.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class StandInPlayer extends ServerPlayer {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("aeroflux:spawner".getBytes(StandardCharsets.UTF_8)), "[AeroFlux]");
    private static final Map<ResourceKey<Level>, StandInPlayer> BY_DIMENSION = new HashMap<>();

    private StandInPlayer(ServerLevel level) {
        super(level.getServer(), level, PROFILE, ClientInformation.createDefault());
        // The listener sets itself as this player's connection.
        new NoConnection(level.getServer(), this);
    }

    /** The stand-in for a dimension. */
    public static StandInPlayer of(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), dimension -> new StandInPlayer(level));
    }

    /** Lets go of a dimension's stand-in with the dimension, so an old world is never kept alive. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BY_DIMENSION.remove(level.dimension());
        }
    }

    /** A connection to nowhere: whatever is sent is dropped, and it cannot be closed. */
    private static final class NoConnection extends ServerGamePacketListenerImpl {
        NoConnection(MinecraftServer server, ServerPlayer player) {
            // Spelled out because a player's waypoint code has a Connection type of its own.
            super(server, new net.minecraft.network.Connection(PacketFlow.SERVERBOUND), player,
                    new CommonListenerCookie(player.getGameProfile(), 0, ClientInformation.createDefault(), false));
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }

        @Override
        public void disconnect(Component reason) {
        }

        @Override
        public void disconnect(DisconnectionDetails details) {
        }
    }
}
