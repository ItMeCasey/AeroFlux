package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Sends the server's recipe import rules to its players, so JEI can list the imported recipes
 * under the machines that run them. The recipes themselves need no sending: the client already
 * gets every recipe of every mod and works the imports out the same way.
 *
 * <p>The rules go out whenever the server syncs its data packs, which is on joining and after
 * {@code /reload}. That happens just before the recipes are sent, so the rules are there by the
 * time the viewers read the recipes. The channel is optional, like the storage size one.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImportsSync {
    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    private RecipeImportsSync() {
    }

    /** The most rules one message may claim; a longer one is a broken or hostile packet. */
    private static final int MAX_RULES = 100_000;

    /** The message: every rule, written as JSON. */
    public record Payload(List<String> rules) {
        /** Writes the message. */
        public static void write(Payload payload, FriendlyByteBuf buf) {
            buf.writeCollection(payload.rules(), FriendlyByteBuf::writeUtf);
        }

        /** Reads the message. */
        public static Payload read(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_RULES) {
                throw new io.netty.handler.codec.DecoderException("Recipe import rule list too long: " + size);
            }
            List<String> rules = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rules.add(buf.readUtf());
            }
            return new Payload(rules);
        }
    }

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = ChannelBuilder.named(Ids.mod("recipe_imports"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Payload.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Payload::write)
                .decoder(Payload::read)
                .consumerNetworkThread((payload, context) -> {
                    receive(payload);
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** The message as the server would send it now. */
    public static Payload current() {
        return new Payload(RecipeImports.rulesForSync());
    }

    /** What a client does with the message; public so the self-test can play the client's part. */
    public static void receive(Payload payload) {
        RecipeImports.useSyncedRules(payload.rules());
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        Payload payload = current();
        for (ServerPlayer player : event.getPlayers()) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    /** The client's side: leaving a world drops that server's rules. */
    @Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RecipeImports.forgetSyncedRules();
        }
    }
}
