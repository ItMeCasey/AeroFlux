package com.itmecasey.aeroflux.machine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * Lets a machine use another mod's recipes, so a pack does not have to copy them by hand. A data
 * pack file at {@code data/<namespace>/aeroflux/recipe_imports/<name>.json} names a recipe type and
 * the machine that should run it:
 *
 * <pre>{@code {"machine": "grinder", "recipe_type": "somemod:crushing", "time": 200, "flux": 0}}</pre>
 *
 * <p>Every recipe of that type then works in the machine too, as one input item to one output.
 * Recipes of any type built on vanilla's single-item recipe (stonecutting, smelting and many mods'
 * one-to-one processing) can be read this way. The machine's own recipes always win where both
 * match. Imported recipes are worked out on the server only, so recipe viewers do not list them.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImports {
    /** One import rule. */
    record Rule(MachineType machine, ResourceLocation recipeType, int time, int flux) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.xmap(id -> MachineType.valueOf(id.toUpperCase(Locale.ROOT)), MachineType::id)
                        .fieldOf("machine").forGetter(Rule::machine),
                ResourceLocation.CODEC.fieldOf("recipe_type").forGetter(Rule::recipeType),
                Codec.intRange(1, 1_000_000).optionalFieldOf("time", 200).forGetter(Rule::time),
                Codec.intRange(0, 1_000_000).optionalFieldOf("flux", 0).forGetter(Rule::flux)
        ).apply(instance, Rule::new));
    }

    private static volatile List<Rule> rules = List.of();
    /** The recipe manager the cached imports were made from; a reload brings a new one. */
    private static RecipeManager cachedFrom;
    private static Map<MachineType, List<RecipeHolder<FluxRecipe>>> cached = Map.of();

    private RecipeImports() {
    }

    /** The recipes imported for one machine, worked out again whenever the server's recipes change. */
    public static synchronized List<RecipeHolder<FluxRecipe>> of(ServerLevel level, MachineType machine) {
        RecipeManager recipes = level.getRecipeManager();
        if (recipes != cachedFrom) {
            cached = build(recipes);
            cachedFrom = recipes;
        }
        return cached.getOrDefault(machine, List.of());
    }

    private static Map<MachineType, List<RecipeHolder<FluxRecipe>>> build(RecipeManager recipes) {
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> byMachine = new EnumMap<>(MachineType.class);
        for (Rule rule : rules) {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(rule.recipeType());
            if (type == null || !rule.machine().hasOwnRecipes()) {
                continue;
            }
            for (RecipeHolder<?> holder : recipes.getAllRecipesFor(cast(type))) {
                RecipeHolder<FluxRecipe> converted = convert(rule, holder);
                if (converted != null) {
                    byMachine.computeIfAbsent(rule.machine(), machine -> new ArrayList<>()).add(converted);
                }
            }
        }
        return byMachine;
    }

    /** One foreign recipe as a machine recipe, or {@code null} when it is not a one-item-to-one-item recipe. */
    private static RecipeHolder<FluxRecipe> convert(Rule rule, RecipeHolder<?> holder) {
        if (!(holder.value() instanceof SingleItemRecipe single)) {
            return null;
        }
        ItemStack[] accepted = single.getIngredients().getFirst().getItems();
        if (accepted.length == 0) {
            return null;
        }
        ItemStack result = single.getResultItem(RegistryAccess.EMPTY);
        if (result.isEmpty()) {
            return null;
        }
        FluxRecipe recipe = new FluxRecipe(rule.machine(),
                List.of(new FluxRecipe.Input(single.getIngredients().getFirst(), 1)),
                List.of(new FluxRecipe.Output(result.copy(), 1.0F)),
                rule.time(), rule.flux(), null);
        ResourceLocation source = holder.id();
        ResourceLocation id = Ids.mod("imported/" + source.getNamespace() + "/" + source.getPath());
        return new RecipeHolder<>(id, recipe);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeType<T> cast(RecipeType<?> type) {
        // Only used to list recipes, which are then checked one by one.
        return (RecipeType<T>) type;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "aeroflux/recipe_imports");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Rule> loaded = new HashMap<>();
            json.forEach((id, element) -> Rule.CODEC.parse(JsonOps.INSTANCE, element).result()
                    .ifPresent(rule -> loaded.put(id, rule)));
            rules = List.copyOf(loaded.values());
            cachedFrom = null;
            if (!loaded.isEmpty()) {
                AeroFlux.LOGGER.info("Loaded {} recipe import rule(s)", loaded.size());
            }
        }
    }
}
