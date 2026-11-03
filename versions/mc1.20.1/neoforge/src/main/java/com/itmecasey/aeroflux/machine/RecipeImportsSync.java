package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModRecipes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Sends the recipes AeroFlux imports from other mods, and the sieve drops of the Sifter, to
 * its players, so JEI, REI and EMI can list them under the machines that run them. Imported
 * recipes are worked out on the server from recipe files that a 1.20.1 client never has, so unlike
 * on newer versions the finished recipes travel, not the rules.
 *
 * <p>They go out whenever the server syncs its data packs, which is on joining and after
 * {@code /reload}, in small messages so none comes near the size limit. The channel is optional
 * on both ends, like the storage size one.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImportsSync {
    private static final String PROTOCOL = "1";
    /** The most recipes or drops in one message. */
    private static final int BATCH = 60;

    /** Built by {@link #register()}, while mods load. */
    private static SimpleChannel channel;

    /** What the client got: imported recipes by machine, and the sieve drops. */
    private static final Map<MachineType, List<FluxRecipe>> RECIPES = new EnumMap<>(MachineType.class);
    private static volatile List<SieveRoll> sieve = List.of();

    private RecipeImportsSync() {
    }

    /**
     * One message: a batch of imported recipes of a machine, or, with no machine, of sieve drops.
     *
     * @param machine the machine the recipes belong to, or {@code null} for sieve drops
     * @param first   whether this batch starts the list, so the client drops what it had
     */
    public record Batch(MachineType machine, boolean first, List<FluxRecipe> recipes, List<SieveRoll> drops) {
    }

    /** Builds the channel. Forge wants channels made while mods load, so the mod constructor calls this. */
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(Ids.mod("recipe_imports"), () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        channel.registerMessage(0, Batch.class, RecipeImportsSync::write, RecipeImportsSync::read,
                RecipeImportsSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    // --- What a client knows -----------------------------------------------------------------

    /** The imported recipes of a machine as this client was sent them. */
    public static synchronized List<FluxRecipe> recipesFor(MachineType machine) {
        return List.copyOf(RECIPES.getOrDefault(machine, List.of()));
    }

    /** The sieve drops as this client was sent them. */
    public static List<SieveRoll> sieve() {
        return sieve;
    }

    /** Takes one batch; public so the self-test can play the client's part. */
    public static synchronized void receive(Batch batch) {
        if (batch.machine() == null) {
            List<SieveRoll> all = batch.first() ? new ArrayList<>() : new ArrayList<>(sieve);
            all.addAll(batch.drops());
            sieve = List.copyOf(all);
            return;
        }
        List<FluxRecipe> list = RECIPES.computeIfAbsent(batch.machine(), machine -> new ArrayList<>());
        if (batch.first()) {
            list.clear();
        }
        list.addAll(batch.recipes());
    }

    /** Forgets everything the server sent, for when the player leaves it. */
    public static synchronized void forget() {
        RECIPES.clear();
        sieve = List.of();
    }

    // --- The server's side -------------------------------------------------------------------

    /** Every batch the server would send now, in order. */
    public static List<Batch> batches(ServerLevel level) {
        List<Batch> batches = new ArrayList<>();
        for (MachineType machine : MachineType.values()) {
            if (!machine.hasOwnRecipes()) {
                continue;
            }
            List<FluxRecipe> recipes = RecipeImports.of(level, machine);
            for (int start = 0; start == 0 || start < recipes.size(); start += BATCH) {
                batches.add(new Batch(machine, start == 0,
                        recipes.subList(start, Math.min(recipes.size(), start + BATCH)), List.of()));
            }
        }
        List<SieveRoll> drops = SieveImports.forViewer(level);
        for (int start = 0; start == 0 || start < drops.size(); start += BATCH) {
            batches.add(new Batch(null, start == 0, List.of(), drops.subList(start, Math.min(drops.size(), start + BATCH))));
        }
        return batches;
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        if (channel == null || ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().overworld();
        List<Batch> batches = batches(level);
        List<ServerPlayer> players = event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer());
        for (ServerPlayer player : players) {
            for (Batch batch : batches) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), batch);
            }
        }
    }

    // --- Wire format -------------------------------------------------------------------------

    private static void write(Batch batch, FriendlyByteBuf buf) {
        buf.writeBoolean(batch.first());
        buf.writeVarInt(batch.machine() == null ? -1 : batch.machine().ordinal());
        buf.writeVarInt(batch.recipes().size());
        for (FluxRecipe recipe : batch.recipes()) {
            buf.writeResourceLocation(recipe.getId());
            ModRecipes.serializer(recipe.machine()).get().toNetwork(buf, recipe);
        }
        buf.writeVarInt(batch.drops().size());
        for (SieveRoll drop : batch.drops()) {
            drop.material().toNetwork(buf);
            drop.mesh().toNetwork(buf);
            buf.writeItem(drop.product());
            writeAmount(buf, drop.amount());
            buf.writeVarInt(drop.water());
        }
    }

    private static Batch read(FriendlyByteBuf buf) {
        boolean first = buf.readBoolean();
        int machineIndex = buf.readVarInt();
        MachineType machine = machineIndex < 0 || machineIndex >= MachineType.values().length ? null : MachineType.values()[machineIndex];
        List<FluxRecipe> recipes = new ArrayList<>();
        int recipeCount = readCount(buf);
        for (int i = 0; i < recipeCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            recipes.add(ModRecipes.serializer(machine).get().fromNetwork(id, buf));
        }
        List<SieveRoll> drops = new ArrayList<>();
        int dropCount = readCount(buf);
        for (int i = 0; i < dropCount; i++) {
            Ingredient material = Ingredient.fromNetwork(buf);
            Ingredient mesh = Ingredient.fromNetwork(buf);
            drops.add(new SieveRoll(material, mesh, buf.readItem(), readAmount(buf), buf.readVarInt()));
        }
        return new Batch(machine, first, recipes, drops);
    }

    private static void writeAmount(FriendlyByteBuf buf, SieveMesh.Amount amount) {
        if (amount instanceof SieveMesh.Constant constant) {
            buf.writeByte(0);
            buf.writeVarInt(constant.count());
        } else if (amount instanceof SieveMesh.Uniform uniform) {
            buf.writeByte(1);
            buf.writeVarInt(uniform.min());
            buf.writeVarInt(uniform.max());
        } else if (amount instanceof SieveMesh.Binomial binomial) {
            buf.writeByte(2);
            buf.writeVarInt(binomial.tries());
            buf.writeDouble(binomial.chance());
        } else if (amount instanceof SieveMesh.Sum sum) {
            buf.writeByte(3);
            buf.writeVarInt(sum.parts().size());
            sum.parts().forEach(part -> writeAmount(buf, part));
        }
    }

    private static SieveMesh.Amount readAmount(FriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 1 -> new SieveMesh.Uniform(buf.readVarInt(), buf.readVarInt());
            case 2 -> new SieveMesh.Binomial(buf.readVarInt(), buf.readDouble());
            case 3 -> {
                int count = readCount(buf);
                List<SieveMesh.Amount> parts = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    parts.add(readAmount(buf));
                }
                yield new SieveMesh.Sum(parts);
            }
            default -> new SieveMesh.Constant(buf.readVarInt());
        };
    }

    private static void handle(Batch batch, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context network = context.get();
        // Plain list updates, safe on the network thread, and they land before the viewers read them.
        receive(batch);
        network.setPacketHandled(true);
    }

    /** The client's side: leaving a world drops that server's recipes. */
    @Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            forget();
        }
    }

    /** The most entries any one list in this packet may claim; a longer one is a broken or hostile packet. */
    private static final int MAX_ENTRIES = 100_000;

    private static int readCount(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException("Recipe sync list too long: " + count);
        }
        return count;
    }
}
