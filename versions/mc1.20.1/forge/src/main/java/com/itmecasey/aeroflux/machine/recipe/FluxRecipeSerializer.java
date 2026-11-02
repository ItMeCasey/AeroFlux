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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes {@link FluxRecipe}s for one machine, from datapack JSON and over the network.
 * Every machine has its own instance, so the recipe a file defines belongs to the folder it is in.
 *
 * <p>The file format:
 * <pre>{@code
 * {
 *   "type": "aeroflux:grinder",
 *   "inputs": [ { "ingredient": { "item": "minecraft:bone" }, "count": 1 } ],
 *   "outputs": [ { "item": "minecraft:bone_meal", "count": 6, "chance": 1.0 } ],
 *   "time": 100,
 *   "flux": 0,
 *   "flux_type": "moon",
 *   "mesh": "flint",
 *   "water": 50
 * }
 * }</pre>
 * Only {@code inputs} and {@code outputs} are required; the counts default to 1, the chance to 1,
 * the time to 200 ticks and the extra flux to 0, and a missing {@code flux_type} means either flux
 * will do. {@code mesh} (the Sifter's mesh) and {@code water} (mB per run, Purifier and Sifter)
 * default to none.
 */
public final class FluxRecipeSerializer implements RecipeSerializer<FluxRecipe> {
    private static final int DEFAULT_TIME = 200;

    private final MachineType machine;

    public FluxRecipeSerializer(MachineType machine) {
        this.machine = machine;
    }

    // --- JSON --------------------------------------------------------------------------------

    @Override
    public FluxRecipe fromJson(ResourceLocation id, JsonObject json) {
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "inputs")) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "input");
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(entry, "ingredient"));
            inputs.add(new FluxRecipe.Input(ingredient, intInRange(entry, "count", 1, 1, 64)));
        }

        List<FluxRecipe.Output> outputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "outputs")) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "output");
            float chance = GsonHelper.getAsFloat(entry, "chance", 1.0F);
            if (chance < 0.0F || chance > 1.0F) {
                throw new JsonSyntaxException("chance must be between 0 and 1, was " + chance);
            }
            outputs.add(new FluxRecipe.Output(CraftingHelper.getItemStack(entry, true), chance));
        }

        int time = intInRange(json, "time", DEFAULT_TIME, 1, 1_000_000);
        int flux = intInRange(json, "flux", 0, 0, 1_000_000);
        FluxType fluxType = json.has("flux_type") ? parseFlux(GsonHelper.getAsString(json, "flux_type")) : null;
        String mesh = GsonHelper.getAsString(json, "mesh", "");
        int water = intInRange(json, "water", 0, 0, 1_000_000);
        return new FluxRecipe(id, machine, inputs, outputs, time, flux, fluxType, mesh, water);
    }

    /** Writes a recipe in the format {@link #fromJson} reads; the data generator uses it. */
    public void toJson(FluxRecipe recipe, JsonObject json) {
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
            entry.addProperty("item", String.valueOf(ForgeRegistries.ITEMS.getKey(output.stack().getItem())));
            if (output.stack().getCount() != 1) {
                entry.addProperty("count", output.stack().getCount());
            }
            if (output.chance() != 1.0F) {
                entry.addProperty("chance", output.chance());
            }
            outputs.add(entry);
        }
        json.add("outputs", outputs);

        if (recipe.time() != DEFAULT_TIME) {
            json.addProperty("time", recipe.time());
        }
        if (recipe.flux() != 0) {
            json.addProperty("flux", recipe.flux());
        }
        if (recipe.fluxType() != null) {
            json.addProperty("flux_type", recipe.fluxType().name().toLowerCase(Locale.ROOT));
        }
        if (!recipe.mesh().isEmpty()) {
            json.addProperty("mesh", recipe.mesh());
        }
        if (recipe.water() != 0) {
            json.addProperty("water", recipe.water());
        }
    }

    private static int intInRange(JsonObject json, String key, int fallback, int min, int max) {
        int value = GsonHelper.getAsInt(json, key, fallback);
        if (value < min || value > max) {
            throw new JsonSyntaxException(key + " must be between " + min + " and " + max + ", was " + value);
        }
        return value;
    }

    private static FluxType parseFlux(String name) {
        try {
            return FluxType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonSyntaxException("Unknown flux_type '" + name + "', expected aero, moon or twilight");
        }
    }

    // --- Network -----------------------------------------------------------------------------

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
        String mesh = buf.readUtf();
        int water = buf.readVarInt();
        return new FluxRecipe(id, machine, inputs, outputs, time, flux,
                type < 0 || type >= FluxType.values().length ? null : FluxType.values()[type], mesh, water);
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
        // VarInt cannot hold a negative number compactly, so "either flux" goes over the wire as a large value.
        buf.writeVarInt(recipe.fluxType() == null ? Integer.MAX_VALUE : recipe.fluxType().ordinal());
        buf.writeUtf(recipe.mesh());
        buf.writeVarInt(recipe.water());
    }
}
