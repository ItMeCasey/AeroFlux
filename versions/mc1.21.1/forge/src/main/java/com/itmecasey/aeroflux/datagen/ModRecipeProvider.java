package com.itmecasey.aeroflux.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.crafting.ConditionalRecipe;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.crafting.conditions.OrCondition;
import org.jetbrains.annotations.Nullable;

/**
 * Generates the recipes that ship with the mod. The crafting recipes build the whole survival chain
 * from vanilla items: Flux Dust and crystals by hand, the copper collector, tank, pipes and machines
 * from vanilla parts, and every higher tier from the tier below. Each one unlocks in the recipe book
 * the way vanilla's do, when the player picks up its key ingredient.
 *
 * <p>The machine recipes are the defaults the plan derives from vanilla
 * (ores into double raw material, logs into planks, seeds into crops and so on). Packs can add
 * their own files next to these in {@code data/<namespace>/recipe/} with the machine's recipe type.
 * Every recipe is checked against its machine's slot counts, so a recipe that could never run
 * fails the data run instead of failing in game.
 */
public class ModRecipeProvider extends RecipeProvider {
    /** The default time of a recipe, in ticks, the same as a vanilla smelt. */
    private static final int STANDARD = 200;

    private RecipeOutput output;

    public ModRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
        super(packOutput, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        this.output = recipeOutput;
        materials();
        tools();
        collectorsAndTanks();
        machines();
        chunkLoaders();
        converters();
        pipes();
        upgradeCards();

        grinder();
        sawmill();
        alloySmelter();
        infuser();
        plantGrower();
        purifier();
        lamps();
    }

    // --- Crafting: materials -----------------------------------------------------------------

    /**
     * The hand-made start of the chain. Flux Dust is the plan's glowstone, quartz and redstone. The
     * crystals can be crafted from four dust around their gem before there is an Infuser, which later
     * makes the same crystal from a single dust and some Moon Flux. Fluxsteel and Aerogold have no
     * crafting recipe on purpose: the plan makes them in the Alloy Smelter, and nothing needed to build
     * the first Alloy Smelter calls for either of them.
     */
    private void materials() {
        // Two Sawdust, from the first tree chopped (see SawdustFromLogs) or later from the Sawmill.
        shapeless(RecipeCategory.MISC, ModItems.FLUX_DUST.get())
                .requires(ModItems.SAWDUST.get(), 2)
                .unlockedBy(getHasName(ModItems.SAWDUST.get()), has(ModItems.SAWDUST.get()))
                .save(output);
        crystal(ModItems.AERO_CRYSTAL.get(), Tags.Items.GEMS_QUARTZ);
        crystal(ModItems.MOON_CRYSTAL.get(), Tags.Items.GEMS_AMETHYST);
    }

    /** A gem with Flux Dust on its four sides; the slow way to a crystal, before the Infuser. */
    private void crystal(ItemLike crystal, TagKey<Item> gem) {
        shaped(RecipeCategory.MISC, crystal)
                .define('D', ModItems.FLUX_DUST.get())
                .define('G', gem)
                .pattern(" D ")
                .pattern("DGD")
                .pattern(" D ")
                .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                .save(output);
    }

    // --- Crafting: tools and upgrades --------------------------------------------------------

