package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraftforge.fluids.FluidStack;

/**
 * The transfer part of the self-test. Every scenario is built at once, high above the world and
 * inside the forced chunk, and the checks run at fixed tick counts afterwards. Each scenario has
 * its own row of blocks so they cannot disturb each other.
 *
 * <p>Energy is not covered here: no AeroFlux block offers an energy storage to build a line
 * from. The energy mover shares its planning code with the fluid mover, which is covered.
 */
final class TransferSelfTest {
    /** Tick at which the fluid, gating, distribution and item checks look at the result. */
    private static final int CHECK_FLUID = 60;
    private static final int CHECK_SPEED = 10;
    private static final int CHECK_ITEMS_EARLY = 20;
    private static final int CHECK_ITEMS = 100;
    private static final int REDSTONE_ON = 20;
    private static final int REDSTONE_OFF = 40;
    private static final int LAST_CHECK = CHECK_ITEMS;

    private static ServerLevel level;
    private static int tick;
    private static boolean finished;
    private static boolean built;

    // Scenario (a): a plain copper line, and the largest drop of the source in one tick.
    private static Line plain;
    private static int plainLargestStep;
    private static int plainPreviousAmount;

    // Scenario (b): lines that must not move anything.
    private static Line gatedOff;
    private static Line gatedBlocked;
    private static Line gatedExportSource;
    private static Line gatedImportSink;

    // Scenario (c): one source feeding three sinks, one scenario per mode.
    private static Star firstToLast;
    private static Star lastToFirst;
    private static Star roundRobin;
    private static Star random;

    // Scenario (d): item lines.
    private static ItemLine copperItems;
    private static ItemLine netheriteItems;
    private static int copperItemsEarly;
    private static int netheriteItemsEarly;

    // Scenarios (e) and (f): speed.
    private static Line baseline;
    private static Line upgraded;
    private static Line mixed;
    private static Line gold;

    // Scenario (g) and the slot limit.
    private static BlockPos persistent;
    private static BlockPos slotTest;

    // Scenario (h): lever, redstone pipe, lamp.
    private static BlockPos lever;
    private static BlockPos redstoneBundle;
    private static BlockPos lamp;

    /** A source tank, some pipe bundles in a row, and a sink tank. */
    private record Line(BlockPos source, List<BlockPos> bundles, BlockPos sink) {
    }

    /** One bundle with a source tank on its west side and three sink tanks. */
    private record Star(BlockPos bundle, BlockPos source, BlockPos first, BlockPos second, BlockPos third) {
    }

    private record ItemLine(BlockPos source, BlockPos sink) {
    }

    private TransferSelfTest() {
    }

    /** Whether every transfer check has run. */
    static boolean finished() {
        return finished;
    }

