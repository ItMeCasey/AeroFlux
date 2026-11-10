package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.client.screen.FluxMachineScreen;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.client.screen.ConfiguratorScreen;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.FluxCost;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.creative.CreativeFluxTankBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorActions;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * A development-only camera tour: it opens a fresh creative superflat world, builds the pipe
 * scenes a player would build, and saves a screenshot of each into {@code run/screenshots} as
 * {@code aeroflux_<name>.png}, then quits the game. It does nothing unless the environment
 * variable {@code AEROFLUX_SHOTS} is set, so a normal game never runs it.
 *
 * <p>This is the pipe scenes and the Flux screens of the newer builds' tour. Pipes and machines are
 * placed through the client's own right-click path ({@code MultiPlayerGameMode.useItemOn}), so the
 * shots show what a player sees after the client's prediction and the server's answer. The
 * screens are opened the way the server opens them for a player.
 *
 * <p>The tour runs on a desktop that someone may be using, so it stays out of the way: it lets go
 * of the mouse every tick, keeps rendering while unfocused instead of pausing, never asks for the
 * focus and keeps itself short. Progress goes to the log as {@code AEROFLUX-SHOTS} lines.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class ShotTour {
    private static final boolean ENABLED = System.getenv("AEROFLUX_SHOTS") != null;
    /** With {@code AEROFLUX_SHOTS=bei} the tour opens the inventory with Barely Enough Items and shoots its browser and recipe popup. */
    private static final boolean BEI = "bei".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    /** With {@code AEROFLUX_SHOTS=smwti} the tour looks at a furnace and a collector and shoots the Show Me What This Is overlay. */
    private static final boolean SMWTI = "smwti".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    /** With {@code AEROFLUX_SHOTS=guis} the tour opens the screen of every machine, converter, chunk loader, collector and tank. */
    private static final boolean GUIS = "guis".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    /** With {@code AEROFLUX_SHOTS=jei} the tour opens JEI's recipe screen on the AeroFlux machine pages. */
    private static final boolean JEI = "jei".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    /** With {@code AEROFLUX_SHOTS=emi} the tour opens EMI's recipe screen on the AeroFlux machine pages. */
    private static final boolean EMI = "emi".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    private static final String WORLD = "aeroflux_shots";
    /** Ticks for the chunk renderer to catch up after blocks change. */
    private static final int SETTLE = 30;

    private enum Stage { WAITING_FOR_MENU, LOADING_WORLD, TOURING, DONE }

    /** One thing to do, after waiting the given number of ticks. */
    private record Step(int delay, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static Stage stage = Stage.WAITING_FOR_MENU;
    private static int idle;
    private static int wait;
    /** The first air block above the superflat ground. */
    private static int ground;
    private static boolean pauseOnLostFocus;

    private ShotTour() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || stage == Stage.DONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
        switch (stage) {
            case WAITING_FOR_MENU -> waitForMenu(minecraft);
            case LOADING_WORLD -> waitForWorld(minecraft);
            case TOURING -> runSteps();
            default -> {
            }
        }
    }

    // --- Getting into a world ----------------------------------------------------------------

    /** Once loading has finished and a menu is up, creates the world (replacing an old one). */
    private static void waitForMenu(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || minecraft.level != null || minecraft.screen == null || ++idle < 20) {
            return;
        }
        pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
        minecraft.options.pauseOnLostFocus = false;

        LevelStorageSource source = minecraft.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            } catch (IOException e) {
                AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not delete the old world", e);
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        stage = Stage.LOADING_WORLD;
        idle = 0;
        AeroFlux.LOGGER.info("AEROFLUX-SHOTS creating world");
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions());
    }

    /** Waits for the player to stand in the world, then plans the tour. */
    private static void waitForWorld(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getSingleplayerServer() == null || ++idle < 40) {
            return;
        }
        ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        AeroFlux.LOGGER.info("AEROFLUX-SHOTS in the world, ground at y={}", ground);
        planTour();
        stage = Stage.TOURING;
    }

    private static void runSteps() {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            return;
        }
        step.action().run();
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    // --- The tour ----------------------------------------------------------------------------

    private static void planTour() {
        then(0, () -> {
            server("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon", "weather clear",
                    "kill @e[type=!player]");
            LocalPlayer player = player();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        });

        if (BEI) {
            beiShots();
        } else if (GUIS) {
            guisShots();
        } else if (EMI) {
            emiShots();
        } else if (JEI) {
            jeiShots();
        } else if (SMWTI) {
            smwtiShots();
        } else {
            sceneShots();
            pipeScreenShots();
            fluxScreenShots();
            configuratorShot();

        }

        then(40, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
            AeroFlux.LOGGER.info("AEROFLUX-SHOTS DONE");
            stage = Stage.DONE;
            minecraft.stop();
        });
    }

    /** The pipe scenes in the world. */
    private static void sceneShots() {
        // A single pipe on its own.
        BlockPos one = at(0, 0);
        placePipe(PipeType.FLUID, one, true);
        settleAndShoot("bundle_one", eye(0.5, 1.15, -1.3), Vec3.atCenterOf(one));

        // Three pipes in one block, and two in the next, added the way a player adds them.
        BlockPos three = at(4, 0);
        BlockPos two = at(6, 0);
        placePipe(PipeType.FLUID, three, true);
        placePipe(PipeType.ITEM, three, false);
        placePipe(PipeType.ENERGY, three, false);
        placePipe(PipeType.FLUID, two, true);
        placePipe(PipeType.REDSTONE, two, false);
        settleAndShoot("bundle_mixed", eye(5.5, 1.4, -2.0), new Vec3(5.5, ground + 0.4, 0.5));

        // A run of two-pipe bundles, and beside it a run of single pipes.
        for (int x = 10; x <= 13; x++) {
            placePipe(PipeType.FLUID, at(x, 0), true);
            placePipe(PipeType.ITEM, at(x, 0), false);
            placePipe(PipeType.FLUID, at(x, 2), true);
        }
        settleAndShoot("pipe_run", eye(11.5, 2.6, -3.0), new Vec3(12.0, ground + 0.3, 1.2));

        // A furnace with a pipe placed after it, and a pipe with a furnace placed after it.
        Item furnace = ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get().asItem();
        placeBlock(furnace, at(20, 0));
        BlockPos pipeAfter = at(21, 0);
        placePipe(PipeType.FLUID, pipeAfter, true);
        placePipe(PipeType.ITEM, pipeAfter, false);
        placePipe(PipeType.ENERGY, pipeAfter, false);
        BlockPos pipeBefore = at(24, 0);
        placePipe(PipeType.FLUID, pipeBefore, true);
        placePipe(PipeType.ITEM, pipeBefore, false);
        placeBlock(furnace, at(25, 0));
        settleAndShoot("furnace", eye(22.5, 2.3, -2.6), new Vec3(22.5, ground + 0.4, 0.5));

        // A tank with a fluid and item bundle next to it: only the fluid pipe may attach.
        placeBlock(ModBlocks.tank(Tier.COPPER).get().asItem(), at(30, 0));
        BlockPos byTank = at(31, 0);
        placePipe(PipeType.FLUID, byTank, true);
        placePipe(PipeType.ITEM, byTank, false);
        settleAndShoot("tank", eye(31.0, 1.8, -2.2), new Vec3(30.8, ground + 0.5, 0.5));

        // Bundles of different sets in a row, with a corner: each pipe joins its own kind, and the
        // corner bundle draws a junction.
        placePipe(PipeType.FLUID, at(35, 0), true);
        placePipe(PipeType.ITEM, at(35, 0), false);
        placePipe(PipeType.FLUID, at(36, 0), true);
        placePipe(PipeType.FLUID, at(37, 0), true);
        placePipe(PipeType.ENERGY, at(37, 0), false);
        placePipe(PipeType.FLUID, at(37, 1), true);
        settleAndShoot("mixed", eye(36.5, 2.4, -2.6), new Vec3(36.5, ground + 0.8, 0.5));

        polishShots();
    }

    /**
     * The Creative Flux Tank feeding a working furnace (lit, with its motes) beside a collector in
     * the noon sun (with its sparkles), then the same tank switched to Moon Flux.
     */
    private static void polishShots() {
        BlockPos creative = at(41, 0);
        BlockPos furnace = at(42, 0);
        BlockPos collector = at(40, 0);
        placeBlock(ModBlocks.CREATIVE_TANK.get().asItem(), creative);
        placeBlock(ModBlocks.machine(MachineType.FURNACE, Tier.GOLD).get().asItem(), furnace);
        placeBlock(ModBlocks.collector(Tier.GOLD).get().asItem(), collector);
        then(2, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(furnace) instanceof FluxMachineBlockEntity machine) {
                machine.setStackIn(0, new ItemStack(Items.RAW_IRON, 64));
            }
        }));
        settleAndShoot("creative_tank", eye(41.0, 1.7, -2.2), new Vec3(41.0, ground + 0.5, 0.5));
        then(2, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(creative) instanceof CreativeFluxTankBlockEntity tank) {
                tank.cycleFlux();
            }
        }));
        settleAndShoot("creative_tank_moon", eye(41.5, 1.2, -1.4), new Vec3(41.5, ground + 0.5, 0.5));
    }

    // --- Screens -----------------------------------------------------------------------------

    /**
     * The flux badge and cost bar on machine screens (a furnace at work with less than one smelt of
     * Flux, one with none, and a Plant Grower holding Moon Flux for the two-flux badge), then a
     * collector feeding a tank, a collector under leaves, and the screens of all three.
     */
    private static void fluxScreenShots() {
        BlockPos working = at(62, 6);
        BlockPos starved = at(64, 6);
        BlockPos grower = at(66, 6);
        Item furnace = ModBlocks.machine(MachineType.FURNACE, Tier.IRON).get().asItem();
        placeBlock(furnace, working);
        placeBlock(furnace, starved);
        placeBlock(ModBlocks.machine(MachineType.PLANT_GROWER, Tier.GOLD).get().asItem(), grower);
        then(SETTLE, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            if (level.getBlockEntity(working) instanceof FluxMachineBlockEntity machine) {
                // Three quarters of a smelt, so the cost bar shows part full while it works.
                int smelt = FluxCost.perOperation(MachineUpgrades.fluxPerTick(Tier.IRON, 0),
                        MachineUpgrades.ticksFor(200, Tier.IRON, 0), 0);
                machine.setFlux(new FluidStack(ModFluids.AERO_FLUX.get(), smelt * 3 / 4));
                machine.setStackIn(0, new ItemStack(Items.RAW_IRON, 8));
            }
            if (level.getBlockEntity(starved) instanceof FluxMachineBlockEntity machine) {
                machine.setStackIn(0, new ItemStack(Items.RAW_IRON, 8));
            }
            if (level.getBlockEntity(grower) instanceof FluxMachineBlockEntity machine) {
                machine.setFlux(new FluidStack(ModFluids.MOON_FLUX.get(), 3_000));
            }
        }));
        openShot(working, "screen_cost_working");
        openShot(starved, "screen_cost_starved");
        openShot(grower, "screen_badge_either");

        // A collector pushing into the tank beside it, and a second collector under a leaf roof.
        BlockPos collector = at(70, 6);
        BlockPos tank = at(71, 6);
        BlockPos covered = at(74, 6);
        placeBlock(ModBlocks.collector(Tier.GOLD).get().asItem(), collector);
        placeBlock(ModBlocks.tank(Tier.GOLD).get().asItem(), tank);
        placeBlock(ModBlocks.collector(Tier.IRON).get().asItem(), covered);
        then(2, () -> onServer(server -> {
            server.overworld().setBlockAndUpdate(covered.above(3), Blocks.OAK_LEAVES.defaultBlockState());
            if (server.overworld().getBlockEntity(tank) instanceof FluxTankBlockEntity entity) {
                entity.tank().fill(new FluidStack(ModFluids.AERO_FLUX.get(), 40_000), IFluidHandler.FluidAction.EXECUTE);
            }
        }));
        // Long enough for the sky checks and a full second of flow for the rates.
        then(60, () -> {
        });
        openShot(collector, "screen_collector");
        openShot(covered, "screen_collector_blocked");
        openShot(tank, "screen_tank");
    }

    /**
     * The Flux Configurator's preset screen: a side-configured furnace in a named preset, a second
     * preset from a Magnet, and the furnace's preset selected so its face grid shows.
     */
    private static void configuratorShot() {
        BlockPos furnace = at(80, 6);
        BlockPos magnet = at(82, 6);
        placeBlock(ModBlocks.machine(MachineType.FURNACE, Tier.IRON).get().asItem(), furnace);
        placeBlock(ModBlocks.machine(MachineType.MAGNET, Tier.IRON).get().asItem(), magnet);
        then(SETTLE, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            ItemStack configurator = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
            if (level.getBlockEntity(furnace) instanceof FluxMachineBlockEntity source
                    && level.getBlockEntity(magnet) instanceof FluxMachineBlockEntity other) {
                ConfiguratorActions.copy(configurator, source);
                ConfiguratorActions.renamePreset(configurator, 0, "Ore line");
                ConfiguratorActions.selectPreset(configurator, 1);
                ConfiguratorActions.copy(configurator, other);
                ConfiguratorActions.selectPreset(configurator, 0);
            }
            server.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, configurator);
        }));
        then(10, () -> {
            Minecraft.getInstance().options.hideGui = false;
            ConfiguratorScreen.open(InteractionHand.MAIN_HAND);
        });
        shoot("screen_configurator", 10);
        then(2, () -> Minecraft.getInstance().setScreen(null));
    }

    /** Opens the screen of the block at {@code pos} the way the server opens it for a player, and shoots it. */
    private static void openShot(BlockPos pos, String name) {
        then(5, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            BlockEntity entity = server.overworld().getBlockEntity(pos);
            if (entity instanceof FluxMachineBlockEntity machine) {
                NetworkHooks.openScreen(player, machine, machine::writeOpenData);
            } else if (entity instanceof FluxCollectorBlockEntity collector) {
                NetworkHooks.openScreen(player, collector, collector::writeOpenData);
            } else if (entity instanceof FluxTankBlockEntity tank) {
                NetworkHooks.openScreen(player, tank, tank::writeOpenData);
            } else if (entity instanceof com.itmecasey.aeroflux.converter.FluxConverterBlockEntity converter) {
                NetworkHooks.openScreen(player, converter, converter::writeOpenData);
            } else if (entity instanceof com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity loader) {
                NetworkHooks.openScreen(player, loader, loader::writeOpenData);
            } else if (com.itmecasey.aeroflux.compat.create.CreateCompat.loaded()
                    && entity instanceof com.itmecasey.aeroflux.compat.create.StressConverterBlockEntity stress) {
                NetworkHooks.openScreen(player, stress, stress::writeOpenData);
            } else {
                AeroFlux.LOGGER.warn("AEROFLUX-SHOTS nothing to open for {}", name);
            }
        }));
        then(0, () -> Minecraft.getInstance().options.hideGui = false);
        shoot(name, 10);
        then(2, () -> player().closeContainer());
    }

    /**
     * An Iron Furnace with its side-config window open, then a gold item pipe next to it (with
     * the distribution and upgrade rows) and an ME pipe (with neither). The faces and the pipe get
     * a mix of settings first, so the shots show every color. Same scene as the newer builds.
     */
    private static void pipeScreenShots() {
        BlockPos machinePos = at(50, 6);
        BlockPos itemPipe = at(51, 6);
        BlockPos mePipe = at(49, 6);
        placeBlock(ModBlocks.machine(MachineType.FURNACE, Tier.IRON).get().asItem(), machinePos);
        placePipe(PipeType.ITEM, Tier.GOLD, itemPipe, true);
        placePipe(PipeType.ME, Tier.COPPER, mePipe, true);
        then(SETTLE, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                ServerLevel level = server.overworld();
                if (level.getBlockEntity(machinePos) instanceof FluxMachineBlockEntity machine) {
                    Face facing = machine.facing();
                    setFace(machine, FaceLabel.TOP, facing, ConnectionMode.OFF);
                    setFace(machine, FaceLabel.LEFT, facing, ConnectionMode.IMPORT);
                    setFace(machine, FaceLabel.FRONT, facing, ConnectionMode.OPEN);
                    setFace(machine, FaceLabel.RIGHT, facing, ConnectionMode.EXPORT);
                    setFace(machine, FaceLabel.BOTTOM, facing, ConnectionMode.BLOCKED);
                    setFace(machine, FaceLabel.BACK, facing, ConnectionMode.OFF);
                    NetworkHooks.openScreen(server.getPlayerList().getPlayers().get(0), machine, machine::writeOpenData);
                }
            });
        });
        then(10, () -> {
            if (Minecraft.getInstance().screen instanceof FluxMachineScreen<?> screen) {
                screen.sidePanel().setOpen(true);
            } else {
                AeroFlux.LOGGER.warn("AEROFLUX-SHOTS the machine screen did not open");
            }
        });
        shoot("screen_machine", 5);

        then(2, () -> player().closeContainer());
        then(5, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(itemPipe) instanceof ConduitBundleBlockEntity bundle) {
                // The pipe's west face looks at the furnace.
                PipeConfigActions.setMode(bundle, PipeType.ITEM, Face.WEST, ConnectionMode.IMPORT);
                PipeConfigActions.addUpgrade(bundle, PipeType.ITEM, PipeUpgrades.Kind.SPEED);
                PipeConfigActions.addUpgrade(bundle, PipeType.ITEM, PipeUpgrades.Kind.CAPACITY);
                PipeConfigMenu.open(server.getPlayerList().getPlayers().get(0), bundle, PipeType.ITEM, Face.WEST);
            }
        }));
        shoot("screen_pipe_item", 10);

        then(2, () -> player().closeContainer());
        then(5, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(mePipe) instanceof ConduitBundleBlockEntity bundle) {
                PipeConfigMenu.open(server.getPlayerList().getPlayers().get(0), bundle, PipeType.ME, Face.EAST);
            }
        }));
        shoot("screen_pipe_me", 10);
        then(2, () -> player().closeContainer());

        // A gold redstone pipe tuned to channel 2: the channel row of the pipe screen.
        BlockPos redstonePipe = at(47, 6);
        placePipe(PipeType.REDSTONE, Tier.GOLD, redstonePipe, true);
        then(5, () -> onServer(server -> {
            if (server.overworld().getBlockEntity(redstonePipe) instanceof ConduitBundleBlockEntity bundle) {
                PipeConfigActions.setChannel(bundle, PipeType.REDSTONE, Face.EAST, 2);
                PipeConfigActions.setMode(bundle, PipeType.REDSTONE, Face.EAST, ConnectionMode.EXPORT);
                PipeConfigMenu.open(server.getPlayerList().getPlayers().get(0), bundle, PipeType.REDSTONE, Face.EAST);
            }
        }));
        shoot("screen_pipe_redstone", 10);
        then(2, () -> player().closeContainer());
    }

    /** Sets the item mode of one face of a machine the way a click in the side-config window does. */
    private static void setFace(FluxMachineBlockEntity machine, FaceLabel label, Face facing, ConnectionMode mode) {
        MachineConfigActions.apply(machine, SideCommand.set(SideResource.ITEMS, label.faceFor(facing), mode));
    }

    /** Places a pipe of a given tier, like {@link #placePipe(PipeType, BlockPos, boolean)}. */
    private static void placePipe(PipeType type, Tier tier, BlockPos pos, boolean onGround) {
        Item item = ModItems.pipe(type, tier).get();
        then(1, () -> {
            moveTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() - 2.5), 0.0F, 30.0F);
            hold(item);
        });
        then(3, () -> {
            if (onGround) {
                click(pos.below(), Direction.UP);
            } else {
                click(pos, Direction.NORTH);
            }
        });
    }

    // --- Building blocks of the tour ---------------------------------------------------------

    /**
     * Places a pipe with a right-click: on the ground below {@code pos} for a new bundle, or on
     * the bundle at {@code pos} to add the pipe to it. The player first moves within reach.
     */
    private static void placePipe(PipeType type, BlockPos pos, boolean onGround) {
        Item item = ModItems.pipe(type, Tier.COPPER).get();
        then(1, () -> {
            moveTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() - 2.5), 0.0F, 30.0F);
            hold(item);
        });
        then(3, () -> {
            if (onGround) {
                click(pos.below(), Direction.UP);
            } else {
                click(pos, Direction.NORTH);
            }
        });
    }

    /** Places a block item on the ground at {@code pos} with a right-click. */
    private static void placeBlock(Item item, BlockPos pos) {
        then(1, () -> {
            moveTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() - 2.5), 0.0F, 30.0F);
            hold(item);
        });
        then(3, () -> click(pos.below(), Direction.UP));
    }

    /**
     * Lets the chunk renderer catch up, then shoots the scene from a free camera. The player turns
     * spectator for the shot: the server would push a creative player back out of the ground or a
     * block, but lets a spectator stand anywhere.
     */
    private static void settleAndShoot(String name, Vec3 eye, Vec3 target) {
        then(SETTLE, () -> server("gamemode spectator @a"));
        then(5, () -> {
            Minecraft.getInstance().options.hideGui = true;
            lookFrom(eye, target);
        });
        shoot(name, 20);
        then(2, () -> server("gamemode creative @a"));
        then(5, () -> {
            player().getAbilities().flying = true;
            player().onUpdateAbilities();
        });
    }

    // --- Barely Enough Items ---------------------------------------------------------------

    /**
     * Opens the player inventory with the BEI browser, searches for AeroFlux items, opens the
     * recipe popup of two of them and shoots each state. BEI is reached through reflection and the
     * game's own mouse handler, so nothing here touches the desktop cursor or keyboard.
     */
    private static void beiShots() {
        then(2, () -> server("gamemode survival @a", "gamerule doFireTick false"));
        then(20, () -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player())));
        then(40, () -> beiSearch("@aeroflux"));
        beiHoverFirst();
        shoot("bei_browser", 20);
        beiOpenRecipes("flux dust", "bei_popup_dust");
        beiOpenRecipes("fluxsteel ingot", "bei_popup_fluxsteel");
        then(10, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS BEI popup open={}", beiPopupOpen()));
    }

    private static void beiOpenRecipes(String query, String shot) {
        then(20, () -> {
            try {
                Class.forName("com.itmecasey.bei.client.RecipePopup").getMethod("close").invoke(null);
            } catch (ReflectiveOperationException e) {
                AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not close the popup", e);
            }
            beiSearch(query);
        });
        beiHoverFirst();
        then(5, () -> mouseClick());
        then(30, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS BEI '{}' popup open={}", query, beiPopupOpen()));
        shoot(shot, 10);
    }

    private static void beiHoverFirst() {
        then(30, () -> beiMoveToFirstCell());
        then(10, () -> beiMoveToFirstCell());
    }

    private static Object beiField(String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = Class.forName("com.itmecasey.bei.client.BrowserPanel").getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void beiSearch(String text) {
        try {
            ((net.minecraft.client.gui.components.EditBox) beiField("box")).setValue(text);
        } catch (ReflectiveOperationException e) {
            AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not reach BEI's search box", e);
        }
    }

    private static boolean beiPopupOpen() {
        try {
            return (Boolean) Class.forName("com.itmecasey.bei.client.RecipePopup").getMethod("isOpen").invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Moves the game's own cursor over the first cell of BEI's grid (twice: the game ignores a first move). */
    private static void beiMoveToFirstCell() {
        try {
            Minecraft game = Minecraft.getInstance();
            int gx = (Integer) beiField("gridX") + 9;
            int gy = (Integer) beiField("gridY") + 9;
            double sx = gx * (double) game.getWindow().getScreenWidth() / game.getWindow().getGuiScaledWidth();
            double sy = gy * (double) game.getWindow().getScreenHeight() / game.getWindow().getGuiScaledHeight();
            java.lang.reflect.Method move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            move.setAccessible(true);
            move.invoke(game.mouseHandler, game.getWindow().getWindow(), sx + 3, sy);
            move.invoke(game.mouseHandler, game.getWindow().getWindow(), sx, sy);
        } catch (ReflectiveOperationException e) {
            AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not move the game cursor", e);
        }
    }

    /** A left click through the game's own mouse handler, at wherever the game cursor is. */
    private static void mouseClick() {
        try {
            Minecraft game = Minecraft.getInstance();
            java.lang.reflect.Method press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
            press.setAccessible(true);
            press.invoke(game.mouseHandler, game.getWindow().getWindow(), 0, 1, 0);
            press.invoke(game.mouseHandler, game.getWindow().getWindow(), 0, 0, 0);
        } catch (ReflectiveOperationException e) {
            AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not click", e);
        }
    }

    // --- Show Me What This Is --------------------------------------------------------------

    /**
     * Puts a furnace and a collector in front of the player with vanilla commands, looks at each
     * through the game's own camera (a teleport with a rotation) and shoots the overlay SMWTI
     * draws for the block under the crosshair.
     */
    private static void smwtiShots() {
        then(2, () -> {
            int y = (int) Math.floor(player().getY());
            server("setblock 0 " + y + " 3 aeroflux:flux_furnace", "setblock 3 " + y + " 3 aeroflux:copper_collector",
                    "tp @a 0.5 " + y + " 0.5 0 18");
        });
        then(60, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS SMWTI furnace in view"));
        shoot("smwti_furnace", 60);
        then(5, () -> server("tp @a 3.5 " + (int) Math.floor(player().getY()) + " 0.5 0 18"));
        then(60, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS SMWTI collector in view"));
        shoot("smwti_collector", 60);
    }

    // --- Every screen ----------------------------------------------------------------------

    /**
     * Opens the screen of every machine (at gold, plus the furnace at copper and fluxium), the
     * converters, the chunk loader where there is one, and the collector and tank at copper and
     * fluxium, each with some Flux in it, and shoots each one. The blocks are set by the server,
     * the player is moved next to each so the menu stays valid, and the screen opens the way the
     * server opens it for a player.
     */
    private static void guisShots() {
        then(0, () -> server("gamemode survival @a"));
        java.util.List<Object[]> targets = new java.util.ArrayList<>();
        for (MachineType type : MachineType.values()) {
            targets.add(new Object[] {"machine_" + type.id() + "_gold", ModBlocks.machine(type, Tier.GOLD).get(), type});
        }
        targets.add(new Object[] {"machine_" + MachineType.FURNACE.id() + "_copper", ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get(), MachineType.FURNACE});
        targets.add(new Object[] {"machine_" + MachineType.FURNACE.id() + "_fluxium", ModBlocks.machine(MachineType.FURNACE, Tier.FLUXIUM).get(), MachineType.FURNACE});
        targets.add(new Object[] {"collector_copper", ModBlocks.collector(Tier.COPPER).get(), null});
        targets.add(new Object[] {"collector_fluxium", ModBlocks.collector(Tier.FLUXIUM).get(), null});
        targets.add(new Object[] {"tank_copper", ModBlocks.tank(Tier.COPPER).get(), null});
        targets.add(new Object[] {"tank_fluxium", ModBlocks.tank(Tier.FLUXIUM).get(), null});
        targets.add(new Object[] {"chunk_loader_gold", ModBlocks.chunkLoader(Tier.GOLD).get(), null});
        if (com.itmecasey.aeroflux.compat.create.CreateCompat.loaded()) {
            targets.add(new Object[] {"converter_stress_gold", com.itmecasey.aeroflux.compat.create.CreateContent.converter(Tier.GOLD).get(), null});
        }
        targets.add(new Object[] {"converter_energy_gold", ModBlocks.converter(com.itmecasey.aeroflux.core.converter.ConverterType.ENERGY, Tier.GOLD).get(), null});
        try {
            targets.add(new Object[] {"converter_ae_gold", ModBlocks.converter(com.itmecasey.aeroflux.core.converter.ConverterType.AE, Tier.GOLD).get(), null});
        } catch (RuntimeException e) {
            // No AE2 converter on this build.
        }
        for (int i = 0; i < targets.size(); i++) {
            String name = (String) targets.get(i)[0];
            net.minecraft.world.level.block.Block block = (net.minecraft.world.level.block.Block) targets.get(i)[1];
            MachineType type = (MachineType) targets.get(i)[2];
            BlockPos pos = at(2 * i, 12);
            then(2, () -> {
                moveTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() - 2.5), 0.0F, 30.0F);
                onServer(server -> {
                    ServerLevel level = server.overworld();
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity instanceof FluxMachineBlockEntity machine && type != null) {
                        boolean moonOnly = !type.acceptedFlux().contains(com.itmecasey.aeroflux.core.FluxType.AERO);
                        machine.setFlux(new FluidStack(moonOnly ? ModFluids.MOON_FLUX.get() : ModFluids.AERO_FLUX.get(), 3_000));
                    } else if (entity instanceof FluxTankBlockEntity tank) {
                        tank.tank().fill(new FluidStack(ModFluids.AERO_FLUX.get(), 5_000), IFluidHandler.FluidAction.EXECUTE);
                    }
                });
            });
            then(20, () -> {
            });
            openShot(pos, "gui_" + name);
        }
        // The guidebook: its contents page, then three pages (a chapter opener, a middle page, the last).
        then(2, () -> {
            player().closeContainer();
            com.itmecasey.aeroflux.client.screen.GuideScreen.open();
        });
        shoot("gui_guide_contents", 20);
        for (int page : new int[] {1, 9, 22}) {
            then(5, () -> {
                try {
                    java.lang.reflect.Method show = com.itmecasey.aeroflux.client.screen.GuideScreen.class.getDeclaredMethod("show", int.class);
                    show.setAccessible(true);
                    show.invoke(Minecraft.getInstance().screen, page);
                } catch (ReflectiveOperationException e) {
                    AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not turn the guide page", e);
                }
            });
            shoot("gui_guide_page_" + page, 15);
        }
        then(2, () -> Minecraft.getInstance().setScreen(null));
    }

    /** JEI's recipe screen on the Alloy Smelter and Infuser pages. */
    private static void jeiShots() {
        MachineType[] pages = {MachineType.ALLOY_SMELTER, MachineType.INFUSER};
        for (MachineType page : pages) {
            then(40, () -> {
                try {
                    boolean shown = com.itmecasey.aeroflux.compat.jei.AeroFluxJeiPlugin.showRecipes(page);
                    AeroFlux.LOGGER.info("AEROFLUX-SHOTS JEI recipe page {} shown={}", page.id(), shown);
                } catch (RuntimeException | LinkageError e) {
                    AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not open the JEI page of {}", page.id(), e);
                }
            });
            shoot("jei_" + page.id(), 30);
            then(5, () -> Minecraft.getInstance().setScreen(null));
        }
    }

    /** EMI's recipe screen on the Alloy Smelter and Infuser pages. */
    private static void emiShots() {
        MachineType[] pages = {MachineType.ALLOY_SMELTER, MachineType.INFUSER};
        for (MachineType page : pages) {
            then(40, () -> {
                try {
                    boolean shown = com.itmecasey.aeroflux.compat.emi.AeroFluxEmiPlugin.showRecipes(page);
                    AeroFlux.LOGGER.info("AEROFLUX-SHOTS EMI recipe page {} shown={}", page.id(), shown);
                } catch (RuntimeException | LinkageError e) {
                    AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not open the EMI page of {}", page.id(), e);
                }
            });
            shoot("emi_" + page.id(), 30);
            then(5, () -> Minecraft.getInstance().setScreen(null));
        }
    }

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            // Command echoes and recipe toasts would only clutter the shots.
            minecraft.gui.getChat().clearMessages(false);
            minecraft.getToasts().clear();
        });
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screenshot.grab(minecraft.gameDirectory, "aeroflux_" + name + ".png", minecraft.getMainRenderTarget(),
                    message -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS saved {}", name));
        });
    }

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    /** Puts a stack of the item in the first hotbar slot, selects it, and tells the server. */
    private static void hold(Item item) {
        ItemStack stack = new ItemStack(item);
        LocalPlayer player = player();
        player.getInventory().setItem(0, stack.copy());
        player.getInventory().selected = 0;
        // Slot 36 of the inventory menu is the first hotbar slot.
        Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(stack, 36);
    }

    /** A right-click on the middle of one face of a block, as the crosshair would make it. */
    private static void click(BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        Minecraft.getInstance().gameMode.useItemOn(player(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    private static void lookFrom(Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        moveTo(eye, yaw, pitch);
    }

    /**
     * Moves the camera: {@code eye} is where the eyes go; the feet end up 1.62 below. The server is
     * told too, or it pulls the player back to where it last saw them before the shot is taken.
     */
    private static void moveTo(Vec3 eye, float yaw, float pitch) {
        server(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
        LocalPlayer player = player();
        player.moveTo(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, ground, z);
    }

    private static Vec3 eye(double x, double heightAboveGround, double z) {
        return new Vec3(x, ground + heightAboveGround, z);
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static void server(String... commands) {
        onServer(server -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            }
        });
    }

    private static void onServer(Consumer<MinecraftServer> work) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> work.accept(server));
        }
    }
}
