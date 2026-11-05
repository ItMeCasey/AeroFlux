package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A development-only check of the Barely Enough Items plugin: it opens a fresh creative superflat
 * world, opens the inventory with BEI's browser, searches for AeroFlux items, opens two recipe
 * popups and saves a screenshot of each into {@code run/screenshots} as {@code aeroflux_bei_*.png},
 * then quits. It does nothing unless the environment variable {@code AEROFLUX_SHOTS} is
 * {@code bei}. BEI is reached through reflection and the game's own mouse handler, so nothing here
 * touches the desktop cursor or keyboard, and the game lets go of the mouse every tick.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class BeiTour {
    private static final boolean SMWTI = "smwti".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS"));
    private static final boolean ENABLED = "bei".equalsIgnoreCase(System.getenv("AEROFLUX_SHOTS")) || SMWTI;
    private static final String WORLD = "aeroflux_shots";

    private enum Stage { WAITING_FOR_MENU, LOADING_WORLD, TOURING, DONE }

    /** One thing to do, after waiting the given number of ticks. */
    private record Step(int delay, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static Stage stage = Stage.WAITING_FOR_MENU;
    private static int idle;
    private static int wait;

    private BeiTour() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.DONE) {
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

    private static void waitForMenu(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || minecraft.level != null || minecraft.screen == null || ++idle < 20) {
            return;
        }
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
                        .value().createWorldDimensions(),
                new TitleScreen());
    }

    private static void waitForWorld(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getSingleplayerServer() == null || ++idle < 40) {
            return;
        }
        AeroFlux.LOGGER.info("AEROFLUX-SHOTS in the world");
        plan();
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

    // --- The check ---------------------------------------------------------------------------

    private static void plan() {
        // Forge crashes on fire ticks in a fresh world, so fire is switched off before anything else.
        if (SMWTI) {
            then(0, () -> server("gamerule doFireTick false", "gamerule doDaylightCycle false", "time set noon"));
            smwtiShots();
            then(40, () -> {
                AeroFlux.LOGGER.info("AEROFLUX-SHOTS DONE");
                stage = Stage.DONE;
                Minecraft.getInstance().stop();
            });
            return;
        }
        then(0, () -> server("gamerule doFireTick false", "gamerule doDaylightCycle false", "time set noon", "gamemode survival @a"));
        then(20, () -> Minecraft.getInstance().setScreen(new InventoryScreen(player())));
        then(40, () -> search("@aeroflux"));
        hoverFirst();
        shoot("bei_browser", 20);
        openRecipes("flux dust", "bei_popup_dust");
        openRecipes("fluxsteel ingot", "bei_popup_fluxsteel");
        then(10, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS BEI popup open={}", popupOpen()));
        then(40, () -> {
            AeroFlux.LOGGER.info("AEROFLUX-SHOTS DONE");
            stage = Stage.DONE;
            Minecraft.getInstance().stop();
        });
    }

    private static void openRecipes(String query, String shot) {
        then(20, () -> {
            try {
                Class.forName("com.itmecasey.bei.client.RecipePopup").getMethod("close").invoke(null);
            } catch (ReflectiveOperationException e) {
                AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not close the popup", e);
            }
            search(query);
        });
        hoverFirst();
        then(5, BeiTour::mouseClick);
        then(30, () -> AeroFlux.LOGGER.info("AEROFLUX-SHOTS BEI '{}' popup open={}", query, popupOpen()));
        shoot(shot, 10);
    }

    private static void hoverFirst() {
        then(30, BeiTour::moveToFirstCell);
        then(10, BeiTour::moveToFirstCell);
    }

    private static Object field(String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = Class.forName("com.itmecasey.bei.client.BrowserPanel").getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void search(String text) {
        try {
            ((net.minecraft.client.gui.components.EditBox) field("box")).setValue(text);
        } catch (ReflectiveOperationException e) {
            AeroFlux.LOGGER.warn("AEROFLUX-SHOTS could not reach BEI's search box", e);
        }
    }

    private static boolean popupOpen() {
        try {
            return (Boolean) Class.forName("com.itmecasey.bei.client.RecipePopup").getMethod("isOpen").invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Moves the game's own cursor over the first cell of BEI's grid (twice: the game ignores a first move). */
    private static void moveToFirstCell() {
        try {
            Minecraft game = Minecraft.getInstance();
            int gx = (Integer) field("gridX") + 9;
            int gy = (Integer) field("gridY") + 9;
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

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft minecraft = Minecraft.getInstance();
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

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static void server(String... commands) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            Consumer<MinecraftServer> work = s -> {
                for (String command : commands) {
                    s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command);
                }
            };
            server.execute(() -> work.accept(server));
        }
    }
}
