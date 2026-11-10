package com.itmecasey.aeroflux.compat.create;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Stress converter's registry entries. This class names Create's classes, so only
 * {@link CreateCompat} touches it, and only with Create installed.
 */
public final class CreateContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AeroFlux.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AeroFlux.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AeroFlux.MODID);

    private static final Map<Tier, RegistryObject<StressConverterBlock>> CONVERTERS = new EnumMap<>(Tier.class);

    static {
        for (Tier tier : Tier.values()) {
            String name = CreateCompat.converterName(tier);
            RegistryObject<StressConverterBlock> block = BLOCKS.register(name,
                    () -> new StressConverterBlock(tier, properties()));
            CONVERTERS.put(tier, block);
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    /** One block entity type for all six tiers; the block tells it its tier. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<StressConverterBlockEntity>> STRESS_CONVERTER =
            BLOCK_ENTITIES.register("stress_converter", () -> BlockEntityType.Builder.of((pos, state) -> new StressConverterBlockEntity(CreateContent.STRESS_CONVERTER.get(), pos, state),
                    CONVERTERS.values().stream().map(RegistryObject::get).toArray(StressConverterBlock[]::new)).build(null));

    private CreateContent() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** The Stress converter of one tier. */
    public static RegistryObject<StressConverterBlock> converter(Tier tier) {
        return CONVERTERS.get(tier);
    }

    static List<ItemLike> converters() {
        List<ItemLike> all = new ArrayList<>();
        for (RegistryObject<StressConverterBlock> block : CONVERTERS.values()) {
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
