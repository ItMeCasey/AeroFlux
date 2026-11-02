package com.itmecasey.aeroflux.core.conduit;

import com.itmecasey.aeroflux.core.Tier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Everything a conduit bundle remembers: which pipes it holds, where each pipe is connected,
 * and the mode of each pipe at each face.
 *
 * <p>This class has no game classes in it, so the shapes, the model cache keys and the wrench
 * targeting can be worked out and tested without starting Minecraft. The block entity keeps one
 * instance and tells it what the world looks like.
 */
public final class BundleState {
    private static final int FACES = 6;
    private static final int MODE_BITS = 3;
    /** Serialized form: set key, then connected, external and mode words for each lane, then the tiers. */
    private static final int SERIALIZED_LENGTH = 1 + 3 * PipeType.LANES + 3;
    /** The length before upgrades existed; still readable, with no upgrades. */
    private static final int TIERS_LENGTH = 1 + 3 * PipeType.LANES + 1;
    /** The length before tiers existed; still readable, with every pipe at the lowest tier. */
    private static final int LEGACY_LENGTH = 1 + 3 * PipeType.LANES;
    /** Bits per lane for each upgrade count. */
    private static final int UPGRADE_BITS = 3;
    private static final int TIER_BITS = 3;
    /** Bits per lane for the distribution mode; they sit above the tier bits in the same word. */
    private static final int DISTRIBUTION_BITS = 2;
    private static final int DISTRIBUTION_SHIFT = PipeType.LANES * TIER_BITS;

    private PipeSet set = PipeSet.EMPTY;
    /** For each lane, a bit per face that has a connection. */
    private final int[] connected = new int[PipeType.LANES];
    /** For each lane, a bit per connected face whose neighbor is not a pipe (a connector shows). */
    private final int[] external = new int[PipeType.LANES];
    /** For each lane, {@value #MODE_BITS} bits per face holding the {@link ConnectionMode} ordinal. */
    private final int[] modes = new int[PipeType.LANES];
    /** For each lane, the tier of the pipe in it. */
    private final Tier[] tiers = new Tier[PipeType.LANES];
    /** For each lane, the number of Speed upgrades in the pipe. */
    private final int[] speedUpgrades = new int[PipeType.LANES];
    /** For each lane, the number of Capacity upgrades in the pipe. */
    private final int[] capacityUpgrades = new int[PipeType.LANES];
    /** For each lane, how the pipe in it chooses among its destinations. */
    private final DistributionMode[] distribution = new DistributionMode[PipeType.LANES];

    public BundleState() {
        Arrays.fill(tiers, Tier.COPPER);
        Arrays.fill(distribution, DistributionMode.ROUND_ROBIN);
    }

    /**
     * What identifies the shape and look of a bundle. Equal keys mean equal quads and boxes, so
     * the model can cache by this.
     *
     * @param setKey    {@link PipeSet#key()}
     * @param connected the connected masks of all lanes packed into one value
     * @param external  the external masks of all lanes packed into one value
     * @param tiers     the tier of each pipe present, packed; each tier has its own textures
     */
    public record ModelKey(int setKey, long connected, long external, int tiers) {
    }

    public PipeSet set() {
        return set;
    }

    /** An independent copy, safe to hand to another thread such as the model baker. */
    public BundleState copy() {
        return deserialize(serialize());
    }

    /** Adds a lowest-tier pipe, replacing whatever shared its lane, and clears that lane's old connections. */
    public void addPipe(PipeType type) {
        addPipe(type, Tier.COPPER);
    }

    /** Adds a pipe of the given tier, replacing whatever shared its lane, and clears that lane's old connections. */
    public void addPipe(PipeType type, Tier tier) {
        set = set.with(type);
        clearLane(type.lane());
        tiers[type.lane()] = tier;
    }

    /** The tier of the pipe in a lane; meaningless for an empty lane. */
    public Tier tier(int lane) {
        return tiers[lane];
    }

    /** Speed upgrades installed in the pipe in a lane. */
    public int speedUpgrades(int lane) {
        return speedUpgrades[lane];
    }

    /** Capacity upgrades installed in the pipe in a lane. */
    public int capacityUpgrades(int lane) {
        return capacityUpgrades[lane];
    }

    /** Upgrades of all kinds installed in the pipe in a lane. */
    public int upgradeCount(int lane) {
        return speedUpgrades[lane] + capacityUpgrades[lane];
    }

    /**
     * Installs one upgrade if the pipe has a free slot.
     *
     * @return false if the pipe is missing or all its slots are taken
     */
    public boolean addUpgrade(int lane, PipeUpgrades.Kind kind) {
        if (set.get(lane) == null || upgradeCount(lane) >= PipeUpgrades.slots(tiers[lane])) {
            return false;
        }
        if (kind == PipeUpgrades.Kind.SPEED) {
            speedUpgrades[lane]++;
        } else {
            capacityUpgrades[lane]++;
        }
        return true;
    }

