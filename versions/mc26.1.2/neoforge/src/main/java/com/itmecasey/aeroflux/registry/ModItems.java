package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item AeroFlux adds. Block items are registered next to their blocks. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroFlux.MODID);

    /** Crafted from glowstone dust, quartz and redstone; the base ingredient for crystals and alloys. */
    public static final DeferredItem<Item> FLUX_DUST = ITEMS.registerSimpleItem("flux_dust");

    public static final DeferredItem<BucketItem> AERO_FLUX_BUCKET = ITEMS.registerItem("aero_flux_bucket",
            properties -> new BucketItem(ModFluids.AERO_FLUX.get(), properties), ModItems::bucketProperties);

    public static final DeferredItem<BucketItem> MOON_FLUX_BUCKET = ITEMS.registerItem("moon_flux_bucket",
            properties -> new BucketItem(ModFluids.MOON_FLUX.get(), properties), ModItems::bucketProperties);

    private static final Map<PipeType, DeferredItem<PipeItem>> PIPES = new EnumMap<>(PipeType.class);

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final DeferredItem<FluxWrenchItem> FLUX_WRENCH = ITEMS.registerItem("flux_wrench",
            FluxWrenchItem::new, properties -> properties.stacksTo(1));

    /** The block item of the pipe node. */
    public static final DeferredItem<BlockItem> PIPE_NODE = ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_NODE);

    static {
        for (PipeType type : PipeType.values()) {
            PIPES.put(type, ITEMS.registerItem(type.id() + "_pipe", properties -> new PipeItem(type, properties)));
        }
        for (Tier tier : Tier.values()) {
            ITEMS.registerSimpleBlockItem(ModBlocks.collector(tier));
            ITEMS.registerSimpleBlockItem(ModBlocks.tank(tier));
        }
    }

    private ModItems() {
    }

    /** The item that places one kind of pipe. */
    public static DeferredItem<PipeItem> pipe(PipeType type) {
        return PIPES.get(type);
    }

    /** Buckets stack to one and leave an empty bucket behind when used in crafting. */
    private static Item.Properties bucketProperties() {
        return new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
