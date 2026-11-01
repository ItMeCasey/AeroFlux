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
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every item AeroFlux adds. Block items are registered next to their blocks. */
public final class ModItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AeroFlux.MODID);

    /** Crafted from glowstone dust, quartz and redstone; the base ingredient for crystals and alloys. */
    public static final RegistryObject<Item> FLUX_DUST = ITEMS.register("flux_dust",
            () -> new Item(new Item.Properties().setId(ITEMS.key("flux_dust"))));

    public static final RegistryObject<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket",
            () -> new BucketItem(ModFluids.AERO_FLUX, bucketProperties("aero_flux_bucket")));

    public static final RegistryObject<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket",
            () -> new BucketItem(ModFluids.MOON_FLUX, bucketProperties("moon_flux_bucket")));

    private static final Map<PipeType, RegistryObject<PipeItem>> PIPES = new EnumMap<>(PipeType.class);

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final RegistryObject<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench",
            () -> new FluxWrenchItem(new Item.Properties().setId(ITEMS.key("flux_wrench")).stacksTo(1)));

    /** The block item of the pipe node. */
    public static final RegistryObject<BlockItem> PIPE_NODE = registerBlockItem(ModBlocks.PIPE_NODE, "pipe_node");

    static {
        for (PipeType type : PipeType.values()) {
            String name = type.id() + "_pipe";
            PIPES.put(type, ITEMS.register(name,
                    () -> new PipeItem(type, new Item.Properties().setId(ITEMS.key(name)))));
        }
        for (Tier tier : Tier.values()) {
            registerBlockItem(ModBlocks.collector(tier), tier.id() + "_collector");
            registerBlockItem(ModBlocks.tank(tier), tier.id() + "_tank");
        }
    }

    private ModItems() {
    }

    /** The item that places one kind of pipe. */
    public static RegistryObject<PipeItem> pipe(PipeType type) {
        return PIPES.get(type);
    }

    /** Registers the plain block item for a block, under the same name as the block. */
    private static RegistryObject<BlockItem> registerBlockItem(RegistryObject<? extends Block> block, String name) {
        return ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().setId(ITEMS.key(name)).useBlockDescriptionPrefix()));
    }

    /** Buckets stack to one and leave an empty bucket behind when used in crafting. */
    private static Item.Properties bucketProperties(String name) {
        return new Item.Properties().setId(ITEMS.key(name)).craftRemainder(Items.BUCKET).stacksTo(1);
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        ITEMS.register(modBusGroup);
    }
}
