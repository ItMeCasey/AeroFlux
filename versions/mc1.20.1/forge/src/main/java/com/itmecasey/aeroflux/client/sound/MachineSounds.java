package com.itmecasey.aeroflux.client.sound;

import com.itmecasey.aeroflux.AeroFluxClientConfig;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Starts the running sounds of the machines near the player. Every machine the client knows
 * about calls in each tick (see {@link FluxMachineBlock#getTicker}); a lit one gets a
 * {@link MachineLoopSound} unless its position already has one playing, so there is never more
 * than one per machine. Stopping is up to the sound itself, which checks the block every tick.
 */
public final class MachineSounds {
    /** How long to wait before trying again to start a sound the engine did not start, in ticks. */
    private static final long RETRY_TICKS = 40;
    private static final Map<BlockPos, MachineLoopSound> PLAYING = new HashMap<>();

    private MachineSounds() {
    }

    /** The client tick of a machine. */
    public static void tick(Level level, BlockPos pos, BlockState state, FluxMachineBlockEntity machine) {
        if (!MachineLoopSound.lit(state) || AeroFluxClientConfig.machineLoopVolume() <= 0.0F) {
            return;
        }
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        MachineLoopSound playing = PLAYING.get(pos);
        // The sound engine may turn a sound down (too many playing, sound off), so one that never
        // started is tried again, but only now and then.
        if (playing != null && !playing.isStopped()
                && (sounds.isActive(playing) || level.getGameTime() - playing.startedAt() < RETRY_TICKS)) {
            return;
        }
        SoundEvent loop = ModSounds.loopOf(((FluxMachineBlock) state.getBlock()).machine());
        if (loop == null) {
            return;
        }
        if (playing != null) {
            playing.stopNow();
        }
        MachineLoopSound sound = new MachineLoopSound(loop, pos, level.getGameTime());
        PLAYING.values().removeIf(MachineLoopSound::isStopped);
        PLAYING.put(pos.immutable(), sound);
        sounds.play(sound);
    }
}
