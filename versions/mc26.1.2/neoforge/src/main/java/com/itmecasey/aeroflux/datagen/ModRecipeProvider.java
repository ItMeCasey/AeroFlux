package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * Generates the machine recipes that ship with the mod: the defaults the plan derives from vanilla
 * (ores into double raw material, logs into planks, seeds into crops and so on). Packs can add
 * their own files next to these in {@code data/<namespace>/recipe/} with the machine's recipe type.
 * Every recipe is checked against its machine's slot counts, so a recipe that could never run
 * fails the data run instead of failing in game.
 */
public class ModRecipeProvider extends RecipeProvider {
    /** The default time of a recipe, in ticks, the same as a vanilla smelt. */
    private static final int STANDARD = 200;

    private ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    /** The data provider the generator runs; it builds the recipes once the registries are ready. */
    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            super(packOutput, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "AeroFlux recipes";
        }
    }

    @Override
    protected void buildRecipes() {
        grinder();
        sawmill();
        alloySmelter();
        infuser();
        plantGrower();
    }

    // --- Grinder -----------------------------------------------------------------------------

    private void grinder() {
        ore("iron", Tags.Items.ORES_IRON, Items.RAW_IRON, 2);
        ore("gold", Tags.Items.ORES_GOLD, Items.RAW_GOLD, 2);
        ore("copper", Tags.Items.ORES_COPPER, Items.RAW_COPPER, 2);
        ore("coal", Tags.Items.ORES_COAL, Items.COAL, 2);
        ore("diamond", Tags.Items.ORES_DIAMOND, Items.DIAMOND, 2);
        ore("emerald", Tags.Items.ORES_EMERALD, Items.EMERALD, 2);
        ore("lapis", Tags.Items.ORES_LAPIS, Items.LAPIS_LAZULI, 8);
        ore("redstone", Tags.Items.ORES_REDSTONE, Items.REDSTONE, 8);
        ore("quartz", Tags.Items.ORES_QUARTZ, Items.QUARTZ, 2);

        grind("bone", Ingredient.of(Items.BONE), List.of(out(Items.BONE_MEAL, 6, 1.0F)), 100);
        grind("blaze_rod", Ingredient.of(Items.BLAZE_ROD), List.of(out(Items.BLAZE_POWDER, 4, 1.0F)), 100);
        grind("cobblestone", Ingredient.of(Items.COBBLESTONE), List.of(out(Items.GRAVEL, 1, 1.0F)), 100);
        // A chance of flint with the sand: the byproduct that shows off the chance roll.
        grind("gravel", Ingredient.of(Items.GRAVEL), List.of(out(Items.SAND, 1, 1.0F), out(Items.FLINT, 1, 0.25F)), 100);
        grind("sandstone", Ingredient.of(Items.SANDSTONE), List.of(out(Items.SAND, 4, 1.0F)), 100);
    }

    /** Every ore in a tag into more of its raw material (double for most, as the plan says). */
    private void ore(String name, TagKey<Item> ore, ItemLike product, int count) {
        grind(name + "_ore", tag(ore), List.of(out(product, count, 1.0F)), STANDARD);
    }

    private void grind(String name, Ingredient input, List<FluxRecipe.Output> outputs, int time) {
        add(MachineType.GRINDER, name, List.of(in(input, 1)), outputs, time, 0, null);
    }

    // --- Sawmill -----------------------------------------------------------------------------

    private void sawmill() {
        // Each vanilla tag covers the log, the wood and both stripped forms of its type
        // (the stem, hyphae and stripped forms for the nether woods).
        log("oak", ItemTags.OAK_LOGS, Items.OAK_PLANKS);
        log("spruce", ItemTags.SPRUCE_LOGS, Items.SPRUCE_PLANKS);
        log("birch", ItemTags.BIRCH_LOGS, Items.BIRCH_PLANKS);
        log("jungle", ItemTags.JUNGLE_LOGS, Items.JUNGLE_PLANKS);
        log("acacia", ItemTags.ACACIA_LOGS, Items.ACACIA_PLANKS);
        log("dark_oak", ItemTags.DARK_OAK_LOGS, Items.DARK_OAK_PLANKS);
        log("mangrove", ItemTags.MANGROVE_LOGS, Items.MANGROVE_PLANKS);
        log("cherry", ItemTags.CHERRY_LOGS, Items.CHERRY_PLANKS);
        log("pale_oak", ItemTags.PALE_OAK_LOGS, Items.PALE_OAK_PLANKS);
        log("crimson", ItemTags.CRIMSON_STEMS, Items.CRIMSON_PLANKS);
        log("warped", ItemTags.WARPED_STEMS, Items.WARPED_PLANKS);

        // A bamboo block (plain or stripped) crafts into only two planks, so the saw's half again makes three.
        add(MachineType.SAWMILL, "bamboo", List.of(in(tag(ItemTags.BAMBOO_BLOCKS), 1)),
                List.of(out(Items.BAMBOO_PLANKS, 3, 1.0F), sawdust(0.25F)), 100, 0, null);
        // Any plank into two sticks, the crafting rate, but one plank at a time and a quick cut.
        add(MachineType.SAWMILL, "planks", List.of(in(tag(ItemTags.PLANKS), 1)),
                List.of(out(Items.STICK, 2, 1.0F), sawdust(0.10F)), 50, 0, null);
    }

    /** Every log of one wood type into six planks (half again what crafting gives), with a one in four chance of sawdust. */
    private void log(String wood, TagKey<Item> logs, ItemLike planks) {
        add(MachineType.SAWMILL, wood, List.of(in(tag(logs), 1)),
                List.of(out(planks, 6, 1.0F), sawdust(0.25F)), 100, 0, null);
    }

    /** The Sawmill's byproduct: a single sawdust at the given chance. */
    private static FluxRecipe.Output sawdust(float chance) {
        return out(ModItems.SAWDUST.get(), 1, chance);
    }

    // --- Alloy Smelter -----------------------------------------------------------------------

    private void alloySmelter() {
        add(MachineType.ALLOY_SMELTER, "fluxsteel",
                List.of(in(Ingredient.of(Items.IRON_INGOT), 1), in(Ingredient.of(ModItems.FLUX_DUST.get()), 1)),
                List.of(out(ModItems.FLUXSTEEL_INGOT.get(), 1, 1.0F)), STANDARD, 0, null);
        // Three different ingredients, to use all three slots: more Fluxsteel per ingredient, but slower.
        add(MachineType.ALLOY_SMELTER, "fluxsteel_bulk",
                List.of(in(Ingredient.of(Items.IRON_INGOT), 3), in(Ingredient.of(ModItems.FLUX_DUST.get()), 2),
                        in(Ingredient.of(Items.REDSTONE), 1)),
                List.of(out(ModItems.FLUXSTEEL_INGOT.get(), 4, 1.0F)), STANDARD * 2, 0, null);
        add(MachineType.ALLOY_SMELTER, "aerogold",
                List.of(in(Ingredient.of(Items.GOLD_INGOT), 1), in(Ingredient.of(ModItems.AERO_CRYSTAL.get()), 1)),
                List.of(out(ModItems.AEROGOLD_INGOT.get(), 1, 1.0F)), STANDARD, 0, null);
    }

    // --- Infuser -----------------------------------------------------------------------------

    private void infuser() {
        infuse("aero_crystal", Items.QUARTZ, ModItems.AERO_CRYSTAL.get(), 500);
        infuse("moon_crystal", Items.AMETHYST_SHARD, ModItems.MOON_CRYSTAL.get(), 500);
        infuse("crying_obsidian", Items.OBSIDIAN, Items.CRYING_OBSIDIAN, 1_000);
    }

    /** An item and a pinch of Flux Dust, soaked in Moon Flux (the extra Flux is on top of the running cost). */
    private void infuse(String name, ItemLike input, ItemLike product, int flux) {
        add(MachineType.INFUSER, name,
                List.of(in(Ingredient.of(input), 1), in(Ingredient.of(ModItems.FLUX_DUST.get()), 1)),
                List.of(out(product, 1, 1.0F)), STANDARD, flux, FluxType.MOON);
    }

    // --- Plant Grower ------------------------------------------------------------------------

    private void plantGrower() {
        // Day crops: Aero Flux. Seeds come back half of the time.
        crop("wheat", Items.WHEAT_SEEDS, Items.WHEAT, 2, Items.WHEAT_SEEDS, FluxType.AERO);
        crop("beetroot", Items.BEETROOT_SEEDS, Items.BEETROOT, 2, Items.BEETROOT_SEEDS, FluxType.AERO);
        crop("melon", Items.MELON_SEEDS, Items.MELON_SLICE, 3, Items.MELON_SEEDS, FluxType.AERO);
        crop("pumpkin", Items.PUMPKIN_SEEDS, Items.PUMPKIN, 1, Items.PUMPKIN_SEEDS, FluxType.AERO);
        crop("carrot", Items.CARROT, Items.CARROT, 2, null, FluxType.AERO);
        crop("potato", Items.POTATO, Items.POTATO, 2, null, FluxType.AERO);
        crop("sugar_cane", Items.SUGAR_CANE, Items.SUGAR_CANE, 2, null, FluxType.AERO);
        crop("cactus", Items.CACTUS, Items.CACTUS, 2, null, FluxType.AERO);
        crop("dandelion", Items.DANDELION, Items.DANDELION, 2, null, FluxType.AERO);
        crop("poppy", Items.POPPY, Items.POPPY, 2, null, FluxType.AERO);
        tree("oak", Items.OAK_SAPLING, Items.OAK_LOG);
        tree("spruce", Items.SPRUCE_SAPLING, Items.SPRUCE_LOG);
        tree("birch", Items.BIRCH_SAPLING, Items.BIRCH_LOG);
        tree("jungle", Items.JUNGLE_SAPLING, Items.JUNGLE_LOG);
        tree("acacia", Items.ACACIA_SAPLING, Items.ACACIA_LOG);
        tree("dark_oak", Items.DARK_OAK_SAPLING, Items.DARK_OAK_LOG);
        tree("cherry", Items.CHERRY_SAPLING, Items.CHERRY_LOG);

        // Night growers: fungi and pale flowers give a bigger harvest, but only on Moon Flux.
        crop("red_mushroom", Items.RED_MUSHROOM, Items.RED_MUSHROOM, 3, null, FluxType.MOON);
        crop("brown_mushroom", Items.BROWN_MUSHROOM, Items.BROWN_MUSHROOM, 3, null, FluxType.MOON);
        crop("crimson_fungus", Items.CRIMSON_FUNGUS, Items.CRIMSON_FUNGUS, 2, null, FluxType.MOON);
        crop("warped_fungus", Items.WARPED_FUNGUS, Items.WARPED_FUNGUS, 2, null, FluxType.MOON);
        crop("lily_of_the_valley", Items.LILY_OF_THE_VALLEY, Items.LILY_OF_THE_VALLEY, 3, null, FluxType.MOON);
    }

    /** A plant into more of itself; a seed (if given) comes back half of the time so seeds are never used up. */
    private void crop(String name, ItemLike plant, ItemLike harvest, int count, @Nullable ItemLike seedBack, FluxType flux) {
        List<FluxRecipe.Output> outputs = seedBack == null
                ? List.of(out(harvest, count, 1.0F))
                : List.of(out(harvest, count, 1.0F), out(seedBack, 1, 0.5F));
        add(MachineType.PLANT_GROWER, name, List.of(in(Ingredient.of(plant), 1)), outputs, STANDARD * 2, 0, flux);
    }

    private void tree(String name, ItemLike sapling, ItemLike log) {
        add(MachineType.PLANT_GROWER, name + "_tree", List.of(in(Ingredient.of(sapling), 1)),
                List.of(out(log, 3, 1.0F), out(sapling, 1, 0.5F)), STANDARD * 3, 0, FluxType.AERO);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static FluxRecipe.Input in(Ingredient ingredient, int count) {
        return new FluxRecipe.Input(ingredient, count);
    }

    private static FluxRecipe.Output out(ItemLike item, int count, float chance) {
        return new FluxRecipe.Output(new ItemStackTemplate(item.asItem(), count), chance);
    }

    private void add(MachineType machine, String name, List<FluxRecipe.Input> inputs, List<FluxRecipe.Output> outputs,
                     int time, int flux, @Nullable FluxType fluxType) {
        if (inputs.size() > machine.inputSlots() || outputs.size() > machine.outputSlots()) {
            throw new IllegalStateException("Recipe " + machine.id() + "/" + name + " needs more slots than the machine has");
        }
        FluxRecipe recipe = new FluxRecipe(machine, inputs, outputs, time, flux, fluxType);
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id =
                ResourceKey.create(Registries.RECIPE, Ids.mod(machine.id() + "/" + name));
        output.accept(id, recipe, null);
    }
}
