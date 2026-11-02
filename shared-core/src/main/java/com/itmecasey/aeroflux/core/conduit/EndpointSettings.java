package com.itmecasey.aeroflux.core.conduit;

/**
 * The upgrades a bundle holds at single faces rather than in a whole pipe: a Filter upgrade and a
 * Priority upgrade can each sit in one pipe at one face (one connection endpoint), together with
 * their settings.
 *
 * <p>This lives beside {@link BundleState} instead of inside it so the bundle's saved int array
 * keeps its old shape. It is saved as one short string (see {@link #encode()}); a bundle saved
 * before these upgrades existed simply has no string and loads with nothing installed.
 */
public final class EndpointSettings {
    /** The two upgrades that go into a single face. */
    public enum Upgrade {
        /** Lets only some items or fluids through this face. */
        FILTER,
        /** Makes this face fill before (or after) the others. */
        PRIORITY
    }

    private static final int FACES = 6;
    private static final String VERSION = "1";
    private static final String RECORD_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";
    private static final String ENTRY_SEPARATOR = "|";
    private static final int FLAG_FILTER = 1;
    private static final int FLAG_PRIORITY = 2;
    private static final int FLAG_WHITELIST = 4;

    /** The settings of one face; {@code null} in the table means nothing is installed there. */
    private static final class Endpoint {
        EndpointFilter filter;
        boolean priorityInstalled;
        int priority = PriorityPlanner.DEFAULT;

        boolean isEmpty() {
            return filter == null && !priorityInstalled;
        }
    }

    private final Endpoint[][] endpoints = new Endpoint[PipeType.LANES][FACES];

    /** Whether a pipe type can take a Filter upgrade: only the pipes that carry items or fluid. */
    public static boolean supportsFilter(PipeType type) {
        return type == PipeType.ITEM || type == PipeType.FLUID;
    }

    /** Whether a pipe type can take a Priority upgrade: every pipe that has destinations to rank. */
    public static boolean supportsPriority(PipeType type) {
        return type.usesDistribution();
    }

    /** Whether a pipe type can take this upgrade at all. */
    public static boolean supports(PipeType type, Upgrade upgrade) {
        return upgrade == Upgrade.FILTER ? supportsFilter(type) : supportsPriority(type);
    }

    // --- Installing --------------------------------------------------------------------------

    /** Whether the upgrade sits in this lane at this face. */
    public boolean has(int lane, Face face, Upgrade upgrade) {
        Endpoint endpoint = endpoints[lane][face.index()];
        if (endpoint == null) {
            return false;
        }
        return upgrade == Upgrade.FILTER ? endpoint.filter != null : endpoint.priorityInstalled;
    }

    /**
     * Puts an upgrade into a lane at a face. A new filter is an empty whitelist and a new
     * priority is {@value PriorityPlanner#DEFAULT}.
     *
     * @return false if one is already there
     */
    public boolean install(int lane, Face face, Upgrade upgrade) {
        if (has(lane, face, upgrade)) {
            return false;
        }
        Endpoint endpoint = endpoints[lane][face.index()];
        if (endpoint == null) {
            endpoint = new Endpoint();
            endpoints[lane][face.index()] = endpoint;
        }
        if (upgrade == Upgrade.FILTER) {
            endpoint.filter = new EndpointFilter();
        } else {
            endpoint.priorityInstalled = true;
            endpoint.priority = PriorityPlanner.DEFAULT;
        }
        return true;
    }

    /**
     * Takes an upgrade out of a lane at a face; its settings go with it.
     *
     * @return false if there was none
     */
    public boolean remove(int lane, Face face, Upgrade upgrade) {
        if (!has(lane, face, upgrade)) {
            return false;
        }
        Endpoint endpoint = endpoints[lane][face.index()];
        if (upgrade == Upgrade.FILTER) {
            endpoint.filter = null;
        } else {
            endpoint.priorityInstalled = false;
            endpoint.priority = PriorityPlanner.DEFAULT;
        }
        if (endpoint.isEmpty()) {
            endpoints[lane][face.index()] = null;
        }
        return true;
    }

    /** How many upgrades of one kind a lane holds over all its faces, for drops. */
    public int count(int lane, Upgrade upgrade) {
        int count = 0;
        for (Face face : Face.values()) {
            if (has(lane, face, upgrade)) {
                count++;
            }
        }
        return count;
    }

    /** Forgets everything installed in a lane, for when its pipe goes away. */
    public void clearLane(int lane) {
        for (int face = 0; face < FACES; face++) {
            endpoints[lane][face] = null;
        }
    }

