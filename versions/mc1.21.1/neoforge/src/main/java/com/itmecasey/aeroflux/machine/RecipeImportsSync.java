package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Sends the server's recipe import rules to its players, so JEI, REI and EMI can list the
 * imported recipes under the machines that run them. The recipes themselves need no sending:
 * the client already gets every recipe of every mod and works the imports out the same way.
 *
 * <p>The rules go out whenever the server syncs its data packs, which is on joining and after
 * {@code /reload}. That happens just before the recipes are sent, so the rules are there by the
 * time the viewers read the recipes. The message is optional, like the storage size one.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImportsSync {
    private RecipeImportsSync() {
    }

    /** The message: every rule, written as JSON. */
    public record Payload(List<String> rules) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Ids.mod("recipe_imports"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = ByteBufCodecs.STRING_UTF8
                .apply(ByteBufCodecs.list(100_000))
                .map(Payload::new, Payload::rules)
                .cast();

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
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
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.CODEC, (payload, context) -> receive(payload));
    }

    @SubscribeEvent
    static void datapackSync(OnDatapackSyncEvent event) {
        Payload payload = current();
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    /** The client's side: leaving a world drops that server's rules. */
    @EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    static final class Client {
        private Client() {
        }

        @SubscribeEvent
        static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RecipeImports.forgetSyncedRules();
        }
    }
}
