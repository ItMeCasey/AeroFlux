package com.itmecasey.aeroflux.compat.top;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.compat.LookInfo;
import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * The One Probe support. The probe asks on the server, so the lines come straight from
 * {@link LookInfo}. The probe hands itself over through an inter-mod message (see
 * {@code AeroFlux}), which is only sent when The One Probe is installed.
 */
public final class AeroFluxProbeProvider implements IProbeInfoProvider, Function<ITheOneProbe, Void> {
    private static final ResourceLocation ID = Ids.mod("look_info");

    /** Called by The One Probe with its API; registers this provider. */
    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(this);
        return null;
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level, BlockState state, IProbeHitData data) {
        BlockEntity entity = level.getBlockEntity(data.getPos());
        if (entity == null) {
            return;
        }
        // The One Probe cannot read 26.1's fluid handlers on its own, so the Flux level is drawn here.
        LookInfo.Flux flux = LookInfo.flux(entity);
        if (flux != null) {
            int color = flux.fluid() == null ? 0xFF808080
                    : 0xFF000000 | (flux.type() != null ? flux.type().tint() : IClientFluidTypeExtensions.of(flux.fluid()).getTintColor());
            info.progress(flux.amount(), flux.capacity(), info.defaultProgressStyle()
                    .suffix(" / " + flux.capacity() + " mB")
                    .filledColor(color)
                    .alternateFilledColor(color));
        }
        LookInfo.lines(level, entity).forEach(info::text);
    }
}
