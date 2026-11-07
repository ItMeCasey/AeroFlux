package com.itmecasey.aeroflux.conduit.tool;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlock;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.conduit.transfer.LaneNetwork;
import com.itmecasey.aeroflux.conduit.transfer.NetworkEndpoint;
import com.itmecasey.aeroflux.conduit.transfer.TransferNetworks;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.BundleConfig;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * What each mode of the Conduit Tool does to the world. Everything here works on a level, a
 * position or bundle and plain arguments, never on a player (a player only comes in as the
 * {@link Supply} a paste draws pipes from), so it can be run from the self-test. The item class
 * only finds the target, calls one of these and hands the result to the player.
 */
public final class ConduitToolActions {
    private ConduitToolActions() {
    }

    /**
     * What an action did.
     *
     * @param success whether anything changed or was reported as intended
     * @param key     translation key of the message to show
     * @param args    arguments for the message
     * @param items   items to give back to the player (or drop if the inventory is full)
     */
    public record Result(boolean success, String key, List<Object> args, List<ItemStack> items) {
        static Result of(boolean success, String key, Object... args) {
            return new Result(success, key, List.of(args), List.of());
        }

        static Result of(boolean success, String key, List<ItemStack> items, Object... args) {
            return new Result(success, key, List.of(args), items);
        }

        /** The message as a chat component. */
        public Component message() {
            return Component.translatable(key, args.toArray());
        }
    }

    // --- Edit --------------------------------------------------------------------------------

    /** Moves the pipe on to the next connection mode at one face, as the wrench does. */
    public static Result edit(ConduitBundleBlockEntity entity, PipeType pipe, Face face) {
        ConnectionMode next = entity.bundle().mode(pipe.lane(), face).next();
        entity.bundle().setMode(pipe.lane(), face, next);
        entity.refreshConnections();
        entity.changed();
        return Result.of(true, "message.aeroflux.pipe_mode", pipeName(pipe), faceName(face),
                Component.translatable("connection_mode.aeroflux." + lower(next)));
    }

    // --- Remove ------------------------------------------------------------------------------

    /**
     * Takes one pipe out of a bundle, or the whole bundle. A bundle that ends up empty is removed.
     *
     * @param wholeBundle true to take every pipe, false for just {@code pipe}
     */
    public static Result remove(Level level, BlockPos pos, ConduitBundleBlockEntity entity, PipeType pipe,
                                boolean wholeBundle) {
        List<ItemStack> items = new ArrayList<>();
        int pipes = 0;
        if (wholeBundle) {
            for (PipeType type : List.copyOf(entity.bundle().set().types())) {
                takeLane(entity, type, items);
                pipes++;
            }
        } else {
            takeLane(entity, pipe, items);
            pipes = 1;
        }
        settle(level, pos, entity);
        if (wholeBundle) {
            return Result.of(true, "message.aeroflux.tool_removed_bundle", items, pipes);
        }
        return Result.of(true, "message.aeroflux.tool_removed", items, pipeName(pipe));
    }

    /**
     * Removes the pipe in its lane and adds what it was worth (the pipe item, its upgrades and
     * the Filter and Priority upgrades at its faces) to {@code into}.
     */
    private static void takeLane(ConduitBundleBlockEntity entity, PipeType type, List<ItemStack> into) {
        BundleState bundle = entity.bundle();
        int lane = type.lane();
        into.add(new ItemStack(ModItems.pipe(type, bundle.tier(lane)).get()));
        addUpgrades(into, bundle.speedUpgrades(lane), bundle.capacityUpgrades(lane));
        ConduitBundleBlock.addEndpointUpgrades(into, entity.endpoints(), lane);
        entity.endpoints().clearLane(lane);
        bundle.removeLane(lane);
    }

    private static void addUpgrades(List<ItemStack> into, int speed, int capacity) {
        if (speed > 0) {
            into.add(new ItemStack(ModItems.SPEED_UPGRADE.get(), speed));
        }
        if (capacity > 0) {
            into.add(new ItemStack(ModItems.CAPACITY_UPGRADE.get(), capacity));
        }
    }

    /** After a bundle lost pipes: delete the block if it is empty, otherwise save and update the neighbors. */
    private static void settle(Level level, BlockPos pos, ConduitBundleBlockEntity entity) {
        if (entity.bundle().set().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            entity.refreshConnections();
            entity.changed();
        }
    }

    // --- Remove run --------------------------------------------------------------------------

