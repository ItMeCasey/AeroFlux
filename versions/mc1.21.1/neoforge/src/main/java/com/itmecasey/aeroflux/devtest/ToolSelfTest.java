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
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;

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

    private static void checkReplaceTier() {
        BlockPos pos = new BlockPos(X, 260, Z);
        ConduitBundleBlockEntity entity = place(pos, PipeType.FLUID, Tier.NETHERITE);
        int lane = PipeType.FLUID.lane();
        entity.bundle().setMode(lane, Face.NORTH, ConnectionMode.EXPORT);
        for (int i = 0; i < 2; i++) {
            entity.bundle().addUpgrade(lane, PipeUpgrades.Kind.SPEED);
            entity.bundle().addUpgrade(lane, PipeUpgrades.Kind.CAPACITY);
        }
        int slotsBefore = entity.bundle().upgradeCount(lane);

        ItemStack wrongType = new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.COPPER).get());
        ItemStack sameTier = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.NETHERITE).get());
        ConduitToolActions.Result none = ConduitToolActions.replaceTier(entity, PipeType.FLUID, wrongType, true);
        ConduitToolActions.Result same = ConduitToolActions.replaceTier(entity, PipeType.FLUID, sameTier, true);
        ConduitToolActions.Result empty = ConduitToolActions.replaceTier(entity, PipeType.FLUID, ItemStack.EMPTY, true);
        SelfTest.check("tool tier: nothing changes without a usable pipe in the off hand",
                !none.success() && !same.success() && !empty.success()
                        && "message.aeroflux.tool_tier_none".equals(none.key())
                        && entity.bundle().tier(lane) == Tier.NETHERITE && wrongType.getCount() == 1 && sameTier.getCount() == 1);

        ItemStack offhand = new ItemStack(ModItems.pipe(PipeType.FLUID, Tier.COPPER).get(), 2);
        ConduitToolActions.Result result = ConduitToolActions.replaceTier(entity, PipeType.FLUID, offhand, true);
        SelfTest.check("tool tier: the pipe takes the new tier", result.success() && entity.bundle().tier(lane) == Tier.COPPER);
        SelfTest.check("tool tier: the face modes are kept", entity.bundle().mode(lane, Face.NORTH) == ConnectionMode.EXPORT);
        SelfTest.check("tool tier: the old tier's pipe item comes back",
                count(result, ModItems.pipe(PipeType.FLUID, Tier.NETHERITE).get()) == 1);
        SelfTest.check("tool tier: one off-hand item is used up", offhand.getCount() == 1);
        SelfTest.check("tool tier: upgrades beyond the new slot count are returned",
                slotsBefore == 4 && entity.bundle().upgradeCount(lane) == PipeUpgrades.slots(Tier.COPPER)
                        && count(result, ModItems.SPEED_UPGRADE.get()) + count(result, ModItems.CAPACITY_UPGRADE.get()) == 1);

        // The upgrades that fit are kept as they were, and creative mode uses nothing up.
        ConduitBundleBlockEntity second = place(new BlockPos(X, 262, Z), PipeType.ITEM, Tier.COPPER);
        second.bundle().addUpgrade(PipeType.ITEM.lane(), PipeUpgrades.Kind.SPEED);
        ItemStack gold = new ItemStack(ModItems.pipe(PipeType.ITEM, Tier.GOLD).get());
        ConduitToolActions.Result creative = ConduitToolActions.replaceTier(second, PipeType.ITEM, gold, false);
        SelfTest.check("tool tier: upgrades that fit are kept and nothing extra comes back",
                second.bundle().tier(PipeType.ITEM.lane()) == Tier.GOLD && second.bundle().speedUpgrades(PipeType.ITEM.lane()) == 1
                        && count(creative, ModItems.SPEED_UPGRADE.get()) == 0);
        SelfTest.check("tool tier: creative mode keeps the off-hand item", gold.getCount() == 1);
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
