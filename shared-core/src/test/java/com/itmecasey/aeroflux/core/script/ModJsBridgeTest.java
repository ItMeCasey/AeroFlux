package com.itmecasey.aeroflux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.modjs.script.ExtraBindings;
import com.itmecasey.modjs.script.ScriptType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/** Runs real JavaScript (Rhino, the engine ModJS uses) against the API through the reflective bridge. */
class ModJsBridgeTest {
    /** A world with one machine, one failing read and a few fixed answers. */
    static final class FakeWorld implements ScriptWorld {
        @Override
        public String version() {
            return "2026.11.99";
        }

        @Override
        public RecipeShape recipeShape() {
            return RecipeShape.V26;
        }

        @Override
        public ScriptInfo.Machine machine(String dimension, int x, int y, int z) {
            if (y < 0) {
                throw new IllegalStateException("no such chunk");
            }
            if (x != 5) {
                return null;
            }
            return new ScriptInfo.Machine("grinder", "gold", "running", 40, 200, 12, 2, 1, "always",
                    new ScriptInfo.Flux("machine", "aero", 3_000, 8_000));
        }

        @Override
        public ScriptInfo.Flux flux(String dimension, int x, int y, int z) {
            return null;
        }

        @Override
        public ScriptInfo.Network network(String dimension, int x, int y, int z, PipeType pipe) {
            return new ScriptInfo.Network(pipe.id(), 4, 1, 2, 480, 0, 0, false);
        }

        @Override
        public ScriptInfo.Player player(String name) {
            return null;
        }
    }

    @AfterEach
    void tearDown() {
        ScriptEvents.clearAll();
        ExtraBindings.PROVIDERS.clear();
    }

    ScriptableObject lastScope;

    Object run(ScriptType type, String js) {
        assertTrue(ModJsBridge.install(t -> new AeroFluxApi(t, new FakeWorld())));
        Context cx = Context.enter();
        try {
            lastScope = cx.initStandardObjects();
            ExtraBindings.PROVIDERS.get(0).install(type, cx, lastScope);
            return cx.evaluateString(lastScope, js, "test.js", 1, null);
        } finally {
            Context.exit();
        }
    }

    /** Evaluates more script in the scope the last run() made. */
    Object more(String js) {
        Context cx = Context.enter();
        try {
            return cx.evaluateString(lastScope, js, "more.js", 1, null);
        } finally {
            Context.exit();
        }
    }

    @Test
    void serverScriptsGetTheGlobal() {
        assertEquals("2026.11.99", Context.toString(run(ScriptType.SERVER, "AeroFlux.version()")));
        assertEquals("server", Context.toString(run(ScriptType.SERVER, "AeroFlux.type()")));
        assertEquals("startup", Context.toString(run(ScriptType.STARTUP, "AeroFlux.type()")));
    }

    @Test
    void clientScriptsGetNoGlobal() {
        assertEquals("undefined", Context.toString(run(ScriptType.CLIENT, "typeof AeroFlux")));
        assertTrue(ModJsBridge.clientScript("CLIENT"));
        assertFalse(ModJsBridge.clientScript("SERVER"));
        assertFalse(ModJsBridge.clientScript(null));
    }

    @Test
    void scriptsReadMachinesAndNetworks() {
        assertEquals(12.0, Context.toNumber(run(ScriptType.SERVER, "AeroFlux.machine('minecraft:overworld', 5, 64, 5).fluxPerTick")));
        assertEquals("aero", Context.toString(run(ScriptType.SERVER, "AeroFlux.machine('minecraft:overworld', 5, 64, 5).flux.type")));
        assertEquals(480.0, Context.toNumber(run(ScriptType.SERVER, "AeroFlux.network('minecraft:overworld', 0, 64, 0, 'energy').amountPerTick")));
        assertEquals("true", Context.toString(run(ScriptType.SERVER, "AeroFlux.machine('minecraft:overworld', 9, 64, 9) === null")));
    }

    @Test
    void aReadThatFailsOrAsksForAnUnknownThingGivesNullNotAnError() {
        assertEquals("true", Context.toString(run(ScriptType.SERVER, "AeroFlux.machine('minecraft:overworld', 5, -1, 5) === null")));
        assertEquals("true", Context.toString(run(ScriptType.SERVER, "AeroFlux.network('minecraft:overworld', 0, 64, 0, 'steam') === null")));
    }

