package com.itmecasey.aeroflux.machine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the sieve recipes of Ex Deorum ({@code exdeorum:sieve}) and Ex Nihilo Sequentia
 * ({@code exnihilosequentia:sieve}) as plain JSON, so the Flux Sifter runs them without either mod
 * being a dependency or even present at build time. The two mods have changed their field names
 * between versions, so the reader accepts every spelling seen so far and skips anything it cannot
 * make sense of instead of failing:
 *
 * <ul>
 *     <li>Ex Deorum: {@code ingredient}, {@code mesh} (a mesh item such as {@code exdeorum:iron_mesh}),
 *     {@code result} and {@code result_amount} (a number, or a constant, uniform or binomial number
 *     provider). Recipes marked {@code by_hand_only} are left to the hand sieve.</li>
 *     <li>Ex Nihilo Sequentia: {@code input}, {@code result}, a {@code rolls} list of {@code chance}
 *     and {@code mesh}, and {@code waterlogged}, which makes the sifter use 50 mB of water.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class SieveImports {
    /** Water one waterlogged run takes. */
    public static final int WATERLOGGED_WATER = 50;

    private static volatile List<JsonObject> raw = List.of();
    @Nullable
    private static volatile List<RecipeHolder<FluxRecipe>> converted;

    private SieveImports() {
    }

    /** The imported recipes, worked out with the server's registries the first time they are asked for after a reload. */
    public static List<RecipeHolder<FluxRecipe>> of(ServerLevel level) {
        List<RecipeHolder<FluxRecipe>> recipes = converted;
        if (recipes == null) {
            recipes = convertAll(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()));
            converted = recipes;
        }
        return recipes;
    }

    private static List<RecipeHolder<FluxRecipe>> convertAll(RegistryOps<JsonElement> ops) {
        List<RecipeHolder<FluxRecipe>> recipes = new ArrayList<>();
        int index = 0;
        for (JsonObject json : raw) {
            try {
                for (FluxRecipe recipe : convert(json, ops)) {
                    recipes.add(new RecipeHolder<>(Ids.mod("imported/sieve/" + index++), recipe));
                }
            } catch (RuntimeException skipped) {
                // A shape this reader does not know; the recipe is simply not imported.
            }
        }
        if (!recipes.isEmpty()) {
            AeroFlux.LOGGER.info("Imported {} sieve roll(s) for the Flux Sifter", recipes.size());
        }
        return List.copyOf(recipes);
    }

    private static List<FluxRecipe> convert(JsonObject json, RegistryOps<JsonElement> ops) {
        String type = json.get("type").getAsString();
        Ingredient input = ingredient(json.has("ingredient") ? json.get("ingredient") : json.get("input"), ops);
        if (input == null) {
            return List.of();
        }
        List<FluxRecipe> recipes = new ArrayList<>();
        if (type.equals("exdeorum:sieve")) {
            if (json.has("by_hand_only") && json.get("by_hand_only").getAsBoolean()) {
                return List.of();
            }
            ItemStack result = result(json.get("result"));
            if (result == null) {
                return List.of();
            }
            float[] countAndChance = amount(json.get("result_amount"));
            ItemStack product = result.copyWithCount(Math.max(1, (int) countAndChance[0]));
            String mesh = json.has("mesh") ? json.get("mesh").getAsString() : "";
            recipes.add(sifting(input, product, countAndChance[1], mesh, 0));
        } else {
            ItemStack result = result(json.get("result"));
            if (result == null || !json.has("rolls")) {
                return List.of();
            }
            boolean waterlogged = json.has("waterlogged") && json.get("waterlogged").getAsBoolean();
            JsonArray rolls = json.getAsJsonArray("rolls");
            for (JsonElement element : rolls) {
                JsonObject roll = element.getAsJsonObject();
                float chance = roll.has("chance") ? roll.get("chance").getAsFloat() : 1.0F;
                String mesh = roll.has("mesh") ? roll.get("mesh").getAsString() : "";
                recipes.add(sifting(input, result, chance, mesh, waterlogged ? WATERLOGGED_WATER : 0));
            }
        }
        return recipes;
    }

    private static FluxRecipe sifting(Ingredient input, ItemStack product, float chance, String mesh, int water) {
        return new FluxRecipe(MachineType.SIFTER, List.of(new FluxRecipe.Input(input, 1)),
                List.of(new FluxRecipe.Output(product, Math.clamp(chance, 0.0F, 1.0F))),
                SifterBlockEntity.SIFT_TICKS, 0, null, mesh, water);
    }

    /** An ingredient in any of the shapes the two mods use: an id, {@code #tag}, or {@code {"item": ..}} / {@code {"tag": ..}}. */
    @Nullable
    private static Ingredient ingredient(@Nullable JsonElement json, RegistryOps<JsonElement> ops) {
        if (json == null) {
            return null;
        }
        JsonElement object = json;
        if (json.isJsonPrimitive()) {
            // 1.21.1 writes ingredients as objects; a bare id or #tag is turned into one.
            String text = json.getAsString();
            JsonObject wrapped = new JsonObject();
            if (text.startsWith("#")) {
                wrapped.add("tag", new JsonPrimitive(text.substring(1)));
            } else {
                wrapped.add("item", new JsonPrimitive(text));
            }
            object = wrapped;
        }
        return Ingredient.CODEC.parse(ops, object).result().orElse(null);
    }

    /** A product: an id, or an object with {@code id} or {@code item} and an optional {@code count}. */
    @Nullable
    private static ItemStack result(@Nullable JsonElement json) {
        if (json == null) {
            return null;
        }
        String id;
        int count = 1;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            id = (object.has("id") ? object.get("id") : object.get("item")).getAsString();
            if (object.has("count")) {
                count = object.get("count").getAsInt();
            }
        } else {
            id = json.getAsString();
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? null : new ItemStack(item, count);
    }

    /**
     * Ex Deorum's amount as a count and a chance with the same average: a plain number is that many
     * every time, a uniform range its middle, and a binomial roll of n tries at p is n items at
     * chance p.
     */
    private static float[] amount(@Nullable JsonElement json) {
        if (json == null) {
            return new float[] {1, 1};
        }
        if (json.isJsonPrimitive()) {
            return new float[] {json.getAsFloat(), 1};
        }
        JsonObject object = json.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "minecraft:constant";
        return switch (type) {
            case "minecraft:binomial" -> new float[] {object.get("n").getAsFloat(), object.get("p").getAsFloat()};
            case "minecraft:uniform" -> new float[] {
                    Math.round((object.get("min").getAsFloat() + object.get("max").getAsFloat()) / 2.0F), 1};
            default -> new float[] {object.has("value") ? object.get("value").getAsFloat() : 1, 1};
        };
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Collects the sieve recipe files of both mods from every data pack's recipe folder. */
    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "recipe");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler) {
            List<JsonObject> sieves = new ArrayList<>();
            for (JsonElement json : loaded.values()) {
                if (json.isJsonObject() && json.getAsJsonObject().has("type")) {
                    String type = json.getAsJsonObject().get("type").getAsString();
                    if (type.equals("exdeorum:sieve") || type.equals("exnihilosequentia:sieve")) {
                        sieves.add(json.getAsJsonObject());
                    }
                }
            }
            raw = List.copyOf(sieves);
            converted = null;
        }
    }
}
