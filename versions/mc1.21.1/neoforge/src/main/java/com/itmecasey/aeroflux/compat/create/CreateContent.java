package com.itmecasey.aeroflux.compat.create;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Stress converter's registry entries. This class names Create's classes, so only
 * {@link CreateCompat} touches it, and only with Create installed.
 */
public final class CreateContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroFlux.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroFlux.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroFlux.MODID);

    private static final Map<Tier, DeferredBlock<StressConverterBlock>> CONVERTERS = new EnumMap<>(Tier.class);

    static {
        for (Tier tier : Tier.values()) {
            DeferredBlock<StressConverterBlock> block = BLOCKS.register(CreateCompat.converterName(tier),
                    () -> new StressConverterBlock(tier, properties()));
            CONVERTERS.put(tier, block);
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    /** One block entity type for all six tiers; the block tells it its tier. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StressConverterBlockEntity>> STRESS_CONVERTER =
            BLOCK_ENTITIES.register("stress_converter", () -> BlockEntityType.Builder.of(StressConverterBlockEntity::new,
                    CONVERTERS.values().stream().map(DeferredBlock::get).toArray(StressConverterBlock[]::new)).build(null));

    private CreateContent() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STRESS_CONVERTER.get(),
                (converter, side) -> converter.fluidHandler());
    }

    /** The Stress converter of one tier. */
    public static DeferredBlock<StressConverterBlock> converter(Tier tier) {
        return CONVERTERS.get(tier);
    }

    static List<ItemLike> converters() {
        List<ItemLike> all = new ArrayList<>();
        for (DeferredBlock<StressConverterBlock> block : CONVERTERS.values()) {
            all.add(block.get());
        }
        return all;
    }

    /** Like the other converters: a metal block that needs a pickaxe, lit while it turns. */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(StressConverterBlock.LIT) ? FluxMachineBlock.LIT_LIGHT : 0);
    }
}