    /**
     * Drops whatever is installed in lanes that no longer hold a pipe, or in pipes that cannot
     * take that upgrade. Used after loading, so stale data never comes back to life.
     */
    public void retainValid(PipeSet set) {
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            PipeType type = set.get(lane);
            for (Face face : Face.values()) {
                for (Upgrade upgrade : Upgrade.values()) {
                    if (type == null || !supports(type, upgrade)) {
                        remove(lane, face, upgrade);
                    }
                }
            }
        }
    }

    /** Whether nothing is installed anywhere. */
    public boolean isEmpty() {
        for (Endpoint[] lane : endpoints) {
            for (Endpoint endpoint : lane) {
                if (endpoint != null) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- Settings ----------------------------------------------------------------------------

    /**
     * The filter in a lane at a face, which callers may change in place.
     *
     * @return the filter, or {@code null} if no Filter upgrade is installed there
     */
    public EndpointFilter filter(int lane, Face face) {
        Endpoint endpoint = endpoints[lane][face.index()];
        return endpoint == null ? null : endpoint.filter;
    }

    /** The priority of a lane at a face; {@value PriorityPlanner#DEFAULT} without a Priority upgrade. */
    public int priority(int lane, Face face) {
        Endpoint endpoint = endpoints[lane][face.index()];
        return endpoint != null && endpoint.priorityInstalled ? endpoint.priority : PriorityPlanner.DEFAULT;
    }

    /**
     * Sets the priority of a lane at a face, clamped to the allowed range.
     *
     * @return false if no Priority upgrade is installed there or the value did not change
     */
    public boolean setPriority(int lane, Face face, int priority) {
        if (!has(lane, face, Upgrade.PRIORITY)) {
            return false;
        }
        Endpoint endpoint = endpoints[lane][face.index()];
        int clamped = PriorityPlanner.clamp(priority);
        if (endpoint.priority == clamped) {
            return false;
        }
        endpoint.priority = clamped;
        return true;
    }

    // --- Saving ------------------------------------------------------------------------------

    /**
     * The settings as one string: a version, then one record per face that holds anything, as
     * {@code lane,face,flags,priority,entry|entry|...}. Registry ids never contain the separators.
     */
    public String encode() {
        StringBuilder out = new StringBuilder(VERSION);
        for (int lane = 0; lane < PipeType.LANES; lane++) {
            for (int face = 0; face < FACES; face++) {
                Endpoint endpoint = endpoints[lane][face];
                if (endpoint == null) {
                    continue;
                }
                int flags = (endpoint.filter != null ? FLAG_FILTER : 0)
                        | (endpoint.priorityInstalled ? FLAG_PRIORITY : 0)
                        | (endpoint.filter != null && endpoint.filter.isWhitelist() ? FLAG_WHITELIST : 0);
                out.append(RECORD_SEPARATOR).append(lane)
                        .append(FIELD_SEPARATOR).append(face)
                        .append(FIELD_SEPARATOR).append(flags)
                        .append(FIELD_SEPARATOR).append(endpoint.priority)
                        .append(FIELD_SEPARATOR);
                for (int slot = 0; slot < EndpointFilter.SLOTS; slot++) {
                    if (slot > 0) {
                        out.append(ENTRY_SEPARATOR);
                    }
                    String entry = endpoint.filter == null ? null : endpoint.filter.entry(slot);
                    out.append(entry == null ? "" : entry);
                }
            }
        }
        return out.toString();
    }

    /**
     * Reads a string written by {@link #encode()}. A missing or unreadable string gives empty
     * settings, and a broken record is skipped rather than spoiling the rest.
     */
    public static EndpointSettings decode(String text) {
        EndpointSettings settings = new EndpointSettings();
        if (text == null || text.isEmpty()) {
            return settings;
        }
        String[] records = text.split(RECORD_SEPARATOR, -1);
        if (!VERSION.equals(records[0])) {
            return settings;
        }
        for (int r = 1; r < records.length; r++) {
            String[] fields = records[r].split(FIELD_SEPARATOR, -1);
            if (fields.length != 5) {
                continue;
            }
            try {
                int lane = Integer.parseInt(fields[0]);
                int face = Integer.parseInt(fields[1]);
                int flags = Integer.parseInt(fields[2]);
                int priority = Integer.parseInt(fields[3]);
                if (lane < 0 || lane >= PipeType.LANES || face < 0 || face >= FACES) {
                    continue;
                }
                Face side = Face.byIndex(face);
                if ((flags & FLAG_FILTER) != 0) {
                    settings.install(lane, side, Upgrade.FILTER);
                    EndpointFilter filter = settings.filter(lane, side);
                    filter.setWhitelist((flags & FLAG_WHITELIST) != 0);
                    String[] entries = fields[4].split("\\" + ENTRY_SEPARATOR, -1);
                    for (int slot = 0; slot < Math.min(entries.length, EndpointFilter.SLOTS); slot++) {
                        filter.setEntry(slot, entries[slot]);
                    }
                }
                if ((flags & FLAG_PRIORITY) != 0) {
                    settings.install(lane, side, Upgrade.PRIORITY);
                    settings.setPriority(lane, side, priority);
                }
            } catch (NumberFormatException e) {
                // A damaged record; leave that face empty.
            }
        }
        return settings;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EndpointSettings settings && encode().equals(settings.encode());
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }
}
