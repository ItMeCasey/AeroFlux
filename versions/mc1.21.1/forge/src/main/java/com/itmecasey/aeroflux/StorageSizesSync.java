package com.itmecasey.aeroflux;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Sends the server's {@link StorageSizes} to its players: once when a player joins, before the
 * chunks around them arrive, and again to everyone when the config file is reloaded.
 *
 * <p>The channel is optional, so a client without AeroFlux can still join a server that has it
 * for anything else it allows, and the other way round.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class StorageSizesSync {
    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    private StorageSizesSync() {
    }

    /** The message: the whole size table. */
    private record Message(StorageSizes.Table table) {
    }

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = ChannelBuilder.named(Ids.mod("storage_sizes"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Message.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> message.table().write(buf))
                .decoder(buf -> new Message(StorageSizes.Table.read(buf)))
                // Taking the table is a single write, safe on the network thread, and it then lands
                // before the chunk data that follows it is turned into block entities.
                .consumerNetworkThread((message, context) -> {
                    StorageSizes.useServerSizes(message.table());
                    context.setPacketHandled(true);
                })
                .add();
    }

    @SubscribeEvent
    public static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            channel.send(new Message(StorageSizes.fromConfig()), PacketDistributor.PLAYER.with(player));
        }
    }

    /** The config reload, which is a mod bus event on this version. */
    @Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void configReloaded(ModConfigEvent.Reloading event) {
            // Only this mod's own file matters, and only while a server is running to send it from.
            // The file watcher reloads on its own thread, so the sending is handed to the server's.
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (event.getConfig().getSpec() == AeroFluxConfig.SPEC && server != null) {
                server.execute(() -> channel.send(new Message(StorageSizes.fromConfig()), PacketDistributor.ALL.noArg()));
            }
        }
    }

    /** The client's side: leaving a world drops that server's sizes. */
    @Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            StorageSizes.forgetServerSizes();
        }
    }
}
