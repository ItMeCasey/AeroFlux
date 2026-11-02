package com.itmecasey.aeroflux.compat.jade;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

    /** Their block entities; this version of Jade asks for the server data by block entity class. */
    private static final List<Class<? extends BlockEntity>> BLOCK_ENTITIES = List.of(
            ConduitBundleBlockEntity.class, FluxMachineBlockEntity.class, FluxCollectorBlockEntity.class,
            FluxChunkLoaderBlockEntity.class, FluxConverterBlockEntity.class);

    @Override
    public void register(IWailaCommonRegistration registration) {
        for (Class<? extends BlockEntity> entity : BLOCK_ENTITIES) {
            registration.registerBlockDataProvider(ServerData.INSTANCE, entity);
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
                ListTag lines = new ListTag();
                for (Component line : LookInfo.lines(accessor.getLevel(), entity)) {
                    lines.add(StringTag.valueOf(Component.Serializer.toJson(line)));
                }
                data.put(TAG, lines);
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
            ListTag lines = accessor.getServerData().getList(TAG, Tag.TAG_STRING);
            for (int i = 0; i < lines.size(); i++) {
                Component line = Component.Serializer.fromJson(lines.getString(i));
                if (line != null) {
                    tooltip.add(line);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
