package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxCollectorBlockEntity;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The block entity types AeroFlux adds. One type covers all six tiers of its block. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AeroFlux.MODID);

    public static final RegistryObject<BlockEntityType<FluxCollectorBlockEntity>> COLLECTOR =
            BLOCK_ENTITIES.register("collector", () -> new BlockEntityType<>(FluxCollectorBlockEntity::new,
                    validBlocks(ModBlocks.collectors())));

    public static final RegistryObject<BlockEntityType<FluxTankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> new BlockEntityType<>(FluxTankBlockEntity::new,
                    validBlocks(ModBlocks.tanks())));

    public static final RegistryObject<BlockEntityType<ConduitBundleBlockEntity>> CONDUIT_BUNDLE =
            BLOCK_ENTITIES.register("conduit_bundle", () -> new BlockEntityType<>(ConduitBundleBlockEntity::new,
                    Set.<Block>of(ModBlocks.CONDUIT_BUNDLE.get())));

    private ModBlockEntities() {
    }

    // Resolve each holder to its block here; a holder array would not be a Block[] at runtime.
    private static Set<Block> validBlocks(Iterable<? extends RegistryObject<? extends Block>> blocks) {
        return StreamSupport.stream(blocks.spliterator(), false)
                .<Block>map(RegistryObject::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        BLOCK_ENTITIES.register(modBusGroup);
    }
}
