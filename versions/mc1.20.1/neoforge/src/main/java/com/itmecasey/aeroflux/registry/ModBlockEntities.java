package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineFactory;
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
            BLOCK_ENTITIES.register("collector", () -> BlockEntityType.Builder
                    .of(FluxCollectorBlockEntity::new, ModBlocks.collectors().stream().map(RegistryObject::get).toArray(Block[]::new))
                    .build(null));

    public static final RegistryObject<BlockEntityType<FluxTankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> BlockEntityType.Builder
                    .of(FluxTankBlockEntity::new, ModBlocks.tanks().stream().map(RegistryObject::get).toArray(Block[]::new))
                    .build(null));

    /** One block entity type for every machine and tier; {@link MachineFactory} picks the behavior from the block. */
    public static final RegistryObject<BlockEntityType<FluxMachineBlockEntity>> MACHINE =
            BLOCK_ENTITIES.register("machine", () -> BlockEntityType.Builder
                    .of(MachineFactory::create, ModBlocks.machines().stream().map(RegistryObject::get).toArray(Block[]::new))
                    .build(null));

    public static final RegistryObject<BlockEntityType<ConduitBundleBlockEntity>> CONDUIT_BUNDLE =
            BLOCK_ENTITIES.register("conduit_bundle", () -> BlockEntityType.Builder
                    .of(ConduitBundleBlockEntity::new, ModBlocks.CONDUIT_BUNDLE.get())
                    .build(null));

    private ModBlockEntities() {
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
