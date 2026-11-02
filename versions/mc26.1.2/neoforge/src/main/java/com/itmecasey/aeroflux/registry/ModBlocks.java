package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.conduit.PipeNodeBlock;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block AeroFlux adds. */
public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroFlux.MODID);

    private static final Map<Tier, DeferredBlock<FluxCollectorBlock>> COLLECTORS = new EnumMap<>(Tier.class);
    private static final Map<Tier, DeferredBlock<FluxTankBlock>> TANKS = new EnumMap<>(Tier.class);
    private static final Map<MachineType, Map<Tier, DeferredBlock<FluxMachineBlock>>> MACHINES = new EnumMap<>(MachineType.class);

    /** The in-world block for Aero Flux; behaves like water apart from the glow. */
    public static final DeferredBlock<LiquidBlock> AERO_FLUX = BLOCKS.registerBlock("aero_flux",
            properties -> fluidBlock(ModFluids.AERO_FLUX.get(), properties), ModBlocks::fluidProperties);

    /** The in-world block for Moon Flux; behaves like water apart from the glow. */
    public static final DeferredBlock<LiquidBlock> MOON_FLUX = BLOCKS.registerBlock("moon_flux",
            properties -> fluidBlock(ModFluids.MOON_FLUX.get(), properties), ModBlocks::fluidProperties);

    /** The block that carries every kind of pipe; see {@link ConduitBundleBlock}. */
    public static final DeferredBlock<ConduitBundleBlock> CONDUIT_BUNDLE = BLOCKS.registerBlock("conduit_bundle",
            ConduitBundleBlock::new, ModBlocks::bundleProperties);

    /** Where pipes break off or change set; a plain dark gray block. */
    public static final DeferredBlock<PipeNodeBlock> PIPE_NODE = BLOCKS.registerBlock("pipe_node",
            PipeNodeBlock::new, ModBlocks::nodeProperties);

    static {
        for (Tier tier : Tier.values()) {
            COLLECTORS.put(tier, BLOCKS.registerBlock(tier.id() + "_collector",
                    properties -> new FluxCollectorBlock(tier, properties), ModBlocks::machineProperties));
            TANKS.put(tier, BLOCKS.registerBlock(tier.id() + "_tank",
                    properties -> new FluxTankBlock(tier, properties), ModBlocks::tankProperties));
        }
        for (MachineType machine : MachineType.values()) {
            Map<Tier, DeferredBlock<FluxMachineBlock>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                byTier.put(tier, BLOCKS.registerBlock(machineBlockName(machine, tier),
                        properties -> new FluxMachineBlock(tier, machine, properties), () -> factoryProperties(machine)));
            }
            MACHINES.put(machine, byTier);
        }
    }

    private ModBlocks() {
    }

    /** The collector block of one tier. */
    public static DeferredBlock<FluxCollectorBlock> collector(Tier tier) {
        return COLLECTORS.get(tier);
    }

    /** The tank block of one tier. */
    public static DeferredBlock<FluxTankBlock> tank(Tier tier) {
        return TANKS.get(tier);
    }

    /** All collector blocks, lowest tier first. */
    public static Collection<DeferredBlock<FluxCollectorBlock>> collectors() {
        return COLLECTORS.values();
    }

    /** All tank blocks, lowest tier first. */
    public static Collection<DeferredBlock<FluxTankBlock>> tanks() {
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
    public static DeferredBlock<FluxMachineBlock> machine(MachineType machine, Tier tier) {
        return MACHINES.get(machine).get(tier);
    }

    /** Every machine block, grouped by machine and then lowest tier first. */
    public static List<DeferredBlock<FluxMachineBlock>> machines() {
        List<DeferredBlock<FluxMachineBlock>> all = new ArrayList<>();
        for (Map<Tier, DeferredBlock<FluxMachineBlock>> byTier : MACHINES.values()) {
            all.addAll(byTier.values());
        }
        return all;
    }

    /**
     * The furnace, grinder and alloy smelter models are plain cubes; the others have parts that
     * do not fill the block, so they must not hide the faces of their neighbors.
     */
    private static BlockBehaviour.Properties factoryProperties(MachineType machine) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
        boolean cube = machine == MachineType.FURNACE || machine == MachineType.GRINDER || machine == MachineType.ALLOY_SMELTER;
        return cube ? properties : properties.noOcclusion();
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

    /** Tanks are mostly glass, so they must not block light, view or suffocate. */
    private static BlockBehaviour.Properties tankProperties() {
        return machineProperties()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    /** Pipes are thin, so the bundle must not block light or view and its shape follows its contents. */
    private static BlockBehaviour.Properties bundleProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.0F, 3.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .dynamicShape()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties nodeProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F, 4.0F)
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties fluidProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).lightLevel(state -> FluxType.LIGHT_LEVEL);
    }

    private static LiquidBlock fluidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        return new LiquidBlock(fluid, properties);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
