package com.itmecasey.aeroflux.machine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.compat.MysticalTiers;
import com.itmecasey.aeroflux.core.machine.PlantSoilRules;
import com.itmecasey.aeroflux.core.machine.PlantSoilRules.Rule;
import com.itmecasey.aeroflux.core.machine.PlantSoilRules.Verdict;
import com.itmecasey.aeroflux.registry.ModTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The soil slot of the Plant Grower. A plant that goes into the ground (its seed is a block
 * item) needs a soil block in the slot, which is never used up: farmland for crops, dirt for
 * everything else, as the tags {@code aeroflux:soil/farmland} and {@code aeroflux:soil/dirt} say.
 * Plants that go into the grower as plain items need no soil.
 *
 * <p>Data packs refine that with rule files at
 * {@code data/<namespace>/aeroflux/plant_soils/<name>.json} (see {@link PlantSoilRules}):
 *
 * <pre>{@code {"plants": ["#mysticalagriculture:seeds"], "soils": ["mysticalagriculture:inferium_farmland"],
 * "replaces_default": false, "speed": 1.0, "extra_seed_chance": 0.1, "tier_matched": false}}</pre>
 *
 * <p>{@code plants} (seed ids or {@code #tags}) is optional and means every plant when left out;
 * {@code soils} are block ids or {@code #tags}. AeroFlux ships rules for the vanilla plants that
 * grow on something else (nether wart, fungi, cactus, sugar cane, pitcher pods) and for the mods
 * whose soil gives a bonus. {@code tier_matched} limits a rule to soil that suits the seed's tier, which only
 * Mystical Agriculture's seeds have (see {@link MysticalTiers}). The files are read again on {@code /reload}.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class PlantSoils {
    private static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("plants", List.of()).forGetter(Rule::plants),
            Codec.STRING.listOf().fieldOf("soils").forGetter(Rule::soils),
            Codec.BOOL.optionalFieldOf("replaces_default", false).forGetter(Rule::replacesDefault),
            Codec.doubleRange(0.1, 10.0).optionalFieldOf("speed", 1.0).forGetter(Rule::speed),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("extra_seed_chance", 0.0).forGetter(Rule::extraSeedChance),
            Codec.BOOL.optionalFieldOf("tier_matched", false).forGetter(Rule::tierMatched)
    ).apply(instance, Rule::new));

    /** The rules read from data packs. Replaced whole on every reload. */
    private static volatile List<Rule> rules = List.of();

    private PlantSoils() {
    }

    /** Whether this item needs a soil: it is planted as a block. */
    public static boolean needsSoil(ItemStack seed) {
        return seed.getItem() instanceof BlockItem;
    }

    /** Whether the stack can sit in the soil slot: a block some plant may grow on. Server side, where the rules are. */
    public static boolean isSoil(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        Block block = item.getBlock();
        if (isIn(block, ModTags.SOIL_DIRT) || isIn(block, ModTags.SOIL_FARMLAND)) {
            return true;
        }
        for (Rule rule : rules) {
            if (rule.soils().stream().anyMatch(entry -> soilIs(block, entry))) {
                return true;
            }
        }
        return false;
    }

    /** How the soil in the slot suits the seed next to it; a plant that needs no soil always grows. */
    public static Verdict judge(ItemStack seed, ItemStack soil) {
        if (!needsSoil(seed)) {
            return Verdict.PLAIN;
        }
        if (!(soil.getItem() instanceof BlockItem soilItem)) {
            return Verdict.NO;
        }
        Block block = soilItem.getBlock();
        Block plant = ((BlockItem) seed.getItem()).getBlock();
        // Crops stand on farmland; saplings, flowers, mushrooms and the rest on dirt.
        boolean crop = plant instanceof CropBlock || plant instanceof StemBlock;
        boolean defaultSoil = isIn(block, crop ? ModTags.SOIL_FARMLAND : ModTags.SOIL_DIRT);
        return PlantSoilRules.judge(rules, defaultSoil, entry -> plantIs(seed, entry), entry -> soilIs(block, entry),
                () -> MysticalTiers.farmlandMatches(seed.getItem(), block));
    }

    @SuppressWarnings("deprecation") // The builtin holder is the way to ask a block about a tag without a registry lookup.
    private static boolean isIn(Block block, TagKey<Block> tag) {
        return block.builtInRegistryHolder().is(tag);
    }

    @SuppressWarnings("deprecation")
    private static boolean plantIs(ItemStack seed, String entry) {
        if (entry.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
            return tag != null && seed.is(TagKey.create(Registries.ITEM, tag));
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        return id != null && seed.getItem().builtInRegistryHolder().is(id);
    }

    @SuppressWarnings("deprecation")
    private static boolean soilIs(Block block, String entry) {
        if (entry.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
            return tag != null && isIn(block, TagKey.create(Registries.BLOCK, tag));
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        return id != null && block.builtInRegistryHolder().is(id);
    }

    /** Has the server read the rule files along with the rest of the data packs. */
    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Reads every rule file into {@link #rules}. */
    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "aeroflux/plant_soils");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
            List<Rule> loaded = new ArrayList<>();
            json.forEach((id, element) -> CODEC.parse(JsonOps.INSTANCE, element)
                    .resultOrPartial(error -> AeroFlux.LOGGER.warn("Skipping soil rule {}: {}", id, error))
                    .ifPresent(loaded::add));
            rules = loaded;
            AeroFlux.LOGGER.info("Loaded {} soil rule(s) for the Plant Grower", loaded.size());
        }
    }
}