    /**
     * Remembers the level; the scenarios are built on the first tick that blocks there really tick.
     * A freshly forced chunk needs a few ticks to start ticking, and the timed checks count from
     * the moment everything is placed.
     */
    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        finished = false;
        built = false;
    }

    private static void build() {
        // (a) and (b): copper fluid lines, source side pulling and sink side pushing.
        plain = fluidLine(210, 2, Tier.COPPER, 3, 1000, ConnectionMode.IMPORT, ConnectionMode.EXPORT);
        plainPreviousAmount = 1000;
        gatedOff = fluidLine(212, 2, Tier.COPPER, 1, 1000, ConnectionMode.OFF, ConnectionMode.EXPORT);
        gatedBlocked = fluidLine(214, 2, Tier.COPPER, 1, 1000, ConnectionMode.BLOCKED, ConnectionMode.EXPORT);
        gatedExportSource = fluidLine(216, 2, Tier.COPPER, 1, 1000, ConnectionMode.EXPORT, ConnectionMode.EXPORT);
        gatedImportSink = fluidLine(218, 2, Tier.COPPER, 1, 1000, ConnectionMode.IMPORT, ConnectionMode.IMPORT);

        // (c) distribution. The source holds little so that the order of filling stays visible.
        firstToLast = star(220, 300, 0, DistributionMode.FIRST_TO_LAST);
        lastToFirst = star(222, 300, 0, DistributionMode.LAST_TO_FIRST);
        roundRobin = star(224, 300, 0, DistributionMode.ROUND_ROBIN);
        // Each sink has room for only 50 mB, so the three together can take 150 of the 300.
        random = star(226, 300, 50, DistributionMode.RANDOM);

        // (d) items.
        copperItems = itemLine(228, 2, Tier.COPPER);
        netheriteItems = itemLine(228, 6, Tier.NETHERITE);

        // (e) and (f) speed. Tanks are filled generously so the pipe is the limit, not the source.
        baseline = fluidLine(230, 2, Tier.COPPER, 1, 20_000, ConnectionMode.IMPORT, ConnectionMode.EXPORT);
        upgraded = fluidLine(230, 6, Tier.COPPER, 1, 20_000, ConnectionMode.IMPORT, ConnectionMode.EXPORT);
        mixed = fluidLine(230, 10, Tier.COPPER, 2, 20_000, ConnectionMode.IMPORT, ConnectionMode.EXPORT);
        gold = fluidLine(230, 14, Tier.GOLD, 1, 20_000, ConnectionMode.IMPORT, ConnectionMode.EXPORT);
        SelfTest.check("upgrades: a speed upgrade installs through the game's upgrade logic",
                UpgradeItem.install(entity(upgraded.bundles().get(0)), PipeType.FLUID, PipeUpgrades.Kind.SPEED));
        UpgradeItem.install(entity(mixed.bundles().get(0)), PipeType.FLUID, PipeUpgrades.Kind.SPEED);

        // Slot limit: a copper pipe has three slots.
        slotTest = new BlockPos(2, 232, 2);
        placeBundle(slotTest, PipeType.FLUID, Tier.COPPER);
        boolean three = true;
        for (int i = 0; i < 3; i++) {
            three &= UpgradeItem.install(entity(slotTest), PipeType.FLUID, PipeUpgrades.Kind.CAPACITY);
        }
        SelfTest.check("upgrades: a copper pipe takes three upgrades", three);
        SelfTest.check("upgrades: a fourth upgrade is refused",
                !UpgradeItem.install(entity(slotTest), PipeType.FLUID, PipeUpgrades.Kind.SPEED));

        // (g) persistence.
        persistent = new BlockPos(2, 234, 2);
        placeBundle(persistent, PipeType.FLUID, Tier.IRON);
        entity(persistent).addPipe(PipeType.ITEM, Tier.NETHERITE);
        entity(persistent).bundle().setDistribution(PipeType.FLUID.lane(), DistributionMode.LAST_TO_FIRST);
        UpgradeItem.install(entity(persistent), PipeType.FLUID, PipeUpgrades.Kind.SPEED);
        UpgradeItem.install(entity(persistent), PipeType.FLUID, PipeUpgrades.Kind.SPEED);
        UpgradeItem.install(entity(persistent), PipeType.FLUID, PipeUpgrades.Kind.CAPACITY);
        entity(persistent).changed();

        // (h) redstone: a powered lever, a redstone pipe, and a lamp the pipe exports into.
        int y = 236;
        lever = new BlockPos(1, y, 2);
        redstoneBundle = new BlockPos(2, y, 2);
        lamp = new BlockPos(3, y, 2);
        level.setBlock(lever.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        placeBundle(redstoneBundle, PipeType.REDSTONE, Tier.COPPER);
        setMode(redstoneBundle, PipeType.REDSTONE, Face.EAST, ConnectionMode.EXPORT);
        level.setBlock(lever, Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
        entity(redstoneBundle).refreshConnections();
    }

    /** Advances the scenarios by one server tick and runs whichever checks are due. */
    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            // In 26.1 a block entity also waits for the chunk's entities to finish loading, which
            // happens on its own schedule after the chunk starts ticking.
            BlockPos probe = new BlockPos(2, 210, 2);
            if (level.shouldTickBlocksAt(probe) && level.areEntitiesLoaded(ChunkPos.pack(probe))) {
                build();
                built = true;
            }
            return;
        }
        tick++;

        // (a) track the biggest single-tick drop of the plain line's source.
        int amount = tankAmount(plain.source());
        plainLargestStep = Math.max(plainLargestStep, plainPreviousAmount - amount);
        plainPreviousAmount = amount;

        if (tick == CHECK_SPEED) {
            checkSpeed();
        }
        if (tick == CHECK_ITEMS_EARLY) {
            copperItemsEarly = count(copperItems.sink());
            netheriteItemsEarly = count(netheriteItems.sink());
        }
        if (tick == REDSTONE_ON) {
            checkRedstoneOn();
            // Flip the lever off.
            level.setBlock(lever, level.getBlockState(lever).setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
        }
        if (tick == REDSTONE_OFF) {
            checkRedstoneOff();
        }
        if (tick == CHECK_FLUID) {
            checkPlainFluid();
            checkGating();
            checkDistribution();
        }
        if (tick == CHECK_ITEMS) {
            checkItems();
            checkPersistence();
        }
        if (tick >= LAST_CHECK) {
            finished = true;
        }
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkPlainFluid() {
        int source = tankAmount(plain.source());
        int sink = tankAmount(plain.sink());
        SelfTest.check("fluid: fluid arrives in tank B through three pipe bundles", sink > 0);
        SelfTest.check("fluid: tank A holds less than it started with", source < 1000);
        SelfTest.check("fluid: nothing is lost or made on the way", source + sink == 1000);
        SelfTest.check("fluid: a copper pipe never moves more than 100 mB in a tick",
                plainLargestStep > 0 && plainLargestStep <= 100);
    }

    private static void checkGating() {
        SelfTest.check("modes: an OFF face moves nothing", tankAmount(gatedOff.sink()) == 0);
        SelfTest.check("modes: a BLOCKED face moves nothing", tankAmount(gatedBlocked.sink()) == 0);
        SelfTest.check("modes: an export-only source face moves nothing", tankAmount(gatedExportSource.sink()) == 0);
        SelfTest.check("modes: an import-only sink face moves nothing", tankAmount(gatedImportSink.sink()) == 0);
    }

    private static void checkDistribution() {
        int[] first = amounts(firstToLast);
        SelfTest.check("distribution: first to last fills the first sink before the others",
                first[0] == 300 && first[1] == 0 && first[2] == 0);
        int[] last = amounts(lastToFirst);
        SelfTest.check("distribution: last to first fills the last sink before the others",
                last[0] == 0 && last[1] == 0 && last[2] == 300);
        int[] even = amounts(roundRobin);
        SelfTest.check("distribution: round robin shares evenly",
                even[0] + even[1] + even[2] == 300 && Math.abs(even[0] - even[1]) <= 2 && Math.abs(even[1] - even[2]) <= 2);

        // The random sinks were prefilled to within 50 mB of full, so each can take at most 50 more.
        int[] rnd = amounts(random);
        int base = Tier.COPPER.tankCapacity() - 50;
        int gained = (rnd[0] - base) + (rnd[1] - base) + (rnd[2] - base);
        SelfTest.check("distribution: random puts fluid into at least one sink", gained > 0);
        SelfTest.check("distribution: random never exceeds a sink's room",
                rnd[0] <= Tier.COPPER.tankCapacity() && rnd[1] <= Tier.COPPER.tankCapacity() && rnd[2] <= Tier.COPPER.tankCapacity());
        SelfTest.check("distribution: random takes exactly what the sinks gained",
                tankAmount(random.source()) == 300 - gained);
    }

    private static void checkItems() {
        int copper = count(copperItems.sink());
        int netherite = count(netheriteItems.sink());
        SelfTest.check("items: items arrive in chest B through a copper item pipe", copper >= 1);
        SelfTest.check("items: chest A shrank by what chest B received", count(copperItems.source()) == 64 - copper);
        SelfTest.check("items: a copper pipe moves about one item per 20 ticks", copper >= 4 && copper <= 6);
        SelfTest.check("items: a netherite pipe moves a whole batch quickly",
                netheriteItemsEarly >= 32 && netheriteItemsEarly > copperItemsEarly);
        SelfTest.check("items: a netherite line empties the chest", netherite == 64 && count(netheriteItems.source()) == 0);
    }

    private static void checkSpeed() {
        int base = tankAmount(baseline.sink());
        int fast = tankAmount(upgraded.sink());
        int slowestPipe = tankAmount(mixed.sink());
        int golden = tankAmount(gold.sink());
        SelfTest.check("speed: a copper pipe moves fluid", base > 0 && base <= 100 * CHECK_SPEED);
        SelfTest.check("speed: a speed upgrade makes the network faster", fast > base);
        SelfTest.check("speed: a network moves at the speed of its slowest pipe", slowestPipe == base);
        SelfTest.check("tiers: a gold fluid pipe moves more per tick than copper", golden > base);
        SelfTest.check("tiers: a gold fluid pipe stays within 650 mB per tick", golden <= 650 * CHECK_SPEED);
    }

    private static void checkPersistence() {
        BlockState state = level.getBlockState(persistent);
        CompoundTag saved = entity(persistent).saveWithFullMetadata(level.registryAccess());
        ConduitBundleBlockEntity restored = new ConduitBundleBlockEntity(persistent, state);
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        int fluid = PipeType.FLUID.lane();
        SelfTest.check("persistence: tiers survive saving and loading",
                restored.bundle().tier(fluid) == Tier.IRON && restored.bundle().tier(PipeType.ITEM.lane()) == Tier.NETHERITE);
        SelfTest.check("persistence: the distribution mode survives saving and loading",
                restored.bundle().distribution(fluid) == DistributionMode.LAST_TO_FIRST);
        SelfTest.check("persistence: upgrades survive saving and loading",
                restored.bundle().speedUpgrades(fluid) == 2 && restored.bundle().capacityUpgrades(fluid) == 1);
        SelfTest.check("persistence: the whole bundle compares equal after a round trip",
                restored.bundle().equals(entity(persistent).bundle()));
    }

    private static void checkRedstoneOn() {
        BlockState lampState = level.getBlockState(lamp);
        SelfTest.check("redstone: the pipe exports a signal into the lamp's side", entity(redstoneBundle).redstoneOutput(Direction.EAST) == 15);
        SelfTest.check("redstone: the consumer sees power", level.hasNeighborSignal(lamp));
        SelfTest.check("redstone: the lamp lights", lampState.getValue(RedstoneLampBlock.LIT));
        SelfTest.check("redstone: the pipe outputs nothing at faces that do not export",
                entity(redstoneBundle).redstoneOutput(Direction.UP) == 0 && entity(redstoneBundle).redstoneOutput(Direction.NORTH) == 0);
    }

    private static void checkRedstoneOff() {
        SelfTest.check("redstone: the signal drops when the lever turns off", entity(redstoneBundle).redstoneOutput(Direction.EAST) == 0);
        SelfTest.check("redstone: the lamp goes dark again", !level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT));
    }

    // --- Building blocks ---------------------------------------------------------------------

    /** Places a row: source tank at x=1, {@code count} bundles from x=2, and the sink tank after them. */
    private static Line fluidLine(int y, int z, Tier pipeTier, int count, int sourceAmount,
                                  ConnectionMode sourceMode, ConnectionMode sinkMode) {
        BlockPos source = new BlockPos(1, y, z);
        BlockPos sink = new BlockPos(2 + count, y, z);
        placeTank(source, sourceAmount);
        placeTank(sink, 0);
        List<BlockPos> bundles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos pos = new BlockPos(2 + i, y, z);
            placeBundle(pos, PipeType.FLUID, pipeTier);
            bundles.add(pos);
        }
        setMode(bundles.get(0), PipeType.FLUID, Face.WEST, sourceMode);
        setMode(bundles.get(count - 1), PipeType.FLUID, Face.EAST, sinkMode);
        return new Line(source, List.copyOf(bundles), sink);
    }

    /** A single bundle at (5, y, 5) with a source to its west and sinks north, south and east of it. */
    private static Star star(int y, int sourceAmount, int sinkRoom, DistributionMode mode) {
        BlockPos center = new BlockPos(5, y, 5);
        Star star = new Star(center, center.west(), center.north(), center.south(), center.east());
        placeTank(star.source(), sourceAmount);
        for (BlockPos sink : List.of(star.first(), star.second(), star.third())) {
            placeTank(sink, sinkRoom == 0 ? 0 : Tier.COPPER.tankCapacity() - sinkRoom);
        }
        placeBundle(center, PipeType.FLUID, Tier.COPPER);
        entity(center).bundle().setDistribution(PipeType.FLUID.lane(), mode);
        setMode(center, PipeType.FLUID, Face.WEST, ConnectionMode.IMPORT);
        setMode(center, PipeType.FLUID, Face.NORTH, ConnectionMode.EXPORT);
        setMode(center, PipeType.FLUID, Face.SOUTH, ConnectionMode.EXPORT);
        setMode(center, PipeType.FLUID, Face.EAST, ConnectionMode.EXPORT);
        return star;
    }

    /** Chest, three item pipe bundles, chest; the first chest starts with a full stack of cobblestone. */
    private static ItemLine itemLine(int y, int z, Tier tier) {
        BlockPos source = new BlockPos(1, y, z);
        BlockPos sink = new BlockPos(5, y, z);
        level.setBlock(source, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(sink, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ((Container) level.getBlockEntity(source)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        for (int x = 2; x <= 4; x++) {
            placeBundle(new BlockPos(x, y, z), PipeType.ITEM, tier);
        }
        setMode(new BlockPos(2, y, z), PipeType.ITEM, Face.WEST, ConnectionMode.IMPORT);
        setMode(new BlockPos(4, y, z), PipeType.ITEM, Face.EAST, ConnectionMode.EXPORT);
        return new ItemLine(source, sink);
    }

    private static void placeTank(BlockPos pos, int amount) {
        level.setBlock(pos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        if (amount > 0) {
            tank(pos).tank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), amount));
        }
    }

    private static void placeBundle(BlockPos pos, PipeType type, Tier tier) {
        level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        entity(pos).addPipe(type, tier);
    }

    private static void setMode(BlockPos pos, PipeType type, Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity entity = entity(pos);
        entity.bundle().setMode(type.lane(), face, mode);
        entity.refreshConnections();
        entity.changed();
    }

    private static ConduitBundleBlockEntity entity(BlockPos pos) {
        return (ConduitBundleBlockEntity) level.getBlockEntity(pos);
    }

    private static FluxTankBlockEntity tank(BlockPos pos) {
        return (FluxTankBlockEntity) level.getBlockEntity(pos);
    }

    private static int tankAmount(BlockPos pos) {
        return tank(pos).tank().getFluidAmount();
    }

    /** What each of a star's three sinks gained, in the order first, second, third. */
    private static int[] amounts(Star star) {
        return new int[] {tankAmount(star.first()), tankAmount(star.second()), tankAmount(star.third())};
    }

    /** Total number of items in a chest. */
    private static int count(BlockPos pos) {
        Container container = (Container) level.getBlockEntity(pos);
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += container.getItem(slot).getCount();
        }
        return total;
    }
}
