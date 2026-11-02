package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineReach;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Sky Controller: three buttons on its screen move the clock to morning (paid in Aero Flux),
 * to night (paid in Moon Flux from its second tank), or turn rain on and off (paid in Aero Flux).
 * After each use it waits a cooldown that higher tiers shorten; the screen's arrow fills as it
 * recovers. Costs and an off switch for servers are in the config.
 */
public class SkyControllerBlockEntity extends FluxMachineBlockEntity {
    private static final String TAG_COOLDOWN = "cooldown";
    private static final String TAG_COOLDOWN_TOTAL = "cooldown_total";
    /** How long a press of Clear keeps the sky clear, and Rain keeps it raining, in ticks. */
    private static final int CLEAR_TICKS = 12_000;
    private static final int RAIN_TICKS = 6_000;
    private static final long DAY_LENGTH = 24_000;
    /** The times {@code /time set day} and {@code /time set night} use. */
    private static final long DAY = 1_000;
    private static final long NIGHT = 13_000;

    private int cooldown;
    private int cooldownTotal;

    public SkyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    protected Job findJob() {
        return null;
    }

    @Override
    protected void tickAlways(Level level) {
        if (cooldown > 0) {
            cooldown--;
            setChanged();
        }
    }

    /** The arrow on the screen shows the cooldown recovering. */
    @Override
    public int progress() {
        return cooldownTotal - cooldown;
    }

    @Override
    public int totalTime() {
        return cooldown > 0 ? cooldownTotal : 0;
    }

    /** Ticks left before the next press works. */
    public int cooldown() {
        return cooldown;
    }

    @Override
    public boolean machineButton(int buttonId) {
        if (!(level instanceof ServerLevel server) || !AeroFluxConfig.skyControllerEnabled() || cooldown > 0) {
            return false;
        }
        boolean done = switch (buttonId) {
            case MachineConfigActions.SKY_DAY_BUTTON -> payAero(AeroFluxConfig.skyDayCost()) && moveClock(server, DAY);
            case MachineConfigActions.SKY_NIGHT_BUTTON -> payMoon(AeroFluxConfig.skyNightCost()) && moveClock(server, NIGHT);
            case MachineConfigActions.SKY_WEATHER_BUTTON -> payAero(AeroFluxConfig.skyWeatherCost()) && toggleRain(server);
            default -> false;
        };
        if (done) {
            cooldownTotal = MachineReach.skyCooldown(tier());
            cooldown = cooldownTotal;
            setChanged();
        }
        return done;
    }

    private boolean payAero(int cost) {
        if (drainFlux(cost, true) < cost) {
            return false;
        }
        drainFlux(cost, false);
        return true;
    }

    private boolean payMoon(int cost) {
        int tank = extraTankIndex(ExtraTank.MOON_FLUX);
        if (drainExtra(tank, cost, true) < cost) {
            return false;
        }
        drainExtra(tank, cost, false);
        return true;
    }

    /** Moves the clock forward to the next time of day given, as {@code /time set day} does. */
    private static boolean moveClock(ServerLevel server, long timeOfDay) {
        long now = server.getDayTime();
        long next = now - Math.floorMod(now, DAY_LENGTH) + timeOfDay;
        if (next <= now) {
            next += DAY_LENGTH;
        }
        server.setDayTime(next);
        return true;
    }

    private static boolean toggleRain(ServerLevel server) {
        if (server.isRaining()) {
            server.setWeatherParameters(CLEAR_TICKS, 0, false, false);
        } else {
            server.setWeatherParameters(0, RAIN_TICKS, true, false);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_COOLDOWN, cooldown);
        tag.putInt(TAG_COOLDOWN_TOTAL, cooldownTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cooldown = tag.getInt(TAG_COOLDOWN);
        cooldownTotal = tag.getInt(TAG_COOLDOWN_TOTAL);
    }
}
