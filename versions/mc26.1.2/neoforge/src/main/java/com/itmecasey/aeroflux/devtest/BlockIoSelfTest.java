package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import com.itmecasey.aeroflux.core.FillLevel;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModDataComponents;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.List;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import com.itmecasey.aeroflux.converter.ConverterMenu;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
            if (SelfTest.blockEntitiesTick(level, new BlockPos(2, Y, 2))) {
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

        insert(tank.tank(), FluidResource.of(ModFluids.MOON_FLUX.get()), TANK_FLUX);
        List<ItemStack> drops = Block.getDrops(state, level, pos, tank);
        SimpleFluidContent carried = drops.size() == 1
                ? drops.get(0).getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY)
                : SimpleFluidContent.EMPTY;
        SelfTest.check("tanks: a broken tank keeps its flux on the item",
                carried.getAmount() == TANK_FLUX && carried.is(ModFluids.MOON_FLUX.get()));

        // Placing it again: BlockItem hands the item's components to the new block entity.
        BlockPos again = new BlockPos(3, Y, 2);
        level.setBlock(again, state, Block.UPDATE_ALL);
        FluxTankBlockEntity placed = (FluxTankBlockEntity) level.getBlockEntity(again);
        placed.applyComponentsFromItemStack(drops.get(0));
        SelfTest.check("tanks: a placed tank item fills the new tank with its flux",
                placed.tank().getAmountAsLong(0) == TANK_FLUX
                        && placed.tank().getResource(0).equals(FluidResource.of(ModFluids.MOON_FLUX.get())));
        // The flux is on the item, so the copy of the block data on it must not carry it twice.
        SelfTest.check("tanks: the block entity does not keep a second copy of the flux in its item data",
                placed.components().get(ModDataComponents.TANK_FLUID.get()) == null);
        checkAnyFluid();
    }

    /** A tank takes any fluid, one at a time, and a broken tank carries it like it carries flux. */
    private static void checkAnyFluid() {
        BlockPos pos = new BlockPos(5, Y, 2);
        level.setBlock(pos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxTankBlockEntity tank = (FluxTankBlockEntity) level.getBlockEntity(pos);
        insert(tank.tank(), FluidResource.of(Fluids.WATER), TANK_FLUX);
        SelfTest.check("tanks: a tank takes water, not only flux",
                tank.amount() == TANK_FLUX && tank.storedFluid() == Fluids.WATER && tank.storedFlux() == null);
        insert(tank.tank(), FluidResource.of(Fluids.LAVA), TANK_FLUX);
        SelfTest.check("tanks: it holds one fluid at a time, so lava is refused while it holds water",
                tank.amount() == TANK_FLUX && tank.storedFluid() == Fluids.WATER);
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, tank);
        SimpleFluidContent carried = drops.size() == 1
                ? drops.get(0).getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY) : SimpleFluidContent.EMPTY;
        SelfTest.check("tanks: a broken tank keeps water on the item", carried.getAmount() == TANK_FLUX && carried.is(Fluids.WATER));
        BlockPos again = new BlockPos(6, Y, 2);
        level.setBlock(again, state, Block.UPDATE_ALL);
        FluxTankBlockEntity placed = (FluxTankBlockEntity) level.getBlockEntity(again);
        placed.applyComponentsFromItemStack(drops.get(0));
        SelfTest.check("tanks: a placed tank item fills the new tank with its water",
                placed.amount() == TANK_FLUX && placed.storedFluid() == Fluids.WATER);
        // The screen learns the fluid through the menu's data.
        TankMenu menu = new TankMenu(0, null, placed);
        SelfTest.check("tanks: the menu tells the screen which fluid the tank holds", menu.fluid() == Fluids.WATER);
        // A save that names a fluid from a mod that is gone loads as an empty tank, not an error.
        CompoundTag saved = placed.saveWithoutMetadata(level.registryAccess());
        BlockPos orphan = new BlockPos(7, Y, 2);
        level.setBlock(orphan, state, Block.UPDATE_ALL);
        FluxTankBlockEntity orphaned = (FluxTankBlockEntity) level.getBlockEntity(orphan);
        boolean loaded;
        try {
            CompoundTag broken = TagParser.parseCompoundFully(saved.toString().replace("minecraft:water", "aeroflux_test:gone"));
            orphaned.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), broken));
            loaded = true;
        } catch (Exception e) {
            loaded = false;
        }
        SelfTest.check("tanks: a saved fluid that no longer exists loads as an empty tank", loaded && orphaned.amount() == 0);
        // A converter's menu refuses a button press from a player who has walked away.
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos convPos = new BlockPos(8, Y, 2);
        level.setBlock(convPos, ModBlocks.converter(ConverterType.ENERGY, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxConverterBlockEntity conv = (FluxConverterBlockEntity) level.getBlockEntity(convPos);
        ConverterMenu convMenu = new ConverterMenu(2, player.getInventory(), conv);
        player.setPos(convPos.getX() + 0.5, convPos.getY() + 40, convPos.getZ() + 0.5);
        SelfTest.check("converter menu: a button press from far away is refused", !convMenu.clickMenuButton(player, 0));
        player.setPos(convPos.getX() + 0.5, convPos.getY(), convPos.getZ() - 1.5);
        SelfTest.check("converter menu: the same press next to it works", convMenu.clickMenuButton(player, 0));
    }

    private static void checkComparators() {
        BlockPos tankPos = new BlockPos(4, Y, 2);
        level.setBlock(tankPos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxTankBlockEntity tank = (FluxTankBlockEntity) level.getBlockEntity(tankPos);
        int empty = signal(tankPos);
        int capacity = (int) tank.tank().getCapacityAsLong(0, FluidResource.EMPTY);
        insert(tank.tank(), FluidResource.of(ModFluids.AERO_FLUX.get()), capacity / 2);
        int half = signal(tankPos);
        insert(tank.tank(), FluidResource.of(ModFluids.AERO_FLUX.get()), capacity);
        SelfTest.check("comparators: a tank reads 0 empty, 8 half full and 15 full",
                empty == 0 && half == FillLevel.comparatorSignal(capacity / 2, capacity) && half == 8 && signal(tankPos) == 15);

        BlockPos converterPos = new BlockPos(5, Y, 2);
        level.setBlock(converterPos, ModBlocks.converter(ConverterType.ENERGY, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxConverterBlockEntity converter = (FluxConverterBlockEntity) level.getBlockEntity(converterPos);
        int converterEmpty = signal(converterPos);
        insert(converter.tank(), FluidResource.of(ModFluids.AERO_FLUX.get()), 1);
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

    private static void insert(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(0, fluid, amount, transaction);
            transaction.commit();
        }
    }
}
