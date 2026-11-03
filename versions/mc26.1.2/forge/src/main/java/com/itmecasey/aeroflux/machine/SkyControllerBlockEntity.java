package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineReach;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.resources.ResourceKey;
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

    private int cooldown;
    private int cooldownTotal;

    public SkyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected @Nullable Job findJob() {
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
        if (!mayActAt(worldPosition)) {
            // The owner's claims no longer let this controller change the sky.
            return false;
        }
        boolean done = switch (buttonId) {
            case MachineConfigActions.SKY_DAY_BUTTON -> payAero(AeroFluxConfig.skyDayCost()) && moveClock(server, ClockTimeMarkers.DAY);
            case MachineConfigActions.SKY_NIGHT_BUTTON -> payMoon(AeroFluxConfig.skyNightCost()) && moveClock(server, ClockTimeMarkers.NIGHT);
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

    /** Moves this dimension's clock to a time marker, as {@code /time set day} does. */
    private static boolean moveClock(ServerLevel server, ResourceKey<ClockTimeMarker> marker) {
        Holder<WorldClock> clock = server.dimensionTypeRegistration().value().defaultClock().orElse(null);
        return clock != null && server.getServer().clockManager().moveToTimeMarker(clock, marker);
    }

    private static boolean toggleRain(ServerLevel server) {
        if (server.isRaining()) {
            server.getServer().setWeatherParameters(CLEAR_TICKS, 0, false, false);
        } else {
            server.getServer().setWeatherParameters(0, RAIN_TICKS, true, false);
        }
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_COOLDOWN, cooldown);
        output.putInt(TAG_COOLDOWN_TOTAL, cooldownTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cooldown = input.getIntOr(TAG_COOLDOWN, 0);
        cooldownTotal = input.getIntOr(TAG_COOLDOWN_TOTAL, 0);
    }
}
