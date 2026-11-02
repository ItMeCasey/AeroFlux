package com.itmecasey.aeroflux.conduit.config;

import com.itmecasey.aeroflux.compat.mekanism.MekanismCompat;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.EndpointUpgradeItem;
import com.itmecasey.aeroflux.conduit.FluxWrenchItem;
import com.itmecasey.aeroflux.conduit.PipeItem;
import com.itmecasey.aeroflux.conduit.UpgradeItem;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolItem;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.DistributionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointFilter;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.conduit.PriorityPlanner;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

/**
 * Everything the pipe screen decides, with no screen, menu or player in sight. The menu and the
 * block call these and the self-test calls them directly, so the rules (which modes a pipe
 * supports, how upgrades fit in the slots, what a click on a connector means) are written once.
 *
 * <p>Methods that change a pipe take the {@link ConduitBundleBlockEntity}, save and sync through
 * {@link ConduitBundleBlockEntity#changed()}, and say whether they changed anything. Methods
 * that only look take the {@link BundleState}.
 */
public final class PipeConfigActions {
    /**
     * How close, in block pixels, a click must be to an arm or connector to count as a click on
     * it. Rays end exactly on the surface of the hit shape, so this only absorbs rounding.
     */
    private static final double CLICK_REACH = 0.1;

    /** Channels of the two ME pipes. AE2 hands them out itself; this is only what the screen reports. */
    private static final int ME_CHANNELS = 8;
    private static final int DENSE_ME_CHANNELS = 32;

    private PipeConfigActions() {
    }

    // --- Targeting ---------------------------------------------------------------------------

    /**
     * Which lane and face a right-click was about.
     *
     * @param pipe the pipe whose connector was clicked
     * @param face the face that connector sits at
     */
    public record Target(PipeType pipe, Face face) {
    }

