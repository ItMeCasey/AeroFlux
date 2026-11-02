package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.chunkloader.ChunkLoaderMath;
import com.itmecasey.aeroflux.core.converter.ConverterMath;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.fluid.FluxFluidTank;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
/**
 * Checks the storage size table: that with an untouched config every size is the built-in one,
 * that the table comes through the network message unchanged, that sizes from a server win over
 * this game's own, and that a tank made smaller keeps what it holds and takes nothing more.
 *
 * <p>Runs once, straight away, and leaves no blocks behind: the tanks it makes are never placed.
 */
final class StorageSelfTest {
    private StorageSelfTest() {
    }

    static void run(ServerLevel level) {
        checkDefaults();
        checkRoundTrip();
        checkServerSizesWin(level);
        checkTankFillsToItsReportedSize();
    }

    private static void checkDefaults() {
        boolean all = true;
        for (Tier tier : Tier.values()) {
            all &= StorageSizes.tank(tier) == tier.tankCapacity();
            all &= StorageSizes.collector(tier) == tier.collectorBuffer();
            all &= StorageSizes.machine(tier) == MachineMath.inputCapacity(tier);
            all &= StorageSizes.machine(tier, 2) == MachineUpgrades.inputCapacity(tier, 2);
            all &= StorageSizes.converterTank(tier) == ConverterMath.tankCapacity(tier);
            all &= StorageSizes.converterBuffer(tier, ConverterType.ENERGY) == ConverterMath.bufferCapacity(tier, ConverterType.ENERGY);
            all &= StorageSizes.converterBuffer(tier, ConverterType.AE) == ConverterMath.bufferCapacity(tier, ConverterType.AE);
            all &= StorageSizes.chunkLoader(tier) == ChunkLoaderMath.capacity(tier);
        }
        SelfTest.check("storage: an untouched config gives every block its built-in size", all);
        SelfTest.check("storage: an untouched config gives lamps their built-in tank", StorageSizes.lamp() == FluxLampBlockEntity.CAPACITY);
    }

    private static void checkRoundTrip() {
        StorageSizes.Table sent = StorageSizes.fromConfig();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.write(buf);
        StorageSizes.Table received = StorageSizes.Table.read(buf);
        SelfTest.check("storage: the size table comes through the network message unchanged",
                Arrays.deepEquals(sent.sizes(), received.sizes()) && sent.lamp() == received.lamp() && buf.readableBytes() == 0);
    }

    private static void checkServerSizesWin(ServerLevel level) {
        BlockPos pos = new BlockPos(0, 300, 0);
        BlockState state = ModBlocks.tank(Tier.COPPER).get().defaultBlockState();

        // A big server: a copper tank holds ten times as much, and the tanks built now follow it.
        StorageSizes.useServerSizes(withCopperTank(Tier.COPPER.tankCapacity() * 10));
        FluxTankBlockEntity big = new FluxTankBlockEntity(pos, state);
        int filled = big.tank().fill(aero(Tier.COPPER.tankCapacity() * 5), FluidAction.EXECUTE);
        SelfTest.check("storage: sizes sent by a server win over this game's own config",
                StorageSizes.tank(Tier.COPPER) == Tier.COPPER.tankCapacity() * 10 && filled == Tier.COPPER.tankCapacity() * 5);
        CompoundTag saved = big.saveWithoutMetadata(level.registryAccess());

        // Back to the built-in size, smaller than what the tank holds.
        StorageSizes.forgetServerSizes();
        FluxTankBlockEntity shrunk = new FluxTankBlockEntity(pos, state);
        shrunk.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        int held = shrunk.tank().getFluidAmount();
        int more = shrunk.tank().fill(aero(1_000), FluidAction.EXECUTE);
        SelfTest.check("storage: a tank made smaller keeps all it holds and takes nothing more",
                held == filled && more == 0 && shrunk.tank().getFluidAmount() == filled
                        && shrunk.tank().getCapacity() == filled);
        SelfTest.check("storage: leaving a server goes back to this game's own sizes",
                StorageSizes.tank(Tier.COPPER) == Tier.COPPER.tankCapacity());
    }

    /**
     * A tank whose size grows after it is built, the way a machine's does with Capacity Upgrades,
     * fills up to the size it reports and not to the one it was built with.
     */
    private static void checkTankFillsToItsReportedSize() {
        FluxFluidTank grown = new FluxFluidTank(1_000, stack -> true) {
            @Override
            public int getCapacity() {
                return 3_000;
            }
        };
        int filled = grown.fill(aero(2_500), FluidAction.EXECUTE);
        SelfTest.check("storage: a tank with a bigger reported size (Capacity Upgrades) fills to that size",
                filled == 2_500 && grown.getFluidAmount() == 2_500 && grown.getSpace() == 500);
    }

    /** This game's table with only the copper tank changed. */
    private static StorageSizes.Table withCopperTank(int size) {
        StorageSizes.Table own = StorageSizes.fromConfig();
        int[][] sizes = new int[own.sizes().length][];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = own.sizes()[i].clone();
        }
        sizes[StorageSizes.Kind.TANK.ordinal()][Tier.COPPER.ordinal()] = size;
        return new StorageSizes.Table(sizes, own.lamp());
    }

    private static FluidStack aero(int amount) {
        return new FluidStack(ModFluids.AERO_FLUX.get(), amount);
    }
}
