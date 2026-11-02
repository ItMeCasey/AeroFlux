package com.itmecasey.aeroflux.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

/**
 * The sieve recipes of Ex Deorum ({@code exdeorum:sieve}) and Ex Nihilo Sequentia
 * ({@code exnihilosequentia:sieve}, {@code exnihilosequentia:sifting} from 5.0 on), the Flux Sifter's
 * only recipes.
 *
 * <p>Recipes in 1.20.1 cannot write themselves back out, so the recipe files are read instead (see
 * {@link RecipeFiles}), but only those the game really loaded a recipe from, so whatever decides
 * which recipes exist decides it for the Sifter too: load conditions (Ex Deorum only adds an
 * ore's drops when some mod adds that ore), data packs that replace or remove recipes, and KubeJS
 * scripts that remove them. Neither mod is a dependency, so their recipe classes are never named
 * here. The two mods have changed their field names between versions, so the reader accepts every
 * spelling seen so far and skips anything it cannot make sense of instead of failing:
 *
 * <ul>
 *     <li>Ex Deorum: {@code ingredient}, {@code mesh} (an ingredient, such as
 *     {@code {"item": "exdeorum:iron_mesh"}}, or a plain item id in older versions), {@code result}
 *     and {@code result_amount} (a number, or a constant, uniform, binomial or summed number
 *     provider). Recipes marked {@code by_hand_only} are left to the hand sieve.</li>
 *     <li>Ex Nihilo Sequentia: {@code input}, {@code result}, a {@code rolls} list of {@code chance}
 *     and {@code mesh} (a mesh kind such as {@code "flint"}, meaning the mod's flint mesh), and
 *     {@code waterlogged}, which makes the sifter use {@link #WATERLOGGED_WATER} mB of water.</li>
 * </ul>
 */
public final class SieveImports {
    /** Water one waterlogged run takes. */
    public static final int WATERLOGGED_WATER = 50;

    /** The recipe types read, Ex Deorum's and Ex Nihilo Sequentia's. */
    // Ex Nihilo Sequentia named its type "sieve" up to 4.x and "sifting" from 5.0 on.
    private static final List<String> SIEVE_TYPES = List.of("exdeorum:sieve", "exnihilosequentia:sieve", "exnihilosequentia:sifting");

    /** The recipes the self-test gives in place of the loaded ones, or {@code null} in a real game. */
    private static volatile @Nullable List<JsonObject> testRecipes;
    /** The recipe set {@link #converted} was worked out from; a reload brings a new one. */
    private static volatile @Nullable Object source;
    private static volatile List<SieveRoll> converted = List.of();

    private SieveImports() {
    }

    /** Every sieve drop, worked out again only when the server's recipes have changed since last time. */
    public static List<SieveRoll> of(ServerLevel level) {
        RecipeManager recipes = level.getRecipeManager();
        List<JsonObject> forTest = testRecipes;
        Object current = forTest != null ? forTest : recipes;
        if (current != source) {
            converted = convertAll(forTest != null ? forTest : loaded(level));
            source = current;
        }
        return converted;
    }

    /** The loaded sieve recipes of both mods, as their files write them. */
    private static List<JsonObject> loaded(ServerLevel level) {
        List<JsonObject> found = new ArrayList<>();
        for (String type : SIEVE_TYPES) {
            for (RecipeFiles.Entry entry : RecipeFiles.loaded(level, type)) {
                found.add(entry.json());
            }
        }
        return found;
    }

