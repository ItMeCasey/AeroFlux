package com.itmecasey.aeroflux.compat.jade;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade support. The server works out the lines (see {@link LookInfo}) and sends them along with
 * Jade's own data; the client only prints them. Jade finds this class by its annotation and only
 * when Jade is installed, so AeroFlux runs fine without it.
 */
@WailaPlugin
public class AeroFluxJadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = Ids.mod("look_info");
    private static final ResourceLocation DATA_UID = Ids.mod("look_info_data");
    private static final String TAG = "aeroflux_lines";

    /** The AeroFlux blocks that have something to add. */
    private static final List<Class<? extends Block>> BLOCKS = List.of(
            ConduitBundleBlock.class, FluxMachineBlock.class, FluxCollectorBlock.class, FluxChunkLoaderBlock.class,
            FluxConverterBlock.class);

    @Override
    public void register(IWailaCommonRegistration registration) {
        for (Class<? extends Block> block : BLOCKS) {
            registration.registerBlockDataProvider(ServerData.INSTANCE, block);
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        for (Class<? extends Block> block : BLOCKS) {
            registration.registerBlockComponent(Tooltip.INSTANCE, block);
        }
    }

    /**
     * The server half: works the lines out and sends them. Jade wants this kept apart from the
     * client half, which is why there are two providers.
     */
    private enum ServerData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BlockEntity entity = accessor.getBlockEntity();
            if (entity != null) {
                ComponentSerialization.CODEC.listOf()
                        .encodeStart(accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                LookInfo.lines(accessor.getLevel(), entity))
                        .result().ifPresent(lines -> data.put(TAG, lines));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return DATA_UID;
        }
    }

    /** The client half: prints the lines the server sent. */
    private enum Tooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Tag saved = accessor.getServerData().get(TAG);
            List<Component> lines = saved == null ? List.of() : ComponentSerialization.CODEC.listOf()
                    .parse(accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), saved)
                    .result().orElse(List.of());
            lines.forEach(tooltip::add);
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
