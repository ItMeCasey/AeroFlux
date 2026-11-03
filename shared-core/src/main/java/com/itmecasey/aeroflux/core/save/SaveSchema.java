package com.itmecasey.aeroflux.core.save;

/**
 * The version of every kind of saved AeroFlux data.
 *
 * <p>Each block entity (and each piece of world data) writes its kind's current version under the
 * {@value #TAG} key. A save with no such key was written before versioning and counts as version 0.
 * On load the data is compared with the current version: older data goes through the migration steps
 * of its kind, which are pure and live beside this class; data from a newer build is read as well as
 * it can be and reported once, since a downgrade cannot be undone but should never lose what it can read.
 *
 * <p>Raise a version only when the layout changes in a way old data needs help with, and add the step
 * for the old version at the same time ({@link MachineSaveMigrations} shows the pattern). A new optional
 * field that older data simply lacks needs a step too, an empty one, so the chain stays complete;
 * {@code SaveSchemaTest} fails when a version has no step.
 */
public final class SaveSchema {
    /** The key the version is saved under, in every kind of data. */
    public static final String TAG = "schema_version";

    /** The kinds of saved data, each with the version the current build writes. */
    public enum Kind {
        /** Every machine, which includes the Sifter, Magnet and Sky Controller. 1: first stamped layout. 2: the Sifter's mesh slot. 3: the owner. */
        MACHINE("machine", 3),
        /** A Flux Chunk Loader. */
        CHUNK_LOADER("chunk_loader", 1),
        /** A Flux Collector. */
        COLLECTOR("collector", 1),
        /** A Flux Tank. */
        TANK("tank", 1),
        /** A conduit bundle. */
        CONDUIT_BUNDLE("conduit_bundle", 1),
        /** A Flux converter. */
        CONVERTER("converter", 1),
        /** The Creative Flux Tank. */
        CREATIVE_TANK("creative_tank", 1),
        /** A Sun or Moon Lamp. */
        LAMP("lamp", 1),
        /** The per-dimension list of chunk loaders. */
        CHUNK_LOADER_REGISTRY("chunk_loader_registry", 1);

        private final String id;
        private final int current;

        Kind(String id, int current) {
            this.id = id;
            this.current = current;
        }

        /** A short name for log lines. */
        public String id() {
            return id;
        }

        /** The version this build writes. */
        public int current() {
            return current;
        }
    }

    /** How stored data compares with what this build writes. */
    public enum Status {
        /** Written by this build's layout; nothing to do. */
        CURRENT,
        /** Written by an older layout; the migration steps bring it up to date. */
        OLDER,
        /** Written before versioning existed (no version key); treated as version 0. */
        UNVERSIONED,
        /** Written by a newer build; read as far as it is understood. */
        NEWER
    }

    private SaveSchema() {
    }

    /**
     * The version to treat stored data as. A missing, zero or negative number is 0.
     *
     * @param stored the number found under {@link #TAG}, or 0 when the key was missing
     */
    public static int effectiveVersion(int stored) {
        return Math.max(0, stored);
    }

    /** Compares stored data with what this build writes. */
    public static Status inspect(Kind kind, int stored) {
        int version = effectiveVersion(stored);
        if (version == 0) {
            return Status.UNVERSIONED;
        }
        if (version > kind.current()) {
            return Status.NEWER;
        }
        return version == kind.current() ? Status.CURRENT : Status.OLDER;
    }

    /** Whether data of this stored version has to go through migration steps. */
    public static boolean needsMigration(Kind kind, int stored) {
        Status status = inspect(kind, stored);
        return status == Status.OLDER || status == Status.UNVERSIONED;
    }
}