    /**
     * Works out what a click on a bundle meant. The pipe is the one nearest the click, exactly as
     * the wrench and the Conduit Tool choose it. The click only counts if it landed on that pipe's
     * arm or on the machine connector at the end of it, and then the face is the one the arm
     * points to. Clicks on a pipe core or on empty parts of the bundle are not aimed at a connector.
     *
     * @param x x of the click in block pixels
     * @param y y of the click in block pixels
     * @param z z of the click in block pixels
     * @return the lane and face, or {@code null} if the click was not on a connector
     */
    public static Target resolveTarget(BundleState bundle, double x, double y, double z) {
        ConduitLayout layout = ConduitLayout.of(bundle);
        PipeType pipe = layout.nearestPipe(x, y, z);
        if (pipe == null) {
            return null;
        }
        int lane = pipe.lane();

        Face best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Face face : Face.values()) {
            Box arm = layout.armOf(pipe, face);
            if (arm == null) {
                continue;
            }
            double distance = arm.distanceTo(x, y, z);
            Box plate = bundle.isExternal(lane, face) ? layout.plateOf(face) : null;
            if (plate != null) {
                distance = Math.min(distance, plate.distanceTo(x, y, z));
            }
            if (distance <= CLICK_REACH && distance < bestDistance) {
                best = face;
                bestDistance = distance;
            }
        }
        return best == null ? null : new Target(pipe, best);
    }

    /**
     * Whether a held item has a right-click of its own on bundles. These keep that click instead
     * of opening the screen: the wrench, the Conduit Tool, pipes and upgrades.
     */
    public static boolean keepsOwnClick(ItemStack stack) {
        return stack.getItem() instanceof FluxWrenchItem
                || stack.getItem() instanceof ConduitToolItem
                || stack.getItem() instanceof PipeItem
                || stack.getItem() instanceof UpgradeItem
                || stack.getItem() instanceof EndpointUpgradeItem;
    }

    // --- Validity ----------------------------------------------------------------------------

    /**
     * Whether a bundle still holds the pipe a screen was opened for. A pipe that was removed,
     * replaced by another type or swapped to another tier makes the screen stale, because its
     * upgrade slots may no longer fit.
     */
    public static boolean laneMatches(ConduitBundleBlockEntity entity, PipeType type, Tier tier) {
        BundleState bundle = entity.bundle();
        return !entity.isRemoved()
                && bundle.set().contains(type)
                && bundle.tier(type.lane()) == tier;
    }

    // --- Connection modes --------------------------------------------------------------------

    /**
     * The modes a pipe can be set to at a face, in the order the screen lists them. AE2 cables
     * have no direction of their own, so ME and Dense ME pipes only offer open, off and blocked.
     */
    public static List<ConnectionMode> supportedModes(PipeType type) {
        return switch (type) {
            case ME, DENSE_ME -> List.of(ConnectionMode.OPEN, ConnectionMode.OFF, ConnectionMode.BLOCKED);
            default -> List.of(ConnectionMode.values());
        };
    }

    /**
     * Sets the mode of a pipe at a face and updates its connections.
     *
     * @return false if the pipe does not support that mode or is not in the bundle
     */
    public static boolean setMode(ConduitBundleBlockEntity entity, PipeType type, Face face, ConnectionMode mode) {
        if (!entity.bundle().set().contains(type) || !supportedModes(type).contains(mode)) {
            return false;
        }
        entity.bundle().setMode(type.lane(), face, mode);
        entity.refreshConnections();
        entity.changed();
        return true;
    }

    /**
     * Moves to the next supported mode. A pipe whose current mode is not offered (an ME pipe the
     * wrench set to import, say) goes to the first supported mode.
     */
    public static boolean cycleMode(ConduitBundleBlockEntity entity, PipeType type, Face face) {
        List<ConnectionMode> modes = supportedModes(type);
        int current = modes.indexOf(entity.bundle().mode(type.lane(), face));
        return setMode(entity, type, face, modes.get((current + 1) % modes.size()));
    }

    // --- Distribution ------------------------------------------------------------------------

    /**
     * Whether the screen offers a distribution mode for this pipe. Redstone and the ME pipes
     * have no destinations to choose among.
     */
    public static boolean supportsDistribution(PipeType type) {
        return type.usesDistribution();
    }

    /** @return false if the pipe has no distribution setting or is not in the bundle */
    public static boolean setDistribution(ConduitBundleBlockEntity entity, PipeType type, DistributionMode mode) {
        if (!entity.bundle().set().contains(type) || !supportsDistribution(type)) {
            return false;
        }
        entity.bundle().setDistribution(type.lane(), mode);
        entity.changed();
        return true;
    }

    /** Moves to the next distribution mode, like the sneaking wrench does. */
    public static boolean cycleDistribution(ConduitBundleBlockEntity entity, PipeType type) {
        return setDistribution(entity, type, entity.bundle().distribution(type.lane()).next());
    }

    // --- Upgrades ----------------------------------------------------------------------------

    /**
     * How many upgrade slots the screen shows: 3, or 4 from netherite up (see
     * {@link PipeUpgrades#slots}). The untiered ME pipes take no upgrades.
     */
    public static int upgradeSlots(PipeType type, Tier tier) {
        return PipeTiers.isTiered(type) ? PipeUpgrades.slots(tier) : 0;
    }

    /** The item that stands for one upgrade of a kind. */
    public static ItemStack upgradeStack(PipeUpgrades.Kind kind) {
        return new ItemStack(kind == PipeUpgrades.Kind.SPEED ? ModItems.SPEED_UPGRADE.get() : ModItems.CAPACITY_UPGRADE.get());
    }

    /**
     * The upgrade shown in an upgrade slot. A bundle only counts its upgrades, so they are laid
     * out in a fixed order: Speed upgrades first, then Capacity, with no gaps.
     *
     * @return the kind in that slot, or {@code null} for an empty slot
     */
    public static PipeUpgrades.Kind upgradeKindAt(BundleState bundle, PipeType type, int index) {
        int lane = type.lane();
        if (index < 0 || index >= upgradeSlots(type, bundle.tier(lane))) {
            return null;
        }
        if (index < bundle.speedUpgrades(lane)) {
            return PipeUpgrades.Kind.SPEED;
        }
        return index < bundle.upgradeCount(lane) ? PipeUpgrades.Kind.CAPACITY : null;
    }

    /**
     * Whether an upgrade may be put into a slot. Only the first empty slot takes a new upgrade,
     * since the slots always fill from the start; a filled slot may be swapped.
     */
    public static boolean canPlaceUpgrade(BundleState bundle, PipeType type, int index) {
        int lane = type.lane();
        return bundle.set().contains(type) && index >= 0
                && index <= bundle.upgradeCount(lane)
                && index < upgradeSlots(type, bundle.tier(lane));
    }

    /**
     * Puts one upgrade into the pipe if there is a free slot.
     *
     * @return false if the pipe is not in the bundle, takes no upgrades or is full
     */
    public static boolean addUpgrade(ConduitBundleBlockEntity entity, PipeType type, PipeUpgrades.Kind kind) {
        BundleState bundle = entity.bundle();
        if (upgradeSlots(type, bundle.tier(type.lane())) == 0 || !bundle.addUpgrade(type.lane(), kind)) {
            return false;
        }
        entity.changed();
        return true;
    }

    /**
     * Takes out the upgrade that is shown in a slot.
     *
     * @return the kind that was removed, or {@code null} if the slot was empty
     */
    public static PipeUpgrades.Kind removeUpgradeAt(ConduitBundleBlockEntity entity, PipeType type, int index) {
        PipeUpgrades.Kind kind = upgradeKindAt(entity.bundle(), type, index);
        if (kind == null) {
            return null;
        }
        removeOne(entity.bundle(), type.lane(), kind);
        entity.changed();
        return kind;
    }

    /**
     * Swaps the upgrade in a filled slot for another kind with a single save and sync.
     *
     * @return false if the slot is empty
     */
    public static boolean replaceUpgradeAt(ConduitBundleBlockEntity entity, PipeType type, int index, PipeUpgrades.Kind kind) {
        PipeUpgrades.Kind old = upgradeKindAt(entity.bundle(), type, index);
        if (old == null) {
            return false;
        }
        if (old != kind) {
            removeOne(entity.bundle(), type.lane(), old);
            entity.bundle().addUpgrade(type.lane(), kind);
            entity.changed();
        }
        return true;
    }

    /** {@link BundleState} can only take out every upgrade of a kind, so put the rest back. */
    private static void removeOne(BundleState bundle, int lane, PipeUpgrades.Kind kind) {
        int count = bundle.removeUpgrades(lane, kind);
        for (int i = 1; i < count; i++) {
            bundle.addUpgrade(lane, kind);
        }
    }

    // --- Filter and Priority upgrades ----------------------------------------------------------

    /** The item that stands for a Filter or Priority upgrade. */
    public static ItemStack endpointUpgradeStack(EndpointSettings.Upgrade upgrade) {
        return new ItemStack(upgrade == EndpointSettings.Upgrade.FILTER
                ? ModItems.FILTER_UPGRADE.get() : ModItems.PRIORITY_UPGRADE.get());
    }

    /**
     * Puts a Filter or Priority upgrade into a pipe at one face.
     *
     * @return false if the pipe is not in the bundle, cannot take that upgrade or has one there already
     */
    public static boolean installEndpointUpgrade(ConduitBundleBlockEntity entity, PipeType type, Face face,
                                                 EndpointSettings.Upgrade upgrade) {
        if (!entity.bundle().set().contains(type) || !EndpointSettings.supports(type, upgrade)
                || !entity.endpoints().install(type.lane(), face, upgrade)) {
            return false;
        }
        entity.changed();
        return true;
    }

    /**
     * Takes a Filter or Priority upgrade out of a pipe at one face. Its settings are forgotten.
     *
     * @return false if there was none
     */
    public static boolean removeEndpointUpgrade(ConduitBundleBlockEntity entity, PipeType type, Face face,
                                                EndpointSettings.Upgrade upgrade) {
        if (!entity.endpoints().remove(type.lane(), face, upgrade)) {
            return false;
        }
        entity.changed();
        return true;
    }

    /**
     * Switches a face's filter between whitelist and blacklist.
     *
     * @return false if no Filter upgrade is installed there
     */
    public static boolean toggleFilterMode(ConduitBundleBlockEntity entity, PipeType type, Face face) {
        EndpointFilter filter = entity.endpoints().filter(type.lane(), face);
        if (filter == null) {
            return false;
        }
        filter.setWhitelist(!filter.isWhitelist());
        entity.changed();
        return true;
    }

    /**
     * Sets one filter slot from a stack the player holds: the item itself for an item pipe, the
     * fluid inside it for a fluid pipe. An empty stack clears the slot.
     *
     * @return false if there is no filter, the slot is out of range, the stack has nothing this
     *         pipe could carry, or the slot already held that
     */
    public static boolean setFilterEntry(ConduitBundleBlockEntity entity, PipeType type, Face face, int slot,
                                         ItemStack held) {
        EndpointFilter filter = entity.endpoints().filter(type.lane(), face);
        if (filter == null || slot < 0 || slot >= EndpointFilter.SLOTS) {
            return false;
        }
        String id = null;
        if (!held.isEmpty()) {
            id = filterId(type, held);
            if (id == null) {
                return false;
            }
        }
        if (!filter.setEntry(slot, id)) {
            return false;
        }
        entity.changed();
        return true;
    }

    /**
     * What a held stack would put into a filter of this pipe: the item's id for item pipes, the id
     * of the fluid it contains for fluid pipes and of the Mekanism gas it holds for gas pipes.
     *
     * @return the id, or {@code null} if the stack is empty or holds no fluid or gas
     */
    public static String filterId(PipeType type, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (type == PipeType.FLUID) {
            FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            return fluid.isEmpty() ? null : fluidId(fluid.getFluid());
        }
        if (type == PipeType.GAS) {
            return MekanismCompat.gasIn(stack);
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The registry id of a fluid, the form fluid filters store. */
    public static String fluidId(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /**
     * Raises or lowers a face's priority by {@code delta}, within its range.
     *
     * @return false if no Priority upgrade is installed there or it is already at the end of the range
     */
    public static boolean adjustPriority(ConduitBundleBlockEntity entity, PipeType type, Face face, int delta) {
        int lane = type.lane();
        if (!entity.endpoints().setPriority(lane, face, entity.endpoints().priority(lane, face) + delta)) {
            return false;
        }
        entity.changed();
        return true;
    }

    /** How a priority reads on the screen and in tooltips: with a plus sign above zero. */
    public static String priorityText(int priority) {
        return priority > PriorityPlanner.DEFAULT ? "+" + priority : Integer.toString(priority);
    }

    // --- Throughput --------------------------------------------------------------------------

    /** What a pipe moves, in the unit that suits its type. */
    public enum RateKind {
        /** A batch of items every so many ticks; {@code first} is the batch, {@code second} the ticks. */
        ITEMS,
        /** Fluid in mB per tick. */
        FLUID,
        /** Gas in mB per tick. */
        GAS,
        /** Energy in FE per tick. */
        ENERGY,
        /** Redstone channels. */
        CHANNELS,
        /** Channels handled by AE2 itself. */
        ME_CHANNELS
    }

    /**
     * The effective speed of a pipe with its upgrades.
     *
     * @param kind   the unit
     * @param first  the amount, batch size or channel count
     * @param second ticks between batches for {@link RateKind#ITEMS}, otherwise 0
     */
    public record Rate(RateKind kind, long first, long second) {
        /** The rate as text for the screen. */
        public Component text() {
            String key = "screen.aeroflux.pipe_config.rate_" + kind.name().toLowerCase(Locale.ROOT);
            return kind == RateKind.ITEMS
                    ? Component.translatable(key, format(first), format(second))
                    : Component.translatable(key, format(first));
        }

        private static String format(long value) {
            return String.format(Locale.US, "%,d", value);
        }
    }

    /**
     * The rate of a pipe from its tier and upgrade counts, using the same formulas the transfer
     * engine uses, so the screen cannot promise more than the pipe delivers.
     */
    public static Rate rate(PipeType type, Tier tier, int speedUpgrades, int capacityUpgrades) {
        return switch (type) {
            case ITEM -> new Rate(RateKind.ITEMS, PipeUpgrades.itemsPerOperation(tier, capacityUpgrades),
                    PipeUpgrades.ticksPerOperation(tier, speedUpgrades));
            case FLUID -> new Rate(RateKind.FLUID,
                    PipeUpgrades.amountPerTick(type, tier, speedUpgrades, capacityUpgrades), 0);
            case GAS -> new Rate(RateKind.GAS,
                    PipeUpgrades.amountPerTick(type, tier, speedUpgrades, capacityUpgrades), 0);
            case ENERGY -> new Rate(RateKind.ENERGY,
                    PipeUpgrades.amountPerTick(type, tier, speedUpgrades, capacityUpgrades), 0);
            case REDSTONE -> new Rate(RateKind.CHANNELS, PipeTiers.redstoneChannels(tier), 0);
            case ME -> new Rate(RateKind.ME_CHANNELS, ME_CHANNELS, 0);
            case DENSE_ME -> new Rate(RateKind.ME_CHANNELS, DENSE_ME_CHANNELS, 0);
        };
    }
}
