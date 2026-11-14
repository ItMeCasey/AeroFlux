package com.itmecasey.aeroflux.core.script;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the JSON of one recipe for a machine that has recipes of its own, in the keys the recipe files use ({@code inputs},
 * {@code outputs}, {@code time}, {@code flux}, {@code flux_type}, {@code water}, {@code silk_outputs}). The result is a plain
 * map that ModJS's {@code event.custom(...)} takes as it is.
 *
 * <p>Items are ids; an ingredient may be a {@code #tag}; either may start with a count such as {@code 3x minecraft:iron_ingot}.
 */
public final class MachineRecipeBuilder {
    private static final Pattern COUNTED = Pattern.compile("^(\\d+)x\\s*(.+)$");

    private final MachineType machine;
    private final RecipeShape shape;
    private final List<Map<String, Object>> inputs = new ArrayList<>();
    private final List<Map<String, Object>> outputs = new ArrayList<>();
    private final List<Map<String, Object>> silkOutputs = new ArrayList<>();
    private int time = 200;
    private int flux;
    private String fluxType;
    private int water;

    /** @throws IllegalArgumentException when the machine does not exist or has no recipes of its own */
    public MachineRecipeBuilder(String machineId, RecipeShape shape) {
        MachineType found = machineId == null ? null : MachineType.byId(machineId.toLowerCase(Locale.ROOT));
        if (found == null || !found.hasOwnRecipes()) {
            throw new IllegalArgumentException("AeroFlux has no recipes for '" + machineId + "'");
        }
        this.machine = found;
        this.shape = shape;
    }

    /** An ingredient, one item. */
    public MachineRecipeBuilder input(String ingredient) {
        return input(ingredient, 1);
    }

    /** An ingredient and how many of it a run uses. */
    public MachineRecipeBuilder input(String ingredient, int count) {
        Counted item = parse(ingredient, count);
        Map<String, Object> entry = new LinkedHashMap<>();
        if (item.count > 1) {
            entry.put("count", item.count);
        }
        entry.put("ingredient", ingredientJson(item.id));
        inputs.add(entry);
        return this;
    }

    public MachineRecipeBuilder output(String item) {
        return output(item, 1, 1.0);
    }

    public MachineRecipeBuilder output(String item, int count) {
        return output(item, count, 1.0);
    }

    /** A result, how many, and the chance of getting it (1 for always). */
    public MachineRecipeBuilder output(String item, int count, double chance) {
        outputs.add(result(item, count, chance));
        return this;
    }

    /** What a Plant Grower with a Silk Touch Upgrade gives instead; only the Plant Grower has these. */
    public MachineRecipeBuilder silkOutput(String item, int count, double chance) {
        if (machine != MachineType.PLANT_GROWER) {
            throw new IllegalArgumentException("Only the Plant Grower has silk outputs");
        }
        silkOutputs.add(result(item, count, chance));
        return this;
    }

    /** Ticks per run. */
    public MachineRecipeBuilder time(int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("A recipe time must be at least 1 tick");
        }
        this.time = ticks;
        return this;
    }

    /** Extra Flux a run takes, in mB, on top of the running cost. */
    public MachineRecipeBuilder flux(int mB) {
        if (mB < 0) {
            throw new IllegalArgumentException("Flux cannot be negative");
        }
        this.flux = mB;
        return this;
    }

    /** The Flux the recipe needs: aero, moon or twilight. */
    public MachineRecipeBuilder fluxType(String type) {
        String id = type == null ? "" : type.toLowerCase(Locale.ROOT);
        for (FluxType known : FluxType.values()) {
            if (known.id().equals(id)) {
                this.fluxType = id;
                return this;
            }
        }
        throw new IllegalArgumentException("Unknown Flux type '" + type + "'");
    }

    /** Water a run takes, in mB; only machines with a water tank use it. */
    public MachineRecipeBuilder water(int mB) {
        if (mB < 0 || mB > 1_000_000) {
            throw new IllegalArgumentException("Water must be between 0 and 1000000 mB");
        }
        this.water = mB;
        return this;
    }

    /** The recipe as a map in the shape of this Minecraft version, ready for {@code event.custom(...)}. */
    public Map<String, Object> build() {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalStateException("A recipe needs at least one input and one output");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "aeroflux:" + machine.id());
        json.put("inputs", new ArrayList<>(inputs));
        json.put("outputs", new ArrayList<>(outputs));
        if (!silkOutputs.isEmpty()) {
            json.put("silk_outputs", new ArrayList<>(silkOutputs));
        }
        json.put("time", time);
        if (flux > 0) {
            json.put("flux", flux);
        }
        if (fluxType != null) {
            json.put("flux_type", fluxType);
        }
        if (water > 0) {
            json.put("water", water);
        }
        return json;
    }

    private Object ingredientJson(String id) {
        if (!shape.objectIngredients) {
            return id;
        }
        Map<String, Object> object = new LinkedHashMap<>();
        if (id.startsWith("#")) {
            object.put("tag", id.substring(1));
        } else {
            object.put("item", id);
        }
        return object;
    }

    private Map<String, Object> result(String item, int count, double chance) {
        Counted counted = parse(item, count);
        if (counted.id.startsWith("#")) {
            throw new IllegalArgumentException("A result must be an item, not a tag: " + item);
        }
        if (chance <= 0 || chance > 1) {
            throw new IllegalArgumentException("A chance must be above 0 and at most 1");
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        if (shape.stackResults) {
            Map<String, Object> stack = new LinkedHashMap<>();
            stack.put("id", counted.id);
            if (counted.count > 1) {
                stack.put("count", counted.count);
            }
            entry.put("item", stack);
        } else {
            entry.put("item", counted.id);
            if (counted.count > 1) {
                entry.put("count", counted.count);
            }
        }
        if (chance < 1) {
            entry.put("chance", chance);
        }
        return entry;
    }

    private record Counted(String id, int count) {
    }

    private static Counted parse(String text, int count) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("An item id is missing");
        }
        String id = text.trim();
        int n = count;
        Matcher m = COUNTED.matcher(id);
        if (m.matches()) {
            n = Integer.parseInt(m.group(1)) * Math.max(1, count);
            id = m.group(2).trim();
        }
        if (n < 1) {
            throw new IllegalArgumentException("A count must be at least 1");
        }
        return new Counted(id.indexOf(':') < 0 && !id.startsWith("#") ? "minecraft:" + id : id, n);
    }
}
