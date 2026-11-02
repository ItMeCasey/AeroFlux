package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.fluid.TankContents;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModDataComponents;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Checks how AeroFlux blocks meet vanilla's own blocks: a broken Flux Tank keeps its flux and puts
 * it back when placed, comparators read tanks, converters and machines, hoppers feed a machine from
 * above and empty it from below, and a dropper pushes items into a machine's side.
 *
 * <p>Builds in a layer of its own at y 240, with no Flux in the machines so nothing they are given
 * gets processed, and reads the hoppers and dropper once at a fixed tick.
 */
final class BlockIoSelfTest {
    private static final int Y = 240;
    private static final int CHECK = 60;
    private static final int TANK_FLUX = 1_000;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos fedFurnace;
    private static BlockPos fedHopper;
    private static BlockPos drainedFurnace;
    private static BlockPos drainHopper;
    private static BlockPos droppedFurnace;
    private static BlockPos dropper;

    private BlockIoSelfTest() {
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
            if (level.shouldTickBlocksAt(new BlockPos(2, Y, 2))) {
                checkTanks();
                checkComparators();
                buildHoppers();
                built = true;
            }
            return;
        }
        if (++tick == CHECK) {
            checkHoppers();
            finished = true;
        }
    }

    private static void checkTanks() {
        BlockPos pos = new BlockPos(2, Y, 2);
        level.setBlock(pos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxTankBlockEntity tank = (FluxTankBlockEntity) level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        // Empty, it drops a plain tank that stacks with new ones.
        List<ItemStack> emptyDrops = Block.getDrops(state, level, pos, tank);
        SelfTest.check("tanks: an empty tank drops a plain tank",
                emptyDrops.size() == 1 && !emptyDrops.get(0).has(ModDataComponents.TANK_FLUID.get())
                        && ItemStack.isSameItemSameComponents(emptyDrops.get(0), new ItemStack(state.getBlock())));

        insert(tank.tank(), ModFluids.MOON_FLUX.get(), TANK_FLUX);
        List<ItemStack> drops = Block.getDrops(state, level, pos, tank);
        TankContents carried = drops.size() == 1 ? drops.get(0).get(ModDataComponents.TANK_FLUID.get()) : null;
        SelfTest.check("tanks: a broken tank keeps its flux on the item",
                carried != null && carried.amount() == TANK_FLUX && carried.fluid() == ModFluids.MOON_FLUX.get());

        // Placing it again: BlockItem hands the item's components to the new block entity.
        BlockPos again = new BlockPos(3, Y, 2);
        level.setBlock(again, state, Block.UPDATE_ALL);
        FluxTankBlockEntity placed = (FluxTankBlockEntity) level.getBlockEntity(again);
        placed.applyComponentsFromItemStack(drops.get(0));
        SelfTest.check("tanks: a placed tank item fills the new tank with its flux",
                placed.tank().getFluidAmount() == TANK_FLUX
                        && placed.tank().getFluid().getFluid() == ModFluids.MOON_FLUX.get());
        // The flux is on the item, so the copy of the block data on it must not carry it twice.
        SelfTest.check("tanks: the block entity does not keep a second copy of the flux in its item data",
                placed.components().get(ModDataComponents.TANK_FLUID.get()) == null);
    }

    private static void checkComparators() {
        BlockPos tankPos = new BlockPos(4, Y, 2);
        level.setBlock(tankPos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxTankBlockEntity tank = (FluxTankBlockEntity) level.getBlockEntity(tankPos);
        int empty = signal(tankPos);
        int capacity = tank.tank().getCapacity();
        insert(tank.tank(), ModFluids.AERO_FLUX.get(), capacity / 2);
        int half = signal(tankPos);
        insert(tank.tank(), ModFluids.AERO_FLUX.get(), capacity);
        SelfTest.check("comparators: a tank reads 0 empty, 8 half full and 15 full",
                empty == 0 && half == FillLevel.comparatorSignal(capacity / 2, capacity) && half == 8 && signal(tankPos) == 15);

        BlockPos converterPos = new BlockPos(5, Y, 2);
        level.setBlock(converterPos, ModBlocks.converter(ConverterType.ENERGY, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxConverterBlockEntity converter = (FluxConverterBlockEntity) level.getBlockEntity(converterPos);
        int converterEmpty = signal(converterPos);
        insert(converter.tank(), ModFluids.AERO_FLUX.get(), 1);
        SelfTest.check("comparators: a converter reads its Flux tank", converterEmpty == 0 && signal(converterPos) == 1);

        BlockPos furnacePos = new BlockPos(6, Y, 2);
        level.setBlock(furnacePos, ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity furnace = (FluxMachineBlockEntity) level.getBlockEntity(furnacePos);
        int machineEmpty = signal(furnacePos);
        furnace.setStackIn(0, new ItemStack(Items.RAW_IRON, 64));
        SelfTest.check("comparators: a machine reads its item slots, like a furnace",
                machineEmpty == 0 && signal(furnacePos) > 0);
    }

    private static void buildHoppers() {
        // A hopper above a machine feeds its top face, which takes items in.
        fedFurnace = furnace(8, 2);
        fedHopper = fedFurnace.above();
        level.setBlock(fedHopper, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        ((Container) level.getBlockEntity(fedHopper)).setItem(0, new ItemStack(Items.RAW_IRON, 5));
        // A lid, so items another check drops from above cannot fall in and spoil the count.
        level.setBlock(fedHopper.above(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);

        // A hopper below a machine pulls from its bottom face, which lets products out.
        drainedFurnace = furnace(10, 2);
        drainHopper = drainedFurnace.below();
        level.setBlock(drainHopper, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        // Down into a solid block it cannot fill, so the hopper keeps what it pulls.
        level.setBlock(drainHopper.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        int output = MachineType.FURNACE.inputSlots();
        ((FluxMachineBlockEntity) level.getBlockEntity(drainedFurnace)).setStackIn(output, new ItemStack(Items.IRON_INGOT, 3));

        // A dropper facing the side of a machine, fired once by a redstone block.
        droppedFurnace = furnace(13, 4);
        dropper = droppedFurnace.west();
        level.setBlock(dropper, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        ((Container) level.getBlockEntity(dropper)).setItem(0, new ItemStack(Items.RAW_IRON));
        level.setBlock(dropper.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void checkHoppers() {
        SelfTest.check("hoppers: a hopper above a machine feeds it",
                ((Container) level.getBlockEntity(fedHopper)).isEmpty()
                        && furnaceAt(fedFurnace).stackIn(0).is(Items.RAW_IRON) && furnaceAt(fedFurnace).stackIn(0).getCount() == 5);
        int output = MachineType.FURNACE.inputSlots();
        Container below = (Container) level.getBlockEntity(drainHopper);
        int pulled = 0;
        for (int slot = 0; slot < below.getContainerSize(); slot++) {
            pulled += below.getItem(slot).is(Items.IRON_INGOT) ? below.getItem(slot).getCount() : 0;
        }
        SelfTest.check("hoppers: a hopper below a machine empties its output", pulled == 3 && furnaceAt(drainedFurnace).stackIn(output).isEmpty());
        SelfTest.check("droppers: a dropper pushes into the side of a machine",
                ((Container) level.getBlockEntity(dropper)).isEmpty() && furnaceAt(droppedFurnace).stackIn(0).is(Items.RAW_IRON));
    }

    // --- Helpers -----------------------------------------------------------------------------

    /** A furnace with no Flux, so it holds what it is given instead of smelting it. */
    private static BlockPos furnace(int x, int z) {
        BlockPos pos = new BlockPos(x, Y, z);
        level.setBlock(pos, ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        return pos;
    }

    private static FluxMachineBlockEntity furnaceAt(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static int signal(BlockPos pos) {
        return level.getBlockState(pos).getAnalogOutputSignal(level, pos, Direction.NORTH);
    }

    private static void insert(IFluidHandler handler, Fluid fluid, int amount) {
        handler.fill(new FluidStack(fluid, amount), FluidAction.EXECUTE);
    }
}
