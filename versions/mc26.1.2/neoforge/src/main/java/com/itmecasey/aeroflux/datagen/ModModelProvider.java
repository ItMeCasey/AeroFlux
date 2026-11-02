package com.itmecasey.aeroflux.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Generates the item and block models. */
public class ModModelProvider extends ModelProvider {
    /** Turns the model by the way the block faces, the same as the game does for its own horizontal blocks. */
    private static final PropertyDispatch<VariantMutator> FACING = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
            .select(Direction.EAST, BlockModelGenerators.Y_ROT_90)
            .select(Direction.SOUTH, BlockModelGenerators.Y_ROT_180)
            .select(Direction.WEST, BlockModelGenerators.Y_ROT_270)
            .select(Direction.NORTH, BlockModelGenerators.NOP);

    /**
     * The hand-made resources (the {@code --existing} pack, {@code src/main/resources}), where the
     * delivered first-tier models and every texture live.
     */
    private final ResourceManager resources;

    public ModModelProvider(PackOutput output, ResourceManager resources) {
        super(output, AeroFlux.MODID);
        this.resources = resources;
    }

    /** The factory the data run is given: a model provider that reads the hand-made art from {@code resources}. */
    public static GatherDataEvent.DataProviderFromOutput<ModModelProvider> reading(ResourceManager resources) {
        return output -> new ModModelProvider(output, resources);
    }

