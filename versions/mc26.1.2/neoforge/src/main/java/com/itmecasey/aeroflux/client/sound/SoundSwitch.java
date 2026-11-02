package com.itmecasey.aeroflux.client.sound;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxClientConfig;
import com.itmecasey.aeroflux.core.machine.MachineSoundSet;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Applies the player's sound settings to AeroFlux sounds the server sends. The start, stop and
 * finish sounds are played by the server for everyone nearby, so this player's client drops them
 * here when its config switches them off; the running loops check the config themselves.
 */
@EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class SoundSwitch {
    private SoundSwitch() {
    }

    @SubscribeEvent
    static void playing(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !AeroFlux.MODID.equals(sound.getIdentifier().getNamespace())) {
            return;
        }
        boolean loop = MachineSoundSet.isLoop(sound.getIdentifier().getPath());
        if (!AeroFluxClientConfig.sounds() || (!loop && !AeroFluxClientConfig.machineEffects())) {
            event.setSound(null);
        }
    }
}