    /** Takes out all upgrades of one kind from a pipe and says how many there were. */
    public int removeUpgrades(int lane, PipeUpgrades.Kind kind) {
        int removed = kind == PipeUpgrades.Kind.SPEED ? speedUpgrades[lane] : capacityUpgrades[lane];
        if (kind == PipeUpgrades.Kind.SPEED) {
            speedUpgrades[lane] = 0;
        } else {
            capacityUpgrades[lane] = 0;
        }
        return removed;
    }

    /** How the pipe in a lane chooses among its destinations; round robin until changed. */
    public DistributionMode distribution(int lane) {
        return distribution[lane];
    }

    /** Changes the distribution mode of the pipe in a lane. Pipes that do not distribute ignore it. */
    public void setDistribution(int lane, DistributionMode mode) {
        PipeType pipe = set.get(lane);
        if (pipe != null && pipe.usesDistribution()) {
            distribution[lane] = mode;
        }
    }

    /** Changes the tier of the pipe in a lane, keeping its connections and modes. */
    public void setTier(int lane, Tier tier) {
        tiers[lane] = tier;
    }

    /** Removes the pipe in a lane together with its connections and modes. */
    public void removeLane(int lane) {
        set = set.withoutLane(lane);
        clearLane(lane);
    }

    private void clearLane(int lane) {
        connected[lane] = 0;
        external[lane] = 0;
        modes[lane] = 0;
        tiers[lane] = Tier.COPPER;
        distribution[lane] = DistributionMode.ROUND_ROBIN;
        speedUpgrades[lane] = 0;
        capacityUpgrades[lane] = 0;
    }

    /** Whether the pipe in this lane has a connection at this face. */
    public boolean isConnected(int lane, Face face) {
        return (connected[lane] & bit(face)) != 0;
    }

    /** Whether that connection ends at a machine connector rather than at another pipe. */
    public boolean isExternal(int lane, Face face) {
        return (external[lane] & bit(face)) != 0;
    }

    /**
     * Records whether a lane connects at a face. A face set to {@link ConnectionMode#BLOCKED}
     * never connects, whatever the world offers.
     *
     * @param external true when the neighbor is a machine or other non-pipe block
     */
    public void setConnection(int lane, Face face, boolean isConnected, boolean external) {
        boolean allowed = isConnected && mode(lane, face) != ConnectionMode.BLOCKED;
        this.connected[lane] = withBit(this.connected[lane], face, allowed);
        this.external[lane] = withBit(this.external[lane], face, allowed && external);
    }

    /** The mode of a pipe at a face; {@link ConnectionMode#OPEN} until changed. */
    public ConnectionMode mode(int lane, Face face) {
        int code = (modes[lane] >> (face.index() * MODE_BITS)) & ((1 << MODE_BITS) - 1);
        return ConnectionMode.values()[Math.min(code, ConnectionMode.values().length - 1)];
    }

    /** Changes the mode; blocking a face also drops any connection there. */
    public void setMode(int lane, Face face, ConnectionMode mode) {
        int shift = face.index() * MODE_BITS;
        modes[lane] = (modes[lane] & ~(((1 << MODE_BITS) - 1) << shift)) | (mode.ordinal() << shift);
        if (mode == ConnectionMode.BLOCKED) {
            setConnection(lane, face, false, false);
        }
    }

    /** The core and arm boxes of one pipe, as drawn and as used for hit testing. */
    public List<Box> boxesOf(PipeType type) {
        List<Box> boxes = new ArrayList<>();
        int slotIndex = set.slotOf(type);
        if (slotIndex < 0) {
            return boxes;
        }
        PipeGeometry.Slot slot = PipeGeometry.layout(set.size()).get(slotIndex);
        boxes.add(PipeGeometry.core(slot));
        for (Face face : Face.values()) {
            if (isConnected(type.lane(), face)) {
                boxes.add(PipeGeometry.arm(slot, face, isExternal(type.lane(), face)));
            }
        }
        return boxes;
    }

    /** The connector boxes: one on each face where any pipe ends at a machine. */
    public List<Box> connectorBoxes() {
        List<Box> boxes = new ArrayList<>();
        for (Face face : Face.values()) {
            if (hasExternalConnection(face)) {
                boxes.add(PipeGeometry.connector(set.size(), face));
            }
        }
        return boxes;
    }

