package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.compat.ae2.Ae2Compat;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import com.itmecasey.aeroflux.lamp.LampField;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.CrafterBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.SkyControllerBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Checks the second wave of machines in a world: the Crafter crafts from its pattern and keeps it,
 * the Purifier waits for water, the Sifter only runs what its mesh allows, the Blender makes
 * Twilight Flux and bottles it, the Sky Controller moves the clock and then cools down, the Magnet
 * follows its filter, the Breeder makes animals fall in love, the Repairer mends with experience,
 * the Moon Spawner rolls loot without a mob, and a lit Sun Lamp keeps monsters out of its light.
 *
 * <p>Everything is built in a layer of its own inside the force-loaded chunk. The Sky Controller
 * check waits until the collector checks are done, since it moves the clock they depend on.
 */
final class MoreMachinesSelfTest {
    private static final int Y = 250;
    /** By this tick every fast (netherite) machine has done several jobs. */
    private static final int WORKED = 120;
    /** The Magnet's filter is switched at this tick, after it has had time to (wrongly) pull anything. */
    private static final int MAGNET_SWITCH = 40;
    private static final int LAST = 200;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean machinesChecked;
    private static boolean finished;

    private static BlockPos crafter;
    private static BlockPos purifier;
    private static BlockPos dryPurifier;
    private static BlockPos sifter;
    private static BlockPos sandSifter;
    private static BlockPos blender;
    private static BlockPos sky;
    private static BlockPos magnet;
    private static BlockPos breeder;
    private static BlockPos repairer;
    /** Two bundles with an ME pipe each and an AE converter beside them, when AE2 is installed. */
    private static BlockPos meWest;
    private static BlockPos meEast;
    private static BlockPos aeConverter;
    private static BlockPos spawner;
    private static BlockPos sunLamp;

