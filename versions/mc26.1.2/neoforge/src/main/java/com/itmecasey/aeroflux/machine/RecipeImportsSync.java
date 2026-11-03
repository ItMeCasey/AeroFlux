package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipeSerializer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Sends the recipes AeroFlux imports from other mods, and the Sifter's sieve drops, to its
 * players, so JEI and REI can list them under the machines that run them. Since Minecraft 26.1 a
 * client is sent only the recipe types the mod asks for ({@link MachineRecipes}), and the imports
 * are made of other mods' recipes, so the server works them out and sends the result.
 *
 * <p>They go out whenever the server syncs its data packs, which is on joining and after
 * {@code /reload}. The message is optional, like the storage size one.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImportsSync {
    private RecipeImportsSync() {
    }

    /**
     * The message.
     *
     * @param recipes the imported recipes, by machine
     * @param drops   the sieve drops
     */
    public record Payload(Map<MachineType, List<RecipeHolder<FluxRecipe>>> recipes, List<SieveRoll> drops) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Ids.mod("recipe_imports"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(RecipeImportsSync::write, RecipeImportsSync::read);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /** The message as the server would send it now. */
    public static Payload current(net.minecraft.server.level.ServerLevel level) {
        return new Payload(RecipeImports.forSync(level), SieveImports.of(level));
    }

    /** What a client does with the message; public so the self-test can play the client's part. */
    public static void receive(Payload payload) {
        RecipeImports.useSynced(payload.recipes());
        SieveImports.useSynced(payload.drops());
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.CODEC, (payload, context) -> receive(payload));
    }

    @SubscribeEvent
    static void datapackSync(OnDatapackSyncEvent event) {
        Payload payload = current(event.getPlayerList().getServer().overworld());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    /** The client's side: leaving a world drops that server's imports. */
    @EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    static final class Client {
        private Client() {
        }

        @SubscribeEvent
        static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RecipeImports.useSynced(Map.of());
            SieveImports.useSynced(List.of());
        }
    }

    // --- Wire format -------------------------------------------------------------------------

    private static final int CONSTANT = 0;
    private static final int UNIFORM = 1;
    private static final int BINOMIAL = 2;
    private static final int SUM = 3;

    private static final Map<MachineType, RecipeSerializer<FluxRecipe>> SERIALIZERS = new EnumMap<>(MachineType.class);

    private static synchronized RecipeSerializer<FluxRecipe> serializer(MachineType machine) {
        return SERIALIZERS.computeIfAbsent(machine, FluxRecipeSerializer::create);
    }

    private static void write(RegistryFriendlyByteBuf buf, Payload payload) {
        buf.writeVarInt(payload.recipes().size());
        payload.recipes().forEach((machine, holders) -> {
            buf.writeEnum(machine);
            buf.writeVarInt(holders.size());
            for (RecipeHolder<FluxRecipe> holder : holders) {
                buf.writeIdentifier(holder.id().identifier());
                serializer(machine).streamCodec().encode(buf, holder.value());
            }
        });
        buf.writeVarInt(payload.drops().size());
        for (SieveRoll drop : payload.drops()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, drop.material());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, drop.mesh());
            ItemStackTemplate.STREAM_CODEC.encode(buf, drop.product());
            writeAmount(buf, drop.amount());
            buf.writeVarInt(drop.water());
        }
    }

    private static Payload read(RegistryFriendlyByteBuf buf) {
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> recipes = new EnumMap<>(MachineType.class);
        int machines = readCount(buf);
        for (int i = 0; i < machines; i++) {
            MachineType machine = buf.readEnum(MachineType.class);
            int count = readCount(buf);
            List<RecipeHolder<FluxRecipe>> holders = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                Identifier id = buf.readIdentifier();
                FluxRecipe recipe = serializer(machine).streamCodec().decode(buf);
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
                holders.add(new RecipeHolder<>(key, recipe));
            }
            recipes.put(machine, holders);
        }
        int dropCount = readCount(buf);
        List<SieveRoll> drops = new ArrayList<>();
        for (int i = 0; i < dropCount; i++) {
            Ingredient material = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient mesh = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            ItemStackTemplate product = ItemStackTemplate.STREAM_CODEC.decode(buf);
            SieveMesh.Amount amount = readAmount(buf);
            drops.add(new SieveRoll(material, mesh, product, amount, buf.readVarInt()));
        }
        return new Payload(recipes, drops);
    }

    private static void writeAmount(RegistryFriendlyByteBuf buf, SieveMesh.Amount amount) {
        switch (amount) {
            case SieveMesh.Constant constant -> {
                buf.writeByte(CONSTANT);
                buf.writeVarInt(constant.count());
            }
            case SieveMesh.Uniform uniform -> {
                buf.writeByte(UNIFORM);
                buf.writeVarInt(uniform.min());
                buf.writeVarInt(uniform.max());
            }
            case SieveMesh.Binomial binomial -> {
                buf.writeByte(BINOMIAL);
                buf.writeVarInt(binomial.tries());
                buf.writeDouble(binomial.chance());
            }
            case SieveMesh.Sum sum -> {
                buf.writeByte(SUM);
                buf.writeVarInt(sum.parts().size());
                sum.parts().forEach(part -> writeAmount(buf, part));
            }
        }
    }

    private static SieveMesh.Amount readAmount(RegistryFriendlyByteBuf buf) {
        int kind = buf.readByte();
        return switch (kind) {
            case UNIFORM -> new SieveMesh.Uniform(buf.readVarInt(), buf.readVarInt());
            case BINOMIAL -> new SieveMesh.Binomial(buf.readVarInt(), buf.readDouble());
            case SUM -> {
                int parts = readCount(buf);
                List<SieveMesh.Amount> list = new ArrayList<>();
                for (int i = 0; i < parts; i++) {
                    list.add(readAmount(buf));
                }
                yield new SieveMesh.Sum(list);
            }
            default -> new SieveMesh.Constant(buf.readVarInt());
        };
    }

    /** The most entries any one list in this packet may claim; a longer one is a broken or hostile packet. */
    private static final int MAX_ENTRIES = 100_000;

    private static int readCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException("Recipe sync list too long: " + count);
        }
        return count;
    }
}