    /**
     * Handles one use in "remove run" mode. The first use remembers where the run starts and
     * which pipe it is about; the second removes that pipe type from every bundle on the straight
     * line between the two clicks.
     *
     * @param tool the tool stack, which holds the remembered start
     * @param pos  the bundle that was clicked
     * @param pipe the pipe nearest the click
     */
    public static Result removeRun(ServerLevel level, ItemStack tool, BlockPos pos, PipeType pipe) {
        return removeRun(level, tool, pos, pipe, at -> true);
    }

    /**
     * The same, but only bundles at positions {@code allowed} accepts are changed; the rest of the
     * run is skipped (a claim mod's protection, in the game).
     */
    public static Result removeRun(ServerLevel level, ItemStack tool, BlockPos pos, PipeType pipe, Predicate<BlockPos> allowed) {
        String dimension = level.dimension().identifier().toString();
        ToolStackData.RunStart start = ToolStackData.runStart(tool);
        if (start == null || !start.dimension().equals(dimension)) {
            ToolStackData.setRunStart(tool,
                    new ToolStackData.RunStart(new RunPlanner.Pos(pos.getX(), pos.getY(), pos.getZ()), pipe, dimension));
            return Result.of(true, "message.aeroflux.tool_run_start", pipeName(pipe));
        }

        ToolStackData.clearRunStart(tool);
        List<RunPlanner.Pos> run = RunPlanner.line(start.pos(), new RunPlanner.Pos(pos.getX(), pos.getY(), pos.getZ()));
        if (run.isEmpty()) {
            return Result.of(false, "message.aeroflux.tool_run_invalid", RunPlanner.MAX_LENGTH);
        }

        List<ItemStack> items = new ArrayList<>();
        int removed = 0;
        for (RunPlanner.Pos step : run) {
            BlockPos at = new BlockPos(step.x(), step.y(), step.z());
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof ConduitBundleBlockEntity entity
                    && entity.bundle().set().contains(start.pipe()) && allowed.test(at)) {
                takeLane(entity, start.pipe(), items);
                settle(level, at, entity);
                removed++;
            }
        }
        return Result.of(true, "message.aeroflux.tool_run_removed", items, removed, pipeName(start.pipe()));
    }

    // --- Tier upgrade ------------------------------------------------------------------------

    /**
     * Raises a pipe in a bundle to the tier of a held pipe item; {@link PipeItem} calls this when a
     * player uses a higher-tier pipe on a bundle that already holds that kind. Only upgrades: a
     * held pipe of the same or a lower tier changes nothing. Connections, modes and distribution
     * stay; upgrades are put back while there is room and the rest are returned, along with the
     * old pipe's item.
     *
     * @param held    the pipe item that supplies the new tier
     * @param consume whether to use up one of {@code held} (false in creative mode)
     */
    public static Result upgradeTier(ConduitBundleBlockEntity entity, PipeType pipe, ItemStack held, boolean consume) {
        BundleState bundle = entity.bundle();
        int lane = pipe.lane();
        if (!(held.getItem() instanceof PipeItem item) || item.type() != pipe || !PipeTiers.isTiered(pipe)
                || !bundle.set().contains(pipe) || item.tier().ordinal() <= bundle.tier(lane).ordinal()) {
            return Result.of(false, "message.aeroflux.pipe_tier_not_higher", pipeName(pipe), tierName(bundle.tier(lane)));
        }

        Tier oldTier = bundle.tier(lane);
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.pipe(pipe, oldTier).get()));

        int speed = bundle.removeUpgrades(lane, PipeUpgrades.Kind.SPEED);
        int capacity = bundle.removeUpgrades(lane, PipeUpgrades.Kind.CAPACITY);
        bundle.setTier(lane, item.tier());
        // Put the upgrades back while there is room; whatever is left over goes back to the player.
        while (speed > 0 && bundle.addUpgrade(lane, PipeUpgrades.Kind.SPEED)) {
            speed--;
        }
        while (capacity > 0 && bundle.addUpgrade(lane, PipeUpgrades.Kind.CAPACITY)) {
            capacity--;
        }
        addUpgrades(items, speed, capacity);

        if (consume) {
            held.shrink(1);
        }
        entity.refreshConnections();
        entity.changed();
        return Result.of(true, "message.aeroflux.pipe_tier_upgraded", items, pipeName(pipe), tierName(item.tier()));
    }

    // --- Copy and paste ----------------------------------------------------------------------

    /**
     * Where a paste gets the pipes and upgrades it adds to a bundle. A player's supply takes them
     * out of the inventory; in creative mode it has everything.
     */
    @FunctionalInterface
    public interface Supply {
        /** Gives nothing, so a paste only changes settings. */
        Supply NONE = item -> false;
        /** Has everything, for creative mode. */
        Supply UNLIMITED = item -> true;

        /** Takes one of an item; false if there is none. */
        boolean take(Item item);
    }

    /** The supply of a player: their inventory, or everything in creative mode. */
    public static Supply supplyOf(Player player) {
        if (player == null) {
            return Supply.NONE;
        }
        if (player.hasInfiniteMaterials()) {
            return Supply.UNLIMITED;
        }
        return item -> {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item)) {
                    stack.shrink(1);
                    return true;
                }
            }
            return false;
        };
    }

    /** Remembers the bundle's state on the tool. */
    public static Result copy(ItemStack tool, ConduitBundleBlockEntity entity) {
        ToolStackData.setCopiedBundle(tool, entity.bundle().serialize());
        return Result.of(true, "message.aeroflux.tool_copied", entity.bundle().set().size());
    }

    /** Applies the remembered modes and distribution to the pipes this bundle shares with the copy, adding nothing. */
    public static Result paste(ItemStack tool, ConduitBundleBlockEntity entity) {
        return paste(tool, entity, Supply.NONE);
    }

    /**
     * Pastes the remembered bundle. Pipes the copy has and the bundle lacks are added at the
     * copy's tier, with its Speed and Capacity upgrades, as far as {@code supply} has the items;
     * then every shared pipe takes the copy's modes and distribution. Pipes the bundle already has
     * keep their tier: changing it would mean handing items back and forth, which the tier upgrade
     * by hand already does.
     */
    public static Result paste(ItemStack tool, ConduitBundleBlockEntity entity, Supply supply) {
        int[] stored = ToolStackData.copiedBundle(tool);
        if (stored == null) {
            return Result.of(false, "message.aeroflux.tool_nothing_copied");
        }
        PasteCount count = pasteInto(BundleState.deserialize(stored), entity, supply);
        if (count.missing() > 0) {
            return Result.of(count.changed() > 0, "message.aeroflux.tool_pasted_missing", count.changed(), count.added(),
                    count.missing());
        }
        if (count.added() > 0) {
            return Result.of(true, "message.aeroflux.tool_pasted_added", count.changed(), count.added());
        }
        return Result.of(count.changed() > 0, "message.aeroflux.tool_pasted", count.changed());
    }

    /**
     * What one paste did to one bundle.
     *
     * @param changed pipes that took the copy's settings
     * @param added   pipes added from the supply
     * @param missing pipes the supply did not have
     */
    private record PasteCount(int changed, int added, int missing) {
    }

    private static PasteCount pasteInto(BundleState copy, ConduitBundleBlockEntity entity, Supply supply) {
        BundleState bundle = entity.bundle();
        int added = 0;
        int missing = 0;
        for (PipeType type : copy.set().types()) {
            // A lane holding this pipe, or another kind sharing its lane, is left as it is.
            if (bundle.set().hasLaneFor(type)) {
                continue;
            }
            int lane = type.lane();
            Tier tier = copy.tier(lane);
            if (!supply.take(ModItems.pipe(type, tier).get())) {
                missing++;
                continue;
            }
            entity.addPipe(type, tier);
            added++;
            // The new pipe has the copy's tier, so the copy's upgrades always fit.
            for (int i = 0; i < copy.speedUpgrades(lane) && supply.take(ModItems.SPEED_UPGRADE.get()); i++) {
                bundle.addUpgrade(lane, PipeUpgrades.Kind.SPEED);
            }
            for (int i = 0; i < copy.capacityUpgrades(lane) && supply.take(ModItems.CAPACITY_UPGRADE.get()); i++) {
                bundle.addUpgrade(lane, PipeUpgrades.Kind.CAPACITY);
            }
        }
        int changed = BundleConfig.from(copy).applyTo(bundle);
        if (changed > 0 || added > 0) {
            entity.refreshConnections();
            entity.changed();
        }
        return new PasteCount(changed, added, missing);
    }

    /**
     * Handles one sneak-use in paste mode. The first remembers where the run starts; the second
     * pastes the remembered bundle onto every block of the straight line between the two:
     * bundles on the line take the paste, and empty spots (air, grass, anything a block can
     * replace) get a new bundle built from the supply. Solid blocks in the way are skipped.
     *
     * @param pos the bundle that was clicked, or the free spot in front of the clicked block
     */
    public static Result pasteRun(ServerLevel level, ItemStack tool, BlockPos pos, Supply supply) {
        return pasteRun(level, tool, pos, supply, at -> true);
    }

    /**
     * The same, but only spots {@code allowed} accepts are touched; the rest of the run is skipped
     * (a claim mod's protection, in the game).
     */
    public static Result pasteRun(ServerLevel level, ItemStack tool, BlockPos pos, Supply supply, Predicate<BlockPos> allowed) {
        int[] stored = ToolStackData.copiedBundle(tool);
        if (stored == null) {
            return Result.of(false, "message.aeroflux.tool_nothing_copied");
        }
        BundleState copy = BundleState.deserialize(stored);
        if (copy.set().isEmpty()) {
            return Result.of(false, "message.aeroflux.tool_nothing_copied");
        }
        String dimension = level.dimension().identifier().toString();
        ToolStackData.RunStart start = ToolStackData.runStart(tool);
        if (start == null || !start.dimension().equals(dimension)) {
            ToolStackData.setRunStart(tool, new ToolStackData.RunStart(
                    new RunPlanner.Pos(pos.getX(), pos.getY(), pos.getZ()), copy.set().types().get(0), dimension));
            return Result.of(true, "message.aeroflux.tool_paste_run_start");
        }

        ToolStackData.clearRunStart(tool);
        List<RunPlanner.Pos> run = RunPlanner.line(start.pos(), new RunPlanner.Pos(pos.getX(), pos.getY(), pos.getZ()));
        if (run.isEmpty()) {
            return Result.of(false, "message.aeroflux.tool_run_invalid", RunPlanner.MAX_LENGTH);
        }

        int bundles = 0;
        int built = 0;
        int missing = 0;
        for (RunPlanner.Pos step : run) {
            BlockPos at = new BlockPos(step.x(), step.y(), step.z());
            if (!level.isLoaded(at) || !allowed.test(at)) {
                continue;
            }
            if (level.getBlockEntity(at) instanceof ConduitBundleBlockEntity entity) {
                missing += pasteInto(copy, entity, supply).missing();
                bundles++;
            } else if (level.getBlockState(at).canBeReplaced()) {
                level.setBlock(at, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(at) instanceof ConduitBundleBlockEntity entity)) {
                    continue;
                }
                missing += pasteInto(copy, entity, supply).missing();
                if (entity.bundle().set().isEmpty()) {
                    // The supply ran dry before this spot; leave it as it was.
                    level.removeBlock(at, false);
                } else {
                    bundles++;
                    built++;
                }
            }
        }
        return Result.of(bundles > 0, "message.aeroflux.tool_paste_run", bundles, built, missing);
    }

    // --- Inspect -----------------------------------------------------------------------------

    /**
     * Describes the network of one pipe: its type, how many bundles it spans, how many machine
     * faces feed it or take from it, and how fast it can move things. The search itself is the
     * transfer system's own, so the answer matches what the network really does.
     */
    public static Result inspect(ServerLevel level, BlockPos pos, PipeType pipe) {
        LaneNetwork network = TransferNetworks.networkFor(level, pos, pipe);
        if (network == null) {
            return Result.of(false, "message.aeroflux.tool_no_network", pipeName(pipe));
        }
        int sources = 0;
        int sinks = 0;
        for (NetworkEndpoint endpoint : network.endpoints()) {
            if (endpoint.pulls()) {
                sources++;
            }
            if (endpoint.pushes()) {
                sinks++;
            }
        }
        return Result.of(true, "message.aeroflux.tool_inspect", pipeName(pipe), network.members().size(), sources,
                sinks, speed(network));
    }

    /** Every bundle of a pipe's network, for the inspect outline; empty if the pipe is in no network. */
    public static List<BlockPos> networkMembers(ServerLevel level, BlockPos pos, PipeType pipe) {
        LaneNetwork network = TransferNetworks.networkFor(level, pos, pipe);
        return network == null ? List.of() : network.members();
    }

    /** The network's speed limit in words: items per batch for item pipes, an amount per tick for the rest. */
    private static Component speed(LaneNetwork network) {
        return switch (network.type()) {
            case ITEM -> Component.translatable("message.aeroflux.tool_speed_items",
                    network.itemsPerOperation(), network.ticksPerOperation());
            case FLUID, GAS -> Component.translatable("message.aeroflux.tool_speed_milli", network.amountPerTick());
            case ENERGY -> Component.translatable("message.aeroflux.tool_speed_energy", network.amountPerTick());
            default -> Component.translatable("message.aeroflux.tool_speed_none");
        };
    }

    // --- Names -------------------------------------------------------------------------------

    private static Component pipeName(PipeType pipe) {
        return Component.translatable("pipe.aeroflux." + pipe.id());
    }

    private static Component faceName(Face face) {
        return Component.translatable("face.aeroflux." + lower(face));
    }

    private static Component tierName(Tier tier) {
        return Component.translatable("tier.aeroflux." + tier.id());
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
