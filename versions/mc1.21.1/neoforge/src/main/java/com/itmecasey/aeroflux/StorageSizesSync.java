package com.itmecasey.aeroflux;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Sends the server's {@link StorageSizes} to its players: once when a player joins, before the
 * chunks around them arrive, and again to everyone when the config file is reloaded.
 *
 * <p>The message is registered as optional, so a client without AeroFlux can still join a server
 * that has it for anything else it allows, and the other way round.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class StorageSizesSync {
    private StorageSizesSync() {
    }

    /** The message: the whole size table. */
    public record Payload(StorageSizes.Table table) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Ids.mod("storage_sizes"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> payload.table().write(buf),
                buf -> new Payload(StorageSizes.Table.read(buf)));

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> StorageSizes.useServerSizes(payload.table()));
    }

    @SubscribeEvent
    static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new Payload(StorageSizes.fromConfig()));
        }
    }

    @SubscribeEvent
    static void configReloaded(ModConfigEvent.Reloading event) {
        // Only this mod's own file matters, and only while a server is running to send it from.
        // The file watcher reloads on its own thread, so the sending is handed to the server's.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() == AeroFluxConfig.SPEC && server != null) {
            server.execute(() -> PacketDistributor.sendToAllPlayers(new Payload(StorageSizes.fromConfig())));
        }
    }

    /** The client's side: leaving a world drops that server's sizes. */
    @EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    static final class Client {
        private Client() {
        }

        @SubscribeEvent
        static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            StorageSizes.forgetServerSizes();
        }
    }
}
