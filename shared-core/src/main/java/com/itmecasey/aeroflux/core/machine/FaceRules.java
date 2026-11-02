package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.conduit.ConnectionMode;

/**
 * What a machine's face setting allows. The modes are the same ones pipes use, read from the
 * machine's point of view:
 *
 * <ul>
 *   <li>{@link ConnectionMode#IMPORT}: the face lets things in.</li>
 *   <li>{@link ConnectionMode#EXPORT}: the face lets things out.</li>
 *   <li>{@link ConnectionMode#OPEN}: both ("Both" on the screen).</li>
 *   <li>{@link ConnectionMode#OFF}: the face is still there for pipes to attach to, but nothing
 *       passes it.</li>
 *   <li>{@link ConnectionMode#BLOCKED}: the face is hidden, so neither pipes nor other mods'
 *       hoppers can see the machine from that side at all.</li>
 * </ul>
 *
 * <p>The port's role narrows this further: a slot that is an input can never be pulled from, and
 * an output can never be pushed into, whatever the face says. A {@link PortRole#BUFFER} follows
 * the face alone.
 */
public final class FaceRules {
    private FaceRules() {
    }

    /** Whether a machine shows its capability on a face at all. */
    public static boolean exposed(ConnectionMode mode) {
        return mode != ConnectionMode.BLOCKED;
    }

    /** Whether something outside may put things into a port of this role through a face. */
    public static boolean canInsert(PortRole role, ConnectionMode mode) {
        return role != PortRole.OUTPUT && (mode == ConnectionMode.IMPORT || mode == ConnectionMode.OPEN);
    }

    /** Whether something outside may take things from a port of this role through a face. */
    public static boolean canExtract(PortRole role, ConnectionMode mode) {
        return role != PortRole.INPUT && (mode == ConnectionMode.EXPORT || mode == ConnectionMode.OPEN);
    }
}
