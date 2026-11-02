package com.itmecasey.aeroflux.machine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reads and writes {@link FluxRecipe}s for one machine, from datapack JSON and over the network.
 * Every machine has its own instance, so the recipe a file defines belongs to the folder it is in.
 *
 * <p>A recipe file looks like this; only {@code inputs} and {@code outputs} are required:
 * <pre>{@code
 * {
 *   "type": "aeroflux:grinder",
 *   "inputs": [ { "ingredient": { "item": "minecraft:gravel" }, "count": 1 } ],
 *   "outputs": [ { "item": "minecraft:sand", "count": 1 }, { "item": "minecraft:flint", "chance": 0.25 } ],
 *   "time": 100,
 *   "flux": 0,
 *   "flux_type": "aero"
 * }
 * }</pre>
 */
public final class FluxRecipeSerializer implements RecipeSerializer<FluxRecipe> {
    private final MachineType machine;

    public FluxRecipeSerializer(MachineType machine) {
        this.machine = machine;
    }

    @Override
    public FluxRecipe fromJson(ResourceLocation id, JsonObject json) {
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "inputs")) {
            JsonObject input = GsonHelper.convertToJsonObject(element, "input");
            // An empty ingredient could never match anything, so it is an error rather than a dead recipe.
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(input, "ingredient"), false);
            inputs.add(new FluxRecipe.Input(ingredient, boundedInt(input, "count", 1, 1, 64)));
        }
        List<FluxRecipe.Output> outputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "outputs")) {
            JsonObject output = GsonHelper.convertToJsonObject(element, "output");
            ItemStack stack = CraftingHelper.getItemStack(output, true);
            float chance = GsonHelper.getAsFloat(output, "chance", 1.0F);
            if (chance < 0.0F || chance > 1.0F) {
                throw new JsonSyntaxException("chance must be between 0 and 1, not " + chance);
            }
            outputs.add(new FluxRecipe.Output(stack, chance));
        }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw new JsonSyntaxException("A machine recipe needs at least one input and one output");
        }
        int time = boundedInt(json, "time", 200, 1, 1_000_000);
        int flux = boundedInt(json, "flux", 0, 0, 1_000_000);
        FluxType fluxType = json.has("flux_type") ? parseFlux(GsonHelper.getAsString(json, "flux_type")) : null;
        return new FluxRecipe(id, machine, inputs, outputs, time, flux, fluxType);
    }

    /** An integer field that falls back to a default when missing and must stay within a range. */
    private static int boundedInt(JsonObject json, String name, int fallback, int min, int max) {
        int value = GsonHelper.getAsInt(json, name, fallback);
        if (value < min || value > max) {
            throw new JsonSyntaxException(name + " must be between " + min + " and " + max + ", not " + value);
        }
        return value;
    }

    private static FluxType parseFlux(String name) {
        try {
            return FluxType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonSyntaxException("Unknown flux_type '" + name + "'");
        }
    }

    /** Writes a recipe the way {@link #fromJson} reads it; the data generator uses this for the files it creates. */
    public static void toJson(FluxRecipe recipe, JsonObject json) {
        JsonArray inputs = new JsonArray();
        for (FluxRecipe.Input input : recipe.inputs()) {
            JsonObject entry = new JsonObject();
            entry.add("ingredient", input.ingredient().toJson());
            if (input.count() != 1) {
                entry.addProperty("count", input.count());
            }
            inputs.add(entry);
        }
        json.add("inputs", inputs);

        JsonArray outputs = new JsonArray();
        for (FluxRecipe.Output output : recipe.outputs()) {
            JsonObject entry = new JsonObject();
            ResourceLocation item = ForgeRegistries.ITEMS.getKey(output.stack().getItem());
            entry.addProperty("item", item.toString());
            if (output.stack().getCount() != 1) {
                entry.addProperty("count", output.stack().getCount());
            }
            if (output.chance() != 1.0F) {
                entry.addProperty("chance", output.chance());
            }
            outputs.add(entry);
        }
        json.add("outputs", outputs);

        json.addProperty("time", recipe.time());
        if (recipe.flux() != 0) {
            json.addProperty("flux", recipe.flux());
        }
        if (recipe.fluxType() != null) {
            json.addProperty("flux_type", recipe.fluxType().name().toLowerCase(Locale.ROOT));
        }
    }

    @Nullable
    @Override
    public FluxRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        int inputCount = buf.readVarInt();
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            Ingredient ingredient = Ingredient.fromNetwork(buf);
            inputs.add(new FluxRecipe.Input(ingredient, buf.readVarInt()));
        }
        int outputCount = buf.readVarInt();
        List<FluxRecipe.Output> outputs = new ArrayList<>();
        for (int i = 0; i < outputCount; i++) {
            ItemStack stack = buf.readItem();
            outputs.add(new FluxRecipe.Output(stack, buf.readFloat()));
        }
        int time = buf.readVarInt();
        int flux = buf.readVarInt();
        int type = buf.readVarInt();
        return new FluxRecipe(id, machine, inputs, outputs, time, flux,
                type < 0 || type >= FluxType.values().length ? null : FluxType.values()[type]);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, FluxRecipe recipe) {
        buf.writeVarInt(recipe.inputs().size());
        for (FluxRecipe.Input input : recipe.inputs()) {
            input.ingredient().toNetwork(buf);
            buf.writeVarInt(input.count());
        }
        buf.writeVarInt(recipe.outputs().size());
        for (FluxRecipe.Output output : recipe.outputs()) {
            buf.writeItem(output.stack());
            buf.writeFloat(output.chance());
        }
        buf.writeVarInt(recipe.time());
        buf.writeVarInt(recipe.flux());
        // Negative stands for "either flux"; VarInt can carry it, just not compactly, and it is rare.
        buf.writeVarInt(recipe.fluxType() == null ? -1 : recipe.fluxType().ordinal());
    }
}
