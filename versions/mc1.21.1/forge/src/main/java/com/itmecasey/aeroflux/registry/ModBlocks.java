package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.conduit.PipeNodeBlock;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every block AeroFlux adds. */
public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AeroFlux.MODID);

    private static final Map<Tier, RegistryObject<FluxCollectorBlock>> COLLECTORS = new EnumMap<>(Tier.class);
    private static final Map<Tier, RegistryObject<FluxTankBlock>> TANKS = new EnumMap<>(Tier.class);

    /** The in-world block for Aero Flux; behaves like water apart from the glow. */
    public static final RegistryObject<LiquidBlock> AERO_FLUX =
            BLOCKS.register("aero_flux", () -> fluidBlock(ModFluids.AERO_FLUX));

    /** The in-world block for Moon Flux; behaves like water apart from the glow. */
    public static final RegistryObject<LiquidBlock> MOON_FLUX =
            BLOCKS.register("moon_flux", () -> fluidBlock(ModFluids.MOON_FLUX));

    /** The block that carries every kind of pipe; see {@link ConduitBundleBlock}. */
    public static final RegistryObject<ConduitBundleBlock> CONDUIT_BUNDLE = BLOCKS.register("conduit_bundle",
            () -> new ConduitBundleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .dynamicShape()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /** Where pipes break off or change set; a plain dark gray block. */
    public static final RegistryObject<PipeNodeBlock> PIPE_NODE = BLOCKS.register("pipe_node",
            () -> new PipeNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.METAL)));

    static {
        for (Tier tier : Tier.values()) {
            COLLECTORS.put(tier, BLOCKS.register(tier.id() + "_collector",
                    () -> new FluxCollectorBlock(tier, machineProperties())));
            TANKS.put(tier, BLOCKS.register(tier.id() + "_tank",
                    () -> new FluxTankBlock(tier, tankProperties())));
        }
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

    private static LiquidBlock fluidBlock(Supplier<? extends FlowingFluid> fluid) {
        return new LiquidBlock(fluid, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                .lightLevel(state -> FluxType.LIGHT_LEVEL));
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
