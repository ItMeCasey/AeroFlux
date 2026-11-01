package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every item AeroFlux adds. Block items are registered next to their blocks. */
public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AeroFlux.MODID);

    /** Crafted from glowstone dust, quartz and redstone; the base ingredient for crystals and alloys. */
    public static final RegistryObject<Item> FLUX_DUST =
            ITEMS.register("flux_dust", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket",
            () -> new BucketItem(ModFluids.AERO_FLUX, bucketProperties()));

    public static final RegistryObject<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket",
            () -> new BucketItem(ModFluids.MOON_FLUX, bucketProperties()));

    private static final Map<PipeType, RegistryObject<PipeItem>> PIPES = new EnumMap<>(PipeType.class);

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final RegistryObject<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench",
            () -> new FluxWrenchItem(new Item.Properties().stacksTo(1)));

    static {
        blockItem("pipe_node", ModBlocks.PIPE_NODE);
        for (PipeType type : PipeType.values()) {
            PIPES.put(type, ITEMS.register(type.id() + "_pipe", () -> new PipeItem(type, new Item.Properties())));
        }
        for (Tier tier : Tier.values()) {
            blockItem(tier.id() + "_collector", ModBlocks.collector(tier));
            blockItem(tier.id() + "_tank", ModBlocks.tank(tier));
        }
    }

    private ModItems() {
    }

    /** The item that places one kind of pipe. */
    public static RegistryObject<PipeItem> pipe(PipeType type) {
        return PIPES.get(type);
    }

    private static void blockItem(String name, Supplier<? extends Block> block) {
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
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
