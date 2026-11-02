package com.itmecasey.aeroflux.conduit.tool;

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
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What each mode of the Conduit Tool does to the world. Everything here works on a level, a
 * position or bundle and plain arguments, never on a player, so it can be run from the
 * self-test. The item class only finds the target, calls one of these and hands the result to
 * the player.
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
                takeLane(entity.bundle(), type, items);
                pipes++;
            }
        } else {
            takeLane(entity.bundle(), pipe, items);
            pipes = 1;
        }
        settle(level, pos, entity);
        if (wholeBundle) {
            return Result.of(true, "message.aeroflux.tool_removed_bundle", items, pipes);
        }
        return Result.of(true, "message.aeroflux.tool_removed", items, pipeName(pipe));
    }

    /**
     * Removes the pipe in its lane and adds what it was worth (the pipe item and its upgrades)
     * to {@code into}.
     */
    private static void takeLane(BundleState bundle, PipeType type, List<ItemStack> into) {
        int lane = type.lane();
        into.add(new ItemStack(ModItems.pipe(type, bundle.tier(lane)).get()));
        addUpgrades(into, bundle.speedUpgrades(lane), bundle.capacityUpgrades(lane));
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
                    && entity.bundle().set().contains(start.pipe())) {
                takeLane(entity.bundle(), start.pipe(), items);
                settle(level, at, entity);
                removed++;
            }
        }
        return Result.of(true, "message.aeroflux.tool_run_removed", items, removed, pipeName(start.pipe()));
    }

    // --- Replace tier ------------------------------------------------------------------------

    /**
     * Swaps the tier of a pipe for the tier of a pipe item. Connections, modes and upgrades stay;
     * upgrades that no longer fit are returned. The old pipe's item is returned too.
     *
     * @param offhand the stack that supplies the new tier
     * @param consume whether to use up one of {@code offhand} (false in creative mode)
     */
    public static Result replaceTier(ConduitBundleBlockEntity entity, PipeType pipe, ItemStack offhand, boolean consume) {
        BundleState bundle = entity.bundle();
        int lane = pipe.lane();
        if (!(offhand.getItem() instanceof PipeItem item) || item.type() != pipe || !PipeTiers.isTiered(pipe)
                || item.tier() == bundle.tier(lane)) {
            return Result.of(false, "message.aeroflux.tool_tier_none", pipeName(pipe));
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
            offhand.shrink(1);
        }
        entity.refreshConnections();
        entity.changed();
        return Result.of(true, "message.aeroflux.tool_tier_replaced", items, pipeName(pipe), tierName(item.tier()));
    }

    // --- Copy and paste ----------------------------------------------------------------------

    /** Remembers the bundle's state on the tool. */
    public static Result copy(ItemStack tool, ConduitBundleBlockEntity entity) {
        ToolStackData.setCopiedBundle(tool, entity.bundle().serialize());
        return Result.of(true, "message.aeroflux.tool_copied", entity.bundle().set().size());
    }

    /** Applies the remembered modes and distribution to the pipes this bundle shares with the copy. */
    public static Result paste(ItemStack tool, ConduitBundleBlockEntity entity) {
        int[] stored = ToolStackData.copiedBundle(tool);
        if (stored == null) {
            return Result.of(false, "message.aeroflux.tool_nothing_copied");
        }
        int changed = BundleConfig.from(BundleState.deserialize(stored)).applyTo(entity.bundle());
        if (changed > 0) {
            entity.refreshConnections();
            entity.changed();
        }
        return Result.of(changed > 0, "message.aeroflux.tool_pasted", changed);
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
