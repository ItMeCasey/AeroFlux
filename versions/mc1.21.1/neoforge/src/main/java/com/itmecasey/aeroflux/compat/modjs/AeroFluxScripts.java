package com.itmecasey.aeroflux.compat.modjs;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.ChunkLoaderRegistry;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.aeroflux.conduit.transfer.LaneNetwork;
import com.itmecasey.aeroflux.conduit.transfer.NetworkEndpoint;
import com.itmecasey.aeroflux.conduit.transfer.TransferNetworks;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.script.AeroFluxApi;
import com.itmecasey.aeroflux.core.script.ModJsBridge;
import com.itmecasey.aeroflux.core.script.RecipeShape;
import com.itmecasey.aeroflux.core.script.ScriptEvents;
import com.itmecasey.aeroflux.core.script.ScriptInfo;
import com.itmecasey.aeroflux.core.script.ScriptWorld;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The ModJS side of AeroFlux: gives scripts the {@code AeroFlux} global and answers its reads from the server. ModJS is
 * optional and found by reflection (see {@link ModJsBridge}), so this class loads and does nothing without it. Reads never
 * change the game, never load a chunk, and return {@code null} when there is nothing there.
 */
public final class AeroFluxScripts implements ScriptWorld {
    private static final AeroFluxScripts WORLD = new AeroFluxScripts();

    private AeroFluxScripts() {
    }

    /** Offers the {@code AeroFlux} global to ModJS. Called from the mod constructor; ModJS loads after the mod. */
    public static void install() {
        try {
            ModJsBridge.install(type -> new AeroFluxApi(type, WORLD));
        } catch (RuntimeException | LinkageError e) {
            AeroFlux.LOGGER.warn("AeroFlux: ModJS support could not start: {}", e.toString());
        }
    }

    /** The game side of the API, for tests and for Java mods that build their own {@link AeroFluxApi}. */
    public static ScriptWorld world() {
        return WORLD;
    }

    // --- Events ------------------------------------------------------------------------------

    /** A machine finished a job. */
    public static void machineFinished(Level level, BlockPos pos, FluxMachineBlockEntity machine) {
        if (ScriptEvents.hasListeners(ScriptEvents.MACHINE_FINISHED)) {
            ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.MACHINE_FINISHED)
                    .at(dimension(level), pos.getX(), pos.getY(), pos.getZ())
                    .id(machine.machine().id()).tier(machine.tier().id()).build());
        }
    }

    /** A pipe face changed its mode. */
    public static void pipeModeChanged(Level level, BlockPos pos, PipeType pipe, Face face, ConnectionMode mode) {
        if (level != null && ScriptEvents.hasListeners(ScriptEvents.PIPE_MODE_CHANGED)) {
            ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.PIPE_MODE_CHANGED)
                    .at(dimension(level), pos.getX(), pos.getY(), pos.getZ())
                    .id(pipe.id()).face(face.name().toLowerCase(Locale.ROOT)).mode(mode.name().toLowerCase(Locale.ROOT)).build());
        }
    }

    /** An Upgrade Card raised a block one tier. */
    public static void tierUpgraded(Level level, BlockPos pos, Block block, Tier tier, Player player) {
        if (ScriptEvents.hasListeners(ScriptEvents.TIER_UPGRADED)) {
            ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.TIER_UPGRADED)
                    .at(dimension(level), pos.getX(), pos.getY(), pos.getZ())
                    .id(BuiltInRegistries.BLOCK.getKey(block).toString()).tier(tier.id())
                    .player(player.getName().getString()).build());
        }
    }

    // --- The game, for the API ---------------------------------------------------------------

    @Override
    public String version() {
        return ModList.get().getModContainerById(AeroFlux.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("");
    }

    @Override
    public RecipeShape recipeShape() {
        return RecipeShape.V1_21;
    }

    @Override
    public ScriptInfo.Machine machine(String dimension, int x, int y, int z) {
        if (!(entityAt(dimension, x, y, z) instanceof FluxMachineBlockEntity machine)) {
            return null;
        }
        String status = machine.redstoneDisabled() ? "disabled" : machine.status().name().toLowerCase(Locale.ROOT);
        return new ScriptInfo.Machine(machine.machine().id(), machine.tier().id(), status, machine.progress(),
                machine.totalTime(), machine.fluxPerTick(), machine.speedUpgrades(), machine.capacityUpgrades(),
                machine.redstoneMode().id(), flux(machine));
    }

    @Override
    public ScriptInfo.Flux flux(String dimension, int x, int y, int z) {
        BlockEntity entity = entityAt(dimension, x, y, z);
        return entity == null ? null : flux(entity);
    }

    @Override
    public ScriptInfo.Network network(String dimension, int x, int y, int z, PipeType pipe) {
        ServerLevel level = level(dimension);
        BlockPos pos = new BlockPos(x, y, z);
        if (level == null || !level.hasChunkAt(pos)) {
            return null;
        }
        LaneNetwork network = TransferNetworks.networkFor(level, pos, pipe);
        if (network == null) {
            return null;
        }
        int sources = 0;
        int destinations = 0;
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.pulls()) {
                sources++;
            }
            if (endpoint.pushes()) {
                destinations++;
            }
        }
        return new ScriptInfo.Network(pipe.id(), network.members().size(), sources, destinations, network.amountPerTick(),
                network.itemsPerOperation(), network.ticksPerOperation(), network.truncated());
    }

    @Override
    public ScriptInfo.Player player(String name) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null || name == null ? null : server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            return null;
        }
        UUID id = player.getUUID();
        int running = ChunkLoaderRegistry.activeOwnedBy(server, Optional.of(id), null, BlockPos.ZERO);
        return new ScriptInfo.Player(player.getName().getString(), id.toString(), running, AeroFluxConfig.chunkLoadersPerPlayer());
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static String dimension(Level level) {
        return level.dimension().location().toString();
    }

    private static ServerLevel level(String dimension) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || dimension == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (dimension(level).equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    /** The block entity at a place, without loading a chunk for it. */
    private static BlockEntity entityAt(String dimension, int x, int y, int z) {
        ServerLevel level = level(dimension);
        BlockPos pos = new BlockPos(x, y, z);
        return level == null || !level.hasChunkAt(pos) ? null : level.getBlockEntity(pos);
    }

    private static ScriptInfo.Flux flux(BlockEntity entity) {
        LookInfo.Flux held = LookInfo.flux(entity);
        if (held == null) {
            return null;
        }
        return new ScriptInfo.Flux(holder(entity), held.type() == null ? "" : held.type().id(), held.amount(), held.capacity());
    }

    private static String holder(BlockEntity entity) {
        if (entity instanceof FluxMachineBlockEntity) {
            return "machine";
        }
        if (entity instanceof FluxCollectorBlockEntity) {
            return "collector";
        }
        if (entity instanceof FluxTankBlockEntity) {
            return "tank";
        }
        if (entity instanceof FluxConverterBlockEntity) {
            return "converter";
        }
        if (entity instanceof FluxChunkLoaderBlockEntity) {
            return "chunk_loader";
        }
        return "";
    }
}
