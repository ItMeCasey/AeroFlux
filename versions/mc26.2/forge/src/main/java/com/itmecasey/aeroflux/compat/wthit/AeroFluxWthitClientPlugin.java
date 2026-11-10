package com.itmecasey.aeroflux.compat.wthit;

import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.client.render.FluidRenderUtil;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.List;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.data.FluidData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The client half of the WTHIT plugin: prints the lines the server half sent. The Flux bar and
 * the progress arrow need nothing here, as WTHIT draws its own data types by itself.
 */
public class AeroFluxWthitClientPlugin implements IWailaClientPlugin {
    /** The AeroFlux blocks that have lines to show; tanks only have their Flux bar. */
    private static final List<Class<? extends Block>> BLOCKS = List.of(
            ConduitBundleBlock.class, FluxMachineBlock.class, FluxCollectorBlock.class, FluxChunkLoaderBlock.class,
            FluxConverterBlock.class);

    @Override
    public void register(IClientRegistrar registrar) {
        for (Class<? extends Block> block : BLOCKS) {
            registrar.body(Lines.INSTANCE, block);
        }
        // WTHIT names a fluid from its own data; on Forge it did not find ours and printed "Unknown
        // Fluid", so each Flux says its name, sprite and tint itself.
        for (Fluid flux : List.of(ModFluids.AERO_FLUX.get(), ModFluids.MOON_FLUX.get(), ModFluids.TWILIGHT_FLUX.get())) {
            FluidData.describeFluid(flux, (context, description) -> description
                    .name(context.fluid().getFluidType().getDescription())
                    .sprite(FluidRenderUtil.stillSprite(context.fluid()))
                    .tint(FluidRenderUtil.tint(context.fluid())));
        }
    }

    private enum Lines implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            Tag saved = accessor.getData().raw().get(AeroFluxWthitCommonPlugin.TAG);
            List<Component> lines = saved == null ? List.of() : ComponentSerialization.CODEC.listOf()
                    .parse(accessor.getWorld().registryAccess().createSerializationContext(NbtOps.INSTANCE), saved)
                    .result().orElse(List.of());
            lines.forEach(tooltip::addLine);
        }
    }
}