    /** Whether some sieve recipe uses this item as its mesh. */
    public static boolean isMesh(ServerLevel level, ItemStack stack) {
        for (SieveRoll roll : of(level)) {
            if (roll.mesh().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Whether some sieve recipe sifts this item, with any mesh. */
    public static boolean isMaterial(ServerLevel level, ItemStack stack) {
        for (SieveRoll roll : of(level)) {
            if (roll.material().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uses these recipes, written as the mods write them, in place of the loaded ones, for the
     * self-test, which must not depend on either mod. They are read the same way as real ones.
     * {@code null} goes back to the loaded recipes.
     */
    public static void useForTest(@Nullable List<JsonObject> recipes) {
        testRecipes = recipes == null ? null : List.copyOf(recipes);
    }

    private static List<SieveRoll> convertAll(List<JsonObject> recipes) {
        List<SieveRoll> rolls = new ArrayList<>();
        for (JsonObject json : recipes) {
            try {
                rolls.addAll(convert(json));
            } catch (RuntimeException skipped) {
                // A shape this reader does not know; the recipe is simply not imported.
            }
        }
        if (!rolls.isEmpty()) {
            AeroFlux.LOGGER.info("Imported {} sieve drop(s) for the Flux Sifter", rolls.size());
        }
        return List.copyOf(rolls);
    }

    private static List<SieveRoll> convert(JsonObject json) {
        String type = json.get("type").getAsString();
        Ingredient material = ingredient(json.has("ingredient") ? json.get("ingredient") : json.get("input"));
        ItemStack product = result(json.get("result"));
        if (material == null || product == null) {
            return List.of();
        }
        if (type.equals("exdeorum:sieve")) {
            if (json.has("by_hand_only") && json.get("by_hand_only").getAsBoolean()) {
                return List.of();
            }
            Ingredient mesh = ingredient(json.get("mesh"));
            SieveMesh.Amount amount = amount(json.get("result_amount"));
            if (mesh == null || amount == null) {
                return List.of();
            }
            // Ex Deorum ignores the result's own count; the amount says how many.
            return List.of(new SieveRoll(material, mesh, new ItemStack(product.getItem(), 1), amount, 0));
        }
        if (!json.has("rolls")) {
            return List.of();
        }
        int water = json.has("waterlogged") && json.get("waterlogged").getAsBoolean() ? WATERLOGGED_WATER : 0;
        List<SieveRoll> rolls = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("rolls")) {
            JsonObject roll = element.getAsJsonObject();
            float chance = roll.has("chance") ? roll.get("chance").getAsFloat() : 1.0F;
            Ingredient mesh = roll.has("mesh")
                    ? ingredient(new JsonPrimitive(SieveMesh.nihiloMeshItem(roll.get("mesh").getAsString()))) : null;
            if (mesh != null) {
                rolls.add(new SieveRoll(material, mesh, product, new SieveMesh.Binomial(1, Math.max(0.0F, Math.min(1.0F, chance))), water));
            }
        }
        return rolls;
    }

    /**
     * An ingredient in any of the shapes the two mods use: an id, {@code #tag},
     * {@code {"item": ..}} / {@code {"tag": ..}}, or a list of those. Minecraft 1.20.1 reads only the
     * object form, so plain ids and tags are turned into objects first.
     */
    static @Nullable Ingredient ingredient(@Nullable JsonElement json) {
        if (json == null) {
            return null;
        }
        JsonElement objects = json;
        if (json.isJsonPrimitive()) {
            objects = objectIngredient(json.getAsString());
        } else if (json.isJsonArray()) {
            JsonArray list = new JsonArray();
            for (JsonElement entry : json.getAsJsonArray()) {
                list.add(entry.isJsonPrimitive() ? objectIngredient(entry.getAsString()) : entry);
            }
            objects = list;
        }
        try {
            Ingredient ingredient = Ingredient.fromJson(objects);
            return ingredient.isEmpty() ? null : ingredient;
        } catch (RuntimeException unknown) {
            // An item or tag this game does not have, or a shape Forge cannot read.
            return null;
        }
    }

    /** {@code "a:b"} as {@code {"item": "a:b"}}, and {@code "#a:b"} as {@code {"tag": "a:b"}}. */
    private static JsonObject objectIngredient(String id) {
        JsonObject object = new JsonObject();
        if (id.startsWith("#")) {
            object.addProperty("tag", id.substring(1));
        } else {
            object.addProperty("item", id);
        }
        return object;
    }

    /** A product: an id, or an object with {@code id} or {@code item} and an optional {@code count}. */
    private static @Nullable ItemStack result(@Nullable JsonElement json) {
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
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return item == Items.AIR ? null : new ItemStack(item, Math.max(1, count));
    }

    /**
     * Ex Deorum's amount: a plain number, or a vanilla number provider (constant, uniform,
     * binomial) or Ex Deorum's own summation of several. {@code null} for a kind this reader does
     * not know, which leaves the recipe out rather than guessing.
     */
    private static SieveMesh.@Nullable Amount amount(@Nullable JsonElement json) {
        if (json == null) {
            return new SieveMesh.Constant(1);
        }
        if (json.isJsonPrimitive()) {
            return new SieveMesh.Constant(Math.round(json.getAsFloat()));
        }
        JsonObject object = json.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "minecraft:constant";
        return switch (type) {
            case "minecraft:constant", "constant" -> new SieveMesh.Constant(Math.round(object.get("value").getAsFloat()));
            case "minecraft:uniform", "uniform" ->
                    new SieveMesh.Uniform(Math.round(object.get("min").getAsFloat()), Math.round(object.get("max").getAsFloat()));
            case "minecraft:binomial", "binomial" ->
                    new SieveMesh.Binomial(Math.round(object.get("n").getAsFloat()), object.get("p").getAsDouble());
            case "exdeorum:summation" -> {
                List<SieveMesh.Amount> parts = new ArrayList<>();
                for (JsonElement part : object.getAsJsonArray("values")) {
                    SieveMesh.Amount amount = amount(part);
                    if (amount == null) {
                        yield null;
                    }
                    parts.add(amount);
                }
                yield new SieveMesh.Sum(parts);
            }
            default -> null;
        };
    }
}