    @Test
    void listenersRunWhenTheGameFiresAnEventAndABrokenOneIsHarmless() {
        run(ScriptType.SERVER, "var seen = ''; AeroFlux.on('machineFinished', function (e) { throw new Error('oops'); });"
                + "AeroFlux.onMachineFinished(function (e) { seen = e.id + ':' + e.tier + ':' + e.y; });");
        assertTrue(ScriptEvents.hasListeners(ScriptEvents.MACHINE_FINISHED));
        ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.MACHINE_FINISHED).at("minecraft:overworld", 1, 70, 3)
                .id("grinder").tier("gold").build());
        assertEquals("grinder:gold:70", Context.toString(more("seen")));
    }

    @Test
    void anUnknownEventNameIsIgnored() {
        run(ScriptType.SERVER, "AeroFlux.on('machineExploded', function () {});");
        assertFalse(ScriptEvents.hasListeners("machineExploded"));
    }

    @Test
    void loadingTheServerScriptsAgainDropsTheOldListeners() {
        assertTrue(ModJsBridge.install(t -> new AeroFluxApi(t, new FakeWorld())));
        Context cx = Context.enter();
        try {
            ScriptableObject first = cx.initStandardObjects();
            ExtraBindings.PROVIDERS.get(0).install(ScriptType.SERVER, cx, first);
            cx.evaluateString(first, "AeroFlux.onTierUpgraded(function () {});", "a.js", 1, null);
            ScriptableObject startup = cx.initStandardObjects();
            ExtraBindings.PROVIDERS.get(0).install(ScriptType.STARTUP, cx, startup);
            cx.evaluateString(startup, "AeroFlux.onTierUpgraded(function () {});", "b.js", 1, null);
            ScriptableObject reload = cx.initStandardObjects();
            ExtraBindings.PROVIDERS.get(0).install(ScriptType.SERVER, cx, reload);
        } finally {
            Context.exit();
        }
        assertTrue(ScriptEvents.hasListeners(ScriptEvents.TIER_UPGRADED), "the startup listener stays");
        List<String> called = new ArrayList<>();
        ScriptEvents.on("test", ScriptEvents.TIER_UPGRADED, e -> called.add("test"));
        ScriptEvents.fire(new ScriptEvents.Builder(ScriptEvents.TIER_UPGRADED).build());
        assertEquals(List.of("test"), called);
    }

    @Test
    void aScriptBuildsARecipeForEventCustom() {
        Object json = run(ScriptType.SERVER, "AeroFlux.recipe('grinder').input('#c:ores/iron').output('2x minecraft:raw_iron')"
                + ".output('minecraft:gravel', 1, 0.1).time(300).build()");
        // A Java map; ModJS's event.custom reads maps as they are.
        Object unwrapped = json instanceof org.mozilla.javascript.Wrapper w ? w.unwrap() : json;
        assertTrue(unwrapped instanceof java.util.Map, "got " + (unwrapped == null ? "null" : unwrapped.getClass()));
        assertEquals("aeroflux:grinder", ((java.util.Map<?, ?>) unwrapped).get("type"));
    }

    @Test
    void theListsOfNamesComeBack() {
        assertEquals("6", Context.toString(run(ScriptType.SERVER, "AeroFlux.tiers().size()")));
        assertEquals("true", Context.toString(run(ScriptType.SERVER, "AeroFlux.recipeMachines().contains('grinder')")));
        assertEquals("false", Context.toString(run(ScriptType.SERVER, "AeroFlux.recipeMachines().contains('furnace')")));
        assertEquals("true", Context.toString(run(ScriptType.SERVER, "AeroFlux.events().contains('tierUpgraded')")));
    }

    @Test
    void withoutModJsNothingLoadsAndNothingFails() {
        ClassLoader blind = new ClassLoader(null) {
        };
        assertFalse(ModJsBridge.install(blind, t -> new AeroFluxApi(t, new FakeWorld())));
        assertTrue(ExtraBindings.PROVIDERS.isEmpty());
    }
}