    // The collector, tank, pipe, wrench and first-tier machine models, block states and client items
    // are hand-made static files (the machine art ships with its own), so only the blocks and items
    // listed below are expected from the generator.
    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return Stream.concat(Stream.of(ModBlocks.AERO_FLUX, ModBlocks.MOON_FLUX), upperTierMachines());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return Stream.concat(Stream.of(ModItems.FLUX_DUST, ModItems.SAWDUST, ModItems.AERO_FLUX_BUCKET, ModItems.MOON_FLUX_BUCKET),
                upperTierMachines().map(block -> BuiltInRegistries.ITEM.wrapAsHolder(block.get().asItem())));
    }

    /** The machine blocks of every tier but the first, which get a model of their own from the first tier's. */
    private static Stream<DeferredBlock<FluxMachineBlock>> upperTierMachines() {
        return ModBlocks.machines().stream().filter(block -> block.get().tier() != Tier.COPPER);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.FLUX_DUST.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SAWDUST.get(), ModelTemplates.FLAT_ITEM);

        fluidBlock(blockModels, ModBlocks.AERO_FLUX.get());
        fluidBlock(blockModels, ModBlocks.MOON_FLUX.get());

        fluidBucket(itemModels, ModItems.AERO_FLUX_BUCKET.get(), ModFluids.AERO_FLUX.get());
        fluidBucket(itemModels, ModItems.MOON_FLUX_BUCKET.get(), ModFluids.MOON_FLUX.get());

        // Each delivered first-tier model is read once and shared by the five tiers above it.
        Map<MachineType, Map<String, Identifier>> copperTextures = new EnumMap<>(MachineType.class);
        upperTierMachines().forEach(block -> {
            FluxMachineBlock machine = block.get();
            Map<String, Identifier> textures = copperTextures.computeIfAbsent(machine.machine(), this::deliveredTextures);
            upperTierMachine(blockModels, itemModels, machine, textures);
        });
    }

    /**
     * A machine above the first tier: it faces the way the first tier does and shows a model of its
     * own, block and item alike, that reuses the first tier's shape with that tier's textures.
     */
    private void upperTierMachine(BlockModelGenerators blockModels, ItemModelGenerators itemModels, FluxMachineBlock block,
                                  Map<String, Identifier> copperTextures) {
        Identifier model = tierModel(blockModels, block, copperTextures);
        blockModels.blockStateOutput.accept(MultiVariantGenerator
                .dispatch(block, BlockModelGenerators.plainVariant(model))
                .with(FACING));
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(model));
    }

    /**
     * Writes {@code block/<tier>_<machine>}: a child of the delivered first-tier model that
     * overrides nothing but its textures. Each texture becomes its tier variant where the art has
     * one ({@code furnace_front} becomes {@code furnace_front_iron}); the rest stay shared with
     * copper. Every key is written out, so the file lists the full texture set of that tier at a
     * glance.
     */
    private Identifier tierModel(BlockModelGenerators blockModels, FluxMachineBlock block, Map<String, Identifier> copperTextures) {
        Tier tier = block.tier();
        String name = ModBlocks.machineBlockName(block.machine(), tier);

        List<TextureSlot> slots = new ArrayList<>();
        TextureMapping mapping = new TextureMapping();
        List<Identifier> missing = new ArrayList<>();
        for (Map.Entry<String, Identifier> entry : copperTextures.entrySet()) {
            Identifier texture = tierTexture(entry.getValue(), tier);
            if (!textureExists(texture)) {
                missing.add(texture);
                continue;
            }
            TextureSlot slot = TextureSlot.create(entry.getKey());
            slots.add(slot);
            mapping.put(slot, new Material(texture));
        }
        // The game would only show the missing-texture checkerboard; fail the data run instead, naming them all.
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Model block/" + name + " names textures that are not in the mod's resources: " + missing);
        }

        // Every key the copper model sets is a required slot, so the template writes them all (sorted, as all data files are).
        ModelTemplate template = new ModelTemplate(Optional.of(Ids.mod("block/" + block.machine().blockId())),
                Optional.empty(), slots.toArray(TextureSlot[]::new));
        return template.create(Ids.mod("block/" + name), mapping, blockModels.modelOutput);
    }

    /**
     * The texture a tier uses in place of a copper one: {@code <name>_<tier>} if the art has it,
     * or {@code <base>_<tier>} for a texture that is already named {@code <base>_copper} (the
     * plant grower's frame). Without a tier variant the copper texture is shared.
     */
    private Identifier tierTexture(Identifier copper, Tier tier) {
        String path = copper.getPath();
        String suffix = "_" + Tier.COPPER.id();
        String base = path.endsWith(suffix) ? path.substring(0, path.length() - suffix.length()) : path;
        Identifier variant = Ids.of(copper.getNamespace(), base + "_" + tier.id());
        return textureExists(variant) ? variant : copper;
    }

    private boolean textureExists(Identifier texture) {
        return resources.getResource(Ids.of(texture.getNamespace(), "textures/" + texture.getPath() + ".png")).isPresent();
    }

    /**
     * The textures the delivered first-tier model of a machine names, in file order. References to
     * other keys ({@code "#side"}) are skipped: the child inherits them and they follow the swap.
     */
    private Map<String, Identifier> deliveredTextures(MachineType machine) {
        Identifier location = Ids.mod("models/block/" + machine.blockId() + ".json");
        Resource resource = resources.getResource(location)
                .orElseThrow(() -> new IllegalStateException("The delivered model " + location + " is not in the mod's resources"));
        Map<String, Identifier> textures = new LinkedHashMap<>();
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (!value.startsWith("#")) {
                        textures.put(entry.getKey(), Identifier.parse(value));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the delivered model " + location, e);
        }
        if (textures.isEmpty()) {
            throw new IllegalStateException("The delivered model " + location + " names no textures to swap per tier");
        }
        return textures;
    }

    /**
     * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
     * texture (the break and splash particles) from the shared still texture.
     */
    private static void fluidBlock(BlockModelGenerators blockModels, Block block) {
        Identifier model = ModelTemplates.PARTICLE_ONLY.create(block,
                TextureMapping.particle(new Material(Ids.mod("block/flux_still"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
    }

    /**
     * A bucket that draws the fluid it holds, tinted, through NeoForge's dynamic container model.
     * The base and the two masks are the same ones NeoForge uses for its own buckets.
     */
    private static void fluidBucket(ItemModelGenerators itemModels, Item bucket, Fluid fluid) {
        Material base = new Material(Ids.of("minecraft", "item/bucket"));
        DynamicFluidContainerModel.Textures textures = new DynamicFluidContainerModel.Textures(
                Optional.of(base),
                Optional.of(base),
                Optional.of(new Material(Ids.of("neoforge", "item/mask/bucket_fluid"))),
                Optional.of(new Material(Ids.of("neoforge", "item/mask/bucket_fluid_cover"))));
        itemModels.itemModelOutput.accept(bucket,
                new DynamicFluidContainerModel.Unbaked(textures, fluid, false, true, true));
    }
}
