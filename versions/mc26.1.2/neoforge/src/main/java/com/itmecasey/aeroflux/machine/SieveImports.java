package com.itmecasey.aeroflux.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The sieve recipes of Ex Deorum ({@code exdeorum:sieve}) and Ex Nihilo Sequentia
 * ({@code exnihilosequentia:sieve}, {@code exnihilosequentia:sifting} from 5.0 on), the Flux Sifter's
 * only recipes.
 *
 * <p>They are taken from the recipes the game has loaded, not from the files, so whatever decides
 * which recipes exist decides it for the Sifter too: a recipe's load conditions (Ex Deorum only
 * adds an ore's drops when some mod adds that ore), data packs that replace or remove recipes,
 * and KubeJS scripts. Neither mod is a dependency, so their recipe classes are never named here:
 * each recipe is written back out with its own serializer as JSON, and that JSON is read. The two
 * mods have changed their field names between versions, so the reader accepts every spelling
 * seen so far and skips anything it cannot make sense of instead of failing:
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

    /** The drops the server sent this client, for the recipe viewers. */
    private static volatile List<SieveRoll> syncedForViewer = List.of();

    private SieveImports() {
    }

    /**
     * Every sieve drop as a recipe viewer should list it: the ones the Sifter runs on the server,
     * the ones the server sent on a client (a 26.1 client gets no recipes of other mods).
     */
    public static List<SieveRoll> forViewer(Level level) {
        return level instanceof ServerLevel server ? of(server) : syncedForViewer;
    }

    /** Takes the drops a server sent. */
    static void useSynced(List<SieveRoll> drops) {
        syncedForViewer = List.copyOf(drops);
    }

    /** Every sieve drop, worked out again only when the server's recipes have changed since last time. */
    public static List<SieveRoll> of(ServerLevel level) {
        RecipeMap recipes = level.recipeAccess().recipeMap();
        List<JsonObject> forTest = testRecipes;
        Object current = forTest != null ? forTest : recipes;
        if (current != source) {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
            converted = convertAll(forTest != null ? forTest : loaded(recipes, ops), ops);
            source = current;
        }
        return converted;
    }

    /** The loaded sieve recipes of both mods, each written back out as JSON with its own serializer. */
    private static List<JsonObject> loaded(RecipeMap recipes, RegistryOps<JsonElement> ops) {
        List<JsonObject> found = new ArrayList<>();
        for (String id : SIEVE_TYPES) {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(Identifier.parse(id));
            if (type == null) {
                // That mod is not installed.
                continue;
            }
            for (RecipeHolder<?> holder : byType(recipes, type)) {
                JsonObject json = encode(holder.value(), ops);
                if (json != null) {
                    json.addProperty("type", id);
                    found.add(json);
                }
            }
        }
        return found;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<? extends RecipeHolder<?>> byType(RecipeMap recipes, RecipeType<?> type) {
        return recipes.byType((RecipeType) type);
    }

    /** A recipe as its own serializer writes it, or {@code null} if it will not write. */
    @SuppressWarnings("unchecked")
    private static <R extends Recipe<?>> @Nullable JsonObject encode(R recipe, RegistryOps<JsonElement> ops) {
        RecipeSerializer<R> serializer = (RecipeSerializer<R>) recipe.getSerializer();
        return serializer.codec().codec().encodeStart(ops, recipe).result()
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
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

    private static List<SieveRoll> convertAll(List<JsonObject> recipes, RegistryOps<JsonElement> ops) {
        List<SieveRoll> rolls = new ArrayList<>();
        for (JsonObject json : recipes) {
            try {
                rolls.addAll(convert(json, ops));
            } catch (RuntimeException skipped) {
                // A shape this reader does not know; the recipe is simply not imported.
            }
        }
        if (!rolls.isEmpty()) {
            AeroFlux.LOGGER.info("Imported {} sieve drop(s) for the Flux Sifter", rolls.size());
        }
        return List.copyOf(rolls);
    }

    private static List<SieveRoll> convert(JsonObject json, RegistryOps<JsonElement> ops) {
        String type = json.get("type").getAsString();
        Ingredient material = ingredient(json.has("ingredient") ? json.get("ingredient") : json.get("input"), ops);
        ItemStackTemplate product = result(json.get("result"));
        if (material == null || product == null) {
            return List.of();
        }
        if (type.equals("exdeorum:sieve")) {
            if (json.has("by_hand_only") && json.get("by_hand_only").getAsBoolean()) {
                return List.of();
            }
            Ingredient mesh = ingredient(json.get("mesh"), ops);
            SieveMesh.Amount amount = amount(json.get("result_amount"));
            if (mesh == null || amount == null) {
                return List.of();
            }
            // Ex Deorum ignores the result's own count; the amount says how many.
            return List.of(new SieveRoll(material, mesh, new ItemStackTemplate(product.item().value(), 1), amount, 0));
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
                    ? ingredient(new JsonPrimitive(SieveMesh.nihiloMeshItem(roll.get("mesh").getAsString())), ops) : null;
            if (mesh != null) {
                rolls.add(new SieveRoll(material, mesh, product, new SieveMesh.Binomial(1, Math.clamp(chance, 0.0F, 1.0F)), water));
            }
        }
        return rolls;
    }

    /**
     * An ingredient in any of the shapes the two mods use: an id, {@code #tag},
     * {@code {"item": ..}} / {@code {"tag": ..}}, or a list of those.
     */
    private static @Nullable Ingredient ingredient(@Nullable JsonElement json, RegistryOps<JsonElement> ops) {
        if (json == null) {
            return null;
        }
        JsonElement plain = json;
        if (json.isJsonObject()) {
            plain = plainIngredient(json.getAsJsonObject());
        } else if (json.isJsonArray()) {
            JsonArray list = new JsonArray();
            for (JsonElement entry : json.getAsJsonArray()) {
                list.add(entry.isJsonObject() ? plainIngredient(entry.getAsJsonObject()) : entry);
            }
            plain = list;
        }
        return Ingredient.CODEC.parse(ops, plain).result().orElse(null);
    }

    /** {@code {"item": "a:b"}} as {@code "a:b"}, and {@code {"tag": "a:b"}} as {@code "#a:b"}, the form 26.1 reads. */
    private static JsonElement plainIngredient(JsonObject object) {
        if (object.has("item")) {
            return object.get("item");
        }
        if (object.has("tag")) {
            return new JsonPrimitive("#" + object.get("tag").getAsString());
        }
        return object;
    }

    /** A product: an id, or an object with {@code id} or {@code item} and an optional {@code count}. */
    private static @Nullable ItemStackTemplate result(@Nullable JsonElement json) {
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
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return item == Items.AIR ? null : new ItemStackTemplate(item, Math.max(1, count));
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
