package com.itmecasey.aeroflux.core.script;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The script surface of AeroFlux, exposed to ModJS scripts as the global {@code AeroFlux}. Nothing here throws into a script
 * except the recipe builder's own checks: a read that fails or finds nothing logs one plain line and returns {@code null}.
 *
 * <p>One instance exists per script type. Server scripts and events can read the game; startup scripts can register listeners
 * and build recipes but have no world to read yet.
 */
public final class AeroFluxApi {
    private static final Logger LOG = Logger.getLogger("aeroflux");

    private final String type;
    private final ScriptWorld world;

    public AeroFluxApi(String type, ScriptWorld world) {
        this.type = type;
        this.world = world;
    }

    // --- Information -------------------------------------------------------------------------

    /** The script type this global belongs to: startup or server. */
    public String type() {
        return type;
    }

    /** The mod's version. */
    public String version() {
        return guard("version", world::version);
    }

    /** The tier ids, lowest first. */
    public List<String> tiers() {
        List<String> ids = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            ids.add(tier.id());
        }
        return ids;
    }

    /** The machine ids. */
    public List<String> machines() {
        List<String> ids = new ArrayList<>();
        for (MachineType machine : MachineType.values()) {
            ids.add(machine.id());
        }
        return ids;
    }

    /** The machines that have recipes of their own, the ones {@link #recipe} accepts. */
    public List<String> recipeMachines() {
        List<String> ids = new ArrayList<>();
        for (MachineType machine : MachineType.values()) {
            if (machine.hasOwnRecipes()) {
                ids.add(machine.id());
            }
        }
        return ids;
    }

    /** The pipe kinds. */
    public List<String> pipes() {
        List<String> ids = new ArrayList<>();
        for (PipeType pipe : PipeType.values()) {
            ids.add(pipe.id());
        }
        return ids;
    }

    /** The event names {@link #on} takes. */
    public List<String> events() {
        return ScriptEvents.NAMES;
    }

    // --- Events ------------------------------------------------------------------------------

    /** Listens to an event by name; see {@link #events()}. An unknown name is reported once and ignored. */
    public void on(String event, Consumer<ScriptEvents.Event> listener) {
        if (event == null || !ScriptEvents.NAMES.contains(event)) {
            LOG.warning("AeroFlux: there is no event '" + event + "'; the events are " + ScriptEvents.NAMES);
            return;
        }
        if (listener == null) {
            LOG.warning("AeroFlux: the listener for " + event + " is missing");
            return;
        }
        ScriptEvents.on(type, event, listener);
    }

    public void onMachineFinished(Consumer<ScriptEvents.Event> listener) {
        on(ScriptEvents.MACHINE_FINISHED, listener);
    }

    public void onPipeModeChanged(Consumer<ScriptEvents.Event> listener) {
        on(ScriptEvents.PIPE_MODE_CHANGED, listener);
    }

    public void onTierUpgraded(Consumer<ScriptEvents.Event> listener) {
        on(ScriptEvents.TIER_UPGRADED, listener);
    }

    // --- Reading the game --------------------------------------------------------------------

    /** The machine at a block, or null. */
    public ScriptInfo.Machine machine(String dimension, int x, int y, int z) {
        return guard("machine", () -> world.machine(dimension, x, y, z));
    }

    /** The Flux held by the machine, collector, tank, converter or chunk loader at a block, or null. */
    public ScriptInfo.Flux flux(String dimension, int x, int y, int z) {
        return guard("flux", () -> world.flux(dimension, x, y, z));
    }

    /** The network of one pipe kind through the bundle at a block, or null. {@code pipe} is one of {@link #pipes()}. */
    public ScriptInfo.Network network(String dimension, int x, int y, int z, String pipe) {
        PipeType kind = pipeOf(pipe);
        if (kind == null) {
            LOG.warning("AeroFlux: there is no pipe '" + pipe + "'; the pipes are " + pipes());
            return null;
        }
        return guard("network", () -> world.network(dimension, x, y, z, kind));
    }

    /** An online player's chunk loader numbers, or null. */
    public ScriptInfo.Player player(String name) {
        return guard("player", () -> world.player(name));
    }

    // --- Recipes -----------------------------------------------------------------------------

    /**
     * Starts a recipe for a machine; finish it with {@code build()} and hand the result to ModJS's {@code event.custom(...)}
     * inside {@code ServerEvents.recipes}. The same keys the recipe files use, in the shape of this Minecraft version.
     */
    public MachineRecipeBuilder recipe(String machine) {
        return new MachineRecipeBuilder(machine, world.recipeShape());
    }

    // --- Helpers -----------------------------------------------------------------------------

    static PipeType pipeOf(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.toLowerCase(Locale.ROOT);
        for (PipeType pipe : PipeType.values()) {
            if (pipe.id().equals(wanted)) {
                return pipe;
            }
        }
        return null;
    }

    private <T> T guard(String what, Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException | LinkageError e) {
            LOG.warning("AeroFlux: " + what + " could not be read: " + e);
            return null;
        }
    }
}
