package com.itmecasey.aeroflux.machine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.imports.ForeignRecipeReader;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a machine run another mod's processing recipes, so a pack does not have to copy them by
 * hand. A data pack file at {@code data/<namespace>/aeroflux/recipe_imports/<name>.json} names a
 * recipe type and the machine that should run it:
 *
 * <pre>{@code {"machine": "grinder", "recipe_type": "enderio:sag_milling", "time_key": "energy", "time_scale": 0.05}}</pre>
 *
 * <p>AeroFlux ships rules for the mods it knows; a rule for a mod that is not installed does
 * nothing. Every recipe of the type then works in the machine too. The recipes are taken as the
 * game loaded them, so load conditions, data packs and scripts that change them count. Each is
 * written back out with its own serializer and read by {@link ForeignRecipeReader}, so no mod is a
 * dependency. Optional fields of a rule:
 *
 * <ul>
 *     <li>{@code time}: the base time in ticks (200 when left out), or {@code time_key} and
 *     {@code time_scale}: take it from that number in the recipe, times the scale (Ender IO gives
 *     energy, not time).</li>
 *     <li>{@code flux}: extra Flux each run takes, and {@code flux_type}: {@code aero} or {@code moon}
 *     when only one will do.</li>
 *     <li>{@code inputs} and {@code outputs}: the recipe's keys, when the mod uses ones the reader
 *     does not look at by itself (see {@link ForeignRecipeReader.Keys}).</li>
 * </ul>
 *
 * <p>A recipe needing more input slots than the machine has, or more different certain products
 * than it has output slots, is left out. The machine's own recipes win where both match. Products
 * given as a tag become the tag's first item, a vanilla one when the tag has one.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeImports {
    /** The shortest and longest a run of an imported recipe may take, in ticks before tier speed. */
    private static final int MIN_TIME = 20;
    private static final int MAX_TIME = 2_400;

    /** One import rule. */
    record Rule(MachineType machine, ResourceLocation recipeType, int time, Optional<String> timeKey, float timeScale,
                int flux, Optional<String> fluxType, Optional<List<String>> inputs, Optional<List<String>> outputs) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.comapFlatMap(RecipeImports::machine, MachineType::id).fieldOf("machine").forGetter(Rule::machine),
                ResourceLocation.CODEC.fieldOf("recipe_type").forGetter(Rule::recipeType),
                Codec.intRange(1, 1_000_000).optionalFieldOf("time", 200).forGetter(Rule::time),
                Codec.STRING.optionalFieldOf("time_key").forGetter(Rule::timeKey),
                Codec.FLOAT.optionalFieldOf("time_scale", 1.0F).forGetter(Rule::timeScale),
                Codec.intRange(0, 1_000_000).optionalFieldOf("flux", 0).forGetter(Rule::flux),
                Codec.STRING.optionalFieldOf("flux_type").forGetter(Rule::fluxType),
                Codec.STRING.listOf().optionalFieldOf("inputs").forGetter(Rule::inputs),
                Codec.STRING.listOf().optionalFieldOf("outputs").forGetter(Rule::outputs)
        ).apply(instance, Rule::new));

        ForeignRecipeReader.Keys keys() {
            return new ForeignRecipeReader.Keys(inputs.orElse(ForeignRecipeReader.DEFAULT_KEYS.inputs()),
                    outputs.orElse(ForeignRecipeReader.DEFAULT_KEYS.outputs()));
        }
    }

    /** The machine a rule names: {@code "grinder"} and {@code "flux_grinder"} both name the grinder. */
    private static DataResult<MachineType> machine(String id) {
        String name = id.toLowerCase(Locale.ROOT);
        for (MachineType type : MachineType.values()) {
            if (type.id().equals(name) || type.id().equals("flux_" + name) || type.name().toLowerCase(Locale.ROOT).equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "No AeroFlux machine called " + id);
    }

    private static volatile List<Rule> rules = List.of();
    /** The recipe map the cached imports were made from; a reload brings a new map. */
    private static @Nullable RecipeManager cachedFrom;
    private static Map<MachineType, List<RecipeHolder<FluxRecipe>>> cached = Map.of();

    private RecipeImports() {
    }

    /** The recipes imported for one machine, worked out again whenever the server's recipes change. */
    public static synchronized List<RecipeHolder<FluxRecipe>> of(ServerLevel level, MachineType machine) {
        RecipeManager recipes = level.getRecipeManager();
        if (recipes != cachedFrom) {
            cached = build(recipes, RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()));
            cachedFrom = recipes;
        }
        return cached.getOrDefault(machine, List.of());
    }

    private static Map<MachineType, List<RecipeHolder<FluxRecipe>>> build(RecipeManager recipes, RegistryOps<JsonElement> ops) {
        Map<MachineType, List<RecipeHolder<FluxRecipe>>> byMachine = new EnumMap<>(MachineType.class);
        int total = 0;
        for (Rule rule : rules) {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(rule.recipeType());
            if (type == null || !rule.machine().hasOwnRecipes()) {
                // The mod is not installed, or the rule names a machine without recipes.
                continue;
            }
            for (RecipeHolder<?> holder : byType(recipes, type)) {
                RecipeHolder<FluxRecipe> converted = convert(rule, holder, ops);
                if (converted != null) {
                    byMachine.computeIfAbsent(rule.machine(), machine -> new ArrayList<>()).add(converted);
                    total++;
                }
            }
        }
        if (total > 0) {
            AeroFlux.LOGGER.info("Imported {} recipe(s) from other mods into AeroFlux machines", total);
        }
        return byMachine;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<? extends RecipeHolder<?>> byType(RecipeManager recipes, RecipeType<?> type) {
        return recipes.getAllRecipesFor((RecipeType) type);
    }

    /** One foreign recipe as a machine recipe, or {@code null} when it does not fit the machine. */
    private static @Nullable RecipeHolder<FluxRecipe> convert(Rule rule, RecipeHolder<?> holder, RegistryOps<JsonElement> ops) {
        JsonObject json = encode(holder.value(), ops);
        if (json == null) {
            return null;
        }
        ForeignRecipeReader.Recipe read = ForeignRecipeReader.read(json, rule.keys());
        MachineType machine = rule.machine();
        if (!read.usable() || read.inputs().size() > machine.inputSlots()) {
            return null;
        }
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (ForeignRecipeReader.Input input : read.inputs()) {
            Ingredient ingredient = ingredient(input.ingredient(), ops);
            if (ingredient == null) {
                return null;
            }
            inputs.add(new FluxRecipe.Input(ingredient, Math.min(64, input.count())));
        }
        List<FluxRecipe.Output> outputs = new ArrayList<>();
        Set<Item> certain = new HashSet<>();
        for (ForeignRecipeReader.Output output : read.outputs()) {
            Item item = output.tag() ? firstOfTag(output.id()) : item(output.id());
            if (item == null) {
                continue;
            }
            outputs.add(new FluxRecipe.Output(new ItemStack(item, Math.max(1, Math.min(64, output.count()))),
                    (float) Math.min(1.0, output.chance())));
            if (output.chance() >= 1.0) {
                certain.add(item);
            }
        }
        // A run must be able to place every product it always makes, or the machine would stall.
        if (outputs.isEmpty() || certain.size() > machine.outputSlots()) {
            return null;
        }
        int time = rule.timeKey()
                .map(key -> (int) Math.round(ForeignRecipeReader.number(json, key, rule.time() / rule.timeScale()) * rule.timeScale()))
                .orElse(rule.time());
        FluxType fluxType = rule.fluxType().map(name -> FluxType.valueOf(name.toUpperCase(Locale.ROOT))).orElse(null);
        FluxRecipe recipe = new FluxRecipe(machine, inputs, outputs, Math.clamp(time, MIN_TIME, MAX_TIME), rule.flux(), fluxType);
        ResourceLocation source = holder.id();
        return new RecipeHolder<>(Ids.mod("imported/" + source.getNamespace() + "/" + source.getPath()), recipe);
    }

    /** A recipe as its own serializer writes it, or {@code null} if it will not write. */
    @SuppressWarnings("unchecked")
    static <R extends Recipe<?>> @Nullable JsonObject encode(R recipe, RegistryOps<JsonElement> ops) {
        try {
            RecipeSerializer<R> serializer = (RecipeSerializer<R>) recipe.getSerializer();
            return serializer.codec().codec().encodeStart(ops, recipe).result()
                    .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        } catch (RuntimeException e) {
            // A serializer that cannot write its own recipe; leave that recipe out.
            return null;
        }
    }

    /** An ingredient from its JSON, in any of the shapes mods write; see {@link SieveImports#ingredient}. */
    static @Nullable Ingredient ingredient(JsonElement json, RegistryOps<JsonElement> ops) {
        return SieveImports.ingredient(json, ops);
    }

    private static @Nullable Item item(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
    }

    /** The item a tag product becomes: a vanilla item when the tag has one, else the first by id. */
    private static @Nullable Item firstOfTag(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return null;
        }
        List<Holder<Item>> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, parsed)).forEach(items::add);
        Comparator<Holder<Item>> vanillaFirst = Comparator.comparing(holder -> !itemId(holder).getNamespace().equals("minecraft"));
        return items.stream()
                .min(vanillaFirst.thenComparing(holder -> itemId(holder).toString()))
                .map(Holder::value).orElse(null);
    }

    private static ResourceLocation itemId(Holder<Item> holder) {
        return BuiltInRegistries.ITEM.getKey(holder.value());
    }

    /**
     * Adds a rule as if a data pack had it, for the self-test, which checks the reading on vanilla
     * recipes without needing another mod. A reload drops it again.
     */
    public static synchronized void addRuleForTest(String json) {
        Rule rule = Rule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        List<Rule> all = new ArrayList<>(rules);
        all.add(rule);
        rules = List.copyOf(all);
        cachedFrom = null;
    }

    /** Whether any imported recipe of a machine takes this stack; the server's judge of its input slots. */
    public static boolean uses(ServerLevel level, MachineType machine, ItemStack stack) {
        for (RecipeHolder<FluxRecipe> holder : of(level, machine)) {
            if (holder.value().usesItem(stack)) {
                return true;
            }
        }
        return false;
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
            json.forEach((id, element) -> Rule.CODEC.parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(error -> AeroFlux.LOGGER.warn("Skipping recipe import rule {}: {}", id, error))
                    .ifPresent(rule -> loaded.put(id, rule)));
            rules = List.copyOf(loaded.values());
            cachedFrom = null;
            if (!loaded.isEmpty()) {
                AeroFlux.LOGGER.info("Loaded {} recipe import rule(s)", loaded.size());
            }
        }
    }
}
