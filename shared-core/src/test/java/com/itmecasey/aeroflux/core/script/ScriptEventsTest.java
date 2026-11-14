package com.itmecasey.aeroflux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScriptEventsTest {
    @AfterEach
    void tearDown() {
        ScriptEvents.clearAll();
    }

    private static ScriptEvents.Event finished() {
        return new ScriptEvents.Builder(ScriptEvents.MACHINE_FINISHED).at("minecraft:overworld", 1, 2, 3)
                .id("grinder").tier("gold").build();
    }

    @Test
    void listenersReceiveTheFieldsOfTheEvent() {
        List<ScriptEvents.Event> seen = new ArrayList<>();
        ScriptEvents.on("server", ScriptEvents.MACHINE_FINISHED, seen::add);
        ScriptEvents.fire(finished());
        assertEquals(1, seen.size());
        ScriptEvents.Event e = seen.get(0);
        assertEquals("minecraft:overworld", e.dimension);
        assertEquals(2, e.y);
        assertEquals("grinder", e.id);
        assertEquals("gold", e.tier);
        assertEquals("", e.face);
    }

    @Test
    void anEventWithNoListenersDoesNothing() {
        assertFalse(ScriptEvents.hasListeners(ScriptEvents.MACHINE_FINISHED));
        ScriptEvents.fire(finished());
    }

    @Test
    void aFailingListenerNeverStopsTheOthers() {
        List<String> order = new ArrayList<>();
        ScriptEvents.on("server", ScriptEvents.MACHINE_FINISHED, e -> {
            throw new IllegalStateException("script bug");
        });
        ScriptEvents.on("server", ScriptEvents.MACHINE_FINISHED, e -> order.add("second"));
        ScriptEvents.fire(finished());
        ScriptEvents.fire(finished());
        assertEquals(List.of("second", "second"), order);
    }

    @Test
    void clearingATypeKeepsTheListenersOfTheOtherType() {
        List<String> order = new ArrayList<>();
        ScriptEvents.on("startup", ScriptEvents.TIER_UPGRADED, e -> order.add("startup"));
        ScriptEvents.on("server", ScriptEvents.TIER_UPGRADED, e -> order.add("server"));
        ScriptEvents.clear("server");
        assertTrue(ScriptEvents.hasListeners(ScriptEvents.TIER_UPGRADED));
        ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.TIER_UPGRADED).build());
        assertEquals(List.of("startup"), order);
    }

    @Test
    void everyEventNameIsListed() {
        assertEquals(List.of("machineFinished", "pipeModeChanged", "tierUpgraded"), ScriptEvents.NAMES);
    }
}
