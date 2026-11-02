package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
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

    /** Quartz and Flux Dust, infused with Moon Flux; the Aero half of the crystal pair. */
    public static final RegistryObject<Item> AERO_CRYSTAL =
            ITEMS.register("aero_crystal", () -> new Item(new Item.Properties()));

    /** Amethyst and Flux Dust, infused with Moon Flux. */
    public static final RegistryObject<Item> MOON_CRYSTAL =
            ITEMS.register("moon_crystal", () -> new Item(new Item.Properties()));

    /** Iron and Flux Dust, made in the Alloy Smelter. */
    public static final RegistryObject<Item> FLUXSTEEL_INGOT =
            ITEMS.register("fluxsteel_ingot", () -> new Item(new Item.Properties()));

    /** Gold and an Aero Crystal, made in the Alloy Smelter. */
    public static final RegistryObject<Item> AEROGOLD_INGOT =
            ITEMS.register("aerogold_ingot", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket",
            () -> new BucketItem(ModFluids.AERO_FLUX, bucketProperties()));

    public static final RegistryObject<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket",
            () -> new BucketItem(ModFluids.MOON_FLUX, bucketProperties()));

    /** One item per pipe type and tier. The untiered ME pipes have a single entry, under copper. */
    private static final Map<PipeType, Map<Tier, RegistryObject<PipeItem>>> PIPES = new EnumMap<>(PipeType.class);

    /** Installs into the pipe nearest the crosshair; makes it move faster. */
    public static final RegistryObject<UpgradeItem> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.SPEED, new Item.Properties()));

    /** Installs into the pipe nearest the crosshair; makes it move more at a time. */
    public static final RegistryObject<UpgradeItem> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.CAPACITY, new Item.Properties()));

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final RegistryObject<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench",
            () -> new FluxWrenchItem(new Item.Properties().stacksTo(1)));

    /** Edits, removes, copies and inspects pipes; its mode is kept on the stack. */
    public static final RegistryObject<ConduitToolItem> CONDUIT_TOOL = ITEMS.register("conduit_tool",
            () -> new ConduitToolItem(new Item.Properties().stacksTo(1)));

    static {
        blockItem("pipe_node", ModBlocks.PIPE_NODE);
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
            blockItem(tier.id() + "_collector", ModBlocks.collector(tier));
            blockItem(tier.id() + "_tank", ModBlocks.tank(tier));
            for (MachineType machine : MachineType.values()) {
                blockItem(ModBlocks.machineBlockName(machine, tier), ModBlocks.machine(machine, tier));
            }
        }
    }

    private ModItems() {
    }

    private static RegistryObject<PipeItem> registerPipe(String id, PipeType type, Tier tier) {
        return ITEMS.register(id, () -> new PipeItem(type, tier, new Item.Properties()));
    }

    /**
     * The item that places one kind of pipe at one tier. The tier is ignored for ME and dense ME
     * pipes, which do not come in tiers.
     */
    public static RegistryObject<PipeItem> pipe(PipeType type, Tier tier) {
        Map<Tier, RegistryObject<PipeItem>> byTier = PIPES.get(type);
        return PipeTiers.isTiered(type) ? byTier.get(tier) : byTier.get(Tier.COPPER);
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
