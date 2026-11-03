package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
 *
 * <p>The protection layer also uses this kind of player, one per machine owner (see {@link #ownedBy}):
 * the claim checks are asked as that owner, so their id and name are the ones it carries.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class StandInPlayer extends ServerPlayer {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("aeroflux:spawner".getBytes(StandardCharsets.UTF_8)), "[AeroFlux]");
    /** The most owners' stand-ins kept at once; the longest unused one goes first. */
    private static final int MAX_OWNERS = 64;
    private static final Map<ResourceKey<Level>, StandInPlayer> BY_DIMENSION = new HashMap<>();
    private static final Map<OwnerKey, StandInPlayer> BY_OWNER = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OwnerKey, StandInPlayer> eldest) {
            return size() > MAX_OWNERS;
        }
    };

    private record OwnerKey(ResourceKey<Level> dimension, UUID owner) {
    }

    private StandInPlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        // The listener sets itself as this player's connection.
        new NoConnection(level.getServer(), this);
    }

    /** The stand-in for a dimension. */
    public static StandInPlayer of(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), dimension -> new StandInPlayer(level, PROFILE));
    }

    /**
     * The stand-in for a machine's owner in a dimension: a player with the owner's id and name, so a
     * claim mod judges what the machine does as that owner, whether or not they are online.
     */
    public static StandInPlayer ownedBy(ServerLevel level, UUID owner) {
        return BY_OWNER.computeIfAbsent(new OwnerKey(level.dimension(), owner), key -> {
            GameProfileCache names = level.getServer().getProfileCache();
            String name = names == null ? PROFILE.getName()
                    : names.get(owner).map(GameProfile::getName).orElse(PROFILE.getName());
            return new StandInPlayer(level, new GameProfile(owner, name));
        });
    }

    /** Lets go of a dimension's stand-in with the dimension, so an old world is never kept alive. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BY_DIMENSION.remove(level.dimension());
            BY_OWNER.keySet().removeIf(key -> key.dimension().equals(level.dimension()));
        }
    }

    /** A connection to nowhere: whatever is sent is dropped, and it cannot be closed. */
    private static final class NoConnection extends ServerGamePacketListenerImpl {
        NoConnection(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    new CommonListenerCookie(player.getGameProfile(), 0, ClientInformation.createDefault(), false));
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        }

        @Override
        public void disconnect(Component reason) {
        }

        @Override
        public void disconnect(DisconnectionDetails details) {
        }
    }
}
