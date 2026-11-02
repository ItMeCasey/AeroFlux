package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every block AeroFlux adds. */
public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AeroFlux.MODID);

    private static final Map<Tier, RegistryObject<FluxCollectorBlock>> COLLECTORS = new EnumMap<>(Tier.class);
    private static final Map<Tier, RegistryObject<FluxTankBlock>> TANKS = new EnumMap<>(Tier.class);
    private static final Map<MachineType, Map<Tier, RegistryObject<FluxMachineBlock>>> MACHINES = new EnumMap<>(MachineType.class);
    private static final Map<Tier, RegistryObject<FluxChunkLoaderBlock>> CHUNK_LOADERS = new EnumMap<>(Tier.class);
    private static final Map<ConverterType, Map<Tier, RegistryObject<FluxConverterBlock>>> CONVERTERS = new EnumMap<>(ConverterType.class);
    private static final Map<LampKind, Map<Tier, RegistryObject<FluxLampBlock>>> LAMPS = new EnumMap<>(LampKind.class);

    /**
     * The converters this version has. The AE converter needs AE2 and the stress converter Create,
     * and neither has a Forge build for 26.1, so only the FE converter is here.
     */
    public static final List<ConverterType> CONVERTER_TYPES = List.of(ConverterType.ENERGY);

    /** The in-world block for Aero Flux; behaves like water apart from the glow. */
    public static final RegistryObject<LiquidBlock> AERO_FLUX = BLOCKS.register("aero_flux", () -> fluidBlock("aero_flux", ModFluids.AERO_FLUX));

    /** The in-world block for Moon Flux; behaves like water apart from the glow. */
    public static final RegistryObject<LiquidBlock> MOON_FLUX = BLOCKS.register("moon_flux", () -> fluidBlock("moon_flux", ModFluids.MOON_FLUX));

    /** The in-world block for Twilight Flux, the blend of the two. */
    public static final RegistryObject<LiquidBlock> TWILIGHT_FLUX = BLOCKS.register("twilight_flux", () -> fluidBlock("twilight_flux", ModFluids.TWILIGHT_FLUX));

    /** The block that carries every kind of pipe; see {@link ConduitBundleBlock}. */
    public static final RegistryObject<ConduitBundleBlock> CONDUIT_BUNDLE = BLOCKS.register("conduit_bundle", () -> new ConduitBundleBlock(bundleProperties("conduit_bundle")));

    static {
        for (Tier tier : Tier.values()) {
            COLLECTORS.put(tier, BLOCKS.register(tier.id() + "_collector", () -> new FluxCollectorBlock(tier, machineProperties(tier.id() + "_collector"))));
            TANKS.put(tier, BLOCKS.register(tier.id() + "_tank", () -> new FluxTankBlock(tier, tankProperties(tier.id() + "_tank"))));
        }
        for (MachineType machine : MachineType.values()) {
            Map<Tier, RegistryObject<FluxMachineBlock>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                String name = machineBlockName(machine, tier);
                byTier.put(tier, BLOCKS.register(name, () -> new FluxMachineBlock(tier, machine, factoryProperties(name))));
            }
            MACHINES.put(machine, byTier);
        }
        for (Tier tier : Tier.values()) {
            CHUNK_LOADERS.put(tier, BLOCKS.register(chunkLoaderName(tier), () -> new FluxChunkLoaderBlock(tier, machineProperties(chunkLoaderName(tier)))));
        }
        for (ConverterType type : CONVERTER_TYPES) {
            Map<Tier, RegistryObject<FluxConverterBlock>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                String name = converterName(type, tier);
                byTier.put(tier, BLOCKS.register(name, () -> new FluxConverterBlock(type, tier, factoryProperties(name))));
            }
            CONVERTERS.put(type, byTier);
        }
        for (LampKind kind : LampKind.values()) {
            Map<Tier, RegistryObject<FluxLampBlock>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                String name = lampName(kind, tier);
                byTier.put(tier, BLOCKS.register(name, () -> new FluxLampBlock(kind, tier, lampProperties(name))));
            }
            LAMPS.put(kind, byTier);
        }
    }

    /** The registry name of a lamp: the bare name for copper, the tier in front for the rest. */
    public static String lampName(LampKind kind, Tier tier) {
        return tier == Tier.COPPER ? kind.blockId() : tier.id() + "_" + kind.blockId();
    }

    /** The lamp block of one kind and tier. */
    public static RegistryObject<FluxLampBlock> lamp(LampKind kind, Tier tier) {
        return LAMPS.get(kind).get(tier);
    }

    /** All lamp blocks, kind by kind, lowest tier first. */
    public static List<RegistryObject<FluxLampBlock>> lamps() {
        List<RegistryObject<FluxLampBlock>> all = new ArrayList<>();
        LAMPS.values().forEach(byTier -> all.addAll(byTier.values()));
        return all;
    }

    /** A lamp is small and caged, so it hides nothing behind it; lit, it is as bright as a block can be. */
    private static BlockBehaviour.Properties lampProperties(String name) {
        return BlockBehaviour.Properties.of()
                .setId(BLOCKS.key(name))
                .mapColor(MapColor.METAL)
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(FluxLampBlock.LIT) ? 15 : 0)
                .noOcclusion();
    }

    private ModBlocks() {
    }

    /** The collector block of one tier. */
    public static RegistryObject<FluxCollectorBlock> collector(Tier tier) {
        return COLLECTORS.get(tier);
    }

    /** The tank block of one tier. */
    public static RegistryObject<FluxTankBlock> tank(Tier tier) {
        return TANKS.get(tier);
    }

    /** All collector blocks, lowest tier first. */
    public static Collection<RegistryObject<FluxCollectorBlock>> collectors() {
        return COLLECTORS.values();
    }

    /** All tank blocks, lowest tier first. */
    public static Collection<RegistryObject<FluxTankBlock>> tanks() {
        return TANKS.values();
    }

    /**
     * The registry name of a machine block. The first tier keeps the bare name the art is
     * delivered under ({@code flux_furnace}); the others put the tier in front
     * ({@code iron_flux_furnace}) and share the same model.
     */
    public static String machineBlockName(MachineType machine, Tier tier) {
        return tier == Tier.COPPER ? machine.blockId() : tier.id() + "_" + machine.blockId();
    }

    /** The block of one machine at one tier. */
    public static RegistryObject<FluxMachineBlock> machine(MachineType machine, Tier tier) {
        return MACHINES.get(machine).get(tier);
    }

    /** Every machine block, grouped by machine and then lowest tier first. */
    public static List<RegistryObject<FluxMachineBlock>> machines() {
        List<RegistryObject<FluxMachineBlock>> all = new ArrayList<>();
        for (Map<Tier, RegistryObject<FluxMachineBlock>> byTier : MACHINES.values()) {
            all.addAll(byTier.values());
        }
        return all;
    }

    /**
     * The registry name of a chunk loader. Like the machines, the copper one keeps the bare name
     * its art is delivered under ({@code chunk_loader}) and the others share its model.
     */
    public static String chunkLoaderName(Tier tier) {
        return tier == Tier.COPPER ? "chunk_loader" : tier.id() + "_chunk_loader";
    }

    /** The chunk loader block of one tier. */
    public static RegistryObject<FluxChunkLoaderBlock> chunkLoader(Tier tier) {
        return CHUNK_LOADERS.get(tier);
    }

    /** The registry name of a converter: the bare name for copper, the tier in front for the rest. */
    public static String converterName(ConverterType type, Tier tier) {
        return tier == Tier.COPPER ? type.blockId() : tier.id() + "_" + type.blockId();
    }

    /** The converter block of one kind and tier. */
    public static RegistryObject<FluxConverterBlock> converter(ConverterType type, Tier tier) {
        return CONVERTERS.get(type).get(tier);
    }

    /** All converter blocks, kind by kind, lowest tier first. */
    public static List<RegistryObject<FluxConverterBlock>> converters() {
        List<RegistryObject<FluxConverterBlock>> all = new ArrayList<>();
        CONVERTERS.values().forEach(byTier -> all.addAll(byTier.values()));
        return all;
    }

    /** All chunk loader blocks, lowest tier first. */
    public static Collection<RegistryObject<FluxChunkLoaderBlock>> chunkLoaders() {
        return CHUNK_LOADERS.values();
    }

    /**
     * Every machine is a full block to touch, but the casings have recessed windows (and the plant
     * grower a glass chamber), so none of them may hide the faces of their neighbors. A working
     * machine glows like a lit furnace.
     */
    private static BlockBehaviour.Properties factoryProperties(String name) {
        return BlockBehaviour.Properties.of()
                .setId(BLOCKS.key(name))
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(FluxMachineBlock.LIT) ? FluxMachineBlock.LIT_LIGHT : 0)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties machineProperties(String name) {
        return BlockBehaviour.Properties.of()
                .setId(BLOCKS.key(name))
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

    /** Tanks are mostly glass, so they must not block light, view or suffocate. */
    private static BlockBehaviour.Properties tankProperties(String name) {
        return machineProperties(name)
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    /** Pipes are thin, so the bundle must not block light or view and its shape follows its contents. */
    private static BlockBehaviour.Properties bundleProperties(String name) {
        return BlockBehaviour.Properties.of()
                .setId(BLOCKS.key(name))
                .mapColor(MapColor.METAL)
                .strength(1.0F, 3.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .dynamicShape()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    // Since 26.1 every block's properties must carry its registry id.
    private static LiquidBlock fluidBlock(String name, Supplier<? extends FlowingFluid> fluid) {
        return new LiquidBlock(fluid, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                .setId(BLOCKS.key(name))
                .lightLevel(state -> FluxType.LIGHT_LEVEL));
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        BLOCKS.register(modBusGroup);
    }
}
