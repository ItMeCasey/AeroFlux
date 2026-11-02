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

    public static final DeferredItem<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket",
            () -> new BucketItem(ModFluids.AERO_FLUX.get(), bucketProperties()));

    public static final DeferredItem<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket",
            () -> new BucketItem(ModFluids.MOON_FLUX.get(), bucketProperties()));

    /** One item per pipe type and tier. The untiered ME pipes have a single entry, under copper. */
    private static final Map<PipeType, Map<Tier, DeferredItem<PipeItem>>> PIPES = new EnumMap<>(PipeType.class);

    /** Installs into the pipe nearest the crosshair; makes it move faster. */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.SPEED, new Item.Properties()));

    /** Installs into the pipe nearest the crosshair; makes it move more at a time. */
    public static final DeferredItem<UpgradeItem> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade",
            () -> new UpgradeItem(PipeUpgrades.Kind.CAPACITY, new Item.Properties()));

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final DeferredItem<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench",
            () -> new FluxWrenchItem(new Item.Properties().stacksTo(1)));

    /** Edits, removes, copies and inspects pipes; its mode is kept on the stack. */
    public static final DeferredItem<ConduitToolItem> CONDUIT_TOOL = ITEMS.register("conduit_tool",
            () -> new ConduitToolItem(new Item.Properties().stacksTo(1)));

    /** The block item of the pipe node. */
    public static final DeferredItem<BlockItem> PIPE_NODE = ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_NODE);

    static {
        for (PipeType type : PipeType.values()) {
            Map<Tier, DeferredItem<PipeItem>> byTier = new EnumMap<>(Tier.class);
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
            ITEMS.registerSimpleBlockItem(ModBlocks.collector(tier));
            ITEMS.registerSimpleBlockItem(ModBlocks.tank(tier));
        }
    }

    private ModItems() {
    }

    private static DeferredItem<PipeItem> registerPipe(String id, PipeType type, Tier tier) {
        return ITEMS.register(id, () -> new PipeItem(type, tier, new Item.Properties()));
    }

    /**
     * The item that places one kind of pipe at one tier. The tier is ignored for ME and dense ME
     * pipes, which do not come in tiers.
     */
    public static DeferredItem<PipeItem> pipe(PipeType type, Tier tier) {
        Map<Tier, DeferredItem<PipeItem>> byTier = PIPES.get(type);
        return PipeTiers.isTiered(type) ? byTier.get(tier) : byTier.get(Tier.COPPER);
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
