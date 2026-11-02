package com.itmecasey.aeroflux.core.conduit;

import java.util.EnumMap;
import java.util.Map;

/**
 * The settings of a bundle that can be copied from one bundle to another: for each kind of pipe,
 * the mode at every face and the distribution mode.
 *
 * <p>Tiers and upgrades are not part of it, because applying them would need the matching items
 * to be handed over. Settings are matched by pipe type, so pasting onto a bundle that holds other
 * pipes changes only the pipes the two bundles have in common.
 */
public final class BundleConfig {
    private final Map<PipeType, ConnectionMode[]> modes = new EnumMap<>(PipeType.class);
    private final Map<PipeType, DistributionMode> distribution = new EnumMap<>(PipeType.class);

    private BundleConfig() {
    }

    /** Copies the settings out of a bundle. */
    public static BundleConfig from(BundleState state) {
        BundleConfig config = new BundleConfig();
        for (PipeType type : state.set().types()) {
            ConnectionMode[] byFace = new ConnectionMode[Face.values().length];
            for (Face face : Face.values()) {
                byFace[face.index()] = state.mode(type.lane(), face);
            }
            config.modes.put(type, byFace);
            config.distribution.put(type, state.distribution(type.lane()));
        }
        return config;
    }

    /** The kinds of pipe this configuration has settings for. */
    public PipeSetView pipes() {
        PipeSet set = PipeSet.EMPTY;
        for (PipeType type : modes.keySet()) {
            set = set.with(type);
        }
        return new PipeSetView(set);
    }

    /**
     * Applies the settings to a bundle.
     *
     * @return how many pipes of the target were changed
     */
    public int applyTo(BundleState target) {
        int changed = 0;
        for (PipeType type : target.set().types()) {
            ConnectionMode[] byFace = modes.get(type);
            if (byFace == null) {
                continue;
            }
            for (Face face : Face.values()) {
                target.setMode(type.lane(), face, byFace[face.index()]);
            }
            target.setDistribution(type.lane(), distribution.get(type));
            changed++;
        }
        return changed;
    }

    /** A small read-only wrapper so callers can ask what is inside without getting the map. */
    public record PipeSetView(PipeSet set) {
        public boolean isEmpty() {
            return set.isEmpty();
        }
    }
}
