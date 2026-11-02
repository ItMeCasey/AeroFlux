package com.itmecasey.aeroflux.compat.ae2;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.capabilities.Capabilities;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.converter.FluxConverterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the rest of AeroFlux asks of AE2, behind a check that AE2 is installed. Callers only
 * ever hold the link as an {@code Object}, so no AE2 class is loaded when AE2 is absent.
 */
public final class Ae2Compat {
    private static final boolean LOADED = ModList.get().isLoaded("ae2");

    private Ae2Compat() {
    }

    /** Whether AE2 is installed. */
    public static boolean loaded() {
        return LOADED;
    }

    /**
     * Answers AE2's grid node capability with a bundle's or converter's link. AE2 on 1.20.1 finds a
     * block's node by asking its block entity for this capability; any other question gets an
     * empty answer, so callers fall through to their own capabilities.
     */
    public static <T> LazyOptional<T> gridNode(Capability<T> capability, @Nullable Object link) {
        return LOADED && link != null ? Hooks.gridNode(capability, link) : LazyOptional.empty();
    }

    /**
     * Brings a bundle's ME link up to date with its pipes: creates it when an ME pipe arrives,
     * drops it when the pipe goes, and otherwise refreshes the faces it is exposed on.
     *
     * @param link the bundle's current link, or {@code null}
     * @return the link to keep, or {@code null}
     */
    public static @Nullable Object sync(ConduitBundleBlockEntity bundle, @Nullable Object link) {
        return LOADED ? Hooks.sync(bundle, link) : null;
    }

    /** Takes a bundle's or converter's link out of AE2's grid, for when the block is broken or unloaded. */
    public static void stop(@Nullable Object link) {
        if (LOADED && link != null) {
            Hooks.stop(link);
        }
    }

    /**
     * Joins an AE converter to the ME network around it.
     *
     * @return the link to keep on the converter, or {@code null} without AE2
     */
    public static @Nullable Object startConverter(FluxConverterBlockEntity converter) {
        return LOADED ? Hooks.startConverter(converter) : null;
    }

    /** Moves an AE converter's network connection to the faces it now outputs on. */
    public static void updateConverterSides(@Nullable Object link) {
        if (LOADED && link != null) {
            Hooks.updateConverterSides(link);
        }
    }

    /** Feeds an AE converter's buffer into its network; called every tick by the converter. */
    public static void pushConverter(@Nullable Object link) {
        if (LOADED && link != null) {
            Hooks.pushConverter(link);
        }
    }

    /** Whether an ME pipe attaches to the block at {@code pos} on its given side: anything with an AE2 node there. */
    public static boolean attaches(Level level, BlockPos pos, Direction side) {
        return LOADED && Hooks.attaches(level, pos, side);
    }

    /** The part that names AE2 classes, only ever loaded when AE2 is installed. */
    private static final class Hooks {
        static <T> LazyOptional<T> gridNode(Capability<T> capability, Object link) {
            if (capability == Capabilities.IN_WORLD_GRID_NODE_HOST && link instanceof IInWorldGridNodeHost host) {
                return LazyOptional.of(() -> host).cast();
            }
            return LazyOptional.empty();
        }

        static Object startConverter(FluxConverterBlockEntity converter) {
            if (converter.getLevel() == null || converter.getLevel().isClientSide) {
                return null;
            }
            ConverterAeLink link = new ConverterAeLink(converter);
            link.start();
            return link;
        }

        static @Nullable Object sync(ConduitBundleBlockEntity bundle, @Nullable Object current) {
            Level level = bundle.getLevel();
            if (level == null || level.isClientSide) {
                return current;
            }
            MeLink link = (MeLink) current;
            if (link != null && !link.matches(bundle.bundle())) {
                link.stop();
                link = null;
            }
            if (link == null) {
                link = MeLink.forBundle(bundle);
                if (link != null) {
                    link.start(level, bundle.getBlockPos());
                }
                return link;
            }
            link.updateSides();
            return link;
        }

        static void updateConverterSides(Object link) {
            if (link instanceof ConverterAeLink converter) {
                converter.updateSides();
            }
        }

        static void pushConverter(Object link) {
            if (link instanceof ConverterAeLink converter) {
                converter.push();
            }
        }

        static void stop(Object link) {
            if (link instanceof MeLink pipe) {
                pipe.stop();
            } else if (link instanceof ConverterAeLink converter) {
                converter.stop();
            }
        }

        static boolean attaches(Level level, BlockPos pos, Direction side) {
            return GridHelper.getExposedNode(level, pos, side) != null;
        }
    }
}
