package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolActions;
import com.itmecasey.aeroflux.conduit.tool.ToolStackData;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.world.OutlineParticles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

/**
 * The Conduit Tool part of the self-test. The actions need no player, so every scenario is built
 * and checked in one go, each on its own layer high above the world inside the forced chunk. It
 * starts once blocks there really tick, as {@link TransferSelfTest} does, because a freshly
 * forced chunk needs a few ticks first.
 */
final class ToolSelfTest {
    /** Where the scenarios stand: inside the forced chunk, above everything the other tests build. */
    private static final int X = 8;
    private static final int Z = 8;

    private static ServerLevel level;
    private static boolean finished;
    private static boolean started;

    private ToolSelfTest() {
    }

    /** Whether every tool check has run. */
    static boolean finished() {
        return finished;
    }

    /** Remembers the level; the checks run on the first tick that blocks there tick. */
    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        finished = false;
        started = false;
    }

    /** Runs all checks once the chunk is ticking. */
    static void tick() {
        if (level == null || finished || started || !level.shouldTickBlocksAt(new BlockPos(X, 250, Z))) {
            return;
        }
        started = true;
        checkModeCycling();
        checkEdit();
        checkRemove();
        checkRemoveWholeBundle();
        checkRemoveRun();
        checkBadRuns();
        checkReplaceTier();
        checkCopyPaste();
        checkPasteAddsPipes();
        checkPasteRun();
        checkTierUpgradeByHand();
        checkInspect();
        finished = true;
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkModeCycling() {
        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        SelfTest.check("tool: a new tool starts in edit mode", ToolStackData.mode(tool) == ToolMode.EDIT);
        boolean inOrder = true;
        for (int i = 0; i < ToolMode.values().length; i++) {
            ToolMode expected = ToolMode.values()[i];
            inOrder &= ToolStackData.mode(tool) == expected;
            ToolStackData.setMode(tool, ToolStackData.mode(tool).next());
        }
        SelfTest.check("tool: cycling through all seven modes visits each in order", inOrder && ToolMode.values().length == 7);
        SelfTest.check("tool: after seven steps the mode is back at the start", ToolStackData.mode(tool) == ToolMode.EDIT);

        ToolStackData.setMode(tool, ToolMode.PASTE);
        ItemStack reloaded = ItemStack.parse(level.registryAccess(), tool.save(level.registryAccess())).orElse(ItemStack.EMPTY);
        SelfTest.check("tool: the mode survives saving and loading the stack", ToolStackData.mode(reloaded) == ToolMode.PASTE);
        SelfTest.check("tool: the mode survives copying the stack", ToolStackData.mode(tool.copy()) == ToolMode.PASTE);
        SelfTest.check("tool: the tool stacks to one", tool.getMaxStackSize() == 1);
    }

    private static void checkEdit() {
        BlockPos pos = new BlockPos(X, 250, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.COPPER);
        ConnectionMode before = entity.bundle().mode(PipeType.FLUID.lane(), Face.NORTH);
        ConduitToolActions.Result result = ConduitToolActions.edit(entity, PipeType.FLUID, Face.NORTH);
        SelfTest.check("tool edit: a use cycles the mode at the clicked face",
                entity.bundle().mode(PipeType.FLUID.lane(), Face.NORTH) == before.next() && result.success());
        SelfTest.check("tool edit: other faces are left alone",
                entity.bundle().mode(PipeType.FLUID.lane(), Face.SOUTH) == before);
        SelfTest.check("tool edit: the message names the pipe mode", "message.aeroflux.pipe_mode".equals(result.key()));
    }

    private static void checkRemove() {
        BlockPos pos = new BlockPos(X, 252, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.IRON);
        entity.addPipe(PipeType.ITEM, Tier.GOLD);
        entity.bundle().addUpgrade(PipeType.FLUID.lane(), PipeUpgrades.Kind.SPEED);
        entity.bundle().addUpgrade(PipeType.FLUID.lane(), PipeUpgrades.Kind.SPEED);
        entity.bundle().addUpgrade(PipeType.FLUID.lane(), PipeUpgrades.Kind.CAPACITY);

        ConduitToolActions.Result result = ConduitToolActions.remove(level, pos, entity, PipeType.FLUID, false);
        SelfTest.check("tool remove: the nearest lane is taken out of the bundle",
                !entity.bundle().set().contains(PipeType.FLUID) && entity.bundle().set().contains(PipeType.ITEM));
        SelfTest.check("tool remove: the pipe item of its tier comes back",
                count(result, ModItems.pipe(PipeType.FLUID, Tier.IRON).get()) == 1
                        && count(result, ModItems.pipe(PipeType.FLUID, Tier.COPPER).get()) == 0);
        SelfTest.check("tool remove: its upgrades come back",
                count(result, ModItems.SPEED_UPGRADE.get()) == 2 && count(result, ModItems.CAPACITY_UPGRADE.get()) == 1);
        SelfTest.check("tool remove: the bundle stays while another pipe is in it", !level.getBlockState(pos).isAir());

        ConduitToolActions.Result last = ConduitToolActions.remove(level, pos, entity, PipeType.ITEM, false);
        SelfTest.check("tool remove: taking the last pipe removes the block", level.getBlockState(pos).isAir());
        SelfTest.check("tool remove: the last pipe's item comes back",
                count(last, ModItems.pipe(PipeType.ITEM, Tier.GOLD).get()) == 1);
    }

    private static void checkRemoveWholeBundle() {
        BlockPos pos = new BlockPos(X, 254, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.COPPER);
        entity.addPipe(PipeType.ENERGY, Tier.DIAMOND);
        entity.addPipe(PipeType.ME, Tier.COPPER);
        entity.bundle().addUpgrade(PipeType.ENERGY.lane(), PipeUpgrades.Kind.CAPACITY);

        ConduitToolActions.Result result = ConduitToolActions.remove(level, pos, entity, PipeType.FLUID, true);
        SelfTest.check("tool remove: sneaking takes the whole bundle", level.getBlockState(pos).isAir());
        SelfTest.check("tool remove: sneaking returns every pipe",
                count(result, ModItems.pipe(PipeType.FLUID, Tier.COPPER).get()) == 1
                        && count(result, ModItems.pipe(PipeType.ENERGY, Tier.DIAMOND).get()) == 1
                        && count(result, ModItems.pipe(PipeType.ME, Tier.COPPER).get()) == 1);
        SelfTest.check("tool remove: sneaking returns the upgrades too", count(result, ModItems.CAPACITY_UPGRADE.get()) == 1);
    }

    private static void checkRemoveRun() {
        int y = 256;
        // Five bundles in a row, all with fluid; the second and fourth also carry an item pipe.
        for (int i = 0; i < 5; i++) {
            ConduitBundleBlockEntity entity = place(new BlockPos(X + i, y, Z), PipeType.FLUID, Tier.COPPER);
            if (i % 2 == 1) {
                entity.addPipe(PipeType.ITEM, Tier.COPPER);
            }
        }
        BlockPos beside = new BlockPos(X + 2, y, Z + 3);
        place(beside, PipeType.FLUID, Tier.COPPER);

        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        ToolStackData.setMode(tool, ToolMode.REMOVE_RUN);
        ConduitToolActions.Result start = ConduitToolActions.removeRun(level, tool, new BlockPos(X, y, Z), PipeType.FLUID);
        SelfTest.check("tool run: the first use remembers the start",
                "message.aeroflux.tool_run_start".equals(start.key()) && ToolStackData.runStart(tool) != null
                        && ToolStackData.runStart(tool).pipe() == PipeType.FLUID);
        SelfTest.check("tool run: the first use removes nothing", level.getBlockEntity(new BlockPos(X, y, Z)) != null);

        ConduitToolActions.Result end = ConduitToolActions.removeRun(level, tool, new BlockPos(X + 4, y, Z), PipeType.ITEM);
        boolean fluidGone = true;
        for (int i = 0; i < 5; i++) {
            ConduitBundleBlockEntity entity = bundleAt(new BlockPos(X + i, y, Z));
            fluidGone &= entity == null || !entity.bundle().set().contains(PipeType.FLUID);
        }
        SelfTest.check("tool run: the remembered type is removed along the whole line", fluidGone);
        SelfTest.check("tool run: the count of removed pipes is reported",
                "message.aeroflux.tool_run_removed".equals(end.key()) && Integer.valueOf(5).equals(end.args().get(0)));
        SelfTest.check("tool run: the removed pipes come back as items",
                count(end, ModItems.pipe(PipeType.FLUID, Tier.COPPER).get()) == 5);
        SelfTest.check("tool run: bundles left empty are removed",
                level.getBlockState(new BlockPos(X, y, Z)).isAir() && level.getBlockState(new BlockPos(X + 2, y, Z)).isAir()
                        && level.getBlockState(new BlockPos(X + 4, y, Z)).isAir());
        SelfTest.check("tool run: other pipe types in the run stay",
                hasPipe(new BlockPos(X + 1, y, Z), PipeType.ITEM) && hasPipe(new BlockPos(X + 3, y, Z), PipeType.ITEM));
        SelfTest.check("tool run: a bundle off the line is untouched", hasPipe(beside, PipeType.FLUID));
        SelfTest.check("tool run: the remembered start is cleared afterwards", ToolStackData.runStart(tool) == null);

        // A pending start is dropped when the mode changes.
        ConduitToolActions.removeRun(level, tool, beside, PipeType.FLUID);
        boolean pending = ToolStackData.runStart(tool) != null;
        ToolStackData.setMode(tool, ToolMode.EDIT);
        SelfTest.check("tool run: changing the mode forgets a pending start", pending && ToolStackData.runStart(tool) == null);
    }

    private static void checkBadRuns() {
        int y = 258;
        for (int i = 0; i < 3; i++) {
            place(new BlockPos(X + i, y, Z), PipeType.FLUID, Tier.COPPER);
        }
        place(new BlockPos(X + 2, y, Z + 2), PipeType.FLUID, Tier.COPPER);

        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        ToolStackData.setMode(tool, ToolMode.REMOVE_RUN);
        ConduitToolActions.removeRun(level, tool, new BlockPos(X, y, Z), PipeType.FLUID);
        ConduitToolActions.Result bent = ConduitToolActions.removeRun(level, tool, new BlockPos(X + 2, y, Z + 2), PipeType.FLUID);
        SelfTest.check("tool run: a bent run is refused", !bent.success() && "message.aeroflux.tool_run_invalid".equals(bent.key()));
        SelfTest.check("tool run: a refused run removes nothing",
                hasPipe(new BlockPos(X, y, Z), PipeType.FLUID) && hasPipe(new BlockPos(X + 1, y, Z), PipeType.FLUID)
                        && hasPipe(new BlockPos(X + 2, y, Z), PipeType.FLUID) && hasPipe(new BlockPos(X + 2, y, Z + 2), PipeType.FLUID));

        ConduitToolActions.removeRun(level, tool, new BlockPos(X, y, Z), PipeType.FLUID);
        BlockPos far = new BlockPos(X + RunPlanner.MAX_LENGTH, y, Z);
        ConduitToolActions.Result tooLong = ConduitToolActions.removeRun(level, tool, far, PipeType.FLUID);
        SelfTest.check("tool run: an overlong run is refused", !tooLong.success() && tooLong.items().isEmpty());
        SelfTest.check("tool run: a refused run clears the start so the next use begins again",
                ToolStackData.runStart(tool) == null && hasPipe(new BlockPos(X, y, Z), PipeType.FLUID));
    }

    /** A higher-tier pipe used on a bundle raises the pipe already there; nothing else does. */
    private static void checkReplaceTier() {
        BlockPos pos = new BlockPos(X, 260, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.IRON);
        int lane = PipeType.FLUID.lane();
        entity.bundle().setMode(lane, Face.NORTH, ConnectionMode.EXPORT);
        entity.bundle().addUpgrade(lane, PipeUpgrades.Kind.SPEED);
        entity.bundle().addUpgrade(lane, PipeUpgrades.Kind.CAPACITY);

        ItemStack wrongType = new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.DIAMOND).get());
        ItemStack sameTier = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.IRON).get());
        ItemStack lowerTier = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.COPPER).get());
        ConduitToolActions.Result none = ConduitToolActions.upgradeTier(entity, PipeType.FLUID, wrongType, true);
        ConduitToolActions.Result same = ConduitToolActions.upgradeTier(entity, PipeType.FLUID, sameTier, true);
        ConduitToolActions.Result lower = ConduitToolActions.upgradeTier(entity, PipeType.FLUID, lowerTier, true);
        SelfTest.check("pipe tier: the same or a lower tier, or another kind, changes nothing",
                !none.success() && !same.success() && !lower.success()
                        && "message.aeroflux.pipe_tier_not_higher".equals(lower.key())
                        && entity.bundle().tier(lane) == Tier.IRON
                        && wrongType.getCount() == 1 && sameTier.getCount() == 1 && lowerTier.getCount() == 1);

        ItemStack held = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.DIAMOND).get(), 2);
        ConduitToolActions.Result result = ConduitToolActions.upgradeTier(entity, PipeType.FLUID, held, true);
        SelfTest.check("pipe tier: a higher tier is taken, more than one step at once", result.success() && entity.bundle().tier(lane) == Tier.DIAMOND);
        SelfTest.check("pipe tier: the face modes are kept", entity.bundle().mode(lane, Face.NORTH) == ConnectionMode.EXPORT);
        SelfTest.check("pipe tier: the upgrades stay installed",
                entity.bundle().speedUpgrades(lane) == 1 && entity.bundle().capacityUpgrades(lane) == 1);
        SelfTest.check("pipe tier: the old pipe's item comes back", count(result, ModItems.pipe(PipeType.FLUID, Tier.IRON).get()) == 1);
        SelfTest.check("pipe tier: one held pipe is used up", held.getCount() == 1);

        // Creative mode uses nothing up, and a kind the bundle lacks is still simply added.
        ConduitBundleBlockEntity second = place(new BlockPos(X, 262, Z), PipeType.ITEM, Tier.COPPER);
        ItemStack gold = new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.GOLD).get());
        ConduitToolActions.upgradeTier(second, PipeType.ITEM, gold, false);
        SelfTest.check("pipe tier: creative mode keeps the held pipe", second.bundle().tier(PipeType.ITEM.lane()) == Tier.GOLD && gold.getCount() == 1);
        SelfTest.check("pipe tier: a missing kind is still added as a new pipe", second.addPipe(PipeType.ENERGY, Tier.IRON)
                && second.bundle().tier(PipeType.ENERGY.lane()) == Tier.IRON);
    }

    private static void checkCopyPaste() {
        BlockPos sourcePos = new BlockPos(X, 264, Z);
        ConduitBundleBlockEntity source = place(sourcePos, PipeType.FLUID, Tier.COPPER);
        source.addPipe(PipeType.ITEM, Tier.COPPER);
        source.bundle().setMode(PipeType.FLUID.lane(), Face.NORTH, ConnectionMode.EXPORT);
        source.bundle().setMode(PipeType.FLUID.lane(), Face.EAST, ConnectionMode.IMPORT);
        source.bundle().setMode(PipeType.ITEM.lane(), Face.SOUTH, ConnectionMode.OFF);
        source.bundle().setDistribution(PipeType.FLUID.lane(), DistributionMode.LAST_TO_FIRST);

        BlockPos targetPos = new BlockPos(X, 266, Z);
        ConduitBundleBlockEntity target = place(targetPos, PipeType.FLUID, Tier.IRON);
        target.addPipe(PipeType.ENERGY, Tier.COPPER);

        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        int[] untouched = target.bundle().serialize();
        ConduitToolActions.Result nothing = ConduitToolActions.paste(tool, target);
        SelfTest.check("tool paste: with nothing copied nothing changes",
                !nothing.success() && "message.aeroflux.tool_nothing_copied".equals(nothing.key())
                        && Arrays.equals(untouched, target.bundle().serialize()));

        ConduitToolActions.Result copied = ConduitToolActions.copy(tool, source);
        SelfTest.check("tool copy: the bundle's state is stored on the tool",
                copied.success() && Arrays.equals(ToolStackData.copiedBundle(tool), source.bundle().serialize()));

        ConduitToolActions.Result pasted = ConduitToolActions.paste(tool, target);
        int fluid = PipeType.FLUID.lane();
        SelfTest.check("tool paste: modes land on the shared pipe",
                target.bundle().mode(fluid, Face.NORTH) == ConnectionMode.EXPORT
                        && target.bundle().mode(fluid, Face.EAST) == ConnectionMode.IMPORT);
        SelfTest.check("tool paste: the distribution mode is applied",
                target.bundle().distribution(fluid) == DistributionMode.LAST_TO_FIRST);
        SelfTest.check("tool paste: only the shared pipe is counted as changed", Integer.valueOf(1).equals(pasted.args().get(0)));
        SelfTest.check("tool paste: pipes the copy does not have are left alone",
                target.bundle().mode(PipeType.ENERGY.lane(), Face.NORTH) == ConnectionMode.OPEN
                        && !target.bundle().set().contains(PipeType.ITEM));
        SelfTest.check("tool paste: tiers are not copied", target.bundle().tier(fluid) == Tier.IRON);
        SelfTest.check("tool copy: copying leaves the source as it was",
                source.bundle().mode(fluid, Face.NORTH) == ConnectionMode.EXPORT && source.bundle().set().contains(PipeType.ITEM));
    }

    /** A paste adds the pipes the bundle lacks, at the copy's tier and with its upgrades, from the supply. */
    private static void checkPasteAddsPipes() {
        int y = 272;
        ConduitBundleBlockEntity source = place(new BlockPos(X, y, Z), PipeType.FLUID, Tier.GOLD);
        source.addPipe(PipeType.ITEM, Tier.IRON);
        int item = PipeType.ITEM.lane();
        source.bundle().addUpgrade(item, PipeUpgrades.Kind.SPEED);
        source.bundle().setMode(item, Face.UP, ConnectionMode.EXPORT);
        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        ConduitToolActions.copy(tool, source);

        ConduitBundleBlockEntity target = place(new BlockPos(X + 3, y, Z), PipeType.FLUID, Tier.COPPER);
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.IRON).get()), new ItemStack(ModItems.SPEED_UPGRADE.get(), 3)));
        ConduitToolActions.Result result = ConduitToolActions.paste(tool, target, supplyOf(inventory));
        SelfTest.check("tool paste: a pipe the bundle lacks is added at the copied tier",
                "message.aeroflux.tool_pasted_added".equals(result.key()) && target.bundle().set().contains(PipeType.ITEM)
                        && target.bundle().tier(item) == Tier.IRON);
        SelfTest.check("tool paste: the added pipe gets the copied upgrades and modes",
                target.bundle().speedUpgrades(item) == 1 && target.bundle().mode(item, Face.UP) == ConnectionMode.EXPORT);
        SelfTest.check("tool paste: the pipe and upgrade are taken from the supply",
                inventory.get(0).isEmpty() && inventory.get(1).getCount() == 2);
        SelfTest.check("tool paste: a pipe the bundle already has keeps its tier",
                target.bundle().tier(PipeType.FLUID.lane()) == Tier.COPPER);

        ConduitBundleBlockEntity poor = place(new BlockPos(X + 6, y, Z), PipeType.FLUID, Tier.COPPER);
        ConduitToolActions.Result missing = ConduitToolActions.paste(tool, poor, supplyOf(new ArrayList<>()));
        SelfTest.check("tool paste: a pipe missing from the supply is reported and not added",
                "message.aeroflux.tool_pasted_missing".equals(missing.key()) && Integer.valueOf(1).equals(missing.args().get(2))
                        && !poor.bundle().set().contains(PipeType.ITEM));
    }

    /** Sneak-use twice in paste mode: the copy is pasted onto every bundle and free spot of the line. */
    private static void checkPasteRun() {
        int y = 274;
        ConduitBundleBlockEntity source = place(new BlockPos(X, y - 2, Z + 4), PipeType.FLUID, Tier.GOLD);
        source.addPipe(PipeType.ITEM, Tier.IRON);
        source.bundle().setMode(PipeType.ITEM.lane(), Face.UP, ConnectionMode.EXPORT);
        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        ConduitToolActions.copy(tool, source);
        ToolStackData.setMode(tool, ToolMode.PASTE);

        place(new BlockPos(X, y, Z), PipeType.ENERGY, Tier.COPPER);
        BlockPos stone = new BlockPos(X + 2, y, Z);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        ConduitToolActions.Result start = ConduitToolActions.pasteRun(level, tool, new BlockPos(X, y, Z),
                ConduitToolActions.Supply.UNLIMITED);
        SelfTest.check("tool paste run: the first sneak-use only marks the start",
                "message.aeroflux.tool_paste_run_start".equals(start.key()) && !hasPipe(new BlockPos(X, y, Z), PipeType.FLUID));
        ConduitToolActions.Result run = ConduitToolActions.pasteRun(level, tool, new BlockPos(X + 4, y, Z),
                ConduitToolActions.Supply.UNLIMITED);
        SelfTest.check("tool paste run: the run is pasted onto four spots, three of them new bundles",
                "message.aeroflux.tool_paste_run".equals(run.key()) && Integer.valueOf(4).equals(run.args().get(0))
                        && Integer.valueOf(3).equals(run.args().get(1)));
        boolean everyBundle = true;
        for (int i : new int[] {0, 1, 3, 4}) {
            ConduitBundleBlockEntity entity = bundleAt(new BlockPos(X + i, y, Z));
            everyBundle &= entity != null && entity.bundle().set().contains(PipeType.FLUID)
                    && entity.bundle().set().contains(PipeType.ITEM)
                    && entity.bundle().mode(PipeType.ITEM.lane(), Face.UP) == ConnectionMode.EXPORT;
        }
        SelfTest.check("tool paste run: every spot holds the copied pipes and modes", everyBundle
                && bundleAt(new BlockPos(X + 1, y, Z)).bundle().tier(PipeType.FLUID.lane()) == Tier.GOLD);
        SelfTest.check("tool paste run: a bundle already on the line keeps its own pipes",
                hasPipe(new BlockPos(X, y, Z), PipeType.ENERGY));
        SelfTest.check("tool paste run: a solid block on the line is left alone", level.getBlockState(stone).is(Blocks.STONE));

        // With nothing to build from, empty spots stay empty.
        int emptyY = y + 2;
        ConduitToolActions.pasteRun(level, tool, new BlockPos(X, emptyY, Z), ConduitToolActions.Supply.NONE);
        ConduitToolActions.Result dry = ConduitToolActions.pasteRun(level, tool, new BlockPos(X + 2, emptyY, Z),
                ConduitToolActions.Supply.NONE);
        SelfTest.check("tool paste run: without pipes in the supply no bundle is left behind",
                !dry.success() && level.getBlockState(new BlockPos(X, emptyY, Z)).isAir()
                        && level.getBlockState(new BlockPos(X + 1, emptyY, Z)).isAir());
    }

    /**
     * The tier upgrade by hand, through the pipe item itself as a player's click reaches it: the
     * bundle keeps its connections, modes and upgrades, and a lower tier never downgrades.
     */
    private static void checkTierUpgradeByHand() {
        int y = 278;
        // Forge's item use hook needs a player; this one has no connection, like the screen checks' player.
        ScreenSelfTest.TestPlayer player = new ScreenSelfTest.TestPlayer(level);
        BlockPos pos = new BlockPos(X, y, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.COPPER);
        place(pos.east(), PipeType.FLUID, Tier.COPPER);
        int lane = PipeType.FLUID.lane();
        entity.bundle().addUpgrade(lane, PipeUpgrades.Kind.SPEED);
        setMode(pos, PipeType.FLUID, Face.NORTH, ConnectionMode.OFF);
        boolean joinedBefore = entity.bundle().isConnected(lane, Face.EAST);

        ItemStack iron = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.IRON).get(), 2);
        iron.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, iron,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        SelfTest.check("pipe tier by hand: a higher-tier pipe used on the bundle raises the pipe in place",
                entity.bundle().tier(lane) == Tier.IRON && iron.getCount() == 1 && level.getBlockState(pos.above()).isAir());
        SelfTest.check("pipe tier by hand: connections, modes and upgrades are kept",
                joinedBefore && entity.bundle().isConnected(lane, Face.EAST) && entity.bundle().mode(lane, Face.NORTH) == ConnectionMode.OFF
                        && entity.bundle().speedUpgrades(lane) == 1);

        ItemStack copper = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.COPPER).get());
        copper.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, copper,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        SelfTest.check("pipe tier by hand: a lower tier never downgrades; it is laid as a new bundle beside",
                entity.bundle().tier(lane) == Tier.IRON && hasPipe(pos.above(), PipeType.FLUID)
                        && bundleAt(pos.above()).bundle().tier(lane) == Tier.COPPER);
    }

    private static void checkInspect() {
        // Fluid: a source tank, three bundles in a row, a sink at the end and a second sink beside the middle one.
        int y = 268;
        BlockPos sourceTank = new BlockPos(X, y, Z);
        placeTank(sourceTank, 500);
        placeTank(new BlockPos(X + 4, y, Z), 0);
        placeTank(new BlockPos(X + 2, y, Z + 1), 0);
        for (int i = 1; i <= 3; i++) {
            place(new BlockPos(X + i, y, Z), PipeType.FLUID, Tier.COPPER);
        }
        setMode(new BlockPos(X + 1, y, Z), PipeType.FLUID, Face.WEST, ConnectionMode.IMPORT);
        setMode(new BlockPos(X + 2, y, Z), PipeType.FLUID, Face.SOUTH, ConnectionMode.EXPORT);
        setMode(new BlockPos(X + 3, y, Z), PipeType.FLUID, Face.EAST, ConnectionMode.EXPORT);

        ConduitToolActions.Result result = ConduitToolActions.inspect(level, new BlockPos(X + 2, y, Z), PipeType.FLUID);
        SelfTest.check("tool inspect: the report is the inspect message", result.success() && "message.aeroflux.tool_inspect".equals(result.key()));
        SelfTest.check("tool inspect: it counts the bundles of the network", Integer.valueOf(3).equals(result.args().get(1)));
        SelfTest.check("tool inspect: it counts one source", Integer.valueOf(1).equals(result.args().get(2)));
        SelfTest.check("tool inspect: it counts two destinations", Integer.valueOf(2).equals(result.args().get(3)));
        SelfTest.check("tool inspect: a copper fluid network is limited to 100 mB per tick",
                speedKey(result).equals("message.aeroflux.tool_speed_milli") && speedArgs(result)[0].equals(100L));

        ConduitToolActions.Result fromEnd = ConduitToolActions.inspect(level, new BlockPos(X + 1, y, Z), PipeType.FLUID);
        SelfTest.check("tool inspect: any bundle of the network gives the same answer",
                fromEnd.args().get(1).equals(result.args().get(1)) && fromEnd.args().get(3).equals(result.args().get(3)));

        // The outline: every bundle of the network, and the edges two neighbors share drawn once.
        List<BlockPos> members = ConduitToolActions.networkMembers(level, new BlockPos(X + 2, y, Z), PipeType.FLUID);
        SelfTest.check("tool inspect: the outline covers every bundle of the network",
                members.size() == 3 && members.contains(new BlockPos(X + 1, y, Z)) && members.contains(new BlockPos(X + 3, y, Z)));
        // A block's outline at half a block has its 8 corners and 12 edge middles; three in a row share two faces.
        SelfTest.check("tool inspect: the outline of one block has 20 points, three in a row 44",
                OutlineParticles.blockOutlines(List.of(new BlockPos(X, y, Z))).size() == 20
                        && OutlineParticles.blockOutlines(members).size() == 44);
        SelfTest.check("tool inspect: a pipe in no network has no outline",
                ConduitToolActions.networkMembers(level, new BlockPos(X, y, Z), PipeType.FLUID).isEmpty());

        // Items: one bundle with a chest it pulls from.
        int itemY = 270;
        BlockPos chest = new BlockPos(X, itemY, Z);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos itemBundle = new BlockPos(X + 1, itemY, Z);
        place(itemBundle, PipeType.ITEM, Tier.COPPER);
        setMode(itemBundle, PipeType.ITEM, Face.WEST, ConnectionMode.IMPORT);
        ConduitToolActions.Result items = ConduitToolActions.inspect(level, itemBundle, PipeType.ITEM);
        SelfTest.check("tool inspect: an item network reports its batch size and interval",
                speedKey(items).equals("message.aeroflux.tool_speed_items") && speedArgs(items)[0].equals(1)
                        && speedArgs(items)[1].equals(20));
        SelfTest.check("tool inspect: a lone item bundle has one bundle and one source",
                Integer.valueOf(1).equals(items.args().get(1)) && Integer.valueOf(1).equals(items.args().get(2))
                        && Integer.valueOf(0).equals(items.args().get(3)));
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static ConduitBundleBlockEntity place(BlockPos pos, PipeType type, Tier tier) {
        level.setBlock(pos, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
        ConduitBundleBlockEntity entity = (ConduitBundleBlockEntity) level.getBlockEntity(pos);
        entity.addPipe(type, tier);
        return entity;
    }

    private static void setMode(BlockPos pos, PipeType type, Face face, ConnectionMode mode) {
        ConduitBundleBlockEntity entity = bundleAt(pos);
        entity.bundle().setMode(type.lane(), face, mode);
        entity.refreshConnections();
        entity.changed();
    }

    private static void placeTank(BlockPos pos, int amount) {
        level.setBlock(pos, ModBlocks.tank(Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        if (amount > 0) {
            ((FluxTankBlockEntity) level.getBlockEntity(pos)).tank().setFluid(new FluidStack(ModFluids.AERO_FLUX.get(), amount));
        }
    }

    private static ConduitBundleBlockEntity bundleAt(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity entity ? entity : null;
    }

    private static boolean hasPipe(BlockPos pos, PipeType type) {
        ConduitBundleBlockEntity entity = bundleAt(pos);
        return entity != null && entity.bundle().set().contains(type);
    }

    /** A supply that takes from a list of stacks, the way a player's inventory would. */
    private static ConduitToolActions.Supply supplyOf(List<ItemStack> stacks) {
        return item -> {
            for (ItemStack stack : stacks) {
                if (stack.is(item)) {
                    stack.shrink(1);
                    return true;
                }
            }
            return false;
        };
    }

    /** How many of one item a result hands back, over all its stacks. */
    private static int count(ConduitToolActions.Result result, Item item) {
        int total = 0;
        for (ItemStack stack : result.items()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The last argument of an inspect result is the speed component. */
    private static String speedKey(ConduitToolActions.Result result) {
        return ((TranslatableContents) speed(result).getContents()).getKey();
    }

    private static Object[] speedArgs(ConduitToolActions.Result result) {
        return ((TranslatableContents) speed(result).getContents()).getArgs();
    }

    private static Component speed(ConduitToolActions.Result result) {
        List<Object> args = result.args();
        return (Component) args.get(args.size() - 1);
    }
}
