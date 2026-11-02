package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import com.itmecasey.aeroflux.core.machine.MachineLayout;
import com.itmecasey.aeroflux.core.machine.MachineMath;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.MachineFactory;
import com.itmecasey.aeroflux.machine.MachineMenu;
import com.itmecasey.aeroflux.machine.RecipeMachineBlockEntity;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModRecipes;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The machine part of the self-test: the Flux Furnace and the seven other machines, run through
 * a real server. Every scenario is built at once, high above the world and inside the forced
 * chunk, and the checks run at fixed tick counts afterwards (a machine needs real ticks to work).
 * The checks that need no waiting (face rules, saving, menus) run as soon as everything is built.
 *
 * <p>Nothing here draws anything, so how the machine screens look is not covered; their layout
 * numbers and the menu's data are.
 */
final class MachineSelfTest {
    private static final int Y = 300;
    private static final int PIPE_Y = 304;
    /** Tick at which the early checks (tier speed, pipes, the empty tank) run. */
    private static final int EARLY = 40;
    private static final int RUN_OUT = 60;
    private static final int LAST = 200;
    /**
     * Tick at which the sawdust count is read. The sawdust sawmill runs {@link #SAWDUST_LOGS}
     * jobs of 9 ticks each at the top tier, so this leaves plenty of slack.
     */
    private static final int SAWDUST_DONE = 400;
    /** How many logs the sawdust sawmill cuts: enough jobs for its one-in-four chance to show. */
    private static final int SAWDUST_LOGS = 32;
    /** Seed for the sawdust sawmill's rolls, so its count is the same on every run. */
    private static final long SAWDUST_SEED = 0x5A3D057L;

    private static ServerLevel level;
    private static FakePlayer player;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    // Furnace scenarios.
    private static BlockPos slowFurnace;
    private static BlockPos fastFurnace;
    private static BlockPos hungryFurnace;
    private static BlockPos persistedFurnace;
    private static int persistedProgress;
    private static int persistedFlux;
    private static CompoundTag persistedTag;
    private static int slowStartFlux;
    private static int fastStartFlux;

    // The pipe scenarios.
    private static BlockPos pipeFedFurnace;
    private static BlockPos pipeFedTank;
    private static BlockPos drainedFurnace;
    private static BlockPos drainedTank;

    // The other machines.
    private static BlockPos grinder;
    private static BlockPos gravelGrinder;
    private static BlockPos sawmill;
    private static BlockPos stemSawmill;
    private static BlockPos plankSawmill;
    private static BlockPos sawdustSawmill;
    /** Planks taken out of the sawdust sawmill so far, the way a pipe would empty it. */
    private static int sawdustPlanks;
    private static BlockPos alloy;
    private static BlockPos bulkAlloy;
    private static BlockPos infuser;
    private static BlockPos shortInfuser;
    private static BlockPos enchanter;
    private static BlockPos clashEnchanter;
    private static BlockPos poorEnchanter;
    private static BlockPos disenchanter;
    private static BlockPos aeroGrower;
    private static BlockPos wrongFluxGrower;
    private static BlockPos moonGrower;
    private static int infuserStartFlux;
    private static int enchanterStartFlux;
    private static int disenchanterStartFlux;

