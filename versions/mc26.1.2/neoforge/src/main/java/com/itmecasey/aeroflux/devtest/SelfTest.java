package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A development-only check that runs inside a real server and exercises the blocks the way a
 * player would: placing bundles, joining them, saving and loading them, and letting collectors
 * run through a day and a night. It does nothing unless the environment variable
 * {@code AEROFLUX_SELFTEST} is set, so a normal game never runs it.
 *
 * <p>Results are written to the log as {@code AEROFLUX-SELFTEST PASS/FAIL <name>}, followed by a
 * summary line, and the server then stops. The transfer scenarios (pipes moving fluid, items and
 * redstone) live in {@link TransferSelfTest}, and the Conduit Tool actions in {@link ToolSelfTest}, and the pipe screen's menu in {@link ScreenSelfTest}. The machines and their screens' menus are in {@link MachineSelfTest}, and machine upgrades and redstone control in {@link MachineUpgradeSelfTest}. The chunk loaders are in {@link ChunkLoaderSelfTest}, and the Flux Configurator in {@link ConfiguratorSelfTest}.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class SelfTest {
    private static final String ENV = "AEROFLUX_SELFTEST";
    /** Ticks to let collectors run before looking at their tanks. */
    private static final int RUN_TICKS = 120;

    private static MinecraftServer server;
    private static ServerLevel level;
    private static BlockPos origin;
    private static int phase = -1;
    private static int ticks;
    private static int passed;
    private static int failed;

    // The collector scenario: a collector with a tank beside it, and one with its sky blocked.
    private static BlockPos openCollector;
    private static BlockPos openTank;
    private static BlockPos coveredCollector;
    private static BlockPos coveredTank;

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv(ENV) == null) {
            return;
        }
        server = event.getServer();
        level = server.overworld();
        // High above any terrain, with open sky, inside the always-loaded spawn chunks.
        origin = new BlockPos(2, 200, 2);

        // Time and weather must hold still; 26.1 renamed these rules from the old doDaylightCycle.
        command("gamerule advance_time false");
        command("gamerule advance_weather false");
        command("weather clear");
        // Only forced chunks tick without a player nearby, and block entities tick only in those.
        command("forceload add 0 0");
        runBundleChecks();
        setUpCollectors();
        TransferSelfTest.setUp(level);
        ToolSelfTest.setUp(level);
        ScreenSelfTest.setUp(level);
        MachineSelfTest.setUp(level);
        MachineUpgradeSelfTest.setUp(level);
        ChunkLoaderSelfTest.setUp(level);
        MoreMachinesSelfTest.setUp(level);
        StorageSelfTest.run(level);
        MachineExtrasSelfTest.setUp(level);
        BlockIoSelfTest.setUp(level);
        EnchantConfigSelfTest.setUp(level);
        FluxScreenSelfTest.setUp(level);
        PolishSelfTest.setUp(level);
        ConfiguratorSelfTest.setUp(level);
        // In 26.1 the time command takes a clock time marker; these two sit at 1000 and 18000.
        command("time set day");
        phase = 0;
        ticks = 0;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (phase < 0) {
            return;
        }
        TransferSelfTest.tick();
        // The collector, tank and cost bar screen checks build in a layer of their own and need no other check done.
        FluxScreenSelfTest.tick();
        // So do the creative tank, creative tab and sound checks.
        PolishSelfTest.tick();
        // The tool checks come last: they place and remove many bundles, which must not disturb the timed transfer checks.
        if (TransferSelfTest.finished()) {
            ToolSelfTest.tick();
        }
        // The screen checks follow the tool checks for the same reason.
        if (ToolSelfTest.finished()) {
            ScreenSelfTest.tick();
        }
        // The machine checks run beside the tool and screen checks: they build in their own layer of the
        // chunk and use no timing the other two disturb. They still wait for the timed transfer checks.
        if (TransferSelfTest.finished()) {
            MachineSelfTest.tick();
            // The upgrade and redstone checks build in a layer of their own above the machine checks.
            MachineUpgradeSelfTest.tick();
            // The chunk loaders build in a layer of their own too, below the tool checks.
            ChunkLoaderSelfTest.tick();
            // The second wave of machines builds in a layer of its own as well.
            MoreMachinesSelfTest.tick();
            // The upgrades and imported recipes build in a layer of their own too.
            MachineExtrasSelfTest.tick();
            // The tank, comparator, hopper and dropper checks build in a layer of their own too.
            BlockIoSelfTest.tick();
            // So do the Flux Configurator checks.
            ConfiguratorSelfTest.tick();
        }
        // The enchanting config checks change global settings, so they wait until the machine checks,
        // which test the defaults, are done.
        if (MachineSelfTest.finished()) {
            EnchantConfigSelfTest.tick();
        }
        if (phase == 2) {
            // The collector checks are done; wait for the transfer, tool, screen and machine checks to finish too.
            if (TransferSelfTest.finished() && ToolSelfTest.finished() && ScreenSelfTest.finished()
                    && MachineSelfTest.finished() && MachineUpgradeSelfTest.finished() && ChunkLoaderSelfTest.finished()
                    && MoreMachinesSelfTest.finished() && MachineExtrasSelfTest.finished() && BlockIoSelfTest.finished()
                    && EnchantConfigSelfTest.finished() && FluxScreenSelfTest.finished()
                    && PolishSelfTest.finished() && ConfiguratorSelfTest.finished()) {
                finish();
            }
            return;
        }
        if (++ticks < RUN_TICKS) {
            return;
        }
        ticks = 0;
        if (phase == 0) {
            checkDay();
            // Empty the tanks and move to the middle of the night.
            drain(tank(openTank).tank());
            drain(tank(coveredTank).tank());
            command("time set midnight");
            phase = 1;
        } else if (phase == 1) {
            checkNight();
            phase = 2;
        }
    }

    // --- Conduit bundles ---------------------------------------------------------------------

    private static void runBundleChecks() {
        BlockPos a = origin;
        BlockPos b = origin.east();
        level.setBlock(a, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(b, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        ConduitBundleBlockEntity bundleA = (ConduitBundleBlockEntity) level.getBlockEntity(a);
        ConduitBundleBlockEntity bundleB = (ConduitBundleBlockEntity) level.getBlockEntity(b);
        check("bundle blocks have block entities", bundleA != null && bundleB != null);
        if (bundleA == null || bundleB == null) {
            return;
        }

        check("a free lane accepts a pipe", bundleA.addPipe(PipeType.FLUID));
        check("a taken lane refuses a second pipe", !bundleA.addPipe(PipeType.FLUID));
        bundleB.addPipe(PipeType.FLUID);
        int fluid = PipeType.FLUID.lane();
        check("matching pipes join (A east)", bundleA.bundle().isConnected(fluid, Face.EAST)
                && !bundleA.bundle().isExternal(fluid, Face.EAST));
        check("matching pipes join (B west)", bundleB.bundle().isConnected(fluid, Face.WEST));

        bundleB.addPipe(PipeType.ITEM);
        check("a pipe still joins its kind next to a bundle with more pipes",
                bundleA.bundle().isConnected(fluid, Face.EAST) && bundleB.bundle().isConnected(fluid, Face.WEST));
        check("a pipe with no match next door does not join",
                !bundleB.bundle().isConnected(PipeType.ITEM.lane(), Face.WEST));

        bundleA.bundle().setMode(fluid, Face.EAST, ConnectionMode.BLOCKED);
        bundleA.changed();
        check("a blocked face never connects", !bundleA.bundle().isConnected(fluid, Face.EAST));
        check("blocking one side drops the other side too", !bundleB.bundle().isConnected(fluid, Face.WEST));

        // A fluid pipe should attach to a tank, which draws a machine connector.
        BlockPos tankPos = a.south();
        level.setBlock(tankPos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        bundleA.refreshConnections();
        check("a fluid pipe attaches to a tank", bundleA.bundle().isConnected(fluid, Face.SOUTH)
                && bundleA.bundle().isExternal(fluid, Face.SOUTH));
        check("an attachment adds a plate", ConduitLayout.of(bundleA.bundle()).plateOf(Face.SOUTH) != null);

        BlockState state = level.getBlockState(a);
        VoxelShape shape = state.getShape(level, a);
        check("the hit shape is not empty", !shape.isEmpty());
        check("the hit shape grows with connections", shape.max(Direction.Axis.Z) > 0.625);

        // Save, then load into a fresh block entity: the contents must survive.
        CompoundTag saved = bundleA.saveWithFullMetadata(level.registryAccess());
        ConduitBundleBlockEntity restored = new ConduitBundleBlockEntity(a, state);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        check("a bundle survives saving and loading", restored.bundle().equals(bundleA.bundle()));

        bundleA.addPipe(PipeType.ENERGY);
        List<ItemStack> drops = Block.getDrops(state, level, a, bundleA);
        check("breaking a bundle drops one item per pipe", drops.size() == bundleA.bundle().set().size());
    }

    // --- Collectors and tanks ----------------------------------------------------------------

    private static void setUpCollectors() {
        openCollector = origin.offset(10, 0, 0);
        openTank = openCollector.east();
        coveredCollector = origin.offset(10, 0, 6);
        coveredTank = coveredCollector.east();

        level.setBlock(openCollector, ModBlocks.collector(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(openTank, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(coveredCollector, ModBlocks.collector(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(coveredTank, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        // A roof over the second collector hides it from the sky.
        level.setBlock(coveredCollector.above(3), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void checkDay() {
        FluxCollectorBlockEntity open_ = collector(openCollector);
        AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO day={} gameTime={} sees-sky={} ticks-blocks={} raining={} buffer={}mB tank={}mB",
                level.getDefaultClockTime(), level.getGameTime(), level.canSeeSky(openCollector.above()),
                level.shouldTickBlocksAt(openCollector), level.isRaining(),
                open_.outputView().getAmountAsLong(0), tank(openTank).tank().getAmountAsLong(0));
        ResourceHandler<FluidResource> open = tank(openTank).tank();
        check("a collector in the open makes Aero Flux by day",
                !open.getResource(0).isEmpty() && open.getResource(0).getFluid().isSame(ModFluids.AERO_FLUX.get()));
        check("a covered collector makes nothing", tank(coveredTank).tank().getAmountAsLong(0) == 0);
        check("the collector pushed its flux into the tank beside it", open.getAmountAsLong(0) > 0);
    }

    private static void checkNight() {
        ResourceHandler<FluidResource> open = tank(openTank).tank();
        check("a collector in the open makes Moon Flux by night",
                !open.getResource(0).isEmpty() && open.getResource(0).getFluid().isSame(ModFluids.MOON_FLUX.get()));
        check("a covered collector still makes nothing", tank(coveredTank).tank().getAmountAsLong(0) == 0);
        check("the collector keeps a buffer object", collector(openCollector).outputView() != null);

        // Clients draw the fluid from the tank's update data, so it must carry the fluid.
        FluxTankBlockEntity filled = tank(openTank);
        CompoundTag update = filled.getUpdateTag(level.registryAccess());
        FluxTankBlockEntity clientCopy = new FluxTankBlockEntity(openTank, level.getBlockState(openTank));
        clientCopy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), update));
        check("a tank's update data carries its fluid", clientCopy.tank().getAmountAsLong(0) == filled.tank().getAmountAsLong(0)
                && clientCopy.tank().getResource(0).getFluid().isSame(ModFluids.MOON_FLUX.get()));
    }

    private static FluxTankBlockEntity tank(BlockPos pos) {
        return (FluxTankBlockEntity) level.getBlockEntity(pos);
    }

    private static FluxCollectorBlockEntity collector(BlockPos pos) {
        return (FluxCollectorBlockEntity) level.getBlockEntity(pos);
    }

    /** Takes everything out of a tank, as a machine draining it would. */
    private static void drain(ResourceHandler<FluidResource> handler) {
        FluidResource held = handler.getResource(0);
        if (held.isEmpty()) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            handler.extract(0, held, (int) handler.getAmountAsLong(0), transaction);
            transaction.commit();
        }
    }

    // --- Plumbing ----------------------------------------------------------------------------

    private static void command(String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** Whether the collector checks, which need day and night on time, are done with the clock. */
    static boolean collectorChecksDone() {
        return phase == 2;
    }

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
        }
        AeroFlux.LOGGER.info("AEROFLUX-SELFTEST {} {}", condition ? "PASS" : "FAIL", name);
    }

    private static void finish() {
        AeroFlux.LOGGER.info("AEROFLUX-SELFTEST RESULT passed={} failed={}", passed, failed);
        phase = -1;
        server.halt(false);
    }
}
