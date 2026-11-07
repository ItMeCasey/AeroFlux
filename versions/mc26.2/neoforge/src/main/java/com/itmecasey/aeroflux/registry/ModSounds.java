package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineSoundSet;
import com.itmecasey.aeroflux.core.machine.MachineType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * The machine sounds: a running loop per kind of machine (see {@link MachineSoundSet}) and the
 * start, stop and done sounds every machine shares. The files and their subtitles are listed in
 * {@code sounds.json}; the sounds were synthesized for the mod (see {@code assets-src/aeroflux_sounds}).
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AeroFlux.MODID);

    /** The rising sweep when a machine starts working. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_START = sound("machine.start");
    /** The falling sweep when a machine stops. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_STOP = sound("machine.stop");
    /** The chime when a machine finishes a product. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_DONE = sound("machine.done");

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> LOOPS = new HashMap<>();

    static {
        for (String loop : MachineSoundSet.LOOPS) {
            LOOPS.put(loop, sound("machine." + loop + ".loop"));
        }
    }

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Ids.mod(name)));
    }

    /** The loop a machine plays while it works, or {@code null} if it plays none. */
    public static @Nullable SoundEvent loopOf(MachineType machine) {
        String loop = MachineSoundSet.loopOf(machine);
        return loop == null ? null : LOOPS.get(loop).get();
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
