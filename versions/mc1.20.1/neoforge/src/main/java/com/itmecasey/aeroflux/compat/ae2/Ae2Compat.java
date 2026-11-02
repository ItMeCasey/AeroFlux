package com.itmecasey.aeroflux.compat.ae2;

import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Where AeroFlux would talk to Applied Energistics 2. The AE2 hookup is not ported to 1.20.1
 * yet, so on this version every call does nothing: ME pipes join only each other and there is
 * no AE converter. The rest of the mod calls these the same way on every version, which keeps the
 * shared code alike.
 */
public final class Ae2Compat {
    private Ae2Compat() {
    }

    /** Never: there is no AE2 to find. */
    public static boolean loaded() {
        return false;
    }

    @Nullable
    public static Object sync(ConduitBundleBlockEntity bundle, @Nullable Object link) {
        return null;
    }

    public static void stop(@Nullable Object link) {
    }

    @Nullable
    public static Object startConverter(FluxConverterBlockEntity converter) {
        return null;
    }

    public static void updateConverterSides(@Nullable Object link) {
    }

    public static void pushConverter(@Nullable Object link) {
    }

    /** An ME pipe only joins AE2's blocks, and there are none. */
    public static boolean attaches(Level level, BlockPos pos, Direction side) {
        return false;
    }
}