    /**
     * The plan puts the wrench in the hand-made bootstrap set, so it takes only copper and dust. The
     * Conduit Tool is the plan's Fluxsteel tool and comes after the Alloy Smelter. The two pipe
     * upgrades use the plan's ingredients: Aero Crystal, redstone and gold for Speed; Moon Crystal,
     * iron and quartz for Capacity.
     */
    private void tools() {
        shapeless(RecipeCategory.MISC, ModItems.GUIDEBOOK.get())
                .requires(Items.BOOK)
                .requires(ModItems.FLUX_DUST.get())
                .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                .save(output);
        shaped(RecipeCategory.TOOLS, ModItems.FLUX_WRENCH.get())
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('D', ModItems.FLUX_DUST.get())
                .pattern("C C")
                .pattern(" D ")
                .pattern(" C ")
                .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                .save(output);
        shaped(RecipeCategory.TOOLS, ModItems.CONDUIT_TOOL.get())
                .define('F', ModItems.FLUXSTEEL_INGOT.get())
                .define('A', ModItems.AERO_CRYSTAL.get())
                .pattern("F F")
                .pattern(" A ")
                .pattern(" F ")
                .unlockedBy(getHasName(ModItems.FLUXSTEEL_INGOT.get()), has(ModItems.FLUXSTEEL_INGOT.get()))
                .save(output);
        // The Configurator is for setting up a row of machines, which players do well before
        // Fluxsteel, so it is copper, glass and redstone around the Moon Crystal its art shows.
        shaped(RecipeCategory.TOOLS, ModItems.FLUX_CONFIGURATOR.get())
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('G', Tags.Items.GLASS_PANES)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('M', ModItems.MOON_CRYSTAL.get())
                .define('D', ModItems.FLUX_DUST.get())
                .pattern("CGC")
                .pattern("RMR")
                .pattern("CDC")
                .unlockedBy(getHasName(ModItems.MOON_CRYSTAL.get()), has(ModItems.MOON_CRYSTAL.get()))
                .save(output);

        shaped(RecipeCategory.MISC, ModItems.SPEED_UPGRADE.get())
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('G', Tags.Items.INGOTS_GOLD)
                .define('A', ModItems.AERO_CRYSTAL.get())
                .pattern("RGR")
                .pattern("RAR")
                .pattern("RGR")
                .unlockedBy(getHasName(ModItems.AERO_CRYSTAL.get()), has(ModItems.AERO_CRYSTAL.get()))
                .save(output);
        machineUpgrades();
        shaped(RecipeCategory.MISC, ModItems.CAPACITY_UPGRADE.get())
                .define('Q', Tags.Items.GEMS_QUARTZ)
                .define('I', Tags.Items.INGOTS_IRON)
                .define('M', ModItems.MOON_CRYSTAL.get())
                .pattern("QIQ")
                .pattern("QMQ")
                .pattern("QIQ")
                .unlockedBy(getHasName(ModItems.MOON_CRYSTAL.get()), has(ModItems.MOON_CRYSTAL.get()))
                .save(output);
        // The plan: Filter and Priority come from Fluxsteel with a hopper and a comparator.
        shaped(RecipeCategory.MISC, ModItems.FILTER_UPGRADE.get())
                .define('F', ModItems.FLUXSTEEL_INGOT.get())
                .define('P', Items.PAPER)
                .define('H', Items.HOPPER)
                .pattern("FPF")
                .pattern("PHP")
                .pattern("FPF")
                .unlockedBy(getHasName(ModItems.FLUXSTEEL_INGOT.get()), has(ModItems.FLUXSTEEL_INGOT.get()))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.PRIORITY_UPGRADE.get())
                .define('F', ModItems.FLUXSTEEL_INGOT.get())
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('C', Items.COMPARATOR)
                .pattern("FRF")
                .pattern("RCR")
                .pattern("FRF")
                .unlockedBy(getHasName(ModItems.FLUXSTEEL_INGOT.get()), has(ModItems.FLUXSTEEL_INGOT.get()))
                .save(output);
    }

    // --- Crafting: tiers ---------------------------------------------------------------------

    /**
     * What it takes to raise a block or a run of pipe to one tier: the slots above, beside and below
     * the thing being upgraded. The materials follow the plan's frame column (gold comes with
     * amethyst, Fluxium is Fluxsteel and both crystals). Diamond and Netherite also take the mod's
     * alloys, so that the top tiers lean on the machines rather than on raw ore alone.
     */
    private record TierKit(Ingredient top, Ingredient side, Ingredient bottom) {
    }

    /** The upgrade kit for a tier above copper; copper is the base tier, so nothing upgrades into it. */
    private TierKit kit(Tier tier) {
        return switch (tier) {
            case COPPER -> throw new IllegalArgumentException("Copper is crafted from scratch, not upgraded into");
            case IRON -> new TierKit(tag(Tags.Items.INGOTS_IRON), tag(Tags.Items.INGOTS_IRON), tag(Tags.Items.INGOTS_IRON));
            case GOLD -> new TierKit(tag(Tags.Items.INGOTS_GOLD), tag(Tags.Items.GEMS_AMETHYST), tag(Tags.Items.INGOTS_GOLD));
            case DIAMOND -> new TierKit(tag(Tags.Items.GEMS_DIAMOND), Ingredient.of(ModItems.FLUXSTEEL_INGOT.get()),
                    tag(Tags.Items.GEMS_DIAMOND));
            case NETHERITE -> new TierKit(tag(Tags.Items.INGOTS_NETHERITE), Ingredient.of(ModItems.AEROGOLD_INGOT.get()),
                    Ingredient.of(ModItems.AEROGOLD_INGOT.get()));
            case FLUXIUM -> new TierKit(Ingredient.of(ModItems.AERO_CRYSTAL.get()), Ingredient.of(ModItems.FLUXSTEEL_INGOT.get()),
                    Ingredient.of(ModItems.MOON_CRYSTAL.get()));
        };
    }

    /** One block of a tier from the same block one tier down, wrapped in that tier's kit. */
    private void upgrade(ItemLike result, ItemLike previous, Tier tier) {
        upgrade(result, previous, tier, output);
    }

    private void upgrade(ItemLike result, ItemLike previous, Tier tier, RecipeOutput target) {
        TierKit kit = kit(tier);
        shaped(RecipeCategory.MISC, result)
                .define('T', kit.top())
                .define('S', kit.side())
                .define('B', kit.bottom())
                .define('P', previous)
                .pattern(" T ")
                .pattern("SPS")
                .pattern(" B ")
                .unlockedBy(getHasName(previous), has(previous))
                .save(target);
    }

    /**
     * The upgrade cards: a tier's kit around one Flux Dust. Unlike the block upgrade recipes they
     * need no block of the tier below, since the card works on a block that is already placed.
     */
    private void upgradeCards() {
        for (Tier tier : Tier.values()) {
            if (tier == Tier.COPPER) {
                continue;
            }
            TierKit kit = kit(tier);
            shaped(RecipeCategory.MISC, ModItems.upgradeCard(tier).get())
                    .define('T', kit.top())
                    .define('S', kit.side())
                    .define('B', kit.bottom())
                    .define('D', ModItems.FLUX_DUST.get())
                    .pattern(" T ")
                    .pattern("SDS")
                    .pattern(" B ")
                    .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                    .save(output);
        }
    }

    // --- Crafting: collectors and tanks ------------------------------------------------------

    /**
     * The copper collector and tank are part of the hand-made bootstrap. A collector is a daylight
     * detector under glass on a copper base; a tank is a bucket boxed in glass and copper. Every
     * higher tier is the tier below plus that tier's kit.
     */
    private void collectorsAndTanks() {
        // Glass to catch the sky over a copper frame, built from things found on the first day.
        shaped(RecipeCategory.MISC, ModBlocks.collector(Tier.COPPER).get())
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('D', ModItems.FLUX_DUST.get())
                .pattern("GGG")
                .pattern("CDC")
                .pattern("CCC")
                .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                .save(output);
        shaped(RecipeCategory.MISC, ModBlocks.tank(Tier.COPPER).get())
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('B', Items.BUCKET)
                .pattern("CGC")
                .pattern("GBG")
                .pattern("CGC")
                .unlockedBy(getHasName(ModBlocks.collector(Tier.COPPER).get()), has(ModBlocks.collector(Tier.COPPER).get()))
                .save(output);

        Tier[] tiers = Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            upgrade(ModBlocks.collector(tiers[i]).get(), ModBlocks.collector(tiers[i - 1]).get(), tiers[i]);
            upgrade(ModBlocks.tank(tiers[i]).get(), ModBlocks.tank(tiers[i - 1]).get(), tiers[i]);
        }
    }

    // --- Crafting: machines ------------------------------------------------------------------

    /**
     * The vanilla block a copper machine is built around, the accent above it and the pair beside it,
     * and the item whose pickup unlocks the recipe.
     */
    private record MachineParts(ItemLike core, Ingredient top, Ingredient sides, ItemLike unlock) {
    }

    /**
     * Each machine wraps the vanilla block that already does its job. The first five need nothing the
     * player cannot make by hand, so the Alloy Smelter can be built before there is any Fluxsteel. The
     * enchanting pair come later, as the plan's advancement order has them, and take Fluxsteel.
     */
    private MachineParts parts(MachineType machine) {
        Ingredient copper = tag(Tags.Items.INGOTS_COPPER);
        Ingredient fluxsteel = Ingredient.of(ModItems.FLUXSTEEL_INGOT.get());
        Ingredient moonCrystal = Ingredient.of(ModItems.MOON_CRYSTAL.get());
        return switch (machine) {
            case FURNACE -> new MachineParts(Items.FURNACE, copper, copper, ModItems.FLUX_DUST.get());
            case GRINDER -> new MachineParts(Items.GRINDSTONE, Ingredient.of(Items.FLINT), copper, ModItems.FLUX_DUST.get());
            case SAWMILL -> new MachineParts(Items.STONECUTTER, tag(Tags.Items.INGOTS_IRON), copper, ModItems.FLUX_DUST.get());
            case ALLOY_SMELTER -> new MachineParts(Items.BLAST_FURNACE, Ingredient.of(ModItems.AERO_CRYSTAL.get()), copper,
                    ModItems.AERO_CRYSTAL.get());
            // A cauldron to soak things in, under amethyst, so the Infuser comes before the Nether.
            case INFUSER -> new MachineParts(Items.CAULDRON, tag(Tags.Items.GEMS_AMETHYST), copper, ModItems.FLUX_DUST.get());
            case ENCHANTER -> new MachineParts(Items.ENCHANTING_TABLE, moonCrystal, fluxsteel, ModItems.FLUXSTEEL_INGOT.get());
            // Vanilla takes enchantments off with a grindstone, so the disenchanter is built around one too.
            case DISENCHANTER -> new MachineParts(Items.GRINDSTONE, moonCrystal, fluxsteel, ModItems.FLUXSTEEL_INGOT.get());
            // A composter is vanilla's bone meal machine; the glass is the growing chamber.
            case PLANT_GROWER -> new MachineParts(Items.COMPOSTER, tag(Tags.Items.GLASS_BLOCKS_COLORLESS),
                    tag(Tags.Items.GLASS_BLOCKS_COLORLESS), ModItems.FLUX_DUST.get());
            case CRAFTER -> new MachineParts(Items.CRAFTING_TABLE, tag(Tags.Items.INGOTS_IRON), copper, ModItems.FLUX_DUST.get());
            case PURIFIER -> new MachineParts(Items.CAULDRON, tag(Tags.Items.INGOTS_IRON), copper, ModItems.FLUX_DUST.get());
            case SIFTER -> new MachineParts(Items.SCAFFOLDING, Ingredient.of(Items.STRING), copper, ModItems.FLUX_DUST.get());
            case BLENDER -> new MachineParts(Items.CAULDRON, moonCrystal, Ingredient.of(ModItems.AERO_CRYSTAL.get()),
                    ModItems.MOON_CRYSTAL.get());
            case SKY_CONTROLLER -> new MachineParts(Items.DAYLIGHT_DETECTOR, moonCrystal, fluxsteel, ModItems.FLUXSTEEL_INGOT.get());
            case MAGNET -> new MachineParts(Items.HOPPER, Ingredient.of(Items.REDSTONE), tag(Tags.Items.INGOTS_IRON),
                    ModItems.FLUX_DUST.get());
            case BREEDER -> new MachineParts(Items.HAY_BLOCK, Ingredient.of(Items.WHEAT), copper, ModItems.FLUX_DUST.get());
            // An anvil mends; the moon crystal and Fluxsteel make it run on experience.
            case REPAIRER -> new MachineParts(Items.ANVIL, moonCrystal, fluxsteel, ModItems.FLUXSTEEL_INGOT.get());
            case SPAWNER -> new MachineParts(Items.CRYING_OBSIDIAN, moonCrystal, fluxsteel, ModItems.FLUXSTEEL_INGOT.get());
        };
    }

    /**
     * Where recipes go that should only load when a condition holds. Forge has no conditions on a
     * recipe output, so each recipe is wrapped in a {@link ConditionalRecipe} under its own id.
     */
    private RecipeOutput conditional(ICondition condition) {
        RecipeOutput target = output;
        return new RecipeOutput() {
            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
                ConditionalRecipe.builder()
                        .condition(condition)
                        .recipe(id, recipe, advancement)
                        .save(target, id);
            }

            /** Forge's other way in, with the advancement still as JSON: read it and go the usual way. */
            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable ResourceLocation advancementId,
                               @Nullable JsonElement advancement) {
                AdvancementHolder holder = null;
                if (advancementId != null && advancement != null) {
                    holder = new AdvancementHolder(advancementId,
                            Advancement.CODEC.parse(JsonOps.INSTANCE, advancement).getOrThrow(JsonParseException::new));
                }
                accept(id, recipe, holder);
            }

            @Override
            public Advancement.Builder advancement() {
                return target.advancement();
            }

            @Override
            public HolderLookup.Provider registry() {
                return target.registry();
            }
        };
    }

    /**
     * Every machine: the copper one from its vanilla parts, then each tier from the one below. The
     * Sifter only runs Ex Deorum's and Ex Nihilo Sequentia's sieve recipes, so it is only craftable
     * when one of them is installed.
     */
    private void machines() {
        Tier[] tiers = Tier.values();
        RecipeOutput withSieveMod = conditional(new OrCondition(List.of(
                new ModLoadedCondition("exdeorum"), new ModLoadedCondition("exnihilosequentia"))));
        for (MachineType machine : MachineType.values()) {
            MachineParts parts = parts(machine);
            RecipeOutput target = machine == MachineType.SIFTER ? withSieveMod : output;
            shaped(RecipeCategory.MISC, ModBlocks.machine(machine, Tier.COPPER).get())
                    .define('C', Tags.Items.INGOTS_COPPER)
                    .define('A', parts.top())
                    .define('S', parts.sides())
                    .define('M', parts.core())
                    .define('D', ModItems.FLUX_DUST.get())
                    .pattern("CAC")
                    .pattern("SMS")
                    .pattern("CDC")
                    .unlockedBy(getHasName(parts.unlock()), has(parts.unlock()))
                    .save(target);
            for (int i = 1; i < tiers.length; i++) {
                upgrade(ModBlocks.machine(machine, tiers[i]).get(), ModBlocks.machine(machine, tiers[i - 1]).get(), tiers[i], target);
            }
        }
    }

    // --- Crafting: chunk loaders -------------------------------------------------------------

    /**
     * The copper loader is an ender pearl (the vanilla thing that keeps chunks loaded as it flies)
     * over a block of obsidian, with a copper frame, Flux Dust and both crystals so it runs on
     * either Flux. Higher tiers are the tier below plus that tier's kit, like every other block.
     */
    private void chunkLoaders() {
        shaped(RecipeCategory.MISC, ModBlocks.chunkLoader(Tier.COPPER).get())
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('E', Tags.Items.ENDER_PEARLS)
                .define('A', ModItems.AERO_CRYSTAL.get())
                .define('M', ModItems.MOON_CRYSTAL.get())
                .define('O', Items.OBSIDIAN)
                .define('D', ModItems.FLUX_DUST.get())
                .pattern("CEC")
                .pattern("AOM")
                .pattern("CDC")
                .unlockedBy(getHasName(ModItems.MOON_CRYSTAL.get()), has(ModItems.MOON_CRYSTAL.get()))
                .save(output);
        Tier[] tiers = Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            upgrade(ModBlocks.chunkLoader(tiers[i]).get(), ModBlocks.chunkLoader(tiers[i - 1]).get(), tiers[i]);
        }
    }

    /**
     * The converters: a copper tank of Flux in a copper frame, with redstone for the FE converter and
     * fluix for the AE one. Forge only has the FE converter (see {@link ModBlocks#CONVERTER_TYPES}).
     * Higher tiers upgrade the tier below like every other block.
     */
    private void converters() {
        for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
            Ingredient core = type == ConverterType.AE
                    ? tag(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "gems/fluix")))
                    : tag(Tags.Items.STORAGE_BLOCKS_REDSTONE);
            shaped(RecipeCategory.REDSTONE, ModBlocks.converter(type, Tier.COPPER).get())
                    .define('C', Tags.Items.INGOTS_COPPER)
                    .define('X', core)
                    .define('D', ModItems.FLUX_DUST.get())
                    .define('T', ModBlocks.tank(Tier.COPPER).get())
                    .pattern("CXC")
                    .pattern("DTD")
                    .pattern("CXC")
                    .unlockedBy(getHasName(ModBlocks.tank(Tier.COPPER).get()), has(ModBlocks.tank(Tier.COPPER).get()))
                    .save(output);
            Tier[] tiers = Tier.values();
            for (int i = 1; i < tiers.length; i++) {
                TierKit kit = kit(tiers[i]);
                ItemLike previous = ModBlocks.converter(type, tiers[i - 1]).get();
                shaped(RecipeCategory.REDSTONE, ModBlocks.converter(type, tiers[i]).get())
                        .define('T', kit.top())
                        .define('S', kit.side())
                        .define('B', kit.bottom())
                        .define('P', previous)
                        .pattern(" T ")
                        .pattern("SPS")
                        .pattern(" B ")
                        .unlockedBy(getHasName(previous), has(previous))
                        .save(output);
            }
        }
    }

    // --- Crafting: pipes ---------------------------------------------------------------------

    /** Pipes made by one copper craft. */
    private static final int PIPES_PER_CRAFT = 8;
    /** Pipes raised a tier by one upgrade craft. */
    private static final int PIPES_PER_UPGRADE = 6;

    /**
     * Copper pipes are copper ingots around a vanilla item that already handles what the pipe
     * carries, eight to a craft. A higher tier is six pipes of the tier below in the outer columns
     * with that tier's kit down the middle, so a whole run can be upgraded six at a time. The ME
     * pipes have no tiers; the dense one is four normal ones bundled together, the way AE2 makes its
     * dense cable.
     */
    private void pipes() {
        Tier[] tiers = Tier.values();
        for (PipeType type : PipeType.values()) {
            if (type == PipeType.DENSE_ME || !craftable(type)) {
                continue;
            }
            ItemLike copperPipe = ModItems.pipe(type, Tier.COPPER).get();
            RecipeCategory category = type == PipeType.REDSTONE ? RecipeCategory.REDSTONE : RecipeCategory.MISC;
            shaped(category, copperPipe, PIPES_PER_CRAFT)
                    .define('C', Tags.Items.INGOTS_COPPER)
                    .define('D', ModItems.FLUX_DUST.get())
                    .define('X', pipeCore(type))
                    .pattern("CCC")
                    .pattern("DXD")
                    .pattern("CCC")
                    .unlockedBy(getHasName(ModItems.FLUX_DUST.get()), has(ModItems.FLUX_DUST.get()))
                    .save(output);
            if (!PipeTiers.isTiered(type)) {
                continue;
            }
            for (int i = 1; i < tiers.length; i++) {
                ItemLike previous = ModItems.pipe(type, tiers[i - 1]).get();
                TierKit kit = kit(tiers[i]);
                shaped(category, ModItems.pipe(type, tiers[i]).get(), PIPES_PER_UPGRADE)
                        .define('P', previous)
                        .define('T', kit.top())
                        .define('S', kit.side())
                        .define('B', kit.bottom())
                        .pattern("PTP")
                        .pattern("PSP")
                        .pattern("PBP")
                        .unlockedBy(getHasName(previous), has(previous))
                        .save(output);
            }
        }
    }

    /**
     * Whether a pipe gets recipes on this version. Gas pipes only matter with Mekanism and ME pipes
     * only with AE2, and neither has a Forge build for 1.21.1, so those stay out of survival here.
     */
    private static boolean craftable(PipeType type) {
        return type != PipeType.GAS && type != PipeType.ME && type != PipeType.DENSE_ME;
    }

    /** The vanilla item in the middle of a copper pipe recipe: something that already handles what the pipe carries. */
    private static ItemLike pipeCore(PipeType type) {
        return switch (type) {
            case FLUID -> Items.BUCKET;
            case ITEM -> Items.HOPPER;
            case REDSTONE -> Items.REDSTONE;
            case ENERGY -> Items.LIGHTNING_ROD;
            case GAS -> Items.GLASS_BOTTLE;
            // AE2's cables are quartz and amethyst-colored; without AE2 items to hand, the Moon Crystal stands in.
            case ME, DENSE_ME -> ModItems.MOON_CRYSTAL.get();
        };
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

    // --- Machine-only upgrades ----------------------------------------------------------------

    /**
     * The upgrades only machines take. They come after the Alloy Smelter, since each is built on
     * Fluxsteel, and each wraps the vanilla thing it is named after: lapis and a diamond for
     * Fortune, string for Silk Touch, prismarine for the Purifier's water.
     */
    private void machineUpgrades() {
        Item fluxsteel = ModItems.FLUXSTEEL_INGOT.get();
        shaped(RecipeCategory.MISC, ModItems.machineUpgrade(MachineUpgrades.Special.EFFICIENCY).get())
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('M', ModItems.MOON_CRYSTAL.get())
                .define('F', fluxsteel)
                .define('A', ModItems.AERO_CRYSTAL.get())
                .pattern("CMC")
                .pattern("FAF")
                .pattern("CMC")
                .unlockedBy(getHasName(fluxsteel), has(fluxsteel))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.machineUpgrade(MachineUpgrades.Special.FORTUNE).get())
                .define('L', Tags.Items.GEMS_LAPIS)
                .define('F', fluxsteel)
                .define('D', Tags.Items.GEMS_DIAMOND)
                .pattern("LFL")
                .pattern("FDF")
                .pattern("LFL")
                .unlockedBy(getHasName(fluxsteel), has(fluxsteel))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.machineUpgrade(MachineUpgrades.Special.SILK_TOUCH).get())
                .define('S', Tags.Items.STRINGS)
                .define('F', fluxsteel)
                .define('M', ModItems.MOON_CRYSTAL.get())
                .pattern("SFS")
                .pattern("FMF")
                .pattern("SFS")
                .unlockedBy(getHasName(fluxsteel), has(fluxsteel))
                .save(output);
        shaped(RecipeCategory.MISC, ModItems.machineUpgrade(MachineUpgrades.Special.PURITY).get())
                .define('P', Items.PRISMARINE_CRYSTALS)
                .define('F', fluxsteel)
                .define('A', ModItems.AERO_CRYSTAL.get())
                .pattern("PFP")
                .pattern("FAF")
                .pattern("PFP")
                .unlockedBy(getHasName(fluxsteel), has(fluxsteel))
                .save(output);
    }

    // --- Plant Grower ------------------------------------------------------------------------

    private void plantGrower() {
        // Day crops: Aero Flux. Seeds come back half of the time.
        crop("wheat", Items.WHEAT_SEEDS, Items.WHEAT, 2, Items.WHEAT_SEEDS, FluxType.AERO);
        crop("beetroot", Items.BEETROOT_SEEDS, Items.BEETROOT, 2, Items.BEETROOT_SEEDS, FluxType.AERO);
        crop("melon", Items.MELON_SEEDS, Items.MELON_SLICE, 3, Items.MELON_SEEDS, FluxType.AERO,
                List.of(out(Items.MELON, 1, 1.0F), out(Items.MELON_SEEDS, 1, 0.5F)));
        crop("pumpkin", Items.PUMPKIN_SEEDS, Items.PUMPKIN, 1, Items.PUMPKIN_SEEDS, FluxType.AERO);
        crop("carrot", Items.CARROT, Items.CARROT, 2, null, FluxType.AERO);
        crop("potato", Items.POTATO, Items.POTATO, 2, null, FluxType.AERO);
        crop("sugar_cane", Items.SUGAR_CANE, Items.SUGAR_CANE, 2, null, FluxType.AERO);
        crop("cactus", Items.CACTUS, Items.CACTUS, 2, null, FluxType.AERO);
        crop("dandelion", Items.DANDELION, Items.DANDELION, 2, null, FluxType.AERO);
        crop("poppy", Items.POPPY, Items.POPPY, 2, null, FluxType.AERO);
        tree("oak", Items.OAK_SAPLING, Items.OAK_LOG, Items.OAK_LEAVES);
        tree("spruce", Items.SPRUCE_SAPLING, Items.SPRUCE_LOG, Items.SPRUCE_LEAVES);
        tree("birch", Items.BIRCH_SAPLING, Items.BIRCH_LOG, Items.BIRCH_LEAVES);
        tree("jungle", Items.JUNGLE_SAPLING, Items.JUNGLE_LOG, Items.JUNGLE_LEAVES);
        tree("acacia", Items.ACACIA_SAPLING, Items.ACACIA_LOG, Items.ACACIA_LEAVES);
        tree("dark_oak", Items.DARK_OAK_SAPLING, Items.DARK_OAK_LOG, Items.DARK_OAK_LEAVES);
        tree("cherry", Items.CHERRY_SAPLING, Items.CHERRY_LOG, Items.CHERRY_LEAVES);

        // Night growers: fungi and pale flowers give a bigger harvest, but only on Moon Flux.
        crop("red_mushroom", Items.RED_MUSHROOM, Items.RED_MUSHROOM, 3, null, FluxType.MOON,
                List.of(out(Items.RED_MUSHROOM_BLOCK, 1, 1.0F), out(Items.RED_MUSHROOM, 1, 1.0F)));
        crop("brown_mushroom", Items.BROWN_MUSHROOM, Items.BROWN_MUSHROOM, 3, null, FluxType.MOON,
                List.of(out(Items.BROWN_MUSHROOM_BLOCK, 1, 1.0F), out(Items.BROWN_MUSHROOM, 1, 1.0F)));
        crop("crimson_fungus", Items.CRIMSON_FUNGUS, Items.CRIMSON_FUNGUS, 2, null, FluxType.MOON);
        crop("warped_fungus", Items.WARPED_FUNGUS, Items.WARPED_FUNGUS, 2, null, FluxType.MOON);
        crop("lily_of_the_valley", Items.LILY_OF_THE_VALLEY, Items.LILY_OF_THE_VALLEY, 3, null, FluxType.MOON);
    }

    /** A plant into more of itself; a seed (if given) comes back half of the time so seeds are never used up. */
    private void crop(String name, ItemLike plant, ItemLike harvest, int count, @Nullable ItemLike seedBack, FluxType flux) {
        crop(name, plant, harvest, count, seedBack, flux, List.of());
    }

    /** A crop with what it gives under a Silk Touch Upgrade instead, such as a whole melon. */
    private void crop(String name, ItemLike plant, ItemLike harvest, int count, @Nullable ItemLike seedBack, FluxType flux,
                      List<FluxRecipe.Output> silk) {
        List<FluxRecipe.Output> outputs = seedBack == null
                ? List.of(out(harvest, count, 1.0F))
                : List.of(out(harvest, count, 1.0F), out(seedBack, 1, 0.5F));
        addGrower(name, plant, outputs, STANDARD * 2, flux, silk);
    }

    /** A tree: logs and, half of the time, its sapling back. With Silk Touch it also keeps some leaves. */
    private void tree(String name, ItemLike sapling, ItemLike log, ItemLike leaves) {
        addGrower(name + "_tree", sapling, List.of(out(log, 3, 1.0F), out(sapling, 1, 0.5F)), STANDARD * 3, FluxType.AERO,
                List.of(out(log, 3, 1.0F), out(leaves, 2, 1.0F), out(sapling, 1, 0.5F)));
    }

    private void addGrower(String name, ItemLike plant, List<FluxRecipe.Output> outputs, int time, FluxType flux,
                           List<FluxRecipe.Output> silk) {
        save(MachineType.PLANT_GROWER, name, new FluxRecipe(MachineType.PLANT_GROWER, List.of(in(Ingredient.of(plant), 1)),
                outputs, time, 0, flux, 0, silk));
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static FluxRecipe.Input in(Ingredient ingredient, int count) {
        return new FluxRecipe.Input(ingredient, count);
    }

    // --- Purifier ----------------------------------------------------------------------------

    /** Water used by one purifying run. */
    private static final int PURIFY_WATER = 50;

    /** Ore washed into three times its raw material, half as much again as the Grinder gives. */
    private void purifier() {
        purify("iron", Tags.Items.ORES_IRON, Items.RAW_IRON, 3);
        purify("gold", Tags.Items.ORES_GOLD, Items.RAW_GOLD, 3);
        purify("copper", Tags.Items.ORES_COPPER, Items.RAW_COPPER, 3);
        purify("coal", Tags.Items.ORES_COAL, Items.COAL, 3);
        purify("diamond", Tags.Items.ORES_DIAMOND, Items.DIAMOND, 3);
        purify("emerald", Tags.Items.ORES_EMERALD, Items.EMERALD, 3);
        purify("lapis", Tags.Items.ORES_LAPIS, Items.LAPIS_LAZULI, 12);
        purify("redstone", Tags.Items.ORES_REDSTONE, Items.REDSTONE, 12);
        purify("quartz", Tags.Items.ORES_QUARTZ, Items.QUARTZ, 3);
    }

    private void purify(String name, TagKey<Item> ore, ItemLike product, int count) {
        addFull(MachineType.PURIFIER, name + "_ore", List.of(in(tag(ore), 1)), List.of(out(product, count, 1.0F)),
                STANDARD, 0, null, PURIFY_WATER);
    }

    // --- Lamps -------------------------------------------------------------------------------

    /** A crystal in a copper cage over glass: aero crystal for the sun, moon crystal for the moon. */
    private void lamps() {
        Tier[] tiers = Tier.values();
        for (LampKind kind : LampKind.values()) {
            ItemLike crystal = kind == LampKind.SUN ? ModItems.AERO_CRYSTAL.get() : ModItems.MOON_CRYSTAL.get();
            shaped(RecipeCategory.MISC, ModBlocks.lamp(kind, Tier.COPPER).get())
                    .define('C', Tags.Items.INGOTS_COPPER)
                    .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                    .define('X', crystal)
                    .define('D', ModItems.FLUX_DUST.get())
                    .pattern("CGC")
                    .pattern("GXG")
                    .pattern("CDC")
                    .unlockedBy(getHasName(crystal), has(crystal))
                    .save(output);
            for (int i = 1; i < tiers.length; i++) {
                upgrade(ModBlocks.lamp(kind, tiers[i]).get(), ModBlocks.lamp(kind, tiers[i - 1]).get(), tiers[i]);
            }
        }
    }

    private static FluxRecipe.Output out(ItemLike item, int count, float chance) {
        return new FluxRecipe.Output(new ItemStack(item, count), chance);
    }

    private void add(MachineType machine, String name, List<FluxRecipe.Input> inputs, List<FluxRecipe.Output> outputs,
                     int time, int flux, @Nullable FluxType fluxType) {
        addFull(machine, name, inputs, outputs, time, flux, fluxType, 0);
    }

    private void addFull(MachineType machine, String name, List<FluxRecipe.Input> inputs, List<FluxRecipe.Output> outputs,
                         int time, int flux, @Nullable FluxType fluxType, int water) {
        save(machine, name, new FluxRecipe(machine, inputs, outputs, time, flux, fluxType, water, List.of()));
    }

    private void save(MachineType machine, String name, FluxRecipe recipe) {
        if (recipe.inputs().size() > machine.inputSlots() || recipe.outputs().size() > machine.outputSlots()
                || recipe.silkOutputs().size() > machine.outputSlots()) {
            throw new IllegalStateException("Recipe " + machine.id() + "/" + name + " needs more slots than the machine has");
        }
        output.accept(Ids.mod(machine.id() + "/" + name), recipe, null);
    }

    // --- Builders ----------------------------------------------------------------------------
    // The recipe code is shared with the 26.1 builds, where these come with the provider.

    private static ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result) {
        return ShapedRecipeBuilder.shaped(category, result);
    }

    private static ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike result, int count) {
        return ShapedRecipeBuilder.shaped(category, result, count);
    }

    private static ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike result) {
        return ShapelessRecipeBuilder.shapeless(category, result);
    }

    private static ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike result, int count) {
        return ShapelessRecipeBuilder.shapeless(category, result, count);
    }

    private static Ingredient tag(TagKey<Item> tag) {
        return Ingredient.of(tag);
    }
}
