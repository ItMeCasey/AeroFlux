package com.itmecasey.aeroflux.core.profile;

/**
 * The names the profiler groups block entities under. A machine is named {@code machine/<id>}, so
 * its group (everything before the slash) adds all machines up while each kind still has its own line.
 */
public final class ProfileTypes {
    public static final String COLLECTOR = "collector";
    public static final String CONVERTER = "converter";
    public static final String CHUNK_LOADER = "chunk_loader";
    public static final String CONDUIT_BUNDLE = "conduit_bundle";
    public static final String LAMP = "lamp";
    public static final String CREATIVE_TANK = "creative_tank";

    private static final String MACHINE_PREFIX = "machine/";

    private ProfileTypes() {
    }

    /** The name of one kind of machine, from its id. */
    public static String machine(String machineId) {
        return MACHINE_PREFIX + machineId;
    }

    /** The group a name belongs to: the part before a slash, or the whole name. */
    public static String groupOf(String type) {
        int slash = type.indexOf('/');
        return slash < 0 ? type : type.substring(0, slash);
    }

    /** Whether the name is a conduit bundle, which the budget counts separately from other blocks. */
    public static boolean isBundle(String type) {
        return CONDUIT_BUNDLE.equals(type);
    }
}
