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
import com.itmecasey.aeroflux.machine.configurator.FluxConfiguratorItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item AeroFlux adds. Block items are registered next to their blocks. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroFlux.MODID);

    /** Crafted from glowstone dust, quartz and redstone; the base ingredient for crystals and alloys. */
    public static final DeferredItem<Item> FLUX_DUST = ITEMS.registerSimpleItem("flux_dust");

    /** Quartz and Flux Dust, infused with Moon Flux; the Aero half of the crystal pair. */
    public static final DeferredItem<Item> AERO_CRYSTAL = ITEMS.registerSimpleItem("aero_crystal");

    /** Amethyst and Flux Dust, infused with Moon Flux. */
    public static final DeferredItem<Item> MOON_CRYSTAL = ITEMS.registerSimpleItem("moon_crystal");

    /** Iron and Flux Dust, made in the Alloy Smelter. */
    public static final DeferredItem<Item> FLUXSTEEL_INGOT = ITEMS.registerSimpleItem("fluxsteel_ingot");

    /** Gold and an Aero Crystal, made in the Alloy Smelter. */
    public static final DeferredItem<Item> AEROGOLD_INGOT = ITEMS.registerSimpleItem("aerogold_ingot");

    /** The Sawmill's byproduct: a log or plank sometimes leaves a pile of it behind. */
    public static final DeferredItem<Item> SAWDUST = ITEMS.registerSimpleItem("sawdust");

    /** The AeroFlux Guide; using it opens the guide screen. */
    public static final DeferredItem<GuidebookItem> GUIDEBOOK = ITEMS.registerItem("guidebook",
            GuidebookItem::new, properties -> properties.stacksTo(1));

    public static final DeferredItem<BucketItem> AERO_FLUX_BUCKET = ITEMS.registerItem("aero_flux_bucket",
            properties -> new BucketItem(ModFluids.AERO_FLUX.get(), properties), ModItems::bucketProperties);

    public static final DeferredItem<BucketItem> MOON_FLUX_BUCKET = ITEMS.registerItem("moon_flux_bucket",
            properties -> new BucketItem(ModFluids.MOON_FLUX.get(), properties), ModItems::bucketProperties);

    public static final DeferredItem<BucketItem> TWILIGHT_FLUX_BUCKET = ITEMS.registerItem("twilight_flux_bucket",
            properties -> new BucketItem(ModFluids.TWILIGHT_FLUX.get(), properties), ModItems::bucketProperties);

    /** One item per pipe type and tier. The untiered ME pipes have a single entry, under copper. */
    private static final Map<PipeType, Map<Tier, DeferredItem<PipeItem>>> PIPES = new EnumMap<>(PipeType.class);

    /** The cards that raise a placed machine, collector or tank to their tier; see {@link UpgradeCardItem}. */
    private static final Map<Tier, DeferredItem<UpgradeCardItem>> UPGRADE_CARDS = new EnumMap<>(Tier.class);

    /** Installs into the pipe nearest the crosshair; makes it move faster. */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            properties -> new UpgradeItem(PipeUpgrades.Kind.SPEED, properties));

    /** Installs into the pipe nearest the crosshair; makes it move more at a time. */
    public static final DeferredItem<UpgradeItem> CAPACITY_UPGRADE = ITEMS.registerItem("capacity_upgrade",
            properties -> new UpgradeItem(PipeUpgrades.Kind.CAPACITY, properties));

    /** The upgrades only machines take, one item each; see {@link MachineUpgradeItem}. */
    private static final Map<MachineUpgrades.Special, DeferredItem<MachineUpgradeItem>> MACHINE_UPGRADES =
            new EnumMap<>(MachineUpgrades.Special.class);

    static {
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            MACHINE_UPGRADES.put(kind, ITEMS.registerItem(kind.id() + "_upgrade",
                    properties -> new MachineUpgradeItem(kind, properties)));
        }
    }

    /** Installs into one pipe at one face; only lets chosen items or fluids through that face. */
    public static final DeferredItem<EndpointUpgradeItem> FILTER_UPGRADE = ITEMS.registerItem("filter_upgrade",
            properties -> new EndpointUpgradeItem(EndpointSettings.Upgrade.FILTER, properties));

    /** Installs into one pipe at one face; faces with a higher priority are filled first. */
    public static final DeferredItem<EndpointUpgradeItem> PRIORITY_UPGRADE = ITEMS.registerItem("priority_upgrade",
            properties -> new EndpointUpgradeItem(EndpointSettings.Upgrade.PRIORITY, properties));

    /** Cycles the mode of the pipe nearest the crosshair at the clicked face of a bundle. */
    public static final DeferredItem<FluxWrenchItem> FLUX_WRENCH = ITEMS.registerItem("flux_wrench",
            FluxWrenchItem::new, properties -> properties.stacksTo(1));

    /** Edits, removes, copies and inspects pipes; its mode is kept on the stack. */
    public static final DeferredItem<ConduitToolItem> CONDUIT_TOOL = ITEMS.registerItem("conduit_tool",
            ConduitToolItem::new, properties -> properties.stacksTo(1));

    /** Copies machine settings and pastes them onto other machines, one at a time or a whole area; holds eight presets. */
    public static final DeferredItem<FluxConfiguratorItem> FLUX_CONFIGURATOR = ITEMS.registerItem("flux_configurator",
            FluxConfiguratorItem::new, properties -> properties.stacksTo(1));

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
            // Copper is the base tier, so nothing upgrades into it and it has no card.
            if (tier != Tier.COPPER) {
                UPGRADE_CARDS.put(tier, ITEMS.registerItem("upgrade_card_" + tier.id(),
                        properties -> new UpgradeCardItem(tier, properties)));
            }
            ITEMS.registerSimpleBlockItem(ModBlocks.collector(tier));
            ITEMS.registerSimpleBlockItem(ModBlocks.tank(tier));
            for (MachineType machine : MachineType.values()) {
                ITEMS.registerSimpleBlockItem(ModBlocks.machine(machine, tier));
            }
            ITEMS.registerSimpleBlockItem(ModBlocks.chunkLoader(tier));
            for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
                ITEMS.registerSimpleBlockItem(ModBlocks.converter(type, tier));
            }
            for (LampKind kind : LampKind.values()) {
                ITEMS.registerSimpleBlockItem(ModBlocks.lamp(kind, tier));
            }
        }
        ITEMS.registerSimpleBlockItem(ModBlocks.CREATIVE_TANK, properties -> properties.rarity(Rarity.EPIC));
    }

    private ModItems() {
    }

    private static DeferredItem<PipeItem> registerPipe(String id, PipeType type, Tier tier) {
        return ITEMS.registerItem(id, properties -> new PipeItem(type, tier, properties));
    }

    /**
     * The item that places one kind of pipe at one tier. The tier is ignored for ME and dense ME
     * pipes, which do not come in tiers.
     */
    public static DeferredItem<PipeItem> pipe(PipeType type, Tier tier) {
        Map<Tier, DeferredItem<PipeItem>> byTier = PIPES.get(type);
        return PipeTiers.isTiered(type) ? byTier.get(tier) : byTier.get(Tier.COPPER);
    }

    /** The item of a machine-only upgrade. */
    public static DeferredItem<MachineUpgradeItem> machineUpgrade(MachineUpgrades.Special kind) {
        return MACHINE_UPGRADES.get(kind);
    }

    /** The upgrade card that raises a block to {@code tier}; there is none for copper. */
    public static DeferredItem<UpgradeCardItem> upgradeCard(Tier tier) {
        return UPGRADE_CARDS.get(tier);
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
