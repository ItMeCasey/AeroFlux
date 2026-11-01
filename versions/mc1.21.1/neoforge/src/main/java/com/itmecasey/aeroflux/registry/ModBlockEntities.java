package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
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
            BLOCK_ENTITIES.register("collector", () -> BlockEntityType.Builder
                    .of(FluxCollectorBlockEntity::new, ModBlocks.collectors().stream().map(DeferredBlock::get).toArray(Block[]::new))
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxTankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> BlockEntityType.Builder
                    .of(FluxTankBlockEntity::new, ModBlocks.tanks().stream().map(DeferredBlock::get).toArray(Block[]::new))
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConduitBundleBlockEntity>> CONDUIT_BUNDLE =
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
