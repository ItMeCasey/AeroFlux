package com.itmecasey.aeroflux.core.imports;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads another mod's processing recipe, written out as JSON, into the plain shape AeroFlux's
 * machines work with: some ingredients (each with a count) and some products (each with a count
 * and a chance).
 *
 * <p>Every mod spells its recipes differently, but they draw on a small set of habits, which this
 * reader knows:
 *
 * <ul>
 *     <li>Inputs under {@code ingredient}, {@code input}, {@code input_item}, {@code seed}, the
 *     numbered {@code input0}, {@code input1}, ... (Immersive Engineering) or Mekanism's main and
 *     extra input (one ingredient each, where a list means "any of these"), or under
 *     {@code ingredients}, {@code inputs}, {@code itemInputs} or {@code item_inputs} (several). An
 *     ingredient is an id, a {@code #tag}, an {@code item}/{@code tag} object, or a sized wrapper
 *     such as {@code {"ingredient": .., "count": 2}} or Immersive Engineering's
 *     {@code base_ingredient}. The same ingredient twice counts as two of it.</li>
 *     <li>Products under {@code result}, {@code output}, {@code output_item} or Mekanism's main
 *     output (one), or {@code results}, {@code outputs}, {@code itemResults}, {@code item_outputs},
 *     {@code secondaries} or {@code drops} (several), plus Mekanism's secondary output at its own
 *     chance. A product is an id, or an object with an {@code id} or {@code item} (itself an id or
 *     a nested object), or a {@code tag}, with an optional {@code count} (or {@code amount}) and
 *     {@code chance}, or such an object wrapped once more under {@code output}, {@code result},
 *     {@code stack} or {@code base_ingredient}. A chance above one, as Thermal writes it, means that
 *     many for certain plus the rest as a chance.</li>
 *     <li>Ex Deorum's {@code result_amount}, a vanilla number provider, sets the count.</li>
 * </ul>
 *
 * <p>An import rule can name the keys itself when a mod uses others (see {@link Keys}). Energy and
 * anything else a recipe holds are left out; the machines move items, so a recipe that needs a
 * fluid as an input is not usable.
 */
public final class ForeignRecipeReader {
    /** The keys read when a rule names none. */
    public static final Keys DEFAULT_KEYS = new Keys(
            List.of("ingredient", "input", "input_item", "seed", "input0", "input1", "input2", "main_input", "mainInput",
                    "extra_input", "extraInput", "ingredients[]", "inputs[]", "itemInputs[]", "item_inputs[]"),
            List.of("result", "output", "output_item", "main_output", "mainOutput", "results[]", "outputs[]",
                    "itemResults[]", "item_results[]", "item_outputs[]", "secondaries[]", "drops[]"));

    /** Mekanism's second product and its chance, in the two spellings it has used. */
    private static final List<List<String>> SECONDARY_OUTPUTS = List.of(
            List.of("secondary_output", "secondary_chance"), List.of("secondaryOutput", "secondaryChance"));

    /** Keys that say an ingredient is a fluid, which the machines cannot take. */
    private static final List<String> FLUID_KEYS = List.of("fluid", "fluidTag", "fluid_tag");

    private ForeignRecipeReader() {
    }

    /**
     * Which keys hold the inputs and the products. A key ending in {@code []} holds a list of
     * separate ingredients (or products); without it the key holds one, where a list means "any of
     * these" for an ingredient. Keys a recipe does not have are skipped.
     */
    public record Keys(List<String> inputs, List<String> outputs) {
        public Keys {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }
    }

    /** An ingredient, still as the JSON the game reads ingredients from, and how many a run uses. */
    public record Input(JsonElement ingredient, int count) {
    }

    /**
     * A product: an item id, or a tag id when {@code tag} is true (the game picks an item of the
     * tag), how many, and the chance of getting them.
     */
    public record Output(String id, boolean tag, int count, double chance) {
    }

    /** What a recipe comes to; {@code needsFluid} when one of its inputs is a fluid. */
    public record Recipe(List<Input> inputs, List<Output> outputs, boolean needsFluid) {
        /** Whether a machine can run it: something to put in, something to get out, and no fluid needed. */
        public boolean usable() {
            return !inputs.isEmpty() && !outputs.isEmpty() && !needsFluid;
        }
    }

    /** Reads a recipe with the default keys. */
    public static Recipe read(JsonObject json) {
        return read(json, DEFAULT_KEYS);
    }

    /** Reads a recipe with the given keys. Parts it cannot make sense of are left out, never thrown. */
    public static Recipe read(JsonObject json, Keys keys) {
        Map<String, Input> inputs = new LinkedHashMap<>();
        boolean needsFluid = false;
        for (String key : keys.inputs()) {
            boolean list = key.endsWith("[]");
            JsonElement value = json.get(list ? key.substring(0, key.length() - 2) : key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (list && value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    needsFluid |= isFluid(element);
                    addInput(inputs, isFluid(element) ? null : input(element));
                }
            } else {
                needsFluid |= isFluid(value);
                addInput(inputs, isFluid(value) ? null : input(value));
            }
        }
        List<Output> outputs = new ArrayList<>();
        for (String key : keys.outputs()) {
            boolean list = key.endsWith("[]");
            JsonElement value = json.get(list ? key.substring(0, key.length() - 2) : key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    outputs.addAll(outputs(element, 1.0));
                }
            } else {
                outputs.addAll(outputs(value, 1.0));
            }
        }
        for (List<String> secondary : SECONDARY_OUTPUTS) {
            JsonElement value = json.get(secondary.get(0));
            if (value != null && !value.isJsonNull()) {
                outputs.addAll(outputs(value, number(json, secondary.get(1), 1.0)));
            }
        }
        if (json.has("result_amount") && outputs.size() == 1) {
            outputs = withAmount(outputs.get(0), json.get("result_amount"));
        }
        return new Recipe(List.copyOf(inputs.values()), List.copyOf(outputs), needsFluid);
    }

    /** Whether an input names a fluid rather than an item. */
    private static boolean isFluid(JsonElement element) {
        if (!element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        for (String key : FLUID_KEYS) {
            if (object.has(key)) {
                return true;
            }
        }
        return object.has("ingredient") && isFluid(object.get("ingredient"));
    }

    /** A number from the recipe, such as its time; {@code fallback} when it has none. */
    public static double number(JsonObject json, String key, double fallback) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : fallback;
    }

    // --- Inputs ------------------------------------------------------------------------------

    private static void addInput(Map<String, Input> inputs, Input input) {
        if (input == null) {
            return;
        }
        String key = input.ingredient().toString();
        Input before = inputs.get(key);
        inputs.put(key, before == null ? input : new Input(input.ingredient(), before.count() + input.count()));
    }

    private static Input input(JsonElement element) {
        if (element.isJsonPrimitive() || element.isJsonArray()) {
            return new Input(element, 1);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        int count = Math.max(1, (int) Math.round(number(object, "count", number(object, "amount", 1))));
        for (String wrapper : List.of("ingredient", "base_ingredient", "basePredicate")) {
            if (object.has(wrapper)) {
                return new Input(object.get(wrapper), count);
            }
        }
        // A plain item or tag object, maybe with a count of its own: the count is not part of the ingredient.
        JsonObject plain = object.deepCopy();
        plain.remove("count");
        plain.remove("amount");
        return new Input(plain, count);
    }

    // --- Products ----------------------------------------------------------------------------

    private static List<Output> outputs(JsonElement element, double outerChance) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return split(element.getAsString(), false, 1, outerChance);
        }
        if (!element.isJsonObject()) {
            return List.of();
        }
        JsonObject object = element.getAsJsonObject();
        double chance = outerChance * number(object, "chance", 1.0);
        int count = (int) Math.round(number(object, "count", number(object, "amount", 1)));
        for (String wrapper : List.of("output", "result", "stack")) {
            if (object.has(wrapper) && object.get(wrapper).isJsonObject()) {
                // A product one level down: Immersive Engineering's secondaries and Botany Pots' drops
                // ({"chance": .., "output": {..}}), Actually Additions ("result") and Ars Nouveau ("stack").
                return outputs(object.get(wrapper), chance);
            }
        }
        for (String wrapper : List.of("base_ingredient", "basePredicate")) {
            if (object.has(wrapper) && object.get(wrapper).isJsonObject()) {
                // Immersive Engineering's sized product: the item or tag inside, the count outside.
                List<Output> counted = new ArrayList<>();
                for (Output output : outputs(object.get(wrapper), chance)) {
                    counted.add(new Output(output.id(), output.tag(), output.count() * Math.max(1, count), output.chance()));
                }
                return counted;
            }
        }
        JsonElement item = object.get("item");
        if (item != null && item.isJsonObject()) {
            // Ender IO: {"item": {"id": .., "count": ..}, "chance": ..}.
            return outputs(item, chance);
        }
        if (item != null && item.isJsonPrimitive()) {
            return split(item.getAsString(), false, count, chance);
        }
        if (object.has("id") && object.get("id").isJsonPrimitive()) {
            return split(object.get("id").getAsString(), false, count, chance);
        }
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            return split(object.get("tag").getAsString(), true, count, chance);
        }
        return List.of();
    }

    /**
     * One product, with a chance above one turned into a certain part and a chance for the rest:
     * a chance of 2.5 is two for certain and one more at 50%.
     */
    private static List<Output> split(String id, boolean tag, int count, double chance) {
        if (id.isEmpty() || count <= 0 || chance <= 0) {
            return List.of();
        }
        String clean = tag && id.startsWith("#") ? id.substring(1) : id;
        if (chance <= 1.0) {
            return List.of(new Output(clean, tag, count, chance));
        }
        int whole = (int) Math.floor(chance);
        double rest = chance - whole;
        List<Output> parts = new ArrayList<>();
        parts.add(new Output(clean, tag, count * whole, 1.0));
        if (rest > 1e-9) {
            parts.add(new Output(clean, tag, count, rest));
        }
        return parts;
    }

    /**
     * Ex Deorum's {@code result_amount}: a plain number, or a constant, uniform or binomial number
     * provider, as a count and a chance with the same average.
     */
    private static List<Output> withAmount(Output output, JsonElement amount) {
        if (amount.isJsonPrimitive()) {
            int count = (int) Math.round(amount.getAsDouble());
            return count > 0 ? List.of(new Output(output.id(), output.tag(), count, output.chance())) : List.of();
        }
        if (!amount.isJsonObject()) {
            return List.of(output);
        }
        JsonObject object = amount.getAsJsonObject();
        JsonElement typeElement = object.get("type");
        String type = typeElement == null ? "minecraft:constant" : typeElement.getAsString();
        if (type.endsWith("binomial")) {
            int tries = (int) Math.round(number(object, "n", 1));
            return List.of(new Output(output.id(), output.tag(), Math.max(1, tries), output.chance() * number(object, "p", 1.0)));
        }
        if (type.endsWith("uniform")) {
            int middle = (int) Math.round((number(object, "min", 1) + number(object, "max", 1)) / 2.0);
            return List.of(new Output(output.id(), output.tag(), Math.max(1, middle), output.chance()));
        }
        int count = (int) Math.round(number(object, "value", 1));
        return count > 0 ? List.of(new Output(output.id(), output.tag(), count, output.chance())) : List.of();
    }
}