    private MachineSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        finished = false;
    }

    /** Advances the scenarios by one server tick; they are built on the first tick the chunk really ticks. */
    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            BlockPos probe = new BlockPos(2, Y, 2);
            if (level.shouldTickBlocksAt(probe) && level.isPositionEntityTicking(probe)) {
                player = FakePlayerFactory.getMinecraft(level);
                build();
                built = true;
            }
            return;
        }
        tick++;
        if (tick == 20) {
            checkDrainedFaceStillClosed();
        }
        if (tick == EARLY) {
            checkTierSpeed();
            checkPipeFed();
            checkPersistence();
        }
        if (tick == RUN_OUT) {
            checkRunOut();
        }
        if (tick < SAWDUST_DONE) {
            emptySawdustPlanks();
        }
        if (tick == LAST) {
            checkProcesses();
            checkResumed();
            checkPipeDrain();
        }
        if (tick == SAWDUST_DONE) {
            checkSawdustRate();
            finished = true;
        }
    }

    // --- Building ----------------------------------------------------------------------------

    private static void build() {
        checkRegistration();
        checkRecipesLoaded();

        // Furnaces: the same smelt at two tiers, one that will run dry, one to save and load, one for caps.
        slowFurnace = furnace(1, Y, 1, Tier.COPPER, 4_000, new ItemStack(Items.RAW_IRON));
        fastFurnace = furnace(3, Y, 1, Tier.NETHERITE, 4_000, new ItemStack(Items.RAW_IRON));
        hungryFurnace = furnace(5, Y, 1, Tier.COPPER, 50, new ItemStack(Items.RAW_IRON, 4));
        persistedFurnace = furnace(9, Y, 1, Tier.COPPER, 1_234, new ItemStack(Items.RAW_IRON, 3));
        slowStartFlux = flux(slowFurnace);
        fastStartFlux = flux(fastFurnace);
        MachineConfigActions.apply(machine(persistedFurnace), SideCommand.set(SideResource.ITEMS, Face.NORTH, ConnectionMode.EXPORT));
        MachineConfigActions.apply(machine(persistedFurnace), SideCommand.set(SideResource.FLUID, Face.WEST, ConnectionMode.BLOCKED));
        checkFaceRules(furnace(7, Y, 1, Tier.COPPER, 0, ItemStack.EMPTY));
        checkMenus(furnace(11, Y, 1, Tier.FLUXIUM, 70_000, ItemStack.EMPTY));

        // The other seven, at the top tier so they finish quickly.
        grinder = placed(1, Y, 3, MachineType.GRINDER, 5_000, FluxType.AERO, new ItemStack(Items.IRON_ORE));
        gravelGrinder = placed(3, Y, 3, MachineType.GRINDER, 5_000, FluxType.AERO, new ItemStack(Items.GRAVEL, 12));
        sawmill = placed(5, Y, 3, MachineType.SAWMILL, 5_000, FluxType.AERO, new ItemStack(Items.OAK_LOG, 2));
        stemSawmill = placed(1, Y, 9, MachineType.SAWMILL, 5_000, FluxType.AERO, new ItemStack(Items.CRIMSON_STEM));
        plankSawmill = placed(3, Y, 9, MachineType.SAWMILL, 5_000, FluxType.AERO, new ItemStack(Items.OAK_PLANKS));
        sawdustSawmill = placed(5, Y, 9, MachineType.SAWMILL, 5_000, FluxType.AERO, new ItemStack(Items.OAK_LOG, SAWDUST_LOGS));
        ((RecipeMachineBlockEntity) machine(sawdustSawmill)).seedRolls(SAWDUST_SEED);
        sawdustPlanks = 0;
        alloy = placed(7, Y, 3, MachineType.ALLOY_SMELTER, 5_000, FluxType.AERO,
                new ItemStack(Items.IRON_INGOT), new ItemStack(ModItems.FLUX_DUST.get()));
        // Out of order on purpose: the recipe does not care which slot holds what.
        bulkAlloy = placed(9, Y, 3, MachineType.ALLOY_SMELTER, 5_000, FluxType.AERO,
                new ItemStack(Items.REDSTONE), new ItemStack(ModItems.FLUX_DUST.get(), 2), new ItemStack(Items.IRON_INGOT, 3));
        infuser = placed(11, Y, 3, MachineType.INFUSER, 5_000, FluxType.MOON,
                new ItemStack(Items.QUARTZ), new ItemStack(ModItems.FLUX_DUST.get()));
        shortInfuser = placed(13, Y, 3, MachineType.INFUSER, 160, FluxType.MOON,
                new ItemStack(Items.AMETHYST_SHARD), new ItemStack(ModItems.FLUX_DUST.get()));
        infuserStartFlux = flux(infuser);

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        enchanter = placed(1, Y, 5, MachineType.ENCHANTER, 10_000, FluxType.MOON,
                sword.copy(), book(Enchantments.SHARPNESS, 3, Enchantments.VANISHING_CURSE, 1, Enchantments.MENDING, 1),
                new ItemStack(Items.LAPIS_LAZULI, 10));
        enchanterStartFlux = flux(enchanter);
        ItemStack smiteSword = new ItemStack(Items.IRON_SWORD);
        smiteSword.enchant(Enchantments.SMITE, 2);
        clashEnchanter = placed(3, Y, 5, MachineType.ENCHANTER, 10_000, FluxType.MOON,
                smiteSword, book(Enchantments.SHARPNESS, 3), new ItemStack(Items.LAPIS_LAZULI, 10));
        poorEnchanter = placed(5, Y, 5, MachineType.ENCHANTER, 10_000, FluxType.MOON,
                new ItemStack(Items.IRON_SWORD), book(Enchantments.SHARPNESS, 3), new ItemStack(Items.LAPIS_LAZULI, 1));

        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.enchant(Enchantments.SHARPNESS, 3);
        worn.enchant(Enchantments.VANISHING_CURSE, 1);
        worn.setDamageValue(5);
        disenchanter = placed(7, Y, 5, MachineType.DISENCHANTER, 10_000, FluxType.MOON, worn, new ItemStack(Items.BOOK));
        disenchanterStartFlux = flux(disenchanter);

        aeroGrower = placed(9, Y, 5, MachineType.PLANT_GROWER, 10_000, FluxType.AERO, new ItemStack(Items.WHEAT_SEEDS));
        wrongFluxGrower = placed(11, Y, 5, MachineType.PLANT_GROWER, 10_000, FluxType.AERO, new ItemStack(Items.RED_MUSHROOM));
        moonGrower = placed(13, Y, 5, MachineType.PLANT_GROWER, 10_000, FluxType.MOON, new ItemStack(Items.RED_MUSHROOM));

        // Pipes: a tank feeding a furnace through a fluid pipe, and a furnace the pipe tries to drain.
        pipeFedTank = tank(1, PIPE_Y, 2, 1_000);
        bundle(2, PIPE_Y, 2, Face.WEST, ConnectionMode.IMPORT, Face.EAST, ConnectionMode.EXPORT);
        pipeFedFurnace = furnace(3, PIPE_Y, 2, Tier.COPPER, 0, ItemStack.EMPTY);
        refresh(new BlockPos(2, PIPE_Y, 2));

        drainedFurnace = furnace(1, PIPE_Y, 6, Tier.COPPER, 1_000, ItemStack.EMPTY);
        bundle(2, PIPE_Y, 6, Face.WEST, ConnectionMode.IMPORT, Face.EAST, ConnectionMode.EXPORT);
        drainedTank = tank(3, PIPE_Y, 6, 0);
        refresh(new BlockPos(2, PIPE_Y, 6));
    }

    // --- Registration and data ---------------------------------------------------------------

    private static void checkRegistration() {
        boolean names = true;
        boolean items = true;
        boolean drops = true;
        for (MachineType type : MachineType.values()) {
            for (Tier tier : Tier.values()) {
                Block block = ModBlocks.machine(type, tier).get();
                String expected = tier == Tier.COPPER ? type.blockId() : tier.id() + "_" + type.blockId();
                names &= ForgeRegistries.BLOCKS.getKey(block).getPath().equals(expected);
                items &= block.asItem() != Items.AIR && ForgeRegistries.ITEMS.getKey(block.asItem()).getPath().equals(expected);
            }
            BlockPos pos = new BlockPos(15 - type.ordinal() / 14, Y + 10, 1 + type.ordinal() % 14);
            BlockState state = ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_ALL);
            drops &= Block.getDrops(state, level, pos, level.getBlockEntity(pos)).stream()
                    .anyMatch(stack -> stack.is(state.getBlock().asItem()));
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        SelfTest.check("machines: every machine and tier has its block under the delivered art's name", names);
        SelfTest.check("machines: every machine block has an item", items);
        SelfTest.check("machines: every machine block drops itself", drops);
        SelfTest.check("items: sawdust is registered as aeroflux:sawdust",
                ModItems.SAWDUST.get() != Items.AIR && "sawdust".equals(ForgeRegistries.ITEMS.getKey(ModItems.SAWDUST.get()).getPath()));
        SelfTest.check("machines: the art's ids are the first-tier blocks",
                "flux_furnace".equals(ForgeRegistries.BLOCKS.getKey(ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get()).getPath())
                        && "plant_grower".equals(ForgeRegistries.BLOCKS.getKey(ModBlocks.machine(MachineType.PLANT_GROWER, Tier.COPPER).get()).getPath())
                        && "alloy_smelter".equals(ForgeRegistries.BLOCKS.getKey(ModBlocks.machine(MachineType.ALLOY_SMELTER, Tier.COPPER).get()).getPath()));
    }

    private static void checkRecipesLoaded() {
        boolean all = true;
        for (MachineType type : MachineType.values()) {
            if (type.hasOwnRecipes()) {
                all &= !level.getRecipeManager().getAllRecipesFor(ModRecipes.type(type).get()).isEmpty();
            }
        }
        SelfTest.check("recipes: every recipe machine has data-driven recipes of its own type", all);
        SelfTest.check("recipes: vanilla smelting recipes are there for the furnace",
                !level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).isEmpty());

        // Ten wood types, bamboo and planks; each has a chance of sawdust on the side and nothing else.
        List<FluxRecipe> sawmillRecipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.type(MachineType.SAWMILL).get());
        boolean sawdustOnly = !sawmillRecipes.isEmpty();
        for (FluxRecipe recipe : sawmillRecipes) {
            List<FluxRecipe.Output> outputs = recipe.outputs();
            sawdustOnly &= outputs.size() == 2 && outputs.get(1).stack().is(ModItems.SAWDUST.get()) && outputs.get(1).chance() < 1.0F;
        }
        SelfTest.check("recipes: the sawmill has its twelve recipes", sawmillRecipes.size() == 12);
        SelfTest.check("recipes: every sawmill byproduct is a chance of sawdust", sawdustOnly);

        // The network format must carry a recipe unchanged, or clients would see different recipes.
        FluxRecipe any = level.getRecipeManager().getAllRecipesFor(ModRecipes.type(MachineType.ALLOY_SMELTER).get()).stream()
                .filter(recipe -> recipe.inputs().size() == 3).findFirst().orElseThrow();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RecipeSerializer<FluxRecipe> serializer = ModRecipes.serializer(MachineType.ALLOY_SMELTER).get();
        serializer.toNetwork(buf, any);
        FluxRecipe copy = serializer.fromNetwork(any.getId(), buf);
        SelfTest.check("recipes: a recipe survives the network round trip",
                copy.inputs().size() == 3 && copy.outputs().size() == any.outputs().size()
                        && copy.time() == any.time() && copy.flux() == any.flux()
                        && copy.outputs().get(0).stack().getCount() == any.outputs().get(0).stack().getCount());
    }

    // --- Furnace: speed, running dry, flux types ---------------------------------------------

    private static void checkTierSpeed() {
        BlockPos slow = slowFurnace;
        BlockPos fast = fastFurnace;
        int fastTicks = MachineMath.ticksFor(200, Tier.NETHERITE);
        SelfTest.check("furnace: a netherite furnace smelted raw iron into an ingot using only Flux",
                output(fast, 0).is(Items.IRON_INGOT) && output(fast, 0).getCount() == 1
                        && input(fast, 0).isEmpty());
        SelfTest.check("furnace: it paid exactly ticks times the tier's cost",
                fastStartFlux - flux(fast) == fastTicks * MachineMath.fluxPerTick(Tier.NETHERITE));
        SelfTest.check("furnace: a copper furnace is still working at the same time (tier speed differs)",
                output(slow, 0).isEmpty() && machine(slow).progress() > fastTicks && machine(slow).status() == MachineStatus.RUNNING);
        SelfTest.check("furnace: a running machine reports its total time from the tier",
                machine(slow).totalTime() == MachineMath.ticksFor(200, Tier.COPPER));
    }

    private static void checkRunOut() {
        FluxMachineBlockEntity hungry = machine(hungryFurnace);
        SelfTest.check("furnace: with only 50 mB it burned it all and made no ingot",
                flux(hungryFurnace) == 0 && output(hungryFurnace, 0).isEmpty());
        SelfTest.check("furnace: it stopped at the progress the Flux paid for", hungry.progress() == 25);
        SelfTest.check("furnace: it reports that it is out of Flux and keeps its input",
                hungry.status() == MachineStatus.NO_FLUX && input(hungryFurnace, 0).getCount() == 4);
        hungry.fluxTank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), 2_000));
    }

    private static void checkResumed() {
        SelfTest.check("furnace: after a refill it carried on and finished the ingot",
                output(hungryFurnace, 0).is(Items.IRON_INGOT) && output(hungryFurnace, 0).getCount() >= 1);
        SelfTest.check("furnace: a finished copper smelt cost exactly 200 mB",
                flux(slowFurnace) == slowStartFlux - 200 && output(slowFurnace, 0).getCount() == 1);
        SelfTest.check("furnace: an idle machine uses no Flux after finishing",
                machine(slowFurnace).status() == MachineStatus.IDLE);
    }

    // --- Face rules --------------------------------------------------------------------------

    /** Item and fluid access through the real capability API at each face. */
    private static void checkFaceRules(BlockPos pos) {
        FluxMachineBlockEntity furnace = machine(pos);
        ItemStack ore = new ItemStack(Items.RAW_IRON, 4);
        IItemHandler top = items(pos, Direction.UP);
        IItemHandler bottom = items(pos, Direction.DOWN);
        IItemHandler side = items(pos, Direction.NORTH);

        SelfTest.check("faces: the top (import) takes a smeltable item", top.insertItem(0, ore, true).isEmpty());
        SelfTest.check("faces: a side set to both takes it too", side.insertItem(0, ore, true).isEmpty());
        SelfTest.check("faces: the bottom (export) refuses input", bottom.insertItem(0, ore, true).getCount() == ore.getCount());
        SelfTest.check("faces: the input slot refuses an item with no smelting recipe",
                top.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1);
        SelfTest.check("faces: an item that does smelt is valid there", top.isItemValid(0, ore) && !top.isItemValid(0, new ItemStack(Items.DIRT)));
        SelfTest.check("faces: the output slot never takes items from outside", side.insertItem(1, ore, true).getCount() == ore.getCount());

        top.insertItem(0, new ItemStack(Items.RAW_IRON, 2), false);
        furnace.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 3));
        SelfTest.check("faces: an import-only face cannot take products", top.extractItem(1, 3, false).isEmpty());
        SelfTest.check("faces: an input slot cannot be emptied from outside, even on a both face",
                side.extractItem(0, 2, true).isEmpty() && bottom.extractItem(0, 2, true).isEmpty());
        SelfTest.check("faces: the bottom (export) hands out the products", bottom.extractItem(1, 2, false).getCount() == 2);
        SelfTest.check("faces: a side set to both hands them out too", side.extractItem(1, 5, false).getCount() == 1);

        // Flux: only Aero goes in, never Moon or water, and the mode decides which way.
        IFluidHandler fluid = fluid(pos, Direction.UP);
        // Half the copper tank, so there is room left for the "both" face to fill below.
        SelfTest.check("faces: the furnace takes Aero Flux", fluid.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 500), IFluidHandler.FluidAction.EXECUTE) == 500);
        SelfTest.check("faces: it refuses Moon Flux", fluid.fill(new FluidStack(ModFluids.MOON_FLUX.get(), 1_000), IFluidHandler.FluidAction.SIMULATE) == 0);
        SelfTest.check("faces: it refuses water", fluid.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1_000), IFluidHandler.FluidAction.SIMULATE) == 0);
        SelfTest.check("faces: the fluid tank reports it is a Flux tank only",
                fluid.isFluidValid(0, new FluidStack(ModFluids.AERO_FLUX.get(), 1)) && !fluid.isFluidValid(0, new FluidStack(ModFluids.MOON_FLUX.get(), 1)));
        SelfTest.check("faces: an import-only fluid face cannot be drained",
                fluid.drain(500, IFluidHandler.FluidAction.SIMULATE).isEmpty());

        MachineConfigActions.apply(furnace, SideCommand.set(SideResource.FLUID, Face.SOUTH, ConnectionMode.EXPORT));
        IFluidHandler out = fluid(pos, Direction.SOUTH);
        SelfTest.check("faces: an export fluid face can be drained but not filled",
                out.drain(500, IFluidHandler.FluidAction.SIMULATE).getAmount() == 500
                        && out.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 100), IFluidHandler.FluidAction.SIMULATE) == 0);
        MachineConfigActions.apply(furnace, SideCommand.set(SideResource.FLUID, Face.SOUTH, ConnectionMode.OPEN));
        SelfTest.check("faces: a face set to both fills and drains",
                fluid(pos, Direction.SOUTH).fill(new FluidStack(ModFluids.AERO_FLUX.get(), 100), IFluidHandler.FluidAction.SIMULATE) == 100
                        && fluid(pos, Direction.SOUTH).drain(100, IFluidHandler.FluidAction.SIMULATE).getAmount() == 100);

        MachineConfigActions.apply(furnace, SideCommand.set(SideResource.ITEMS, Face.DOWN, ConnectionMode.OFF));
        SelfTest.check("faces: an off face still shows the machine but lets nothing through",
                items(pos, Direction.DOWN) != null && items(pos, Direction.DOWN).extractItem(1, 1, true).isEmpty());
        MachineConfigActions.apply(furnace, SideCommand.set(SideResource.ITEMS, Face.DOWN, ConnectionMode.BLOCKED));
        SelfTest.check("faces: a blocked face hides the item capability completely",
                items(pos, Direction.DOWN) == null);
        MachineConfigActions.apply(furnace, SideCommand.set(SideResource.FLUID, Face.WEST, ConnectionMode.BLOCKED));
        SelfTest.check("faces: a blocked face hides the fluid capability completely",
                fluid(pos, Direction.WEST) == null
                        && fluid(pos, Direction.EAST) != null);
        SelfTest.check("faces: a query without a side (a player's bucket) always works",
                items(pos, null) != null);

        SelfTest.check("faces: a command that changes nothing reports no change",
                !MachineConfigActions.apply(furnace, SideCommand.set(SideResource.ITEMS, Face.DOWN, ConnectionMode.BLOCKED)));
        SelfTest.check("faces: a resource the machine does not use cannot be set",
                !MachineConfigActions.apply(furnace, SideCommand.allTo(SideResource.ENERGY, ConnectionMode.OPEN)));
        SelfTest.check("faces: a number that is no command does nothing", !MachineConfigActions.applyButton(furnace, 9_999));
        MachineConfigActions.apply(furnace, SideCommand.reset(SideResource.ITEMS));
        SelfTest.check("faces: reset brings the blocked face back",
                items(pos, Direction.DOWN) != null);

        // Which machines take which fluid.
        boolean flux = true;
        for (MachineType type : MachineType.values()) {
            BlockPos at = new BlockPos(14 - type.ordinal() / 14, Y + 12, 1 + type.ordinal() % 14);
            level.setBlock(at, ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
            FluxMachineBlockEntity machine = machine(at);
            flux &= machine.acceptsFlux(new FluidStack(ModFluids.AERO_FLUX.get(), 1)) == type.acceptedFlux().contains(FluxType.AERO);
            flux &= machine.acceptsFlux(new FluidStack(ModFluids.MOON_FLUX.get(), 1)) == type.acceptedFlux().contains(FluxType.MOON);
            flux &= !machine.acceptsFlux(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1));
            level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        SelfTest.check("faces: every machine accepts exactly the flux the plan gives it", flux);
    }

    // --- Pipes -------------------------------------------------------------------------------

    private static void checkPipeFed() {
        int furnace = flux(pipeFedFurnace);
        int tank = ((FluxTankBlockEntity) level.getBlockEntity(pipeFedTank)).tank().getFluidAmount();
        SelfTest.check("pipes: Flux arrived in the furnace from a tank through a fluid pipe", furnace > 0);
        SelfTest.check("pipes: nothing was lost or made on the way", furnace + tank == 1_000);
        SelfTest.check("pipes: the furnace's fluid cap was the one the pipe attached to",
                fluid(pipeFedFurnace, Direction.WEST) != null);
    }

    /** At tick 20 the pipe has had time to pull, but the furnace's import-only face must have refused. */
    private static void checkDrainedFaceStillClosed() {
        int tank = ((FluxTankBlockEntity) level.getBlockEntity(drainedTank)).tank().getFluidAmount();
        SelfTest.check("pipes: a pipe cannot pull Flux out of an import-only face", tank == 0 && flux(drainedFurnace) == 1_000);
        // Opening the face to export must take effect at once.
        MachineConfigActions.apply(machine(drainedFurnace), SideCommand.set(SideResource.FLUID, Face.EAST, ConnectionMode.EXPORT));
    }

    private static void checkPipeDrain() {
        int tank = ((FluxTankBlockEntity) level.getBlockEntity(drainedTank)).tank().getFluidAmount();
        SelfTest.check("pipes: after the face was set to export the pipe drained the furnace", tank > 0 && tank + flux(drainedFurnace) == 1_000);
    }

    // --- Saving ------------------------------------------------------------------------------

    private static void checkPersistence() {
        FluxMachineBlockEntity original = machine(persistedFurnace);
        persistedProgress = original.progress();
        persistedFlux = flux(persistedFurnace);
        // The furnace is mid-smelt: the saved data must carry that, not just the settings.
        persistedTag = original.saveWithFullMetadata();
        BlockState state = level.getBlockState(persistedFurnace);
        FluxMachineBlockEntity restored = MachineFactory.create(persistedFurnace, state);
        restored.load(persistedTag);

        SelfTest.check("persistence: a machine in the middle of a job has progress to save", persistedProgress > 0);
        SelfTest.check("persistence: progress and total time survive saving and loading",
                restored.progress() == persistedProgress && restored.totalTime() == original.totalTime());
        SelfTest.check("persistence: the Flux survives", restored.fluxTank().getFluidAmount() == persistedFlux
                && restored.fluxTank().getFluid().getFluid().isSame(ModFluids.AERO_FLUX.get()));
        SelfTest.check("persistence: the items survive", ItemStack.matches(restored.inventory().getStackInSlot(0), original.inventory().getStackInSlot(0))
                && restored.inventory().getStackInSlot(0).getCount() == 3);
        SelfTest.check("persistence: the face settings survive",
                restored.sides().equals(original.sides())
                        && restored.sides().mode(SideResource.ITEMS, Face.NORTH) == ConnectionMode.EXPORT
                        && restored.sides().mode(SideResource.FLUID, Face.WEST) == ConnectionMode.BLOCKED);
        SelfTest.check("persistence: the saved data carries a schema version", persistedTag.getInt("schema_version") == 2);

        // A damaged save must not crash the load or leave a broken machine.
        CompoundTag damaged = persistedTag.copy();
        damaged.putInt("progress", 999_999);
        damaged.putInt("total", 10);
        damaged.getCompound("sides").putInt("items", -5);
        FluxMachineBlockEntity patched = MachineFactory.create(persistedFurnace, state);
        patched.load(damaged);
        SelfTest.check("persistence: damaged numbers are clamped instead of crashing", patched.progress() <= patched.totalTime());
    }

    // --- Every machine finishes a job --------------------------------------------------------

    private static void checkProcesses() {
        int fastTicks = MachineMath.ticksFor(200, Tier.FLUXIUM);

        SelfTest.check("grinder: iron ore became two raw iron", output(grinder, 0).is(Items.RAW_IRON) && output(grinder, 0).getCount() == 2);
        int sand = output(gravelGrinder, 0).is(Items.SAND) ? output(gravelGrinder, 0).getCount() : 0;
        int flint = output(gravelGrinder, 1).is(Items.FLINT) ? output(gravelGrinder, 1).getCount() : 0;
        SelfTest.check("grinder: every ground gravel gave sand", input(gravelGrinder, 0).isEmpty() && sand == 12);
        SelfTest.check("grinder: the byproduct dropped by chance, never more than one per job", flint >= 0 && flint <= 12);

        SelfTest.check("sawmill: two oak logs became twelve oak planks", output(sawmill, 0).is(Items.OAK_PLANKS) && output(sawmill, 0).getCount() == 12);
        SelfTest.check("sawmill: sawdust only came as the byproduct, at most one per log",
                output(sawmill, 1).isEmpty() || (output(sawmill, 1).is(ModItems.SAWDUST.get()) && output(sawmill, 1).getCount() <= 2));
        SelfTest.check("sawmill: a crimson stem became six crimson planks",
                output(stemSawmill, 0).is(Items.CRIMSON_PLANKS) && output(stemSawmill, 0).getCount() == 6 && input(stemSawmill, 0).isEmpty());
        SelfTest.check("sawmill: a plank became two sticks",
                output(plankSawmill, 0).is(Items.STICK) && output(plankSawmill, 0).getCount() == 2 && input(plankSawmill, 0).isEmpty());

        SelfTest.check("alloy smelter: iron and Flux Dust made Fluxsteel", output(alloy, 0).is(ModItems.FLUXSTEEL_INGOT.get()) && output(alloy, 0).getCount() == 1);
        SelfTest.check("alloy smelter: a three-ingredient recipe works with the slots in any order",
                output(bulkAlloy, 0).is(ModItems.FLUXSTEEL_INGOT.get()) && output(bulkAlloy, 0).getCount() == 4
                        && input(bulkAlloy, 0).isEmpty() && input(bulkAlloy, 1).isEmpty() && input(bulkAlloy, 2).isEmpty());

        SelfTest.check("infuser: quartz and Flux Dust became an Aero Crystal", output(infuser, 0).is(ModItems.AERO_CRYSTAL.get()));
        SelfTest.check("infuser: the recipe's extra Moon Flux was taken on top of the running cost",
                infuserStartFlux - flux(infuser) == fastTicks * MachineMath.fluxPerTick(Tier.FLUXIUM) + 500);
        SelfTest.check("infuser: with too little Flux for the extra cost it waits and makes nothing",
                output(shortInfuser, 0).isEmpty() && machine(shortInfuser).status() == MachineStatus.NO_FLUX
                        && input(shortInfuser, 0).is(Items.AMETHYST_SHARD));

        checkEnchanter();
        checkDisenchanter();
        checkGrower(fastTicks);
    }

    private static void checkEnchanter() {
        ItemStack result = output(enchanter, 0);
        int total = 3 + 1;
        SelfTest.check("enchanter: the sword came out with Sharpness III from the book",
                result.is(Items.IRON_SWORD) && level(result, Enchantments.SHARPNESS) == 3);
        SelfTest.check("enchanter: a treasure enchantment (Mending) is applied by default", level(result, Enchantments.MENDING) == 1);
        SelfTest.check("enchanter: the curse on the book was refused", level(result, Enchantments.VANISHING_CURSE) == 0);
        SelfTest.check("enchanter: it used one lapis per level applied and the book",
                input(enchanter, 2).getCount() == 10 - EnchantRules.lapisCost(total) && input(enchanter, 1).isEmpty() && input(enchanter, 0).isEmpty());
        int ticks = MachineMath.ticksFor(EnchantRules.baseTicks(total), Tier.FLUXIUM);
        SelfTest.check("enchanter: it took Moon Flux for the running time and per level",
                enchanterStartFlux - flux(enchanter) == ticks * MachineMath.fluxPerTick(Tier.FLUXIUM) + EnchantRules.enchantFlux(total));
        SelfTest.check("enchanter: Sharpness will not go on a sword that has Smite",
                output(clashEnchanter, 0).isEmpty() && input(clashEnchanter, 1).is(Items.ENCHANTED_BOOK)
                        && input(clashEnchanter, 2).getCount() == 10 && flux(clashEnchanter) == 10_000);
        SelfTest.check("enchanter: it waits when there is not enough lapis",
                output(poorEnchanter, 0).isEmpty() && input(poorEnchanter, 2).getCount() == 1 && flux(poorEnchanter) == 10_000);
    }

    private static void checkDisenchanter() {
        ItemStack stripped = output(disenchanter, 0);
        ItemStack book = output(disenchanter, 1);
        SelfTest.check("disenchanter: the item survived and kept its damage", stripped.is(Items.IRON_SWORD) && stripped.getDamageValue() == 5);
        SelfTest.check("disenchanter: Sharpness was taken off the item", level(stripped, Enchantments.SHARPNESS) == 0);
        SelfTest.check("disenchanter: the curse stayed on the item", level(stripped, Enchantments.VANISHING_CURSE) == 1);
        SelfTest.check("disenchanter: the enchantment went onto an enchanted book",
                book.is(Items.ENCHANTED_BOOK) && level(book, Enchantments.SHARPNESS) == 3
                        && level(book, Enchantments.VANISHING_CURSE) == 0);
        SelfTest.check("disenchanter: the plain book was used up", input(disenchanter, 1).isEmpty() && input(disenchanter, 0).isEmpty());
        int ticks = MachineMath.ticksFor(EnchantRules.baseTicks(3), Tier.FLUXIUM);
        SelfTest.check("disenchanter: it took Moon Flux for the running time and per level",
                disenchanterStartFlux - flux(disenchanter) == ticks * MachineMath.fluxPerTick(Tier.FLUXIUM) + EnchantRules.disenchantFlux(3));
    }

    private static void checkGrower(int fastTicks) {
        SelfTest.check("plant grower: seeds grew into wheat and the seeds can come back",
                output(aeroGrower, 0).is(Items.WHEAT) && output(aeroGrower, 0).getCount() == 2
                        && (output(aeroGrower, 1).isEmpty() || output(aeroGrower, 1).is(Items.WHEAT_SEEDS)));
        SelfTest.check("plant grower: a fungus recipe needs Moon Flux and waits on Aero Flux",
                output(wrongFluxGrower, 0).isEmpty() && machine(wrongFluxGrower).status() == MachineStatus.NO_FLUX
                        && flux(wrongFluxGrower) == 10_000);
        SelfTest.check("plant grower: on Moon Flux the mushrooms grew into a bigger harvest",
                output(moonGrower, 0).is(Items.RED_MUSHROOM) && output(moonGrower, 0).getCount() == 3);
    }

    // --- Sawdust over many jobs --------------------------------------------------------------

    /** Takes the planks out of the sawdust sawmill each tick, as a pipe would, so it never stops for a full slot. */
    private static void emptySawdustPlanks() {
        ItemStack planks = output(sawdustSawmill, 0);
        if (!planks.isEmpty()) {
            sawdustPlanks += planks.getCount();
            machine(sawdustSawmill).inventory().setStackInSlot(MachineType.SAWMILL.inputSlots(), ItemStack.EMPTY);
        }
    }

    /**
     * The sawdust sawmill rolls from a fixed seed, so its count is the same on every run. The
     * bound is still a fair one for a one-in-four chance (8 expected from 32 logs), so a new seed
     * or a change in the order of the rolls cannot make this fail by bad luck.
     */
    private static void checkSawdustRate() {
        ItemStack sawdust = output(sawdustSawmill, 1);
        int count = sawdust.is(ModItems.SAWDUST.get()) ? sawdust.getCount() : 0;
        AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO sawdust={} from {} logs, planks={}", count, SAWDUST_LOGS, sawdustPlanks);
        SelfTest.check("sawmill: every log of the long run was cut into six planks",
                input(sawdustSawmill, 0).isEmpty() && sawdustPlanks == SAWDUST_LOGS * 6);
        SelfTest.check("sawmill: sawdust dropped at a plausible rate for its one-in-four chance",
                (sawdust.isEmpty() || sawdust.is(ModItems.SAWDUST.get())) && count >= 2 && count <= 16);
    }

    // --- Menus -------------------------------------------------------------------------------

    private static void checkMenus(BlockPos furnacePos) {
        FluxMachineBlockEntity furnace = machine(furnacePos);
        player.closeContainer();
        player.setPos(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() - 1.5);

        MachineMenu server = new MachineMenu(1, player.getInventory(), furnace);
        SelfTest.check("menu: a menu is valid for a player standing next to the machine", server.stillValid(player));
        player.setPos(furnacePos.getX() + 0.5, furnacePos.getY() + 30, furnacePos.getZ() + 0.5);
        SelfTest.check("menu: it stops being valid when the player walks away", !server.stillValid(player));
        player.setPos(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() - 1.5);

        MachineLayout layout = MachineLayout.of(1, 1);
        SelfTest.check("menu: the slots sit where the layout says",
                server.slots.size() == 2 + MachineUpgrades.slots(Tier.FLUXIUM) + 36
                        && server.getSlot(0).x == layout.inputSlot(0).x() && server.getSlot(0).y == layout.inputSlot(0).y()
                        && server.getSlot(1).x == layout.outputSlot(0).x() && server.getSlot(1).y == layout.outputSlot(0).y());

        // The client copy is built from what the server writes, and fed only what a synchronizer would send,
        // truncated to 16 bits per value the way the network does.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        furnace.writeOpenData(buf);
        MachineMenu client = MachineMenu.fromNetwork(1, player.getInventory(), buf);
        SelfTest.check("menu: the client copy knows the machine, tier, facing and position",
                client.machine() == MachineType.FURNACE && client.tier() == Tier.FLUXIUM && client.pos().equals(furnacePos)
                        && client.facing() == furnace.facing());
        server.setSynchronizer(new Mirror(client));
        server.broadcastFullState();
        SelfTest.check("menu: Flux beyond 16 bits arrives whole (70,000 mB)", client.fluxAmount() == 70_000);
        SelfTest.check("menu: the client knows the tank's size and flux type",
                client.fluxCapacity() == MachineMath.inputCapacity(Tier.FLUXIUM) && client.fluxType() == FluxType.AERO);

        furnace.inventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 8));
        furnace.fluxTank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), 100_001));
        FluxMachineBlockEntity.serverTick(level, furnacePos, level.getBlockState(furnacePos), furnace);
        server.broadcastChanges();
        SelfTest.check("menu: progress, total time and status follow the machine",
                client.progress() == furnace.progress() && client.totalTime() == furnace.totalTime() && client.progress() > 0
                        && client.status() == MachineStatus.RUNNING);
        SelfTest.check("menu: a changed tank is sent again", client.fluxAmount() == furnace.fluxTank().getFluidAmount());
        SelfTest.check("menu: the client's item slots show the server's",
                client.getSlot(0).getItem().is(Items.RAW_IRON) && client.getSlot(0).getItem().getCount() == 8);

        // Side settings travel as menu buttons and come back through the same data.
        int before = server.slots.size();
        SelfTest.check("menu: a side-config click from the screen changes the machine",
                server.clickMenuButton(player, SideCommand.set(SideResource.ITEMS, Face.EAST, ConnectionMode.EXPORT).encode())
                        && furnace.sides().mode(SideResource.ITEMS, Face.EAST) == ConnectionMode.EXPORT);
        server.broadcastChanges();
        SelfTest.check("menu: the client sees the new mode", client.mode(SideResource.ITEMS, Face.EAST) == ConnectionMode.EXPORT);
        SelfTest.check("menu: a number that is not a command is refused", !server.clickMenuButton(player, 31_337));
        SelfTest.check("menu: a command for a resource the machine lacks is refused",
                !server.clickMenuButton(player, SideCommand.allTo(SideResource.GAS, ConnectionMode.OPEN).encode()));
        SelfTest.check("menu: the client copy cannot change a machine (it has none)",
                !client.clickMenuButton(player, SideCommand.allTo(SideResource.ITEMS, ConnectionMode.OFF).encode())
                        && furnace.sides().mode(SideResource.ITEMS, Face.EAST) == ConnectionMode.EXPORT);

        // Slots: only smeltable items may be placed, and products can only be taken.
        SelfTest.check("menu: an input slot takes smeltable items only",
                server.getSlot(0).mayPlace(new ItemStack(Items.RAW_IRON)) && !server.getSlot(0).mayPlace(new ItemStack(Items.DIRT))
                        && client.getSlot(0).mayPlace(new ItemStack(Items.RAW_IRON)) && !client.getSlot(0).mayPlace(new ItemStack(Items.DIRT)));
        SelfTest.check("menu: an output slot takes nothing from the player", !server.getSlot(1).mayPlace(new ItemStack(Items.IRON_INGOT)));
        furnace.inventory().setStackInSlot(0, ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.RAW_COPPER, 5));
        server.quickMoveStack(player, before - 36 + 27);
        SelfTest.check("menu: shift-click sends smeltable items into the input slot",
                furnace.inventory().getStackInSlot(0).is(Items.RAW_COPPER) && furnace.inventory().getStackInSlot(0).getCount() == 5);
        furnace.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 3));
        server.quickMoveStack(player, 1);
        SelfTest.check("menu: shift-click on a product moves it to the player",
                furnace.inventory().getStackInSlot(1).isEmpty() && player.getInventory().countItem(Items.IRON_INGOT) == 3);
        player.getInventory().clearContent();

        level.setBlock(furnacePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        SelfTest.check("menu: it is no longer valid once the machine is gone", !server.stillValid(player));

        // One menu per machine type has the right slots and a sensible layout.
        boolean slots = true;
        for (MachineType type : MachineType.values()) {
            // Seven to a row, in a layer of their own, so every machine fits in the loaded chunk.
            BlockPos at = new BlockPos(1 + (type.ordinal() % 7) * 2, Y + 8, 7 + (type.ordinal() / 7) * 2);
            level.setBlock(at, ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
            player.setPos(at.getX() + 0.5, at.getY(), at.getZ() - 1.5);
            MachineMenu menu = new MachineMenu(2, player.getInventory(), machine(at));
            slots &= menu.slots.size() == type.inputSlots() + type.outputSlots() + MachineUpgrades.slots(Tier.COPPER) + 36 && menu.stillValid(player)
                    && menu.machine() == type;
        }
        SelfTest.check("menu: every machine opens a menu with its own slot counts", slots);
        player.closeContainer();
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos furnace(int x, int y, int z, Tier tier, int flux, ItemStack input) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.machine(MachineType.FURNACE, tier).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = machine(pos);
        if (flux > 0) {
            machine.fluxTank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), flux));
        }
        machine.inventory().setStackInSlot(0, input);
        return pos;
    }

    /** A top-tier machine filled with one kind of flux and the given input stacks, in slot order. */
    private static BlockPos placed(int x, int y, int z, MachineType type, int flux, FluxType fluxType, ItemStack... inputs) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.machine(type, Tier.FLUXIUM).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = machine(pos);
        machine.fluxTank().setFluid(new FluidStack(ModFluids.fluidOf(fluxType), flux));
        for (int i = 0; i < inputs.length; i++) {
            machine.inventory().setStackInSlot(i, inputs[i]);
        }
        return pos;
    }

    private static BlockPos tank(int x, int y, int z, int amount) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        if (amount > 0) {
            ((FluxTankBlockEntity) level.getBlockEntity(pos)).tank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), amount));
        }
        return pos;
    }

    private static void bundle(int x, int y, int z, Face firstFace, ConnectionMode firstMode, Face secondFace, ConnectionMode secondMode) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        ConduitBundleBlockEntity entity = (ConduitBundleBlockEntity) level.getBlockEntity(pos);
        entity.addPipe(PipeType.FLUID, Tier.COPPER);
        entity.bundle().setMode(PipeType.FLUID.lane(), firstFace, firstMode);
        entity.bundle().setMode(PipeType.FLUID.lane(), secondFace, secondMode);
    }

    private static void refresh(BlockPos pos) {
        ConduitBundleBlockEntity entity = (ConduitBundleBlockEntity) level.getBlockEntity(pos);
        entity.refreshConnections();
        entity.changed();
    }

    private static FluxMachineBlockEntity machine(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static int flux(BlockPos pos) {
        return machine(pos).fluxTank().getFluidAmount();
    }

    private static ItemStack input(BlockPos pos, int index) {
        return machine(pos).inventory().getStackInSlot(index);
    }

    private static ItemStack output(BlockPos pos, int index) {
        FluxMachineBlockEntity machine = machine(pos);
        return machine.inventory().getStackInSlot(machine.machine().inputSlots() + index);
    }

    /** The item capability a neighbor would get from the machine, or {@code null} if the face hides it. */
    private static IItemHandler items(BlockPos pos, Direction side) {
        return capability(pos, ForgeCapabilities.ITEM_HANDLER, side);
    }

    /** The fluid capability a neighbor would get from the machine, or {@code null} if the face hides it. */
    private static IFluidHandler fluid(BlockPos pos, Direction side) {
        return capability(pos, ForgeCapabilities.FLUID_HANDLER, side);
    }

    private static <T> T capability(BlockPos pos, Capability<T> capability, Direction side) {
        return level.getBlockEntity(pos).getCapability(capability, side).orElse(null);
    }

    /** The level of an enchantment on an item, or in an enchanted book; 0 if it is not there. */
    private static int level(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0);
    }

    /** An enchanted book holding the given enchantments, as pairs of enchantment and level. */
    private static ItemStack book(Object... pairs) {
        Map<Enchantment, Integer> stored = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            stored.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(stored, book);
        return book;
    }
    /** Hands everything the server would send a player to another menu, cut to 16 bits like the network does. */
    record Mirror(AbstractContainerMenu target) implements ContainerSynchronizer {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            for (int i = 0; i < items.size(); i++) {
                target.getSlot(i).set(items.get(i).copy());
            }
            for (int i = 0; i < data.length; i++) {
                target.setData(i, (short) data[i]);
            }
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
            target.getSlot(slot).set(stack.copy());
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu container, ItemStack stack) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            target.setData(id, (short) value);
        }
    }
}
