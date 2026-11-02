package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * All the face settings of one machine: a {@link SideConfig} for each kind of resource the machine
 * uses. This is where side-config clicks are judged, so the rules (a machine ignores resources it
 * does not use, a click that changes nothing reports no change) are written once and unit tested
 * without a game.
 */
public final class SideConfigs {
    private final Map<SideResource, SideConfig> byResource = new EnumMap<>(SideResource.class);

    /** Starts every used resource at its default. */
    public SideConfigs(Set<SideResource> used) {
        for (SideResource resource : used) {
            byResource.put(resource, SideConfig.defaultFor(resource));
        }
    }

    /** The kinds of resource this machine has settings for. */
    public Set<SideResource> used() {
        return byResource.isEmpty() ? EnumSet.noneOf(SideResource.class) : EnumSet.copyOf(byResource.keySet());
    }

    /** Whether the machine has settings for this kind of resource. */
    public boolean uses(SideResource resource) {
        return byResource.containsKey(resource);
    }

    /** The mode at a face; faces of a resource the machine does not use are {@link ConnectionMode#BLOCKED}. */
    public ConnectionMode mode(SideResource resource, Face face) {
        SideConfig config = byResource.get(resource);
        return config == null ? ConnectionMode.BLOCKED : config.mode(face);
    }

    /** The packed settings of a resource (see {@link SideConfig#pack}); 0 if the machine does not use it. */
    public int packed(SideResource resource) {
        SideConfig config = byResource.get(resource);
        return config == null ? 0 : config.pack();
    }

    /** Replaces a resource's settings with a packed value, as read from saved data. Ignored if the resource is unused. */
    public void restore(SideResource resource, int packed) {
        SideConfig config = byResource.get(resource);
        if (config != null) {
            config.copyFrom(SideConfig.unpack(packed));
        }
    }

    /**
     * Applies a click.
     *
     * @return true if anything changed; false for a resource the machine does not use, or a click
     *         that leaves everything as it was
     */
    public boolean apply(SideCommand command) {
        SideConfig config = byResource.get(command.resource());
        if (config == null) {
            return false;
        }
        SideConfig before = config.copy();
        switch (command.kind()) {
            case SET -> config.set(command.face(), command.mode());
            case ALL_TO -> config.setAll(command.mode());
            case COPY_TO_ALL -> config.setAll(config.mode(command.face()));
            case RESET -> config.copyFrom(SideConfig.defaultFor(command.resource()));
        }
        return !before.equals(config);
    }

    /** An independent copy. */
    public SideConfigs copy() {
        SideConfigs copy = new SideConfigs(used());
        for (Map.Entry<SideResource, SideConfig> entry : byResource.entrySet()) {
            copy.byResource.get(entry.getKey()).copyFrom(entry.getValue());
        }
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SideConfigs configs && byResource.equals(configs.byResource);
    }

    @Override
    public int hashCode() {
        return byResource.hashCode();
    }
}
