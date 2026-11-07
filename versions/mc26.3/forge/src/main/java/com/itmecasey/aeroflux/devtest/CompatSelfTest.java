package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCapabilities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Checks the support for other mods that can be checked on a server and exists for this Minecraft
 * version and loader:
 *
 * <ul>
 *     <li>WTHIT, when it is in the dev run: it found the plugin;</li>
 *     <li>fluids: the {@code c:} tags, and other mods pushing fluid into an AeroFlux pipe, which hands it
 *     to its sink within the pipe's rate.</li>
 * </ul>
 *
 * <p>The recipe viewers (JEI, REI, EMI), Jade, The One Probe, KubeJS, AE2, CraftTweaker and Create have
 * no Forge build for Minecraft 26.1 yet, so nothing is checked for them. The blocks stand in a chunk
 * of their own (x and z from 64, forced loaded by {@link GuardSelfTest}).
 */
final class CompatSelfTest {
    private static final int Y = 230;
    private static final int OX = 64;
    private static final int OZ = 64;
    private static final int SETTLE = 40;
    private static final int PUSH = 50;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos inletSource;
    private static BlockPos inletBundle;
    private static BlockPos inletSink;

    private CompatSelfTest() {
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        finished = false;
    }

    static boolean finished() {
        return finished;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            if (SelfTest.blockEntitiesTick(level, new BlockPos(OX + 2, Y, OZ + 2))) {
                build();
                built = true;
            }
            return;
        }
        if (++tick < SETTLE) {
            return;
        }
        checkFluids();
        finished = true;
    }

    private static void build() {
        // A pipe between two tanks, the way another mod's pipe or pump would push into it. The
        // source tank stays empty, so only the push this test makes reaches the sink.
        inletSource = new BlockPos(OX + 1, Y, OZ + 4);
        inletBundle = inletSource.east();
        inletSink = inletBundle.east();
        level.setBlock(inletSource, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletSink, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletBundle, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        bundle().addPipe(PipeType.FLUID, Tier.COPPER);
        setMode(Face.WEST, ConnectionMode.OPEN);
        setMode(Face.EAST, ConnectionMode.EXPORT);
    }

    // --- Fluids ------------------------------------------------------------------------------

    private static void checkFluids() {
        SelfTest.check("fluids: the Flux fluids carry their c: tags",
                ModFluids.AERO_FLUX.get().defaultFluidState().is(ModTags.AERO_FLUX)
                        && ModFluids.FLOWING_MOON_FLUX.get().defaultFluidState().is(ModTags.MOON_FLUX)
                        && ModFluids.TWILIGHT_FLUX.get().defaultFluidState().is(ModTags.TWILIGHT_FLUX));

        // Another mod pushing Aero Flux into the pipe's west face: it goes on to the sink, never back.
        IFluidHandler inlet = ModCapabilities.find(level, ForgeCapabilities.FLUID_HANDLER, inletBundle, Direction.WEST);
        IFluidHandler exportFace = ModCapabilities.find(level, ForgeCapabilities.FLUID_HANDLER, inletBundle, Direction.EAST);
        FluidStack aero = new FluidStack(ModFluids.AERO_FLUX.get(), PUSH);
        long before = held(inletSink);
        int pushed = inlet == null ? 0 : inlet.fill(aero, FluidAction.EXECUTE);
        SelfTest.check("fluids: other mods can push fluid into an AeroFlux pipe, which hands it to the sink",
                pushed == PUSH && held(inletSink) - before == PUSH && held(inletSource) == 0);

        // A push that is only tried (a simulation) must cost the pipe's rate nothing.
        long limit = PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 0, 0);
        int flood = inlet == null ? -1 : inlet.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 1_000_000), FluidAction.SIMULATE);
        SelfTest.check("fluids: a pipe face takes no more than the pipe's rate in one tick, and an Export face takes nothing in",
                flood >= 0 && flood <= limit && exportFace == null);
        SelfTest.check("fluids: a push that was only tried changes nothing", held(inletSink) - before == PUSH);
    }

    private static long held(BlockPos pos) {
        return ((FluxTankBlockEntity) level.getBlockEntity(pos)).tank().getFluidAmount();
    }

    private static void setMode(Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity bundle = bundle();
        bundle.bundle().setMode(PipeType.FLUID.lane(), face, mode);
        bundle.refreshConnections();
        bundle.changed();
    }

    private static ConduitBundleBlockEntity bundle() {
        return (ConduitBundleBlockEntity) level.getBlockEntity(inletBundle);
    }
}
