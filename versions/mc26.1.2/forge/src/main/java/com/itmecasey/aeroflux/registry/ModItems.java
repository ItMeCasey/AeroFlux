package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.EndpointUpgradeItem;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.item.GuidebookItem;
import com.itmecasey.aeroflux.item.UpgradeCardItem;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.MachineUpgradeItem;
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
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AeroFlux.MODID);

    /** Crafted from glowstone dust, quartz and redstone; the base ingredient for crystals and alloys. */
    public static final RegistryObject<Item> FLUX_DUST = ITEMS.register("flux_dust", () -> new Item(new Item.Properties().setId(ITEMS.key("flux_dust"))));

    /** Quartz and Flux Dust, infused with Moon Flux; the Aero half of the crystal pair. */
    public static final RegistryObject<Item> AERO_CRYSTAL = ITEMS.register("aero_crystal", () -> new Item(new Item.Properties().setId(ITEMS.key("aero_crystal"))));

    /** Amethyst and Flux Dust, infused with Moon Flux. */
    public static final RegistryObject<Item> MOON_CRYSTAL = ITEMS.register("moon_crystal", () -> new Item(new Item.Properties().setId(ITEMS.key("moon_crystal"))));

    /** Iron and Flux Dust, made in the Alloy Smelter. */
    public static final RegistryObject<Item> FLUXSTEEL_INGOT = ITEMS.register("fluxsteel_ingot", () -> new Item(new Item.Properties().setId(ITEMS.key("fluxsteel_ingot"))));

    /** Gold and an Aero Crystal, made in the Alloy Smelter. */
    public static final RegistryObject<Item> AEROGOLD_INGOT = ITEMS.register("aerogold_ingot", () -> new Item(new Item.Properties().setId(ITEMS.key("aerogold_ingot"))));

    /** The Sawmill's byproduct: a log or plank sometimes leaves a pile of it behind. */
    public static final RegistryObject<Item> SAWDUST = ITEMS.register("sawdust", () -> new Item(new Item.Properties().setId(ITEMS.key("sawdust"))));

    /** The AeroFlux Guide; using it opens the guide screen. */
    public static final RegistryObject<GuidebookItem> GUIDEBOOK = ITEMS.register("guidebook", () -> new GuidebookItem(new Item.Properties().setId(ITEMS.key("guidebook")).stacksTo(1)));

    public static final RegistryObject<BucketItem> AERO_FLUX_BUCKET = ITEMS.register("aero_flux_bucket", () -> new BucketItem(ModFluids.AERO_FLUX, bucketProperties("aero_flux_bucket")));

    public static final RegistryObject<BucketItem> MOON_FLUX_BUCKET = ITEMS.register("moon_flux_bucket", () -> new BucketItem(ModFluids.MOON_FLUX, bucketProperties("moon_flux_bucket")));

    public static final RegistryObject<BucketItem> TWILIGHT_FLUX_BUCKET = ITEMS.register("twilight_flux_bucket", () -> new BucketItem(ModFluids.TWILIGHT_FLUX, bucketProperties("twilight_flux_bucket")));

    /** One item per pipe type and tier. The untiered ME pipes have a single entry, under copper. */
    private static final Map<PipeType, Map<Tier, RegistryObject<PipeItem>>> PIPES = new EnumMap<>(PipeType.class);

    /** The cards that raise a placed machine, collector or tank to their tier; see {@link UpgradeCardItem}. */
    private static final Map<Tier, RegistryObject<UpgradeCardItem>> UPGRADE_CARDS = new EnumMap<>(Tier.class);

    /** Installs into the pipe nearest the crosshair; makes it move faster. */
    public static final RegistryObject<UpgradeItem> SPEED_UPGRADE = ITEMS.register("speed_upgrade", () -> new UpgradeItem(PipeUpgrades.Kind.SPEED, new Item.Properties().setId(ITEMS.key("speed_upgrade"))));

    /** Installs into the pipe nearest the crosshair; makes it move more at a time. */
    public static final RegistryObject<UpgradeItem> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade", () -> new UpgradeItem(PipeUpgrades.Kind.CAPACITY, new Item.Properties().setId(ITEMS.key("capacity_upgrade"))));

    /** The upgrades only machines take, one item each; see {@link MachineUpgradeItem}. */
    private static final Map<MachineUpgrades.Special, RegistryObject<MachineUpgradeItem>> MACHINE_UPGRADES =
            new EnumMap<>(MachineUpgrades.Special.class);

    static {
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            String id = kind.id() + "_upgrade";
            MACHINE_UPGRADES.put(kind, ITEMS.register(id,
                    () -> new MachineUpgradeItem(kind, new Item.Properties().setId(ITEMS.key(id)))));
        }
    }

    /** Installs into one pipe at one face; only lets chosen items or fluids through that face. */
    public static final RegistryObject<EndpointUpgradeItem> FILTER_UPGRADE = ITEMS.register("filter_upgrade", () -> new EndpointUpgradeItem(EndpointSettings.Upgrade.FILTER, new Item.Properties().setId(ITEMS.key("filter_upgrade"))));

    /** Installs into one pipe at one face; faces with a higher priority are filled first. */
    public static final RegistryObject<EndpointUpgradeItem> PRIORITY_UPGRADE = ITEMS.register("priority_upgrade", () -> new EndpointUpgradeItem(EndpointSettings.Upgrade.PRIORITY, new Item.Properties().setId(ITEMS.key("priority_upgrade"))));

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final RegistryObject<FluxWrenchItem> FLUX_WRENCH = ITEMS.register("flux_wrench", () -> new FluxWrenchItem(new Item.Properties().setId(ITEMS.key("flux_wrench")).stacksTo(1)));

    /** Edits, removes, copies and inspects pipes; its mode is kept on the stack. */
    public static final RegistryObject<ConduitToolItem> CONDUIT_TOOL = ITEMS.register("conduit_tool", () -> new ConduitToolItem(new Item.Properties().setId(ITEMS.key("conduit_tool")).stacksTo(1)));

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
            // Copper is the base tier, so nothing upgrades into it and it has no card.
            if (tier != Tier.COPPER) {
                UPGRADE_CARDS.put(tier, ITEMS.register("upgrade_card_" + tier.id(), () -> new UpgradeCardItem(tier, new Item.Properties().setId(ITEMS.key("upgrade_card_" + tier.id())))));
            }
            registerBlockItem(ModBlocks.collector(tier));
            registerBlockItem(ModBlocks.tank(tier));
            for (MachineType machine : MachineType.values()) {
                registerBlockItem(ModBlocks.machine(machine, tier));
            }
            registerBlockItem(ModBlocks.chunkLoader(tier));
            for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
                registerBlockItem(ModBlocks.converter(type, tier));
            }
            for (LampKind kind : LampKind.values()) {
                registerBlockItem(ModBlocks.lamp(kind, tier));
            }
        }
    }

    private ModItems() {
    }

    /** A plain block item for a block, under the block's own name. */
    private static void registerBlockItem(RegistryObject<? extends Block> block) {
        ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(),
                new Item.Properties().setId(ITEMS.key(block.getId().getPath())).useBlockDescriptionPrefix()));
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

    /** The item of a machine-only upgrade. */
    public static RegistryObject<MachineUpgradeItem> machineUpgrade(MachineUpgrades.Special kind) {
        return MACHINE_UPGRADES.get(kind);
    }

    /** The upgrade card that raises a block to {@code tier}; there is none for copper. */
    public static RegistryObject<UpgradeCardItem> upgradeCard(Tier tier) {
        return UPGRADE_CARDS.get(tier);
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
