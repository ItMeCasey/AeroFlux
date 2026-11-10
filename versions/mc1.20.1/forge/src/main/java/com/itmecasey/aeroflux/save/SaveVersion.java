package com.itmecasey.aeroflux.save;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Reads and writes the version stamp every block entity saves its data with. The rules about what
 * the numbers mean, and the migration steps, are in shared-core ({@link SaveSchema}); this is the
 * glue to the game's save format.
 */
public final class SaveVersion {
    private static final Set<SaveSchema.Kind> WARNED = EnumSet.noneOf(SaveSchema.Kind.class);

    private SaveVersion() {
    }

    /** Writes the current version of a kind of data. Call first in {@code saveAdditional}. */
    public static void stamp(CompoundTag tag, SaveSchema.Kind kind) {
        tag.putInt(SaveSchema.TAG, kind.current());
    }

    /**
     * Reads the version the data was saved with; 0 for data from before versioning. Data from a
     * newer build is reported once per kind and still read, as far as it is understood. Older data
     * needs no report: the migration steps bring it up to date as it loads, and the next save
     * writes the current version.
     */
    public static int read(SaveSchema.Kind kind, CompoundTag tag) {
        return check(kind, tag.getInt(SaveSchema.TAG));
    }

    /** The same as {@link #read} for a version that was already read from somewhere else. */
    public static int check(SaveSchema.Kind kind, int stored) {
        if (SaveSchema.inspect(kind, stored) == SaveSchema.Status.NEWER && WARNED.add(kind)) {
            AeroFlux.LOGGER.warn("Found {} data saved by a newer AeroFlux (version {}, this build writes {}); "
                    + "reading what is understood and saving in the older layout from now on.",
                    kind.id(), stored, kind.current());
        }
        return SaveSchema.effectiveVersion(stored);
    }
}
