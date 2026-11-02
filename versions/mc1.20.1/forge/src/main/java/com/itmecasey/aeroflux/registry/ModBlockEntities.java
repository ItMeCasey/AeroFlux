package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineFactory;
import java.util.Collection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The block entity types AeroFlux adds. One type covers all six tiers of its block. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AeroFlux.MODID);

    public static final RegistryObject<BlockEntityType<FluxCollectorBlockEntity>> COLLECTOR =
            BLOCK_ENTITIES.register("collector", () -> BlockEntityType.Builder.of(FluxCollectorBlockEntity::new, blocksOf(ModBlocks.collectors())).build(null));

    public static final RegistryObject<BlockEntityType<FluxTankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> BlockEntityType.Builder.of(FluxTankBlockEntity::new, blocksOf(ModBlocks.tanks())).build(null));

    /** One block entity type for every machine and tier; {@link MachineFactory} picks the behavior from the block. */
    public static final RegistryObject<BlockEntityType<FluxMachineBlockEntity>> MACHINE =
            BLOCK_ENTITIES.register("machine", () -> BlockEntityType.Builder.of(
                    MachineFactory::create, blocksOf(ModBlocks.machines())).build(null));

    public static final RegistryObject<BlockEntityType<ConduitBundleBlockEntity>> CONDUIT_BUNDLE =
            BLOCK_ENTITIES.register("conduit_bundle", () -> BlockEntityType.Builder.of(ConduitBundleBlockEntity::new, ModBlocks.CONDUIT_BUNDLE.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluxChunkLoaderBlockEntity>> CHUNK_LOADER =
            BLOCK_ENTITIES.register("chunk_loader", () -> BlockEntityType.Builder.of(FluxChunkLoaderBlockEntity::new, blocksOf(ModBlocks.chunkLoaders())).build(null));

    public static final RegistryObject<BlockEntityType<FluxConverterBlockEntity>> CONVERTER =
            BLOCK_ENTITIES.register("converter", () -> BlockEntityType.Builder.of(FluxConverterBlockEntity::new, blocksOf(ModBlocks.converters())).build(null));

    public static final RegistryObject<BlockEntityType<FluxLampBlockEntity>> LAMP =
            BLOCK_ENTITIES.register("lamp", () -> BlockEntityType.Builder.of(FluxLampBlockEntity::new, blocksOf(ModBlocks.lamps())).build(null));

    private ModBlockEntities() {
    }

    // The holders are unwrapped here, inside the supplier, because they only resolve once
    // the block registry has been filled.
    private static Block[] blocksOf(Collection<? extends RegistryObject<?>> holders) {
        return holders.stream()
                .<Block>map(holder -> (Block) holder.get())
                .toArray(Block[]::new);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
