package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.compat.ViewerRecipes;
import com.itmecasey.aeroflux.compat.modjs.AeroFluxScripts;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.config.PipeConfigActions;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.core.script.ModJsBridge;
import com.itmecasey.aeroflux.core.script.ScriptEvents;
import com.itmecasey.aeroflux.core.script.ScriptInfo;
import com.itmecasey.aeroflux.core.script.ScriptWorld;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.RecipeImports;
import com.itmecasey.aeroflux.machine.RecipeImportsSync;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModRecipes;
import com.itmecasey.aeroflux.registry.ModTags;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import mcp.mobius.waila.api.IPluginInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Checks the support for other mods that can be checked on a server and exist for this Minecraft
 * version:
 *
 * <ul>
 *     <li>the recipe viewers' lists (JEI and REI both show {@link ViewerRecipes}): imported recipes
 *     under their machine, the message that carries the imports and the sieve drops to clients, and
 *     the Sifter's sieve pages;</li>
 *     <li>WTHIT, when it is in the dev run: it found the plugin;</li>
 *     <li>fluids: the {@code c:} tags, and other mods pushing fluid into an AeroFlux pipe, which hands it
 *     to its sink within the pipe's rate.</li>
 * </ul>
 *
 * <p>EMI, CraftTweaker and Create have no build for Minecraft 26.1 yet, so nothing is checked for
 * them. The blocks stand in a chunk of their own (x and z from 64, forced loaded by
 * {@link GuardSelfTest}), and the viewer checks wait for the machine checks, which add the import rule
 * they look for.
 */
final class CompatSelfTest {
    private static final int Y = 230;
    private static final int OX = 64;
    private static final int OZ = 64;
    private static final int SETTLE = 40;
    private static final int PUSH = 50;

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;

    private static BlockPos inletSource;
    private static BlockPos inletBundle;
    private static BlockPos inletSink;
    private static BlockPos scriptMachine;

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
            if (SelfTest.blockEntitiesTick(level, new BlockPos(OX + 2, Y, OZ + 2))) {
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
        checkScripts();
        if (ModList.get().isLoaded("wthit")) {
            SelfTest.check("wthit: the AeroFlux plugin is loaded", IPluginInfo.get(Ids.mod("look_info")) != null);
        }
        finished = true;
    }

