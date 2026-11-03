package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.fluid.Buckets;
import com.itmecasey.aeroflux.machine.RecipeImports;
import com.itmecasey.aeroflux.machine.RecipeImportsSync;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCapabilities;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.itmecasey.aeroflux.registry.ModTags;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Checks the support for other mods that can be checked on a server and needs no other mod:
 *
 * <ul>
 *     <li>the recipe viewer lists (JEI shows {@link ViewerRecipes}): imported recipes under their
 *     machine, the client's way of working them out from the rules the server sends, and the
 *     Sifter's sieve pages;</li>
 *     <li>fluids: the {@code c:} tags, buckets, and other mods pushing a fluid into an AeroFlux pipe.</li>
 * </ul>
 *
 * <p>The blocks are built in a layer of their own. The viewer checks wait for the machine checks,
 * which add the import rule they look for.
 */
final class CompatSelfTest {
    private static final int Y = 312;
    private static final int SETTLE = 40;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos inletBundle;
    private static BlockPos inletSource;
    private static BlockPos inletSink;

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
            if (SelfTest.blockEntitiesTick(level, new BlockPos(2, Y, 2))) {
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
        finished = true;
    }

    /** A pipe between two Flux tanks, the way another mod's pipe or pump would push into it. */
    private static void build() {
        inletSource = new BlockPos(1, Y, 4);
        inletBundle = inletSource.east();
        inletSink = inletBundle.east();
        level.setBlock(inletSource, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletSink, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletBundle, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        bundle().addPipe(PipeType.FLUID, Tier.COPPER);
        // The source tank stays empty, so only the push this test makes reaches the sink.
        setMode(Face.WEST, ConnectionMode.OPEN);
        setMode(Face.EAST, ConnectionMode.EXPORT);
    }

    // --- Recipe viewers ----------------------------------------------------------------------

    private static void checkViewers() {
        List<RecipeHolder<FluxRecipe>> sawmill = ViewerRecipes.machine(level, MachineType.SAWMILL);
        int own = level.getRecipeManager().getAllRecipesFor(ModRecipes.type(MachineType.SAWMILL).get()).size();
        List<RecipeHolder<FluxRecipe>> imported = sawmill.stream().filter(holder -> ViewerRecipes.importedFrom(holder.id()) != null).toList();
        SelfTest.check("viewers: the Sawmill page lists its own recipes and then the imported stonecutting ones",
                own > 0 && sawmill.size() == own + imported.size() && !imported.isEmpty()
                        && imported.stream().anyMatch(holder -> "minecraft".equals(ViewerRecipes.importedFrom(holder.id())))
                        && ViewerRecipes.importedFrom(sawmill.get(0).id()) == null);
        SelfTest.check("viewers: an imported recipe names the mod it came from",
                ViewerRecipes.importedLine(imported.isEmpty() ? Ids.mod("none") : imported.get(0).id()) != null);

        // The client's way: the rules come in a message, the recipes are the ones it was sent.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RecipeImportsSync.Payload.write(RecipeImportsSync.current(), buf);
        RecipeImportsSync.receive(RecipeImportsSync.Payload.read(buf));
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
        FluidStack inBucket = Buckets.contained(new ItemStack(ModItems.MOON_FLUX_BUCKET.get()));
        SelfTest.check("fluids: a Flux bucket holds a bucket of its Flux for any mod that asks",
                inBucket.getFluid().isSame(ModFluids.MOON_FLUX.get()) && inBucket.getAmount() == 1_000);

        // Another mod pushing Flux into the pipe's west face: it goes on to the sink, never back.
        IFluidHandler inlet = ModCapabilities.find(level, ForgeCapabilities.FLUID_HANDLER, inletBundle, Direction.WEST);
        IFluidHandler exportFace = ModCapabilities.find(level, ForgeCapabilities.FLUID_HANDLER, inletBundle, Direction.EAST);
        FluxTankBlockEntity sink = (FluxTankBlockEntity) level.getBlockEntity(inletSink);
        FluxTankBlockEntity source = (FluxTankBlockEntity) level.getBlockEntity(inletSource);
        int before = sink.tank().getFluidAmount();
        int pushed = inlet == null ? 0 : inlet.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 50), IFluidHandler.FluidAction.EXECUTE);
        int after = sink.tank().getFluidAmount();
        SelfTest.check("fluids: other mods can push any fluid into an AeroFlux pipe, which hands it to the sink",
                pushed == 50 && after - before == 50 && sink.tank().getFluid().getFluid().isSame(ModFluids.AERO_FLUX.get())
                        && source.tank().isEmpty());
        long limit = PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 0, 0);
        int flood = inlet == null ? -1 : inlet.fill(new FluidStack(ModFluids.AERO_FLUX.get(), 1_000_000), IFluidHandler.FluidAction.SIMULATE);
        SelfTest.check("fluids: a pipe face takes no more than the pipe's rate in one tick, and an Export face takes nothing in",
                flood >= 0 && flood <= limit && exportFace == null);
    }

    private static void setMode(Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity bundle = bundle();
        bundle.bundle().setMode(PipeType.FLUID.lane(), face, mode);
        bundle.refreshConnections();
        bundle.changed();
    }

    private static ConduitBundleBlockEntity bundle() {
        return (ConduitBundleBlockEntity) level.getBlockEntity(inletBundle);
    }
}
