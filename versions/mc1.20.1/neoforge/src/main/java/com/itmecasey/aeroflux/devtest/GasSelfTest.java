package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.compat.mekanism.MekanismGas;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The gas pipe checks, run by {@link TransferSelfTest} only when Mekanism is installed (this class
 * names Mekanism classes). Two Mekanism chemical tanks joined by a line of gas pipes, the first
 * pulled from and the last pushed into, the same way the fluid line is built.
 */
final class GasSelfTest {
    private static final int START = 4_000;
    private static final ResourceLocation TANK = new ResourceLocation("mekanism", "basic_chemical_tank");
    private static final ResourceLocation HYDROGEN = new ResourceLocation("mekanism", "hydrogen");

    private static BlockPos source;
    private static BlockPos sink;
    private static Gas hydrogen;

    private GasSelfTest() {
    }

    /** Builds the line at {@code y}: a full tank, three copper gas pipes, an empty tank. */
    static void build(ServerLevel level, int y, int z) {
        source = new BlockPos(1, y, z);
        sink = new BlockPos(5, y, z);
        // A tank gives out through its front and takes in on every other side, so both face east:
        // the full one's front meets the pipe line and the empty one takes it in through its back.
        placeTank(level, source, Direction.EAST);
        placeTank(level, sink, Direction.EAST);
        hydrogen = MekanismAPI.gasRegistry().getValue(HYDROGEN);
        SelfTest.check("gas: a Mekanism chemical tank offers a gas handler", MekanismGas.handler(level, source, null) != null);
        fill(level, source);
        for (int x = 2; x <= 4; x++) {
            BlockPos pos = new BlockPos(x, y, z);
            level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
            bundle(level, pos).addPipe(PipeType.GAS, Tier.COPPER);
        }
        setMode(level, new BlockPos(2, y, z), Face.WEST, ConnectionMode.IMPORT);
        setMode(level, new BlockPos(4, y, z), Face.EAST, ConnectionMode.EXPORT);
    }

    static void check(ServerLevel level) {
        long left = amount(level, source);
        long arrived = amount(level, sink);
        SelfTest.check("gas: a gas pipe attaches to a Mekanism chemical tank",
                bundle(level, new BlockPos(2, source.getY(), source.getZ())).bundle().isConnected(PipeType.GAS.lane(), Face.WEST));
        SelfTest.check("gas: hydrogen arrives in the far tank through three gas pipes", arrived > 0);
        SelfTest.check("gas: nothing is lost or made on the way", left + arrived == START);
    }

    private static void placeTank(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = BuiltInRegistries.BLOCK.get(TANK).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    /** Fills the source tank through whichever side takes gas in. */
    private static void fill(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            IGasHandler handler = MekanismGas.handler(level, pos, side);
            if (handler != null && handler.insertChemical(new GasStack(hydrogen, START), Action.EXECUTE).isEmpty()) {
                return;
            }
        }
    }

    private static long amount(ServerLevel level, BlockPos pos) {
        IGasHandler handler = MekanismGas.handler(level, pos, null);
        if (handler == null) {
            return 0;
        }
        long total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            GasStack held = handler.getChemicalInTank(tank);
            if (held.isTypeEqual(hydrogen)) {
                total += held.getAmount();
            }
        }
        return total;
    }

    private static ConduitBundleBlockEntity bundle(ServerLevel level, BlockPos pos) {
        return (ConduitBundleBlockEntity) level.getBlockEntity(pos);
    }

    private static void setMode(ServerLevel level, BlockPos pos, Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity entity = bundle(level, pos);
        entity.bundle().setMode(PipeType.GAS.lane(), face, mode);
        entity.refreshConnections();
        entity.changed();
    }
}
