package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.MachineType;
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

    /** Quartz and Flux Dust, infused with Moon Flux; the Aero half of the crystal pair. */
    public static final RegistryObject<Item> AERO_CRYSTAL = ITEMS.register("aero_crystal",
            () -> new Item(new Item.Properties().setId(ITEMS.key("aero_crystal"))));

    /** Amethyst and Flux Dust, infused with Moon Flux. */
    public static final RegistryObject<Item> MOON_CRYSTAL = ITEMS.register("moon_crystal",
            () -> new Item(new Item.Properties().setId(ITEMS.key("moon_crystal"))));

    /** Iron and Flux Dust, made in the Alloy Smelter. */
    public static final RegistryObject<Item> FLUXSTEEL_INGOT = ITEMS.register("fluxsteel_ingot",
            () -> new Item(new Item.Properties().setId(ITEMS.key("fluxsteel_ingot"))));

    /** Gold and an Aero Crystal, made in the Alloy Smelter. */
    public static final RegistryObject<Item> AEROGOLD_INGOT = ITEMS.register("aerogold_ingot",
            () -> new Item(new Item.Properties().setId(ITEMS.key("aerogold_ingot"))));

    public static final RegistryObject<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket",
            () -> new BucketItem(ModFluids.AERO_FLUX, bucketProperties("aero_flux_bucket")));

    public static final RegistryObject<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket",
            () -> new BucketItem(ModFluids.MOON_FLUX, bucketProperties("moon_flux_bucket")));

    /** One item per pipe type and tier. The untiered ME pipes have a single entry, under copper. */
    private static final Map<PipeType, Map<Tier, RegistryObject<PipeItem>>> PIPES = new EnumMap<>(PipeType.class);

    /** Installs into the pipe nearest the crosshair; makes it move faster. */
    public static final RegistryObject<UpgradeItem> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.SPEED, new Item.Properties().setId(ITEMS.key("speed_upgrade"))));

    /** Installs into the pipe nearest the crosshair; makes it move more at a time. */
    public static final RegistryObject<UpgradeItem> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.CAPACITY, new Item.Properties().setId(ITEMS.key("capacity_upgrade"))));

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final RegistryObject<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench",
            () -> new FluxWrenchItem(new Item.Properties().setId(ITEMS.key("flux_wrench")).stacksTo(1)));

    /** Edits, removes, copies and inspects pipes; the sneak-use cycles its mode. */
    public static final RegistryObject<ConduitToolItem> CONDUIT_TOOL = ITEMS.register("conduit_tool",
            () -> new ConduitToolItem(new Item.Properties().setId(ITEMS.key("conduit_tool")).stacksTo(1)));

    /** The block item of the pipe node. */
    public static final RegistryObject<BlockItem> PIPE_NODE = registerBlockItem(ModBlocks.PIPE_NODE, "pipe_node");

    static {
        for (PipeType type : PipeType.values()) {
            Map<Tier, RegistryObject<PipeItem>> byTier = new EnumMap<>(Tier.class);
            if (PipeTiers.isTiered(type)) {
                for (Tier tier : Tier.values()) {
                    byTier.put(tier, registerPipe(tier.id() + "_" + type.id() + "_pipe", type, tier));
                }
            } else {
                byTier.put(Tier.COPPER, registerPipe(type.id() + "_pipe", type, Tier.COPPER));
            }
            PIPES.put(type, byTier);
        }
        for (Tier tier : Tier.values()) {
            registerBlockItem(ModBlocks.collector(tier), tier.id() + "_collector");
            registerBlockItem(ModBlocks.tank(tier), tier.id() + "_tank");
            for (MachineType machine : MachineType.values()) {
                registerBlockItem(ModBlocks.machine(machine, tier), ModBlocks.machineBlockName(machine, tier));
            }
        }
    }

    private ModItems() {
    }

    private static RegistryObject<PipeItem> registerPipe(String id, PipeType type, Tier tier) {
        return ITEMS.register(id, () -> new PipeItem(type, tier, new Item.Properties().setId(ITEMS.key(id))));
    }

    /**
     * The item that places one kind of pipe at one tier. The tier is ignored for ME and dense ME
     * pipes, which do not come in tiers.
     */
    public static RegistryObject<PipeItem> pipe(PipeType type, Tier tier) {
        Map<Tier, RegistryObject<PipeItem>> byTier = PIPES.get(type);
        return PipeTiers.isTiered(type) ? byTier.get(tier) : byTier.get(Tier.COPPER);
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