    private MoreMachinesSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        machinesChecked = false;
        finished = false;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            BlockPos probe = new BlockPos(2, Y, 2);
            if (level.shouldTickBlocksAt(probe) && level.isPositionEntityTicking(probe)) {
                build();
                built = true;
            }
            return;
        }
        tick++;
        if (tick == MAGNET_SWITCH) {
            checkMagnetFilter();
        }
        if (tick == WORKED) {
            checkWork();
            checkAe2();
        }
        if (tick >= LAST && !machinesChecked) {
            checkLamp();
            machinesChecked = true;
        }
        // The clock check waits for the collectors, which need day and night to come on time.
        if (machinesChecked && SelfTest.collectorChecksDone()) {
            checkSkyController();
            cleanUp();
            finished = true;
        }
    }

    // --- Building ----------------------------------------------------------------------------

    private static void build() {
        // AE2: two ME pipes side by side and an AE converter next to them. AE2 makes their grid
        // nodes on their first tick, so they are only looked at once the machines have worked.
        if (Ae2Compat.loaded()) {
            meWest = new BlockPos(3, Y, 9);
            meEast = meWest.east();
            for (BlockPos pos : List.of(meWest, meEast)) {
                level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
                ((ConduitBundleBlockEntity) level.getBlockEntity(pos)).addPipe(PipeType.ME);
            }
            aeConverter = meEast.east();
            level.setBlock(aeConverter, ModBlocks.converter(ConverterType.AE, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        }

        // A stone floor under the Magnet's cobblestone and the Breeder's cows, so they stay put. It
        // leaves the column over the collector checks open: they need to see the sky.
        for (int x = 0; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // Crafter: planks over planks makes sticks; three per slot is two crafts, then the pattern stays.
        crafter = machine(MachineType.CRAFTER, 1, 1, 8_000);
        machine(crafter).setStackIn(0, new ItemStack(Items.OAK_PLANKS, 3));
        machine(crafter).setStackIn(3, new ItemStack(Items.OAK_PLANKS, 3));

        // Purifier: one with water, one without.
        purifier = machine(MachineType.PURIFIER, 3, 1, 8_000);
        machine(purifier).setStackIn(0, new ItemStack(Items.IRON_ORE));
        fill(purifier, Fluids.WATER, 1_000);
        dryPurifier = machine(MachineType.PURIFIER, 5, 1, 8_000);
        machine(dryPurifier).setStackIn(0, new ItemStack(Items.IRON_ORE));

        // Sifter: a copper (string mesh) sifter with gravel, and an iron (flint mesh) one with sand and no water.
        sifter = machineAt(MachineType.SIFTER, Tier.COPPER, 7, 1, 8_000);
        machine(sifter).setStackIn(0, new ItemStack(Items.GRAVEL, 2));
        sandSifter = machineAt(MachineType.SIFTER, Tier.IRON, 9, 1, 8_000);
        machine(sandSifter).setStackIn(0, new ItemStack(Items.SAND, 4));

        // Blender: enough of both for ten blends, which is one bucket of Twilight Flux.
        blender = machine(MachineType.BLENDER, 11, 1, 8_000);
        fill(blender, ModFluids.MOON_FLUX.get(), 1_000);
        machine(blender).setStackIn(0, new ItemStack(Items.BUCKET));

        // Sky Controller: both fluxes, pressed later.
        sky = machine(MachineType.SKY_CONTROLLER, 13, 1, 4_000);
        fill(sky, ModFluids.MOON_FLUX.get(), 4_000);

        // Magnet: cobblestone on the floor, and a dirt filter on whitelist so it is left alone at first.
        magnet = machine(MachineType.MAGNET, 1, 5, 0);
        machine(magnet).setFlux(new FluidStack(ModFluids.MOON_FLUX.get(), 8_000));
        machine(magnet).setStackIn(0, new ItemStack(Items.DIRT));
        ItemEntity cobble = new ItemEntity(level, 2.5, Y + 0.1, 7.5, new ItemStack(Items.COBBLESTONE, 5));
        cobble.setNoPickUpDelay();
        cobble.setUnlimitedLifetime();
        level.addFreshEntity(cobble);

        // Breeder: two cows and wheat.
        breeder = machine(MachineType.BREEDER, 5, 5, 8_000);
        machine(breeder).setStackIn(0, new ItemStack(Items.WHEAT, 4));
        for (int i = 0; i < 2; i++) {
            Animal cow = EntityType.COW.create(level);
            if (cow != null) {
                cow.setPos(5.5 + i, Y, 7.5);
                level.addFreshEntity(cow);
            }
        }

        // Repairer: a worn, enchanted sword and ten bottles.
        repairer = machine(MachineType.REPAIRER, 8, 5, 0);
        machine(repairer).setFlux(new FluidStack(ModFluids.MOON_FLUX.get(), 8_000));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 2);
        sword.setDamageValue(100);
        machine(repairer).setStackIn(0, sword);
        machine(repairer).setStackIn(1, new ItemStack(Items.EXPERIENCE_BOTTLE, 10));

        // Moon Spawner: a chicken always drops its raw chicken.
        spawner = machine(MachineType.SPAWNER, 11, 5, 0);
        machine(spawner).setFlux(new FluidStack(ModFluids.MOON_FLUX.get(), 8_000));
        machine(spawner).setStackIn(0, new ItemStack(Items.CHICKEN_SPAWN_EGG));

        // Sun Lamp: a bucket of Aero Flux.
        sunLamp = new BlockPos(13, Y, 5);
        level.setBlock(sunLamp, ModBlocks.lamp(LampKind.SUN, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        lamp(sunLamp).tank().fill(new FluidStack(ModFluids.AERO_FLUX.get(), 1_000), IFluidHandler.FluidAction.EXECUTE);
        BlockPos moonLamp = new BlockPos(13, Y, 9);
        level.setBlock(moonLamp, ModBlocks.lamp(LampKind.MOON, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        SelfTest.check("lamps: a Moon Lamp refuses Aero Flux",
                !lamp(moonLamp).tank().isFluidValid(new FluidStack(ModFluids.AERO_FLUX.get(), 1)));

        // The rules that need no time to pass.
        CrafterBlockEntity grid = (CrafterBlockEntity) machine(crafter);
        SelfTest.check("crafter: pipes may top up a pattern slot", grid.acceptsFromOutside(0, new ItemStack(Items.OAK_PLANKS)));
        SelfTest.check("crafter: pipes may not change the pattern",
                !grid.acceptsFromOutside(1, new ItemStack(Items.OAK_PLANKS)) && !grid.acceptsFromOutside(0, new ItemStack(Items.STICK)));
        SelfTest.check("blender: its Twilight tank cannot be filled from outside",
                insert(machine(blender).fluidHandler(null), ModFluids.TWILIGHT_FLUX.get(), 100) == 0);
        SelfTest.check("purifier: its water tank takes water and nothing else",
                insert(machine(dryPurifier).fluidHandler(null), ModFluids.MOON_FLUX.get(), 100) == 0);
        SelfTest.check("twilight flux: a tank stores it, a machine's Flux tank does not",
                !machine(crafter).acceptsFlux(new FluidStack(ModFluids.TWILIGHT_FLUX.get(), 1))
                        && ModFluids.isAnyFlux(ModFluids.TWILIGHT_FLUX.get())
                        && ModFluids.fluxTypeOf(ModFluids.TWILIGHT_FLUX.get()) == FluxType.TWILIGHT);
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkMagnetFilter() {
        SelfTest.check("magnet: a whitelist of dirt leaves cobblestone on the ground", cobbleOnGround() == 5);
        MachineConfigActions.applyButton(machine(magnet), MachineConfigActions.MAGNET_MODE_BUTTON);
        SelfTest.check("magnet: the filter button switches to blacklist", machine(magnet).machineMode() == 0);
    }

    /** AE2 finds the nodes of ME pipes and the AE converter through the block entities' capability. */
    private static void checkAe2() {
        if (!Ae2Compat.loaded()) {
            AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO AE2 is not installed; the ME pipe checks are skipped");
            return;
        }
        SelfTest.check("ae2: an ME pipe offers AE2 a grid node toward the next ME pipe",
                Ae2Compat.attaches(level, meWest, Direction.EAST) && Ae2Compat.attaches(level, meEast, Direction.WEST));
        SelfTest.check("ae2: an ME pipe offers no node where it has no connection",
                !Ae2Compat.attaches(level, meWest, Direction.UP));
        SelfTest.check("ae2: the AE converter offers AE2 a grid node",
                Ae2Compat.attaches(level, aeConverter, Direction.WEST));
    }

    private static void checkWork() {
        // Crafter: one craft per slot pair above one item.
        FluxMachineBlockEntity grid = machine(crafter);
        SelfTest.check("crafter: two planks over two planks made sticks",
                output(crafter).is(Items.STICK) && output(crafter).getCount() == 8);
        SelfTest.check("crafter: the last plank of each slot stays as the pattern",
                grid.stackIn(0).getCount() == 1 && grid.stackIn(3).getCount() == 1);

        // Purifier.
        SelfTest.check("purifier: an iron ore and water became three raw iron",
                output(purifier).is(Items.RAW_IRON) && output(purifier).getCount() == 3);
        SelfTest.check("purifier: it used 50 mB of water",
                machine(purifier).extraAmount(machine(purifier).extraTankIndex(ExtraTank.WATER)) == 950);
        SelfTest.check("purifier: without water it waits", machine(dryPurifier).stackIn(0).is(Items.IRON_ORE)
                && outputs(dryPurifier).isEmpty());

        // Sifter.
        List<ItemStack> sifted = outputs(sifter);
        boolean onlyStringMesh = sifted.stream().allMatch(stack -> stack.is(Items.FLINT) || stack.is(Items.WHEAT_SEEDS));
        SelfTest.check("sifter: a copper sifter used up its gravel", machine(sifter).stackIn(0).isEmpty());
        // The rolls are chances, so the outputs may be empty; whatever is there must come from the string mesh.
        SelfTest.check("sifter: a string mesh only gives what string-mesh recipes give", onlyStringMesh);
        SelfTest.check("sifter: sand that only washes out with water waits for water",
                machine(sandSifter).stackIn(0).getCount() == 4);

        // Blender.
        FluxMachineBlockEntity mixer = machine(blender);
        SelfTest.check("blender: ten blends of Moon Flux made a bucket of Twilight Flux",
                output(blender).is(ModItems.TWILIGHT_FLUX_BUCKET.get())
                        && mixer.extraAmount(mixer.extraTankIndex(ExtraTank.MOON_FLUX)) == 0);

        // Magnet.
        SelfTest.check("magnet: on blacklist it pulled the cobblestone in",
                cobbleOnGround() == 0 && count(magnet, Items.COBBLESTONE) == 5);

        // Breeder.
        List<Animal> cows = level.getEntitiesOfClass(Animal.class, new AABB(breeder).inflate(8));
        boolean bred = cows.size() > 2 || cows.stream().anyMatch(Animal::isInLove);
        SelfTest.check("breeder: the cows were fed and fell in love", bred && machine(breeder).stackIn(0).getCount() == 2);

        // Repairer.
        ItemStack sword = output(repairer);
        SelfTest.check("repairer: the sword came out whole with its enchantment",
                sword.is(Items.IRON_SWORD) && sword.getDamageValue() == 0 && sword.isEnchanted());
        SelfTest.check("repairer: it took eight bottles for 100 points of damage",
                machine(repairer).stackIn(1).getCount() == 2);

        // Moon Spawner.
        SelfTest.check("spawner: a chicken egg filled the outputs with raw chicken", count(spawner, Items.CHICKEN) > 0);
        SelfTest.check("spawner: no chicken was put in the world",
                level.getEntitiesOfClass(Chicken.class, new AABB(spawner).inflate(4)).isEmpty());
    }

    private static void checkLamp() {
        SelfTest.check("lamps: a Sun Lamp with Aero Flux is lit",
                level.getBlockState(sunLamp).getValue(FluxLampBlock.LIT) && lamp(sunLamp).fluxAmount() < 1_000);
        SelfTest.check("lamps: monsters cannot spawn in its light, but can outside it",
                LampField.covers(level.dimension(), sunLamp.offset(3, 0, -3))
                        && !LampField.covers(level.dimension(), sunLamp.offset(20, 0, 0)));
    }

    private static void checkSkyController() {
        SkyControllerBlockEntity controller = (SkyControllerBlockEntity) machine(sky);
        boolean pressed = MachineConfigActions.applyButton(controller, MachineConfigActions.SKY_NIGHT_BUTTON);
        SelfTest.check("sky controller: Night moves the clock to nightfall",
                pressed && Math.floorMod(level.getDayTime(), 24_000L) == 13_000L);
        SelfTest.check("sky controller: it cools down after a use",
                controller.cooldown() > 0 && !MachineConfigActions.applyButton(controller, MachineConfigActions.SKY_DAY_BUTTON));
        SelfTest.check("sky controller: Night was paid in Moon Flux",
                controller.extraAmount(controller.extraTankIndex(ExtraTank.MOON_FLUX)) < 4_000);
    }

    /** Takes away the animals and items this test made, so nothing else meets them. */
    private static void cleanUp() {
        level.getEntitiesOfClass(Animal.class, new AABB(breeder).inflate(10)).forEach(Animal::discard);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(magnet).inflate(10)).forEach(ItemEntity::discard);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos machine(MachineType type, int x, int z, int aero) {
        return machineAt(type, Tier.NETHERITE, x, z, aero);
    }

    private static BlockPos machineAt(MachineType type, Tier tier, int x, int z, int aero) {
        BlockPos pos = new BlockPos(x, Y, z);
        level.setBlock(pos, ModBlocks.machine(type, tier).get().defaultBlockState(), Block.UPDATE_ALL);
        if (aero > 0) {
            machine(pos).setFlux(new FluidStack(ModFluids.AERO_FLUX.get(), aero));
        }
        return pos;
    }

    private static FluxMachineBlockEntity machine(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static FluxLampBlockEntity lamp(BlockPos pos) {
        return (FluxLampBlockEntity) level.getBlockEntity(pos);
    }

    /** Fills a machine's extra tank through its unsided fluid view, as a bucket would. */
    private static void fill(BlockPos pos, Fluid fluid, int amount) {
        insert(machine(pos).fluidHandler(null), fluid, amount);
    }

    private static int insert(IFluidHandler handler, Fluid fluid, int amount) {
        return handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    /** The first output slot. */
    private static ItemStack output(BlockPos pos) {
        FluxMachineBlockEntity at = machine(pos);
        return at.stackIn(at.machine().inputSlots());
    }

    /** Every filled output slot. */
    private static List<ItemStack> outputs(BlockPos pos) {
        FluxMachineBlockEntity at = machine(pos);
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = at.machine().inputSlots(); slot < at.machine().inputSlots() + at.machine().outputSlots(); slot++) {
            if (!at.stackIn(slot).isEmpty()) {
                stacks.add(at.stackIn(slot));
            }
        }
        return stacks;
    }

    private static int count(BlockPos pos, Item item) {
        return outputs(pos).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int cobbleOnGround() {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(magnet).inflate(10)).stream()
                .filter(item -> item.getItem().is(Items.COBBLESTONE))
                .mapToInt(item -> item.getItem().getCount()).sum();
    }
}
