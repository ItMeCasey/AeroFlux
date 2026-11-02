package com.itmecasey.aeroflux.conduit;

import com.itmecasey.aeroflux.core.conduit.Box;
import com.itmecasey.aeroflux.core.conduit.BundleState;
import com.itmecasey.aeroflux.core.conduit.ConduitLayout;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns a bundle's boxes into the hit and collision shape. Shapes are cached by the bundle's
 * look, since many bundles in a world share the same set and connections.
 */
public final class BundleShapes {
    /** What an empty or not-yet-loaded bundle looks like to the player's crosshair. */
    private static final VoxelShape EMPTY_BUNDLE = Block.box(6.5, 6.5, 6.5, 9.5, 9.5, 9.5);

    private static final Map<BundleState.ModelKey, VoxelShape> CACHE = new ConcurrentHashMap<>();

    private BundleShapes() {
    }

    /** The shape of a bundle: every pipe core and arm, plus any machine connectors. */
    public static VoxelShape of(BundleState bundle) {
        if (bundle.set().isEmpty()) {
            return EMPTY_BUNDLE;
        }
        return CACHE.computeIfAbsent(bundle.modelKey(), key -> build(bundle));
    }

    private static VoxelShape build(BundleState bundle) {
        VoxelShape shape = Shapes.empty();
        for (Box box : ConduitLayout.of(bundle).hitBoxes()) {
            if (hasVolume(box)) {
                shape = Shapes.or(shape, Block.box(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
            }
        }
        return shape.optimize();
    }

    /**
     * Whether a box encloses any space. The game refuses to build a shape from a flat or inside-out
     * box, so such a box is skipped rather than allowed to crash the chunk.
     */
    public static boolean hasVolume(Box box) {
        return box.minX() < box.maxX() && box.minY() < box.maxY() && box.minZ() < box.maxZ();
    }
}