    /** Whether any pipe in the bundle ends at a machine on this face. */
    public boolean hasExternalConnection(Face face) {
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            if (set.get(lane) != null && isExternal(lane, face)) {
                return true;
            }
        }
        return false;
    }

    /** Every box of the bundle: all pipes plus the connectors. This is the hit and collision shape. */
    public List<Box> allBoxes() {
        List<Box> boxes = new ArrayList<>();
        for (PipeType type : set.types()) {
            boxes.addAll(boxesOf(type));
        }
        boxes.addAll(connectorBoxes());
        return boxes;
    }

    /**
     * The pipe nearest a point, for wrench targeting. A pipe is only 4 pixels wide, so aiming
     * exactly at it would be fiddly; the closest pipe wins instead, and ties go to the lower lane.
     *
     * @param x x of the point in block pixels
     * @param y y of the point in block pixels
     * @param z z of the point in block pixels
     * @return the nearest pipe, or {@code null} if the bundle is empty
     */
    public PipeType nearestPipe(double x, double y, double z) {
        PipeType best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PipeType type : set.types()) {
            for (Box box : boxesOf(type)) {
                double distance = box.distanceTo(x, y, z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = type;
                }
            }
        }
        return best;
    }

    /** The key under which the model caches this bundle's quads. */
    public ModelKey modelKey() {
        return new ModelKey(set.key(), pack(connected), pack(external), packTiers());
    }

    /** The tiers of the lanes that hold a pipe; an empty lane's leftover tier draws nothing, so it counts as 0. */
    private int packTiers() {
        int packed = 0;
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            if (set.get(lane) != null) {
                packed |= tiers[lane].ordinal() << (lane * TIER_BITS);
            }
        }
        return packed;
    }

    private static long pack(int[] masks) {
        long packed = 0;
        for (int lane = 0; lane < masks.length; lane++) {
            packed |= (long) masks[lane] << (lane * FACES);
        }
        return packed;
    }

    /** Flattens the state to ints for saving and for sending to clients. */
    public int[] serialize() {
        int[] data = new int[SERIALIZED_LENGTH];
        data[0] = set.key();
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            data[1 + lane] = connected[lane];
            data[1 + PipeType.LANES + lane] = external[lane];
            data[1 + 2 * PipeType.LANES + lane] = modes[lane];
            data[LEGACY_LENGTH] |= tiers[lane].ordinal() << (lane * TIER_BITS);
            data[LEGACY_LENGTH] |= distribution[lane].ordinal() << (DISTRIBUTION_SHIFT + lane * DISTRIBUTION_BITS);
            data[TIERS_LENGTH] |= speedUpgrades[lane] << (lane * UPGRADE_BITS);
            data[TIERS_LENGTH + 1] |= capacityUpgrades[lane] << (lane * UPGRADE_BITS);
        }
        return data;
    }

    /** Restores a state saved by {@link #serialize()}. Data of the wrong size gives an empty bundle. */
    public static BundleState deserialize(int[] data) {
        BundleState state = new BundleState();
        if (data == null || (data.length != SERIALIZED_LENGTH && data.length != TIERS_LENGTH && data.length != LEGACY_LENGTH)) {
            return state;
        }
        state.set = PipeSet.fromKey(data[0]);
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            // Anything stored for a lane that no longer holds a pipe is dropped.
            if (state.set.get(lane) == null) {
                continue;
            }
            state.connected[lane] = data[1 + lane] & ((1 << FACES) - 1);
            state.external[lane] = data[1 + PipeType.LANES + lane] & state.connected[lane];
            state.modes[lane] = data[1 + 2 * PipeType.LANES + lane];
            if (data.length >= TIERS_LENGTH) {
                int code = (data[LEGACY_LENGTH] >> (lane * TIER_BITS)) & ((1 << TIER_BITS) - 1);
                state.tiers[lane] = Tier.values()[Math.min(code, Tier.values().length - 1)];
                int mode = (data[LEGACY_LENGTH] >> (DISTRIBUTION_SHIFT + lane * DISTRIBUTION_BITS)) & ((1 << DISTRIBUTION_BITS) - 1);
                state.distribution[lane] = DistributionMode.values()[mode];
            }
            if (data.length == SERIALIZED_LENGTH) {
                int mask = (1 << UPGRADE_BITS) - 1;
                state.speedUpgrades[lane] = (data[TIERS_LENGTH] >> (lane * UPGRADE_BITS)) & mask;
                state.capacityUpgrades[lane] = (data[TIERS_LENGTH + 1] >> (lane * UPGRADE_BITS)) & mask;
            }
        }
        return state;
    }

    private static int bit(Face face) {
        return 1 << face.index();
    }

    private static int withBit(int mask, Face face, boolean on) {
        return on ? mask | bit(face) : mask & ~bit(face);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BundleState state
                && set.equals(state.set)
                && Arrays.equals(connected, state.connected)
                && Arrays.equals(external, state.external)
                && Arrays.equals(modes, state.modes)
                && Arrays.equals(tiers, state.tiers)
                && Arrays.equals(distribution, state.distribution)
                && Arrays.equals(speedUpgrades, state.speedUpgrades)
                && Arrays.equals(capacityUpgrades, state.capacityUpgrades);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * (31 * set.hashCode() + Arrays.hashCode(connected))
                + Arrays.hashCode(external)) + Arrays.hashCode(modes)) + Arrays.hashCode(tiers))
                + Arrays.hashCode(distribution) + Arrays.hashCode(speedUpgrades) * 7 + Arrays.hashCode(capacityUpgrades);
    }
}