    private static void build() {
        // A pipe between two tanks, the way another mod's pipe or pump would push into it. The
        // source tank stays empty, so only the push this test makes reaches the sink.
        inletSource = new BlockPos(OX + 1, Y, OZ + 4);
        inletBundle = inletSource.east();
        inletSink = inletBundle.east();
        level.setBlock(inletSource, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletSink, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(inletBundle, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        bundle().addPipe(PipeType.FLUID, Tier.COPPER);
        setMode(Face.WEST, ConnectionMode.OPEN);
        setMode(Face.EAST, ConnectionMode.EXPORT);
        scriptMachine = inletSource.south(2);
        level.setBlock(scriptMachine, ModBlocks.machine(MachineType.GRINDER, Tier.GOLD).get().defaultBlockState(), Block.UPDATE_ALL);
    }

    // --- Scripts -----------------------------------------------------------------------------

    /**
     * The ModJS side: the reads the {@code AeroFlux} global answers, the events it listens to, and, when ModJS is in the dev
     * run ({@code -PmodjsJar=<path>}), a real script through ModJS itself. Without ModJS the bridge must load nothing.
     */
    private static void checkScripts() {
        ScriptWorld world = AeroFluxScripts.world();
        String dimension = level.dimension().identifier().toString();
        ScriptInfo.Machine machine = world.machine(dimension, scriptMachine.getX(), scriptMachine.getY(), scriptMachine.getZ());
        SelfTest.check("scripts: a machine read gives its id, tier, idle status and Flux holder",
                machine != null && machine.id.equals("grinder") && machine.tier.equals("gold") && machine.status.equals("idle")
                        && machine.flux != null && machine.flux.holder.equals("machine") && machine.flux.capacity > 0);
        ScriptInfo.Flux tank = world.flux(dimension, inletSink.getX(), inletSink.getY(), inletSink.getZ());
        SelfTest.check("scripts: a tank read gives its holder and capacity",
                tank != null && tank.holder.equals("tank") && tank.capacity > 0);
        ScriptInfo.Network network = world.network(dimension, inletBundle.getX(), inletBundle.getY(), inletBundle.getZ(), PipeType.FLUID);
        SelfTest.check("scripts: a pipe read gives its network", network != null && network.pipe.equals("fluid") && network.bundles == 1);
        SelfTest.check("scripts: a place with no machine reads as nothing",
                world.machine(dimension, scriptMachine.getX(), scriptMachine.getY() + 5, scriptMachine.getZ()) == null
                        && world.player("nobody-by-this-name") == null);
        SelfTest.check("scripts: a read in a chunk that is not loaded gives nothing and loads nothing",
                world.machine(dimension, 400_000, 64, 400_000) == null && !level.hasChunkAt(new BlockPos(400_000, 64, 400_000)));

        List<ScriptEvents.Event> seen = new ArrayList<>();
        ScriptEvents.on("selftest", ScriptEvents.PIPE_MODE_CHANGED, seen::add);
        ScriptEvents.on("selftest", ScriptEvents.MACHINE_FINISHED, seen::add);
        ScriptEvents.on("selftest", ScriptEvents.MACHINE_FINISHED, e -> {
            throw new IllegalStateException("a broken listener");
        });
        PipeConfigActions.setMode(bundle(), PipeType.FLUID, Face.NORTH, ConnectionMode.OFF);
        AeroFluxScripts.machineFinished(level, scriptMachine, (FluxMachineBlockEntity) level.getBlockEntity(scriptMachine));
        ScriptEvents.clear("selftest");
        SelfTest.check("scripts: a pipe mode change and a finished machine reach their listeners, and a broken listener harms nothing",
                seen.size() == 2 && seen.get(0).id.equals("fluid") && seen.get(0).face.equals("north")
                        && seen.get(0).mode.equals("off") && seen.get(1).id.equals("grinder") && seen.get(1).y == scriptMachine.getY());

        if (!ModList.get().isLoaded("modjs")) {
            SelfTest.check("scripts: without ModJS the bridge loads nothing and fails nothing", !ModJsBridge.install(type -> null));
            return;
        }
        String script = "AeroFlux.type() + ':' + AeroFlux.machine('" + dimension + "', " + scriptMachine.getX() + ", "
                + scriptMachine.getY() + ", " + scriptMachine.getZ() + ").tier + ':'"
                + " + AeroFlux.recipe('grinder').input('#c:ores/iron').output('2x minecraft:raw_iron').build().get('type')";
        SelfTest.check("scripts: ModJS runs a script that reads a machine and builds a recipe through the AeroFlux global",
                "server:gold:aeroflux:grinder".equals(runInModJs(script)));
    }

    /** Runs a server script in ModJS through reflection (this class never names ModJS), or returns {@code null}. */
    private static String runInModJs(String script) {
        try {
            Class<?> typeClass = Class.forName("com.itmecasey.modjs.script.ScriptType");
            Object server = typeClass.getField("SERVER").get(null);
            Class<?> manager = Class.forName("com.itmecasey.modjs.script.ScriptManager");
            Object scripts = manager.getMethod("of", typeClass).invoke(null, server);
            return String.valueOf(manager.getMethod("eval", String.class, String.class).invoke(scripts, script, "aeroflux-selftest.js"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            AeroFlux.LOGGER.warn("AEROFLUX-SELFTEST INFO the ModJS script failed: {}", e.toString());
            return null;
        }
    }

    // --- Recipe viewers ----------------------------------------------------------------------

    private static void checkViewers() {
        List<ViewerRecipes.Entry> sawmill = ViewerRecipes.machine(level, MachineType.SAWMILL);
        int own = level.recipeAccess().recipeMap().byType(ModRecipes.type(MachineType.SAWMILL).get()).size();
        List<ViewerRecipes.Entry> imported = sawmill.stream().filter(entry -> ViewerRecipes.importedFrom(entry.id()) != null).toList();
        SelfTest.check("viewers: the Sawmill page lists its own recipes and then the imported stonecutting ones",
                own > 0 && sawmill.size() == own + imported.size() && !imported.isEmpty()
                        && imported.stream().anyMatch(entry -> "minecraft".equals(ViewerRecipes.importedFrom(entry.id())))
                        && ViewerRecipes.importedFrom(sawmill.getFirst().id()) == null);
        SelfTest.check("viewers: an imported recipe names the mod it came from",
                ViewerRecipes.importedLine(imported.isEmpty() ? Ids.mod("none") : imported.getFirst().id()) != null);

        // The client's way: the server's imports and drops come in one message.
        RecipeImportsSync.Payload sent = RecipeImportsSync.current(level);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        RecipeImportsSync.Payload.CODEC.encode(buf, sent);
        RecipeImportsSync.Payload received = RecipeImportsSync.Payload.CODEC.decode(buf);
        boolean same = true;
        int total = 0;
        for (MachineType machine : MachineType.values()) {
            List<RecipeHolder<FluxRecipe>> before = sent.recipes().getOrDefault(machine, List.of());
            List<RecipeHolder<FluxRecipe>> after = received.recipes().getOrDefault(machine, List.of());
            same &= before.size() == after.size();
            for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
                same &= before.get(i).id().identifier().equals(after.get(i).id().identifier())
                        && before.get(i).value().time() == after.get(i).value().time()
                        && before.get(i).value().inputs().size() == after.get(i).value().inputs().size()
                        && before.get(i).value().outputs().size() == after.get(i).value().outputs().size();
            }
            total += before.size();
        }
        SelfTest.check("viewers: a client gets the same imported recipes the server works out, in one message", same && total > 0);

        boolean dropsSame = sent.drops().size() == received.drops().size();
        for (int i = 0; dropsSame && i < sent.drops().size(); i++) {
            SieveRoll before = sent.drops().get(i);
            SieveRoll after = received.drops().get(i);
            dropsSame = before.amount().equals(after.amount()) && before.water() == after.water()
                    && before.product().equals(after.product());
        }
        SelfTest.check("viewers: the sieve drops, with their amounts, cross the wire unchanged", dropsSame);

        RecipeImportsSync.receive(received);
        SelfTest.check("viewers: what a client received is what its viewers list",
                RecipeImports.syncedForViewer(MachineType.SAWMILL).size() == sent.recipes().getOrDefault(MachineType.SAWMILL, List.of()).size()
                        && !RecipeImports.syncedForViewer(MachineType.SAWMILL).isEmpty());
        RecipeImportsSync.receive(new RecipeImportsSync.Payload(java.util.Map.of(), List.of()));

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

        // Another mod pushing Aero Flux into the pipe's west face: it goes on to the sink, never back.
        ResourceHandler<FluidResource> inlet = level.getCapability(Capabilities.Fluid.BLOCK, inletBundle, Direction.WEST);
        ResourceHandler<FluidResource> exportFace = level.getCapability(Capabilities.Fluid.BLOCK, inletBundle, Direction.EAST);
        FluidResource aero = FluidResource.of(ModFluids.AERO_FLUX.get());
        long before = held(inletSink);
        int pushed = 0;
        if (inlet != null) {
            try (Transaction transaction = Transaction.openRoot()) {
                pushed = inlet.insert(aero, PUSH, transaction);
                transaction.commit();
            }
        }
        SelfTest.check("fluids: other mods can push fluid into an AeroFlux pipe, which hands it to the sink",
                pushed == PUSH && held(inletSink) - before == PUSH && held(inletSource) == 0);

        // A push that is only tried (a simulation) must cost the pipe's rate nothing.
        long limit = PipeUpgrades.amountPerTick(PipeType.FLUID, Tier.COPPER, 0, 0);
        int flood = -1;
        if (inlet != null) {
            try (Transaction transaction = Transaction.openRoot()) {
                flood = inlet.insert(aero, 1_000_000, transaction);
            }
        }
        SelfTest.check("fluids: a pipe face takes no more than the pipe's rate in one tick, and an Export face takes nothing in",
                flood >= 0 && flood <= limit && exportFace == null);
        SelfTest.check("fluids: a push that was only tried changes nothing", held(inletSink) - before == PUSH);
    }

    private static long held(BlockPos pos) {
        return ((FluxTankBlockEntity) level.getBlockEntity(pos)).tank().getAmountAsLong(0);
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
