package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.RecipeImports;
import com.itmecasey.aeroflux.machine.RecipeMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;

/**
 * Checks the machine-only upgrades and the recipes from outside AeroFlux: Efficiency cuts the
 * running cost, each upgrade only fits the machines it works in, Fortune and Purity add to the
 * product, Silk Touch keeps leaves, the Plant Grower grows plants no recipe names, and machines run
 * other mods' recipes (Ex Deorum's hammer recipes in the Grinder when it is in the dev run, and
 * vanilla stonecutting in the Sawmill through a rule added for the test).
 *
 * <p>Everything is netherite, built in a layer of its own and in the chunk's far corner, where it
 * keeps out of the collectors' sky, and read once at a fixed tick.
 */
final class MachineExtrasSelfTest {
    private static final int Y = 280;
    private static final int CHECK = 220;
    private static final int FLUX = 20_000;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos plainGrinder;
    private static BlockPos luckyGrinder;
    private static BlockPos purifier;
    private static BlockPos silkGrower;
    private static BlockPos plainGrower;
    private static BlockPos berryGrower;
    private static BlockPos mangroveGrower;
    private static BlockPos hammerGrinder;
    private static BlockPos stoneSawmill;

    private MachineExtrasSelfTest() {
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
                build();
                built = true;
            }
            return;
        }
        if (++tick == CHECK) {
            checkWork();
            finished = true;
        }
    }

    private static void build() {
        checkSlotsAndEfficiency();

        // Fortune: the same four iron ores with and without four Fortune Upgrades.
        plainGrinder = machine(MachineType.GRINDER, 2, 10, new ItemStack(Items.IRON_ORE, 4));
        luckyGrinder = machine(MachineType.GRINDER, 4, 10, new ItemStack(Items.IRON_ORE, 4));
        install(luckyGrinder, ModItems.machineUpgrade(MachineUpgrades.Special.FORTUNE).get(), 4);
        rolls(luckyGrinder);

        // Purity: four iron ores, which make three raw iron each, with four Purity Upgrades.
        purifier = machine(MachineType.PURIFIER, 6, 10, new ItemStack(Items.IRON_ORE, 4));
        install(purifier, ModItems.machineUpgrade(MachineUpgrades.Special.PURITY).get(), 4);
        fillWater(purifier);
        rolls(purifier);

        // Silk Touch: an oak sapling with and without it.
        silkGrower = machine(MachineType.PLANT_GROWER, 8, 10, new ItemStack(Items.OAK_SAPLING));
        install(silkGrower, ModItems.machineUpgrade(MachineUpgrades.Special.SILK_TOUCH).get(), 1);
        plainGrower = machine(MachineType.PLANT_GROWER, 10, 10, new ItemStack(Items.OAK_SAPLING));

        // Plants without a recipe of ours: a berry bush and a mangrove.
        berryGrower = machine(MachineType.PLANT_GROWER, 2, 12, new ItemStack(Items.SWEET_BERRIES));
        mangroveGrower = machine(MachineType.PLANT_GROWER, 4, 12, new ItemStack(Items.MANGROVE_PROPAGULE));

        // Other mods' recipes: Ex Deorum's hammer turns netherrack into crushed netherrack, which
        // no Grinder recipe of ours does; vanilla stonecutting through a rule added just for this.
        if (ModList.get().isLoaded("exdeorum")) {
            hammerGrinder = machine(MachineType.GRINDER, 6, 12, new ItemStack(Items.NETHERRACK, 2));
        }
        RecipeImports.addRuleForTest("{\"machine\": \"sawmill\", \"recipe_type\": \"minecraft:stonecutting\", \"time\": 100}");
        stoneSawmill = machine(MachineType.SAWMILL, 8, 12, new ItemStack(Items.STONE, 2));
    }

    private static void checkSlotsAndEfficiency() {
        BlockPos furnacePos = machine(MachineType.FURNACE, 12, 10, ItemStack.EMPTY);
        FluxMachineBlockEntity furnace = at(furnacePos);
        Item efficiency = ModItems.machineUpgrade(MachineUpgrades.Special.EFFICIENCY).get();
        Item fortune = ModItems.machineUpgrade(MachineUpgrades.Special.FORTUNE).get();
        SelfTest.check("upgrades: Efficiency fits every machine, Fortune only the Grinder and Sifter",
                furnace.upgrades().isItemValid(0, new ItemStack(efficiency))
                        && !furnace.upgrades().isItemValid(0, new ItemStack(fortune))
                        && at(machine(MachineType.SIFTER, 12, 12, ItemStack.EMPTY)).upgrades().isItemValid(0, new ItemStack(fortune))
                        && !at(machine(MachineType.SAWMILL, 12, 14, ItemStack.EMPTY)).upgrades()
                                .isItemValid(0, new ItemStack(ModItems.machineUpgrade(MachineUpgrades.Special.SILK_TOUCH).get()))
                        && !at(machine(MachineType.GRINDER, 10, 14, ItemStack.EMPTY)).upgrades()
                                .isItemValid(0, new ItemStack(ModItems.machineUpgrade(MachineUpgrades.Special.PURITY).get())));
        install(furnacePos, efficiency, 4);
        SelfTest.check("upgrades: four Efficiency Upgrades halve a netherite furnace's running cost",
                furnace.fluxPerTick() == MachineUpgrades.fluxPerTick(Tier.NETHERITE, 0, 4)
                        && furnace.fluxPerTick() * 2 == MachineUpgrades.fluxPerTick(Tier.NETHERITE, 0, 0));
    }

    private static void checkWork() {
        int plain = count(plainGrinder, Items.RAW_IRON);
        int lucky = count(luckyGrinder, Items.RAW_IRON);
        SelfTest.check("upgrades: Fortune makes the Grinder give more of its main product",
                plain == 8 && lucky > plain && at(luckyGrinder).stackIn(0).isEmpty());
        SelfTest.check("upgrades: Purity makes the Purifier give more than three raw iron an ore",
                count(purifier, Items.RAW_IRON) > 12 && at(purifier).stackIn(0).isEmpty());
        SelfTest.check("upgrades: Silk Touch keeps leaves from a tree, and without it there are none",
                count(silkGrower, Items.OAK_LEAVES) > 0 && count(plainGrower, Items.OAK_LEAVES) == 0
                        && count(plainGrower, Items.OAK_LOG) > 0);
        SelfTest.check("grower: a berry bush grows with no recipe, dropping what its loot table drops",
                count(berryGrower, Items.SWEET_BERRIES) > 0 && at(berryGrower).stackIn(0).isEmpty());
        SelfTest.check("grower: a mangrove propagule grows into mangrove logs with no recipe",
                count(mangroveGrower, Items.MANGROVE_LOG) > 0);
        if (hammerGrinder != null) {
            Item crushed = BuiltInRegistries.ITEM.get(new ResourceLocation("exdeorum:crushed_netherrack"));
            SelfTest.check("imports: the Grinder runs Ex Deorum's hammer recipes", count(hammerGrinder, crushed) == 2);
        }
        List<ItemStack> cut = outputs(stoneSawmill);
        SelfTest.check("imports: a rule lets the Sawmill run vanilla stonecutting recipes",
                !cut.isEmpty() && cut.stream().noneMatch(stack -> stack.is(Items.STONE)) && at(stoneSawmill).stackIn(0).isEmpty());
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos machine(MachineType type, int x, int z, ItemStack input) {
        BlockPos pos = new BlockPos(x, Y, z);
        level.setBlock(pos, ModBlocks.machine(type, Tier.NETHERITE).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = at(pos);
        machine.setFlux(new FluidStack(ModFluids.AERO_FLUX.get(), FLUX));
        if (!input.isEmpty()) {
            machine.setStackIn(0, input);
        }
        return pos;
    }

    private static FluxMachineBlockEntity at(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    /** Puts {@code count} of an upgrade into the first slots, the way a player would through the screen. */
    private static void install(BlockPos pos, Item upgrade, int count) {
        for (int slot = 0; slot < count; slot++) {
            at(pos).upgrades().setStackInSlot(slot, new ItemStack(upgrade));
        }
    }

    /** The same rolls on every run, so the chance-based checks always see the same outcome. */
    private static void rolls(BlockPos pos) {
        ((RecipeMachineBlockEntity) at(pos)).seedRolls(42L);
    }

    private static void fillWater(BlockPos pos) {
        at(pos).fluidHandler(null).fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
    }

    private static List<ItemStack> outputs(BlockPos pos) {
        FluxMachineBlockEntity machine = at(pos);
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = machine.machine().inputSlots(); slot < machine.machine().inputSlots() + machine.machine().outputSlots(); slot++) {
            if (!machine.stackIn(slot).isEmpty()) {
                stacks.add(machine.stackIn(slot));
            }
        }
        return stacks;
    }

    private static int count(BlockPos pos, Item item) {
        int total = 0;
        for (ItemStack stack : outputs(pos)) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
