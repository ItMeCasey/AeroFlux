package com.itmecasey.aeroflux.core.protect;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Remembers recent protection answers for a short time, so a machine that acts every few ticks does
 * not put an event on the bus every time. Claims change rarely and a second or two of delay in
 * noticing is harmless.
 *
 * <p>Keys are whatever the caller makes of the place asked about (a chunk or a block position packed
 * into a long, mixed with the kind of action). Time is the level's game time in ticks, passed in on
 * every call, so there is no clock to fake in a test.
 */
public final class ProtectionCache {
    private static final int MAX_ENTRIES = 256;

    private record Entry(boolean allowed, long expires) {
    }

    private final int ttlTicks;
    private final Map<Long, Entry> entries = new HashMap<>();

    /** @param ttlTicks how long an answer is trusted, in ticks */
    public ProtectionCache(int ttlTicks) {
        this.ttlTicks = Math.max(1, ttlTicks);
    }

    /**
     * The remembered answer for a key, asking {@code ask} (and remembering its answer) when there is
     * none or it has run out.
     */
    public boolean allowed(long key, long now, BooleanSupplier ask) {
        Entry known = entries.get(key);
        if (known != null && now < known.expires()) {
            return known.allowed();
        }
        boolean answer = ask.getAsBoolean();
        if (entries.size() >= MAX_ENTRIES) {
            prune(now);
        }
        entries.put(key, new Entry(answer, now + ttlTicks));
        return answer;
    }

    /** Forgets everything, such as when the owner changes. */
    public void clear() {
        entries.clear();
    }

    /** How many answers are held. */
    public int size() {
        return entries.size();
    }

    /** Drops the answers that ran out; if the table is still full, drops all of them. */
    private void prune(long now) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().expires()) {
                iterator.remove();
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
    }
}
