package com.itmecasey.aeroflux.compat.wthit;

import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.List;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

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
    }

    private enum Lines implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            ListTag lines = accessor.getData().raw().getList(AeroFluxWthitCommonPlugin.TAG, Tag.TAG_STRING);
            for (int i = 0; i < lines.size(); i++) {
                Component line = Component.Serializer.fromJson(lines.getString(i));
                if (line != null) {
                    tooltip.addLine(line);
                }
            }
        }
    }
}
