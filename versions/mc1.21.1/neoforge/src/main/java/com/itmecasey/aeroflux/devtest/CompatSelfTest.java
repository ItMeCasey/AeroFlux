package com.itmecasey.aeroflux.devtest;

import com.blamejared.crafttweaker.api.bracket.custom.RecipeTypeBracketHandler;
import com.blamejared.crafttweaker.api.ingredient.IIngredientWithAmount;
import com.blamejared.crafttweaker.api.ingredient.type.IngredientWithAmount;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.component.BuiltinRecipeComponents;
import com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.compat.LookInfo;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.compat.create.CreateCompat;
import com.itmecasey.aeroflux.compat.create.CreateContent;
import com.itmecasey.aeroflux.compat.create.StressConverterBlock;
import com.itmecasey.aeroflux.compat.create.StressConverterBlockEntity;
import com.itmecasey.aeroflux.compat.crafttweaker.FluxRecipeHandler;
import com.itmecasey.aeroflux.compat.crafttweaker.FluxRecipeManager;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.converter.StressMath;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.machine.RecipeImports;
import com.itmecasey.aeroflux.machine.RecipeImportsSync;
import com.itmecasey.aeroflux.machine.SieveImports;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.itmecasey.aeroflux.registry.ModTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import mcp.mobius.waila.api.IPluginInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Checks the support for other mods that can be checked on a server:
 *
 * <ul>
 *     <li>the recipe viewers' lists (JEI, REI and EMI all show {@link ViewerRecipes}): imported
 *     recipes under their machine, the client's way of working them out from the rules the
 *     server sends, and the Sifter's sieve pages from Ex Deorum's real recipes;</li>
 *     <li>CraftTweaker, when it is in the dev run: a script this test writes before the server
 *     loads adds and removes machine recipes, and the replacer's view of a recipe survives a round
 *     trip;</li>
 *     <li>WTHIT, when it is in the dev run: it found the plugin;</li>
 *     <li>fluids: the {@code c:} tags, buckets, Flux in a Mekanism tank, an AeroFlux pipe filling a
 *     Mekanism tank, and other mods pushing any fluid into an AeroFlux pipe;</li>
 *     <li>the Create Stress converter, when Create is in the dev run: it turns a shaft and adds its
 *     stress capacity, and stops when its tank runs dry.</li>
 * </ul>
 *
 * <p>The blocks are built in a layer of their own. The viewer checks wait for the machine checks,
 * which add the import rule they look for.
 */
