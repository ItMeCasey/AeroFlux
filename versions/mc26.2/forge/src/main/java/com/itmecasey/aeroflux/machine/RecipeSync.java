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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Sends the recipes the recipe viewers list to the players: each machine's own recipes, the ones
 * AeroFlux imports from other mods, and the Sifter's sieve drops. Since Minecraft 26.1 a client
 * gets no recipes of a mod's own types unless the mod sends them, and Forge has no hook for that,
 * so the server works the lists out and sends the result in a message of AeroFlux's own.
 *
 * <p>They go out whenever the server syncs its data packs, which is on joining and after
 * {@code /reload}. The channel is optional, like the storage size one.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeSync {
    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    private RecipeSync() {
    }

    /**
     * The message.
     *
     * @param own     the machines' own recipes, by machine
     * @param imports the imported recipes, by machine
     * @param drops   the sieve drops
     */
    public record Message(Map<MachineType, List<RecipeHolder<FluxRecipe>>> own,
                          Map<MachineType, List<RecipeHolder<FluxRecipe>>> imports, List<SieveRoll> drops) {
    }

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = ChannelBuilder.named(Ids.mod("recipe_sync"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Message.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> write(buf, message))
                .decoder(RecipeSync::read)
                .consumerNetworkThread((message, context) -> {
                    receive(message);
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** The message as the server would send it now. */
    public static Message current(ServerLevel level) {
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> own = new EnumMap<>(MachineType.class);
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                own.put(machine, List.copyOf(MachineRecipes.ownOf(level, machine)));
            }
        }
        return new Message(own, RecipeImports.forSync(level), SieveImports.of(level));
    }

    /** What a client does with the message. */
    public static void receive(Message message) {
        MachineRecipes.receive(message.own());
        RecipeImports.useSynced(message.imports());
        SieveImports.useSynced(message.drops());
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        Message message = current(event.getPlayerList().getServer().overworld());
        for (ServerPlayer player : event.getPlayers()) {
            channel.send(message, PacketDistributor.PLAYER.with(player));
        }
    }

    /** The client's side: leaving a world drops that server's lists. */
    @Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    static final class Client {
        private Client() {
        }

        @SubscribeEvent
        static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MachineRecipes.receive(Map.of());
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

    private static void writeSection(RegistryFriendlyByteBuf buf, Map<MachineType, List<RecipeHolder<FluxRecipe>>> section) {
        buf.writeVarInt(section.size());
        section.forEach((machine, holders) -> {
            buf.writeEnum(machine);
            buf.writeVarInt(holders.size());
            for (RecipeHolder<FluxRecipe> holder : holders) {
                buf.writeIdentifier(holder.id().identifier());
                serializer(machine).streamCodec().encode(buf, holder.value());
            }
        });
    }

    private static Map<MachineType, List<RecipeHolder<FluxRecipe>>> readSection(RegistryFriendlyByteBuf buf) {
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
        return recipes;
    }

    private static void write(RegistryFriendlyByteBuf buf, Message message) {
        writeSection(buf, message.own());
        writeSection(buf, message.imports());
        buf.writeVarInt(message.drops().size());
        for (SieveRoll drop : message.drops()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, drop.material());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, drop.mesh());
            ItemStackTemplate.STREAM_CODEC.encode(buf, drop.product());
            writeAmount(buf, drop.amount());
            buf.writeVarInt(drop.water());
        }
    }

    private static Message read(RegistryFriendlyByteBuf buf) {
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> own = readSection(buf);
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> imports = readSection(buf);
        int dropCount = readCount(buf);
        List<SieveRoll> drops = new ArrayList<>();
        for (int i = 0; i < dropCount; i++) {
            Ingredient material = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient mesh = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            ItemStackTemplate product = ItemStackTemplate.STREAM_CODEC.decode(buf);
            SieveMesh.Amount amount = readAmount(buf);
            drops.add(new SieveRoll(material, mesh, product, amount, buf.readVarInt()));
        }
        return new Message(own, imports, drops);
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
