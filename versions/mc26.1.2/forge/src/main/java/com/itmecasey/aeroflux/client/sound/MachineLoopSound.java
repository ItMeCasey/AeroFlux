package com.itmecasey.aeroflux.client.sound;

import com.itmecasey.aeroflux.AeroFluxClientConfig;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sound of one machine at work: it loops for as long as the machine at its position is lit,
 * and stops by itself the moment that is no longer so, whether the machine stopped, was broken
 * or its chunk went away. The volume follows the client config, so a change there is heard at once.
 */
public class MachineLoopSound extends AbstractTickableSoundInstance {
    private final BlockPos pos;
    private final long startedAt;

    /** A loop for the machine at {@code pos}, started at the given game time. */
    public MachineLoopSound(SoundEvent sound, BlockPos pos, long startedAt) {
        super(sound, SoundSource.BLOCKS, RandomSource.create());
        this.pos = pos.immutable();
        this.startedAt = startedAt;
        this.looping = true;
        this.delay = 0;
        this.volume = AeroFluxClientConfig.machineLoopVolume();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    @Override
    public void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        float wanted = AeroFluxClientConfig.machineLoopVolume();
        if (level == null || wanted <= 0.0F || !level.isLoaded(pos) || !lit(level.getBlockState(pos))) {
            stop();
            return;
        }
        volume = wanted;
    }

    /** The game time the sound was started at. */
    public long startedAt() {
        return startedAt;
    }

    /** Ends the sound, for when a fresh one replaces it. */
    public void stopNow() {
        stop();
    }

    /** Whether a block is a working machine. */
    static boolean lit(BlockState state) {
        return state.getBlock() instanceof FluxMachineBlock && state.getValue(FluxMachineBlock.LIT);
    }
}