final class CompatSelfTest {
    private static final int Y = 312;
    private static final int SETTLE = 40;
    /** The script the CraftTweaker checks run, written before the server loads its data and removed at the end. */
    private static final String SCRIPT = "aeroflux_selftest.zs";

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos fluxTank;
    private static BlockPos pipeToMekanism;
    private static BlockPos mekanismSink;
    private static BlockPos inletBundle;
    private static BlockPos inletSource;
    private static BlockPos inletSink;
    private static BlockPos converter;
    private static BlockPos shaft;

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
            if (level.shouldTickBlocksAt(new BlockPos(2, Y, 2))) {
                build();
                built = true;
            }
            return;
        }
        if (++tick < SETTLE || !MachineExtrasSelfTest.finished()) {
            return;
        }
        checkViewers();
        checkFluids();
        if (CreateCompat.loaded()) {
            Create.check();
        }
        if (ModList.get().isLoaded("crafttweaker")) {
            CraftTweaker.check();
        }
        if (ModList.get().isLoaded("wthit")) {
            SelfTest.check("wthit: the AeroFlux plugin is loaded", IPluginInfo.get(Ids.mod("look_info")) != null);
        }
        removeScript();
        finished = true;
    }

    private static void build() {
        // An AeroFlux tank of Aero Flux, a pipe, and a Mekanism tank the pipe fills.
        if (ModList.get().isLoaded("mekanism")) {
            fluxTank = new BlockPos(1, Y, 1);
            pipeToMekanism = fluxTank.east();
            mekanismSink = pipeToMekanism.east();
            level.setBlock(fluxTank, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
            ((FluxTankBlockEntity) level.getBlockEntity(fluxTank)).tank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), 4_000));
            level.setBlock(mekanismSink, mekanismTank(), Block.UPDATE_ALL);
            placePipe(pipeToMekanism);
            setMode(pipeToMekanism, Face.WEST, ConnectionMode.IMPORT);
            setMode(pipeToMekanism, Face.EAST, ConnectionMode.EXPORT);

            // A pipe between two Mekanism tanks, the way another mod's pipe or pump would push into it.
            inletSource = new BlockPos(1, Y, 4);
            inletBundle = inletSource.east();
            inletSink = inletBundle.east();
            level.setBlock(inletSource, mekanismTank(), Block.UPDATE_ALL);
            level.setBlock(inletSink, mekanismTank(), Block.UPDATE_ALL);
            placePipe(inletBundle);
            // The source tank stays empty, so only the push this test makes reaches the sink.
            setMode(inletBundle, Face.WEST, ConnectionMode.OPEN);
            setMode(inletBundle, Face.EAST, ConnectionMode.EXPORT);
        }

        if (CreateCompat.loaded()) {
            converter = new BlockPos(1, Y, 8);
            shaft = converter.east();
            Create.build();
        }
    }

    // --- Recipe viewers ----------------------------------------------------------------------

    private static void checkViewers() {
        List<RecipeHolder<FluxRecipe>> sawmill = ViewerRecipes.machine(level, MachineType.SAWMILL);
        int own = level.getRecipeManager().getAllRecipesFor(ModRecipes.type(MachineType.SAWMILL).get()).size();
        List<RecipeHolder<FluxRecipe>> imported = sawmill.stream().filter(holder -> ViewerRecipes.importedFrom(holder.id()) != null).toList();
        SelfTest.check("viewers: the Sawmill page lists its own recipes and then the imported stonecutting ones",
                own > 0 && sawmill.size() == own + imported.size() && !imported.isEmpty()
                        && imported.stream().anyMatch(holder -> "minecraft".equals(ViewerRecipes.importedFrom(holder.id())))
                        && ViewerRecipes.importedFrom(sawmill.getFirst().id()) == null);
        SelfTest.check("viewers: an imported recipe names the mod it came from",
                ViewerRecipes.importedLine(imported.isEmpty() ? Ids.mod("none") : imported.getFirst().id()) != null);
        if (ModList.get().isLoaded("mekanism")) {
            SelfTest.check("viewers: Mekanism's crushing recipes are listed under the Grinder",
                    ViewerRecipes.machine(level, MachineType.GRINDER).stream()
                            .anyMatch(holder -> "mekanism".equals(ViewerRecipes.importedFrom(holder.id()))));
        }

        // The client's way: the rules come in a message, the recipes are the ones it was sent.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RecipeImportsSync.Payload.CODEC.encode(buf, RecipeImportsSync.current());
        RecipeImportsSync.receive(RecipeImportsSync.Payload.CODEC.decode(buf));
        var client = RecipeImports.fromSyncedRules(level.getRecipeManager(), level.registryAccess());
        boolean same = true;
        int total = 0;
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                int server = RecipeImports.of(level, machine).size();
                same &= client.getOrDefault(machine, List.of()).size() == server;
                total += server;
            }
        }
        SelfTest.check("viewers: a client works out the same imported recipes from the rules the server sends",
                same && total > 0);

        // The Sifter's pages, from the sieve recipes (the machine checks put test recipes in place of the real ones).
        List<ViewerRecipes.SievePage> pages = ViewerRecipes.sifter(level);
        SelfTest.check("viewers: the Sifter has pages, none with more drops than fit",
                !pages.isEmpty() && pages.stream().allMatch(page -> !page.drops().isEmpty() && page.drops().size() <= ViewerRecipes.SIEVE_PAGE));
        if (ModList.get().isLoaded("exdeorum")) {
            List<SieveRoll> real = SieveImports.fromRecipes(level.getRecipeManager(), level.registryAccess());
            List<ViewerRecipes.SievePage> realPages = ViewerRecipes.sievePages(real);
            ItemStack stringMesh = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("exdeorum:string_mesh")));
            boolean gravelPage = realPages.stream().anyMatch(page -> page.material().test(new ItemStack(Items.GRAVEL))
                    && page.mesh().test(stringMesh));
            int listed = realPages.stream().mapToInt(page -> page.drops().size()).sum();
            SelfTest.check("viewers: Ex Deorum's sieve drops are on Sifter pages, gravel through the string mesh among them",
                    gravelPage && listed > 300);
        }
        SelfTest.check("viewers: sieve amounts read as numbers, ranges and chances",
                ViewerRecipes.amountText(new SieveMesh.Constant(2)).equals("2")
                        && ViewerRecipes.amountText(new SieveMesh.Uniform(1, 3)).equals("1-3")
                        && ViewerRecipes.amountText(new SieveMesh.Binomial(1, 0.25)).equals("25%")
                        && ViewerRecipes.amountText(new SieveMesh.Binomial(3, 0.125)).equals("3 x 12.5%"));
    }

    // --- Fluids ------------------------------------------------------------------------------

    private static void checkFluids() {
        SelfTest.check("fluids: the Flux fluids carry their c: tags",
                ModFluids.AERO_FLUX.get().defaultFluidState().is(ModTags.AERO_FLUX)
                        && ModFluids.FLOWING_MOON_FLUX.get().defaultFluidState().is(ModTags.MOON_FLUX)
                        && ModFluids.TWILIGHT_FLUX.get().defaultFluidState().is(ModTags.TWILIGHT_FLUX));
        FluidStack inBucket = FluidUtil.getFluidContained(new ItemStack(ModItems.MOON_FLUX_BUCKET.get())).orElse(FluidStack.EMPTY);
        SelfTest.check("fluids: a Flux bucket holds a bucket of its Flux for any mod that asks",
                inBucket.getFluid().isSame(ModFluids.MOON_FLUX.get()) && inBucket.getAmount() == 1_000);

        if (!ModList.get().isLoaded("mekanism")) {
            return;
        }
        IFluidHandler loose = level.getCapability(Capabilities.FluidHandler.BLOCK, inletSource, Direction.UP);
        SelfTest.check("fluids: a Mekanism tank takes Aero Flux",
                loose != null && loose.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 1_000), IFluidHandler.FluidAction.SIMULATE) == 1_000);
        IFluidHandler filled = level.getCapability(Capabilities.FluidHandler.BLOCK, mekanismSink, Direction.WEST);
        FluidStack arrived = filled == null ? FluidStack.EMPTY : filled.getFluidInTank(0);
        SelfTest.check("fluids: an AeroFlux pipe fills a Mekanism tank with Aero Flux",
                arrived.getFluid().isSame(ModFluids.AERO_FLUX.get()) && arrived.getAmount() > 0);

        // Another mod pushing water into the pipe's west face: it goes on to the sink, never back.
        IFluidHandler inlet = level.getCapability(Capabilities.FluidHandler.BLOCK, inletBundle, Direction.WEST);
        IFluidHandler exportFace = level.getCapability(Capabilities.FluidHandler.BLOCK, inletBundle, Direction.EAST);
        IFluidHandler sink = level.getCapability(Capabilities.FluidHandler.BLOCK, inletSink, Direction.WEST);
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, inletSource, Direction.EAST);
        int before = sink == null ? -1 : sink.getFluidInTank(0).getAmount();
        int pushed = inlet == null ? 0 : inlet.fill(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.EXECUTE);
        int after = sink == null ? -1 : sink.getFluidInTank(0).getAmount();
        SelfTest.check("fluids: other mods can push any fluid into an AeroFlux pipe, which hands it to the sink",
                pushed == 50 && after - before == 50 && sink.getFluidInTank(0).getFluid().isSame(Fluids.WATER)
                        && source != null && !source.getFluidInTank(0).getFluid().isSame(Fluids.WATER));
        long limit = PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 0, 0);
        int flood = inlet == null ? -1 : inlet.fill(new FluidStack(Fluids.WATER, 1_000_000), IFluidHandler.FluidAction.SIMULATE);
        SelfTest.check("fluids: a pipe face takes no more than the pipe's rate in one tick, and an Export face takes nothing in",
                flood >= 0 && flood <= limit && exportFace == null);
    }

    private static BlockState mekanismTank() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank")).defaultBlockState();
    }

    private static void placePipe(BlockPos pos) {
        level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        bundle(pos).addPipe(PipeType.FLUID, Tier.COPPER);
    }

    private static void setMode(BlockPos pos, Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity bundle = bundle(pos);
        bundle.bundle().setMode(PipeType.FLUID.lane(), face, mode);
        bundle.refreshConnections();
        bundle.changed();
    }

    private static ConduitBundleBlockEntity bundle(BlockPos pos) {
        return (ConduitBundleBlockEntity) level.getBlockEntity(pos);
    }

    // --- CraftTweaker ------------------------------------------------------------------------

    private static Path scriptPath() {
        return FMLPaths.GAMEDIR.get().resolve("scripts").resolve(SCRIPT);
    }

    private static void removeScript() {
        try {
            Files.deleteIfExists(scriptPath());
        } catch (IOException e) {
            AeroFlux.LOGGER.warn("Could not remove the self-test script {}", scriptPath(), e);
        }
    }

    /** Writes the CraftTweaker script before the server loads its data, so CraftTweaker runs it with the rest. */
    @EventBusSubscriber(modid = AeroFlux.MODID)
    static final class Script {
        private Script() {
        }

        @SubscribeEvent
        static void serverSetup(FMLDedicatedServerSetupEvent event) {
            if (System.getenv("AEROFLUX_SELFTEST") == null || !ModList.get().isLoaded("crafttweaker")) {
                return;
            }
            String script = String.join("\n",
                    "// Written by the AeroFlux self-test and removed when it ends.",
                    "<recipetype:aeroflux:grinder>.addRecipe(\"aeroflux_selftest_kept\", [<item:minecraft:dead_bush> * 2],"
                            + " [<item:minecraft:stick> * 3, <item:minecraft:string> % 25], 40, 5);",
                    "<recipetype:aeroflux:grinder>.addRecipe(\"aeroflux_selftest_removed\", [<item:minecraft:dead_horn_coral>],"
                            + " [<item:minecraft:bone_meal>]);",
                    "<recipetype:aeroflux:grinder>.removeByName(\"crafttweaker:aeroflux_selftest_removed\");",
                    "<recipetype:aeroflux:purifier>.addRecipe(\"aeroflux_selftest_water\", [<item:minecraft:clay_ball>],"
                            + " [<item:minecraft:brick>], 60, 0, \"aero\", 100);",
                    "");
            try {
                Files.createDirectories(scriptPath().getParent());
                Files.writeString(scriptPath(), script);
            } catch (IOException e) {
                AeroFlux.LOGGER.warn("Could not write the self-test script {}", scriptPath(), e);
            }
        }
    }

    /** The CraftTweaker checks; this class names CraftTweaker's classes, so it is only loaded with CraftTweaker. */
    private static final class CraftTweaker {
        static void check() {
            boolean managers = true;
            for (MachineType machine : MachineType.values()) {
                if (machine.hasOwnRecipes()) {
                    Object manager = RecipeTypeBracketHandler.getOrDefault(ModRecipes.type(machine).get());
                    managers &= manager instanceof FluxRecipeManager flux && flux.machine() == machine;
                }
            }
            SelfTest.check("crafttweaker: every recipe machine has its own manager", managers);

            Optional<RecipeHolder<?>> kept = level.getRecipeManager().byKey(ResourceLocation.parse("crafttweaker:aeroflux_selftest_kept"));
            boolean added = kept.isPresent() && kept.get().value() instanceof FluxRecipe recipe
                    && recipe.machine() == MachineType.GRINDER && recipe.time() == 40 && recipe.flux() == 5
                    && recipe.inputs().size() == 1 && recipe.inputs().getFirst().count() == 2
                    && recipe.inputs().getFirst().ingredient().test(new ItemStack(Items.DEAD_BUSH))
                    && recipe.outputs().size() == 2 && recipe.outputs().get(0).stack().is(Items.STICK)
                    && recipe.outputs().get(0).stack().getCount() == 3 && Math.abs(recipe.outputs().get(1).chance() - 0.25F) < 1e-4;
            if (!added) {
                AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO crafttweaker recipe as added: {}", kept.map(holder -> describe(holder.value())).orElse("none"));
            }
            SelfTest.check("crafttweaker: a script adds a Grinder recipe with counts and chances", added);
            // The script added this one too, just before removing it.
            SelfTest.check("crafttweaker: a script removes a recipe by name", kept.isPresent()
                    && level.getRecipeManager().byKey(ResourceLocation.parse("crafttweaker:aeroflux_selftest_removed")).isEmpty());
            Optional<RecipeHolder<?>> water = level.getRecipeManager().byKey(ResourceLocation.parse("crafttweaker:aeroflux_selftest_water"));
            SelfTest.check("crafttweaker: a Purifier recipe keeps its flux type and water",
                    water.isPresent() && water.get().value() instanceof FluxRecipe recipe && recipe.water() == 100
                            && recipe.fluxType() == FluxType.AERO);

            // The replacer's view: take a recipe apart, swap its input, put it back together.
            if (kept.isPresent() && kept.get().value() instanceof FluxRecipe recipe) {
                FluxRecipeHandler handler = new FluxRecipeHandler();
                IDecomposedRecipe parts = handler.decompose(FluxRecipeManager.Grinder.INSTANCE, level.registryAccess(), recipe).orElseThrow();
                FluxRecipe same = handler.recompose(FluxRecipeManager.Grinder.INSTANCE, level.registryAccess(), parts).orElseThrow();
                IIngredientWithAmount cobble = new IngredientWithAmount(IItemStack.of(new ItemStack(Items.COBBLESTONE)), 4);
                parts.set(BuiltinRecipeComponents.Input.INGREDIENTS_WITH_AMOUNTS, List.of(cobble));
                FluxRecipe swapped = handler.recompose(FluxRecipeManager.Grinder.INSTANCE, level.registryAccess(), parts).orElseThrow();
                SelfTest.check("crafttweaker: the replacer can swap a machine recipe's input and keep the rest",
                        same.time() == recipe.time() && same.flux() == recipe.flux() && same.outputs().size() == recipe.outputs().size()
                                && swapped.inputs().getFirst().ingredient().test(new ItemStack(Items.COBBLESTONE))
                                && swapped.inputs().getFirst().count() == 4 && swapped.flux() == 5
                                && swapped.outputs().get(0).stack().is(Items.STICK));
            }
        }
    }

    /** A machine recipe in a line, for the log when a check fails. */
    private static String describe(Object value) {
        if (!(value instanceof FluxRecipe recipe)) {
            return String.valueOf(value);
        }
        StringBuilder text = new StringBuilder(recipe.machine() + " time=" + recipe.time() + " flux=" + recipe.flux() + " in=");
        for (FluxRecipe.Input input : recipe.inputs()) {
            text.append(List.of(input.ingredient().getItems())).append('x').append(input.count()).append(' ');
        }
        text.append("out=");
        for (FluxRecipe.Output output : recipe.outputs()) {
            text.append(output.stack()).append('@').append(output.chance()).append(' ');
        }
        return text.toString();
    }

    // --- Create ------------------------------------------------------------------------------

    /** The Create checks; this class names the Create content, so it is only loaded with Create. */
    private static final class Create {
        static void build() {
            BlockState state = CreateContent.converter(Tier.COPPER).get().defaultBlockState()
                    .setValue(StressConverterBlock.FACING, Direction.EAST);
            level.setBlock(converter, state, Block.UPDATE_ALL);
            Block shaftBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"));
            level.setBlock(shaft, shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
            ((StressConverterBlockEntity) level.getBlockEntity(converter)).tank()
                    .setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), 4_000));
        }

        static void check() {
            boolean registered = true;
            for (Tier tier : Tier.values()) {
                ResourceLocation id = Ids.mod(CreateCompat.converterName(tier));
                registered &= BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.ITEM.containsKey(id);
            }
            SelfTest.check("create: the six Stress converters are registered with Create installed",
                    registered && CreateCompat.stressConverters().size() == Tier.values().length);

            StressConverterBlockEntity stress = (StressConverterBlockEntity) level.getBlockEntity(converter);
            BlockEntity shaftEntity = level.getBlockEntity(shaft);
            float shaftSpeed = shaftEntity instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
            SelfTest.check("create: a Stress converter with Flux turns the shaft at its tier's speed",
                    stress.running() && Math.abs(shaftSpeed) == StressMath.rpm(Tier.COPPER));
            SelfTest.check("create: it adds its stress capacity to the network",
                    Math.abs(stress.networkCapacity() - AeroFluxConfig.converterStress(Tier.COPPER)) < 0.5F
                            && stress.output() == AeroFluxConfig.converterStress(Tier.COPPER));
            SelfTest.check("create: with nothing to drive it burns no Flux",
                    stress.fluxAmount() == 4_000 && stress.lastBurn() == 0);
            SelfTest.check("create: the look-at line names its capacity",
                    !LookInfo.lines(level, stress).isEmpty() && LookInfo.flux(stress) != null);

            // Run it dry: the shaft stops on the next tick.
            stress.tank().setFluid(FluidStack.EMPTY);
            stress.tick();
            SelfTest.check("create: with an empty tank the shaft stops",
                    !stress.running() && stress.getGeneratedSpeed() == 0 && stress.output() == 0);
        }
    }
}
