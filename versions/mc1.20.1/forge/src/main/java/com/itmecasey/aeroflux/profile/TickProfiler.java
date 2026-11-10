package com.itmecasey.aeroflux.profile;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.profile.ProfileReport;
import com.itmecasey.aeroflux.core.profile.TickSampler;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Measures how much server time AeroFlux block entities take, for {@code /aeroflux profile}.
 *
 * <p>Every AeroFlux block hands its ticker to {@link #wrap}. While nobody is sampling, the wrapper
 * is one field read and a direct call, and nothing is stored anywhere. A sampling window creates a
 * {@link TickSampler}, the wrappers start timing each tick into it, and when the window ends the
 * sampler is turned into a {@link ProfileReport}, handed to whoever asked, and dropped.
 *
 * <p>One window at a time, all on the server thread.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class TickProfiler {
    /** The sampler of the running window; {@code null} whenever nobody is sampling. */
    @Nullable
    private static volatile TickSampler sampler;
    private static int windowTicks;
    private static int elapsed;
    @Nullable
    private static Consumer<ProfileReport> sink;

    private TickProfiler() {
    }

    // --- Wrapping tickers ---------------------------------------------------------------------

    /** Wraps a ticker so its time is counted under one name while a window runs. */
    public static <T extends BlockEntity> BlockEntityTicker<T> wrap(String type, BlockEntityTicker<T> inner) {
        return wrap(state -> type, inner);
    }

    /** Wraps a ticker whose name depends on the block, such as a machine named by its kind. */
    public static <T extends BlockEntity> BlockEntityTicker<T> wrap(Function<BlockState, String> type, BlockEntityTicker<T> inner) {
        return (level, pos, state, entity) -> {
            TickSampler active = sampler;
            if (active == null) {
                inner.tick(level, pos, state, entity);
                return;
            }
            long started = System.nanoTime();
            inner.tick(level, pos, state, entity);
            long took = System.nanoTime() - started;
            active.record(type.apply(state), level.dimension().location().toString(),
                    pos.getX(), pos.getY(), pos.getZ(), took);
        };
    }

    // --- Windows ------------------------------------------------------------------------------

    /** Whether a window is running. */
    public static boolean sampling() {
        return sampler != null;
    }

    /** How many blocks the running window has seen; 0 when nobody is sampling, which is the point. */
    public static int retainedBlocks() {
        TickSampler active = sampler;
        return active == null ? 0 : active.blocks();
    }

    /**
     * Starts a window.
     *
     * @param ticks how many server ticks to sample
     * @param done  receives the report when the window ends
     * @return false if a window is already running
     */
    public static boolean start(int ticks, Consumer<ProfileReport> done) {
        if (sampler != null) {
            return false;
        }
        windowTicks = Math.max(1, ticks);
        elapsed = 0;
        sink = done;
        sampler = new TickSampler();
        return true;
    }

    /** Stops the running window without a report. Returns false if none was running. */
    public static boolean cancel() {
        boolean running = sampler != null;
        reset();
        return running;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        TickSampler active = sampler;
        if (event.phase != TickEvent.Phase.END || active == null || ++elapsed < windowTicks) {
            return;
        }
        Consumer<ProfileReport> done = sink;
        ProfileReport report = ProfileReport.of(active, windowTicks, ProfileReporter.HEAVIEST_LISTED);
        reset();
        if (done != null) {
            done.accept(report);
        }
    }

    /** Drops the sampler and everything that points at it. */
    private static void reset() {
        sampler = null;
        sink = null;
        windowTicks = 0;
        elapsed = 0;
    }
}
