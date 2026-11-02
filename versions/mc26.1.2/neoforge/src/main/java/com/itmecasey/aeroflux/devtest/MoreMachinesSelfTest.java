package com.itmecasey.aeroflux.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import com.itmecasey.aeroflux.lamp.FluxLampBlockEntity;
import com.itmecasey.aeroflux.lamp.LampField;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.CrafterBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.SieveImports;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.machine.SkyControllerBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Checks the second wave of machines in a world: the Crafter crafts from its pattern and keeps it,
 * the Purifier waits for water, the Sifter runs sieve recipes for the mesh in its slot, the Blender makes
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
    private static BlockPos stringSifter;
    private static BlockPos flintSifter;
    private static BlockPos meshlessSifter;
    private static BlockPos blender;
    private static BlockPos sky;
    private static BlockPos magnet;
    private static BlockPos breeder;
    private static BlockPos repairer;
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

        // Sifter: the same gravel through two meshes, and once with no mesh at all. Neither sieve
        // mod is here, so the recipes are written the way theirs are, with vanilla items as meshes.
        checkRealSieveRecipes();
        SieveImports.useForTest(sieveRecipes());
        stringSifter = machineAt(MachineType.SIFTER, Tier.COPPER, 7, 1, 8_000);
        machine(stringSifter).setStackIn(SieveMesh.MATERIAL_SLOT, new ItemStack(Items.GRAVEL, 2));
        machine(stringSifter).setStackIn(SieveMesh.MESH_SLOT, new ItemStack(Items.STRING));
        flintSifter = machineAt(MachineType.SIFTER, Tier.COPPER, 9, 1, 8_000);
        machine(flintSifter).setStackIn(SieveMesh.MATERIAL_SLOT, new ItemStack(Items.GRAVEL, 2));
        machine(flintSifter).setStackIn(SieveMesh.MESH_SLOT, new ItemStack(Items.FLINT));
        meshlessSifter = machineAt(MachineType.SIFTER, Tier.COPPER, 7, 3, 8_000);
        machine(meshlessSifter).setStackIn(SieveMesh.MATERIAL_SLOT, new ItemStack(Items.GRAVEL, 4));
        checkSifterSlots();

        // Blender: enough of both for ten blends, which is one bucket of Twilight Flux.
        blender = machine(MachineType.BLENDER, 11, 1, 8_000);
        fill(blender, ModFluids.MOON_FLUX.get(), 1_000);
        machine(blender).setStackIn(0, new ItemStack(Items.BUCKET));

        // Sky Controller: both fluxes, pressed later.
        sky = machine(MachineType.SKY_CONTROLLER, 13, 1, 4_000);
        fill(sky, ModFluids.MOON_FLUX.get(), 4_000);

        // Magnet: cobblestone on the floor, and a dirt filter on whitelist so it is left alone at first.
        magnet = machine(MachineType.MAGNET, 1, 5, 0);
        machine(magnet).setFlux(FluidResource.of(ModFluids.MOON_FLUX.get()), 8_000);
        machine(magnet).setStackIn(0, new ItemStack(Items.DIRT));
        ItemEntity cobble = new ItemEntity(level, 2.5, Y + 0.1, 7.5, new ItemStack(Items.COBBLESTONE, 5));
        cobble.setNoPickUpDelay();
        cobble.setUnlimitedLifetime();
        level.addFreshEntity(cobble);

        // Breeder: two cows and wheat.
        breeder = machine(MachineType.BREEDER, 5, 5, 8_000);
        machine(breeder).setStackIn(0, new ItemStack(Items.WHEAT, 4));
        for (int i = 0; i < 2; i++) {
            Animal cow = EntityType.COW.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            if (cow != null) {
                cow.setPos(5.5 + i, Y, 7.5);
                level.addFreshEntity(cow);
            }
        }

        // Repairer: a worn, enchanted sword and ten bottles.
        repairer = machine(MachineType.REPAIRER, 8, 5, 0);
        machine(repairer).setFlux(FluidResource.of(ModFluids.MOON_FLUX.get()), 8_000);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
        sword.setDamageValue(100);
        machine(repairer).setStackIn(0, sword);
        machine(repairer).setStackIn(1, new ItemStack(Items.EXPERIENCE_BOTTLE, 10));

        // Moon Spawner: a chicken always drops its raw chicken.
        spawner = machine(MachineType.SPAWNER, 11, 5, 0);
        machine(spawner).setFlux(FluidResource.of(ModFluids.MOON_FLUX.get()), 8_000);
        machine(spawner).setStackIn(0, new ItemStack(Items.CHICKEN_SPAWN_EGG));

        // Sun Lamp: a bucket of Aero Flux.
        sunLamp = new BlockPos(13, Y, 5);
        level.setBlock(sunLamp, ModBlocks.lamp(LampKind.SUN, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        try (Transaction transaction = Transaction.openRoot()) {
            lamp(sunLamp).tank().insert(0, FluidResource.of(ModFluids.AERO_FLUX.get()), 1_000, transaction);
            transaction.commit();
        }
        BlockPos moonLamp = new BlockPos(13, Y, 9);
        level.setBlock(moonLamp, ModBlocks.lamp(LampKind.MOON, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        SelfTest.check("lamps: a Moon Lamp refuses Aero Flux",
                !lamp(moonLamp).tank().isValid(0, FluidResource.of(ModFluids.AERO_FLUX.get())));

        // The rules that need no time to pass.
        CrafterBlockEntity grid = (CrafterBlockEntity) machine(crafter);
        SelfTest.check("crafter: pipes may top up a pattern slot", grid.acceptsFromOutside(0, new ItemStack(Items.OAK_PLANKS)));
        SelfTest.check("crafter: pipes may not change the pattern",
                !grid.acceptsFromOutside(1, new ItemStack(Items.OAK_PLANKS)) && !grid.acceptsFromOutside(0, new ItemStack(Items.STICK)));
        SelfTest.check("blender: its Twilight tank cannot be filled from outside",
                insert(machine(blender).fluidHandler(null), 2, ModFluids.TWILIGHT_FLUX.get(), 100) == 0);
        SelfTest.check("purifier: its water tank takes water and nothing else",
                insert(machine(dryPurifier).fluidHandler(null), 1, ModFluids.MOON_FLUX.get(), 100) == 0);
        SelfTest.check("twilight flux: a tank stores it, a machine's Flux tank does not",
                !machine(crafter).acceptsFlux(FluidResource.of(ModFluids.TWILIGHT_FLUX.get()))
                        && ModFluids.isAnyFlux(FluidResource.of(ModFluids.TWILIGHT_FLUX.get()))
                        && ModFluids.fluxTypeOf(FluidResource.of(ModFluids.TWILIGHT_FLUX.get())) == FluxType.TWILIGHT);
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkMagnetFilter() {
        SelfTest.check("magnet: a whitelist of dirt leaves cobblestone on the ground", cobbleOnGround() == 5);
        MachineConfigActions.applyButton(machine(magnet), MachineConfigActions.MAGNET_MODE_BUTTON);
        SelfTest.check("magnet: the filter button switches to blacklist", machine(magnet).machineMode() == 0);
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

        // Sifter. Every amount in the test recipes is certain, so the counts are exact.
        List<ItemStack> throughString = outputs(stringSifter);
        SelfTest.check("sifter: gravel through a string mesh gave exactly its string-mesh drop",
                throughString.size() == 1 && throughString.getFirst().is(Items.FLINT) && throughString.getFirst().getCount() == 2);
        SelfTest.check("sifter: the mesh is never used up, the gravel is",
                machine(stringSifter).stackIn(SieveMesh.MESH_SLOT).is(Items.STRING)
                        && machine(stringSifter).stackIn(SieveMesh.MATERIAL_SLOT).isEmpty());
        List<ItemStack> throughFlint = outputs(flintSifter);
        SelfTest.check("sifter: the same gravel through another mesh gives that mesh's drops, binomial rolls counted",
                throughFlint.size() == 1 && throughFlint.getFirst().is(Items.IRON_NUGGET) && throughFlint.getFirst().getCount() == 6);
        SelfTest.check("sifter: without a mesh it waits",
                machine(meshlessSifter).stackIn(SieveMesh.MATERIAL_SLOT).getCount() == 4 && outputs(meshlessSifter).isEmpty());

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
        Holder<WorldClock> clock = level.dimensionTypeRegistration().value().defaultClock().orElseThrow();
        SelfTest.check("sky controller: Night moves the clock to nightfall",
                pressed && level.getServer().clockManager().isAtTimeMarker(clock, ClockTimeMarkers.NIGHT));
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
            machine(pos).setFlux(FluidResource.of(ModFluids.AERO_FLUX.get()), aero);
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
        ResourceHandler<FluidResource> handler = machine(pos).fluidHandler(null);
        for (int index = 1; index < handler.size(); index++) {
            if (insert(handler, index, fluid, amount) > 0) {
                return;
            }
        }
    }

    private static int insert(ResourceHandler<FluidResource> handler, int index, Fluid fluid, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int put = handler.insert(index, FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return put;
        }
    }

    /**
     * Sieve recipes in the shapes Ex Deorum and Ex Nihilo Sequentia write them. Ex Deorum's take
     * any item as a mesh, so vanilla string and flint stand in for real meshes. The hand-only one
     * must never run in a machine, and the Ex Nihilo one names a mesh that is not installed, so
     * it is left out without breaking the rest.
     */
    private static List<JsonObject> sieveRecipes() {
        return List.of(
                JsonParser.parseString("""
                        {"type": "exdeorum:sieve", "ingredient": {"item": "minecraft:gravel"},
                         "mesh": {"item": "minecraft:string"}, "result": {"id": "minecraft:flint", "count": 1},
                         "result_amount": 1}""").getAsJsonObject(),
                JsonParser.parseString("""
                        {"type": "exdeorum:sieve", "ingredient": {"item": "minecraft:gravel"},
                         "mesh": {"item": "minecraft:flint"}, "result": {"id": "minecraft:iron_nugget"},
                         "result_amount": {"type": "minecraft:binomial", "n": 3, "p": 1.0}}""").getAsJsonObject(),
                JsonParser.parseString("""
                        {"type": "exdeorum:sieve", "ingredient": {"item": "minecraft:gravel"},
                         "mesh": {"item": "minecraft:string"}, "result": {"id": "minecraft:diamond"},
                         "result_amount": 1, "by_hand_only": true}""").getAsJsonObject(),
                JsonParser.parseString("""
                        {"type": "exnihilosequentia:sieve", "input": {"item": "minecraft:gravel"},
                         "result": {"item": "minecraft:emerald"}, "rolls": [{"chance": 1.0, "mesh": "string"}],
                         "waterlogged": true}""").getAsJsonObject());
    }

    /**
     * With Ex Deorum in the game (it is in the 26.1 NeoForge dev run), its own sieve recipes come
     * through: gravel sifts through its string mesh into its own items, its meshes fit the mesh slot,
     * and a recipe whose load condition fails (an ore no mod here adds) is left out, as it is in
     * Ex Deorum's own sieve.
     */
    private static void checkRealSieveRecipes() {
        if (!ModList.get().isLoaded("exdeorum")) {
            return;
        }
        SieveImports.useForTest(null);
        List<SieveRoll> rolls = SieveImports.of(level);
        Item stringMesh = BuiltInRegistries.ITEM.getValue(Identifier.parse("exdeorum:string_mesh"));
        Item boron = BuiltInRegistries.ITEM.getValue(Identifier.parse("exdeorum:boron_ore_chunk"));
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        ItemStack mesh = new ItemStack(stringMesh);
        boolean gravelThroughString = rolls.stream().anyMatch(roll -> roll.runs(gravel, mesh));
        boolean ownItems = rolls.stream().anyMatch(roll -> roll.runs(gravel, mesh)
                && BuiltInRegistries.ITEM.getKey(roll.product().item().value()).getNamespace().equals("exdeorum"));
        SelfTest.check("sifter: Ex Deorum's own sieve recipes come through, its own items included",
                rolls.size() > 300 && gravelThroughString && ownItems);
        SelfTest.check("sifter: Ex Deorum's string mesh fits the mesh slot", SieveImports.isMesh(level, mesh));
        boolean boronAdded = BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.create(Identifier.parse("c:ores/boron"))).iterator().hasNext();
        SelfTest.check("sifter: an Ex Deorum recipe whose load condition fails is left out",
                boronAdded || rolls.stream().noneMatch(roll -> roll.product().item().value() == boron));
    }

    /**
     * The slots: the mesh slot takes only meshes and only one, the block slot takes what can be
     * sifted, and a Sifter saved before the mesh slot existed keeps its products in output slots.
     */
    private static void checkSifterSlots() {
        FluxMachineBlockEntity sieve = machine(meshlessSifter);
        ResourceHandler<ItemResource> items = sieve.itemHandler(null);
        int strings;
        int dirt;
        try (Transaction transaction = Transaction.openRoot()) {
            strings = items.insert(SieveMesh.MESH_SLOT, ItemResource.of(Items.STRING), 5, transaction);
            dirt = items.insert(SieveMesh.MESH_SLOT, ItemResource.of(Items.DIRT), 1, transaction);
        }
        SelfTest.check("sifter: the mesh slot takes one mesh and nothing that is not a mesh", strings == 1 && dirt == 0);
        SelfTest.check("sifter: the block slot takes what a recipe sifts and turns away what none does",
                sieve.inventory().isValid(SieveMesh.MATERIAL_SLOT, ItemResource.of(Items.GRAVEL))
                        && !sieve.inventory().isValid(SieveMesh.MATERIAL_SLOT, ItemResource.of(Items.DIRT)));

        // An old Sifter: one input slot, then six outputs, with flint in the first output.
        ItemStacksResourceHandler old = new ItemStacksResourceHandler(1 + MachineType.SIFTER.outputSlots());
        old.set(0, ItemResource.of(Items.GRAVEL), 3);
        old.set(1, ItemResource.of(Items.FLINT), 7);
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        old.serialize(saved.child("items"));
        BlockPos pos = new BlockPos(9, Y, 3);
        level.setBlock(pos, ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity loaded = machine(pos);
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
        int firstOutput = MachineType.SIFTER.inputSlots();
        SelfTest.check("sifter: a Sifter saved before the mesh slot keeps its block and its products in their places",
                loaded.stackIn(SieveMesh.MATERIAL_SLOT).is(Items.GRAVEL) && loaded.stackIn(SieveMesh.MESH_SLOT).isEmpty()
                        && loaded.stackIn(firstOutput).is(Items.FLINT) && loaded.stackIn(firstOutput).getCount() == 7);
        level.removeBlock(pos, false);
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
