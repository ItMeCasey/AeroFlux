package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.chunkloader.ChunkLoaderRegistry;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.chunkloader.LoaderStatus;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The self-test for the Flux Chunk Loader. Two loaders stand in the always-forced chunk 0, 0 so
 * they tick without a player: a gold one (5x5) with plenty of Flux, and a copper one with only a
 * few ticks' worth. The gold one must take its tickets and make a chunk two chunks away tick; the
 * copper one must hold on through its grace period and then let go. Breaking the gold one must
 * take it off the registry.
 */
final class ChunkLoaderSelfTest {
    private static final int Y = 225;
    /** Tick at which both loaders have paid a few times. */
    private static final int RUNNING = 10;
    /** The copper loader's Flux, in mB: five ticks at 2 mB/t. */
    private static final int SHORT_FLUX = 10;
    private static final int LONG_FLUX = 16_000;

    private static ServerLevel level;
    private static int tick;
    private static int released;
    private static boolean built;
    private static boolean finished;

    private static BlockPos fueled;
    private static BlockPos starved;

    private ChunkLoaderSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        finished = false;
        // Past the copper loader's five paid ticks and its whole grace period, with room to spare.
        released = RUNNING + SHORT_FLUX / 2 + AeroFluxConfig.chunkLoaderGraceTicks() + 10;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            BlockPos probe = new BlockPos(8, Y, 8);
            if (level.shouldTickBlocksAt(probe) && level.isPositionEntityTicking(probe)) {
                build();
                built = true;
            }
            return;
        }
        tick++;
        if (tick == RUNNING) {
            checkRunning();
        }
        if (tick == released) {
            checkReleased();
            checkBreaking();
            finished = true;
        }
    }

    private static void build() {
        fueled = new BlockPos(8, Y, 8);
        starved = new BlockPos(11, Y, 8);
        level.setBlock(fueled, ModBlocks.chunkLoader(Tier.GOLD).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(starved, ModBlocks.chunkLoader(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);

        // Fill both through the capability, the way a pipe would, and make sure nothing comes back out.
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, fueled, null);
        SelfTest.check("a chunk loader exposes a fluid handler", handler != null);
        if (handler == null) {
            return;
        }
        FluidResource aero = FluidResource.of(ModFluids.AERO_FLUX.get());
        try (Transaction transaction = Transaction.openRoot()) {
            SelfTest.check("a chunk loader takes Aero Flux from a pipe",
                    handler.insert(0, aero, LONG_FLUX, transaction) == LONG_FLUX);
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openRoot()) {
            SelfTest.check("nothing can pump Flux out of a chunk loader", handler.extract(0, aero, 1_000, transaction) == 0);
        }
        try (Transaction transaction = Transaction.openRoot()) {
            SelfTest.check("a chunk loader refuses water",
                    handler.insert(0, FluidResource.of(Fluids.WATER), 1_000, transaction) == 0);
        }
        loader(starved).tank().set(0, FluidResource.of(ModFluids.MOON_FLUX.get()), SHORT_FLUX);
    }

    private static void checkRunning() {
        FluxChunkLoaderBlockEntity gold = loader(fueled);
        SelfTest.check("a fueled chunk loader takes its tickets", gold.holdingTickets() && gold.status() == LoaderStatus.ACTIVE);
        SelfTest.check("a fueled chunk loader burns its Flux", gold.fluxAmount() < LONG_FLUX
                && gold.fluxAmount() > LONG_FLUX - RUNNING * (ChunkLoaderMath.costPerTickMilli(Tier.GOLD) / ChunkLoaderMath.MILLI + 1));
        // Two chunks out from the always-forced chunk is only loaded at border level, so it
        // ticks only because the gold loader's 5x5 area reaches it. Blocks ticking is what machines,
        // collectors and pipes need, so that is what is checked.
        SelfTest.check("a gold chunk loader makes a chunk two away tick", level.shouldTickBlocksAt(new BlockPos(40, Y, 40)));
        ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
        SelfTest.check("a running chunk loader is registered as active",
                registry.get(fueled).map(ChunkLoaderRegistry.Entry::active).orElse(false));
        SelfTest.check("a chunk loader with a few ticks of Flux is running or in its grace period",
                loader(starved).status().holdsTickets());
    }

    private static void checkReleased() {
        FluxChunkLoaderBlockEntity copper = loader(starved);
        SelfTest.check("an empty chunk loader releases its tickets after the grace period",
                !copper.holdingTickets() && copper.status() == LoaderStatus.NO_FLUX && copper.fluxAmount() < 2);
        SelfTest.check("a released chunk loader is registered as idle",
                !ChunkLoaderRegistry.of(level).get(starved).map(ChunkLoaderRegistry.Entry::active).orElse(true));
    }

    private static void checkBreaking() {
        level.setBlock(fueled, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(starved, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ChunkLoaderRegistry registry = ChunkLoaderRegistry.of(level);
        SelfTest.check("a broken chunk loader leaves the registry", !registry.contains(fueled) && !registry.contains(starved));
    }

    private static FluxChunkLoaderBlockEntity loader(BlockPos pos) {
        return (FluxChunkLoaderBlockEntity) level.getBlockEntity(pos);
    }
}
