package com.itmecasey.aeroflux.core.script;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The events scripts can listen to. The game side calls {@link #fire} on the server thread; a listener that throws is
 * reported with one plain line (once per event until its scripts reload) and never reaches the game.
 *
 * <p>Listeners are kept per script type, so a reload of the server scripts drops the old server listeners and nothing else.
 */
public final class ScriptEvents {
    /** A machine finished a job. Fields: dimension, x, y, z, id (the machine), tier. */
    public static final String MACHINE_FINISHED = "machineFinished";
    /** A pipe face changed its mode. Fields: dimension, x, y, z, id (the pipe), face, mode. */
    public static final String PIPE_MODE_CHANGED = "pipeModeChanged";
    /** An Upgrade Card raised a block one tier. Fields: dimension, x, y, z, id (the block), tier (the new one), player. */
    public static final String TIER_UPGRADED = "tierUpgraded";

    /** Every event name, in the order the documentation lists them. */
    public static final List<String> NAMES = List.of(MACHINE_FINISHED, PIPE_MODE_CHANGED, TIER_UPGRADED);

    private static final Logger LOG = Logger.getLogger("aeroflux");

    /** What a listener receives. A field that does not apply to the event is empty (or 0 for the position). */
    public static final class Event {
        public final String name;
        public final String dimension;
        public final int x;
        public final int y;
        public final int z;
        public final String id;
        public final String tier;
        public final String face;
        public final String mode;
        public final String player;

        private Event(Builder b) {
            this.name = b.name;
            this.dimension = b.dimension;
            this.x = b.x;
            this.y = b.y;
            this.z = b.z;
            this.id = b.id;
            this.tier = b.tier;
            this.face = b.face;
            this.mode = b.mode;
            this.player = b.player;
        }

        @Override
        public String toString() {
            return name + " at " + dimension + " " + x + " " + y + " " + z;
        }
    }

    /** Collects the fields of one event; the game side fills what the event has. */
    public static final class Builder {
        private final String name;
        private String dimension = "";
        private int x;
        private int y;
        private int z;
        private String id = "";
        private String tier = "";
        private String face = "";
        private String mode = "";
        private String player = "";

        public Builder(String name) {
            this.name = name;
        }

        public Builder at(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public Builder face(String face) {
            this.face = face;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder player(String player) {
            this.player = player;
            return this;
        }

        public Event build() {
            return new Event(this);
        }
    }

    private record Registration(String type, Consumer<Event> listener) {
    }

    private static final Map<String, List<Registration>> LISTENERS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private ScriptEvents() {
    }

    /** Whether any listener is waiting for this event; the game side asks first so it builds nothing for no one. */
    public static boolean hasListeners(String name) {
        List<Registration> list = LISTENERS.get(name);
        return list != null && !list.isEmpty();
    }

    /** Adds a listener for a script type ("startup" or "server"). */
    public static void on(String type, String name, Consumer<Event> listener) {
        LISTENERS.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(new Registration(type, listener));
    }

    /** Drops the listeners one script type registered; called when its scripts load again. */
    public static void clear(String type) {
        for (List<Registration> list : LISTENERS.values()) {
            list.removeIf(r -> r.type().equals(type));
        }
        REPORTED.clear();
    }

    /** Drops every listener. */
    public static void clearAll() {
        LISTENERS.clear();
        REPORTED.clear();
    }

    /** Calls every listener of the event. A listener that throws is skipped; the others still run. */
    public static void fire(Event event) {
        List<Registration> list = LISTENERS.get(event.name);
        if (list == null) {
            return;
        }
        for (Registration r : list) {
            try {
                r.listener().accept(event);
            } catch (RuntimeException | LinkageError e) {
                if (REPORTED.add(event.name)) {
                    LOG.warning("AeroFlux: a script listener for " + event.name + " failed: " + e);
                }
            }
        }
    }
}
