package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.creative.CreativeFluxTankBlockEntity;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineFactory;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The block entity types AeroFlux adds. One type covers all six tiers of its block. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroFlux.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxCollectorBlockEntity>> COLLECTOR =
            BLOCK_ENTITIES.register("collector", () -> new BlockEntityType<>(
                    FluxCollectorBlockEntity::new, blocksOf(ModBlocks.collectors())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxTankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> new BlockEntityType<>(
                    FluxTankBlockEntity::new, blocksOf(ModBlocks.tanks())));

    /** One block entity type for every machine and tier; {@link MachineFactory} picks the behavior from the block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxMachineBlockEntity>> MACHINE =
            BLOCK_ENTITIES.register("machine", () -> new BlockEntityType<>(
                    MachineFactory::create, blocksOf(ModBlocks.machines())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConduitBundleBlockEntity>> CONDUIT_BUNDLE =
            BLOCK_ENTITIES.register("conduit_bundle", () -> new BlockEntityType<>(
                    ConduitBundleBlockEntity::new, ModBlocks.CONDUIT_BUNDLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxChunkLoaderBlockEntity>> CHUNK_LOADER =
            BLOCK_ENTITIES.register("chunk_loader", () -> new BlockEntityType<>(
                    FluxChunkLoaderBlockEntity::new, blocksOf(ModBlocks.chunkLoaders())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxConverterBlockEntity>> CONVERTER =
            BLOCK_ENTITIES.register("converter", () -> new BlockEntityType<>(
                    FluxConverterBlockEntity::new, blocksOf(ModBlocks.converters())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxLampBlockEntity>> LAMP =
            BLOCK_ENTITIES.register("lamp", () -> new BlockEntityType<>(
                    FluxLampBlockEntity::new, blocksOf(ModBlocks.lamps())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeFluxTankBlockEntity>> CREATIVE_TANK =
            BLOCK_ENTITIES.register("creative_flux_tank", () -> new BlockEntityType<>(
                    CreativeFluxTankBlockEntity::new, ModBlocks.CREATIVE_TANK.get()));

    private ModBlockEntities() {
    }

    // The holders are unwrapped here, inside the supplier, because they only resolve once
    // the block registry has been filled.
    private static Set<Block> blocksOf(Collection<? extends DeferredBlock<?>> holders) {
        return holders.stream()
                .<Block>map(DeferredBlock::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
