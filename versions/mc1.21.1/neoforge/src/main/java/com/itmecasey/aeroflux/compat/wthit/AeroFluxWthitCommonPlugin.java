package com.itmecasey.aeroflux.compat.wthit;

import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import mcp.mobius.waila.api.data.FluidData;
import mcp.mobius.waila.api.data.ProgressData;
import mcp.mobius.waila.api.neo.NeoFluidData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The server half of the WTHIT plugin. It works out the same lines the Jade and The One Probe
 * plugins show (see {@link LookInfo}) and sends them as raw data, and it hands WTHIT the Flux a
 * block holds and a machine's progress in WTHIT's own data types, so WTHIT draws its usual fluid
 * bar and progress arrow. WTHIT finds this class through {@code waila_plugins.json} and only when
 * WTHIT is installed.
 */
public class AeroFluxWthitCommonPlugin implements IWailaCommonPlugin {
    /** The key the lines travel under in the raw data. */
    static final String TAG = "aeroflux_lines";

    /** The AeroFlux block entities that have something to add. */
    private static final List<Class<? extends BlockEntity>> TARGETS = List.of(
            ConduitBundleBlockEntity.class, FluxMachineBlockEntity.class, FluxCollectorBlockEntity.class,
            FluxChunkLoaderBlockEntity.class, FluxConverterBlockEntity.class, FluxTankBlockEntity.class);

    @Override
    public void register(ICommonRegistrar registrar) {
        for (Class<? extends BlockEntity> target : TARGETS) {
            registrar.blockData(Data.INSTANCE, target);
        }
    }

    /** Writes the lines, the Flux and the progress for whatever AeroFlux block the player looks at. */
    private enum Data implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            BlockEntity entity = accessor.getTarget();
            ComponentSerialization.CODEC.listOf()
                    .encodeStart(accessor.getWorld().registryAccess().createSerializationContext(NbtOps.INSTANCE),
                            LookInfo.lines(accessor.getWorld(), entity))
                    .result().ifPresent(lines -> data.raw().put(TAG, lines));

            LookInfo.Flux flux = LookInfo.flux(entity);
            if (flux != null) {
                data.add(FluidData.TYPE, result -> {
                    FluidStack held = flux.fluid() == null ? FluidStack.EMPTY
                            : new FluidStack(flux.fluid(), (int) Math.min(Integer.MAX_VALUE, flux.amount()));
                    result.add(NeoFluidData.of(1).add(held, flux.amount(), flux.capacity()));
                });
            }

            if (entity instanceof FluxMachineBlockEntity machine && machine.totalTime() > 0) {
                data.add(ProgressData.TYPE, result -> result.add(progress(machine)));
            }
        }

        /** How far the current job is, with the machine's input and output slots on either side of the arrow. */
        private static ProgressData progress(FluxMachineBlockEntity machine) {
            List<ItemStack> inputs = new ArrayList<>();
            List<ItemStack> outputs = new ArrayList<>();
            int inputSlots = machine.machine().inputSlots();
            for (int slot = 0; slot < inputSlots + machine.machine().outputSlots(); slot++) {
                (slot < inputSlots ? inputs : outputs).add(machine.stackIn(slot));
            }
            float ratio = Math.clamp((float) machine.progress() / machine.totalTime(), 0.0F, 1.0F);
            return ProgressData.ratio(ratio).input(inputs).output(outputs);
        }
    }
}
