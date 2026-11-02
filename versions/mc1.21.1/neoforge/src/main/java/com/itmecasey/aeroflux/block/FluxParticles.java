package com.itmecasey.aeroflux.block;

import com.itmecasey.aeroflux.AeroFluxClientConfig;
import com.itmecasey.aeroflux.core.FluxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The small flux-colored motes over working blocks, drawn from the blocks' {@code animateTick},
 * which only runs on the client. They are kept rare (one mote now and then per block) so a
 * room full of machines stays cheap, and the client config can switch them off.
 */
public final class FluxParticles {
    /** Aero Flux gold, a little deeper than the fluid so the motes show against the sky. */
    private static final int AERO = 0xF2C94C;
    /** Moon Flux pale blue. */
    private static final int MOON = 0xAFCBFF;
    private static final int TWILIGHT = FluxType.TWILIGHT.tint();
    /** Out of how many client ticks a working machine gives off one mote. */
    private static final int MACHINE_CHANCE = 3;
    /** Out of how many client ticks a collecting collector gives off one mote. */
    private static final int COLLECTOR_CHANCE = 2;

    private FluxParticles() {
    }

    /** A mote rising from the top of a working machine, in the color of the flux it runs on. */
    public static void machine(Level level, BlockPos pos, RandomSource random, FluxType flux) {
        if (!AeroFluxClientConfig.particles() || random.nextInt(MACHINE_CHANCE) != 0) {
            return;
        }
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(dust(flux, 0.6F), x, pos.getY() + 1.02, z, 0.0, 0.03, 0.0);
    }

    /** A mote drifting up off a collector's dish while the sky feeds it. */
    public static void collector(Level level, BlockPos pos, RandomSource random, FluxType flux) {
        if (!AeroFluxClientConfig.particles() || random.nextInt(COLLECTOR_CHANCE) != 0) {
            return;
        }
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
        double y = pos.getY() + 0.8 + random.nextDouble() * 0.3;
        level.addParticle(dust(flux, 0.5F), x, y, z, 0.0, 0.05, 0.0);
    }

    /** Dust takes its color as a vector of 0 to 1 channels here, not as a packed RGB value. */
    private static DustParticleOptions dust(FluxType flux, float scale) {
        return new DustParticleOptions(Vec3.fromRGB24(colorOf(flux)).toVector3f(), scale);
    }

    private static int colorOf(FluxType flux) {
        return switch (flux) {
            case AERO -> AERO;
            case MOON -> MOON;
            case TWILIGHT -> TWILIGHT;
        };
    }
}
