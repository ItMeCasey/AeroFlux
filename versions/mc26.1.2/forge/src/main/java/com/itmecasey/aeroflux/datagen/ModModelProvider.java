package com.itmecasey.aeroflux.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Generates the item and block models. Forge's model provider covers vanilla content by
 * default, so each hook below narrows it down to AeroFlux's own items and blocks.
 *
 * <p>The two buckets are not generated here: they use the loader's fluid container model, which
 * is a hand-written model file under {@code src/main/resources}.
 */
public class ModModelProvider extends ModelProvider {
    /** Texture slots made for the keys the delivered models use, by name. */
    private static final Map<String, TextureSlot> SLOTS = new HashMap<>();

    /** Reads the delivered art in {@code src/main/resources} (the data run's {@code --existing} pack). */
    private final ExistingFileHelper existingFileHelper;

    public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output);
        this.existingFileHelper = existingFileHelper;
    }

    @Override
    protected Stream<Block> getKnownBlocks() {
        return Stream.concat(Stream.of(ModBlocks.AERO_FLUX.get(), ModBlocks.MOON_FLUX.get()), tierMachines());
    }

    @Override
    protected Stream<Item> getKnownItems() {
        return Stream.concat(Stream.of(ModItems.FLUX_DUST.get(), ModItems.SAWDUST.get()), tierMachines().map(Block::asItem));
    }

    /**
     * The machine blocks of the higher tiers. The first tier's blockstate and models are delivered
     * with the art (assets-src); every other tier gets its blockstate, a model of its own and an
     * item definition from here.
     */
    private static Stream<Block> tierMachines() {
        return Stream.of(MachineType.values())
                .flatMap(machine -> Stream.of(Tier.values())
                        .filter(tier -> tier != Tier.COPPER)
                        .map(tier -> (Block) ModBlocks.machine(machine, tier).get()));
    }

    @Override
    protected BlockModelGenerators getBlockModelGenerators(BlockStateGeneratorCollector blocks,
                                                           ItemInfoCollector items, SimpleModelCollector models) {
        return new BlockModelGenerators(blocks, items, models) {
            @Override
            public void run() {
                fluidBlock(ModBlocks.AERO_FLUX.get());
                fluidBlock(ModBlocks.MOON_FLUX.get());

                // Every higher tier reuses the first tier's shape and only swaps in that tier's textures.
                for (MachineType machine : MachineType.values()) {
                    Map<String, Identifier> copperTextures = deliveredTextures(machine);
                    for (Tier tier : Tier.values()) {
                        if (tier != Tier.COPPER) {
                            Identifier model = tierModel(machine, tier, copperTextures, modelOutput);
                            blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.machine(machine, tier).get(),
                                    plainVariant(model)).with(facing()));
                        }
                    }
                }
            }

            /** Turns the model the way the machine faces; the delivered model looks north. */
            private PropertyDispatch<VariantMutator> facing() {
                return PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
                        .select(Direction.NORTH, NOP)
                        .select(Direction.EAST, Y_ROT_90)
                        .select(Direction.SOUTH, Y_ROT_180)
                        .select(Direction.WEST, Y_ROT_270);
            }

            /**
             * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
             * texture (the break and splash particles) from the shared still texture.
             */
            private void fluidBlock(Block block) {
                Identifier model = ModelTemplates.PARTICLE_ONLY.create(block,
                        TextureMapping.particle(new Material(Ids.mod("block/flux_still"))), modelOutput);
                blockStateOutput.accept(createSimpleBlock(block, plainVariant(model)));
            }
        };
    }

    @Override
    protected ItemModelGenerators getItemModelGenerators(ItemInfoCollector items, SimpleModelCollector models) {
        return new ItemModelGenerators(items, models) {
            @Override
            public void run() {
                generateFlatItem(ModItems.FLUX_DUST.get(), ModelTemplates.FLAT_ITEM);
                generateFlatItem(ModItems.SAWDUST.get(), ModelTemplates.FLAT_ITEM);
                // Each higher-tier machine shows its own tier's block model; the first tier's
                // item definition ships with the art.
                for (MachineType machine : MachineType.values()) {
                    for (Tier tier : Tier.values()) {
                        if (tier != Tier.COPPER) {
                            String name = ModBlocks.machineBlockName(machine, tier);
                            itemModelOutput.accept(ModBlocks.machine(machine, tier).get().asItem(),
                                    ItemModelUtils.plainModel(Ids.mod("block/" + name)));
                        }
                    }
                }
            }
        };
    }

    /**
     * A child of the delivered first-tier model that overrides nothing but its textures. Each
     * texture becomes its tier variant where the art has one ({@code furnace_front} becomes
     * {@code furnace_front_iron}); the rest stay shared with copper. Every key is written out, so
     * the file lists the full texture set of that tier at a glance.
     */
    private Identifier tierModel(MachineType machine, Tier tier, Map<String, Identifier> copperTextures,
                                 BiConsumer<Identifier, ModelInstance> output) {
        String name = ModBlocks.machineBlockName(machine, tier);
        TextureMapping textures = new TextureMapping();
        List<TextureSlot> slots = new ArrayList<>();
        List<Identifier> missing = new ArrayList<>();
        for (Map.Entry<String, Identifier> entry : copperTextures.entrySet()) {
            Identifier texture = tierTexture(entry.getValue(), tier);
            if (textureExists(texture)) {
                TextureSlot slot = slot(entry.getKey());
                slots.add(slot);
                textures.put(slot, new Material(texture));
            } else {
                missing.add(texture);
            }
        }
        // The game would only show a missing-texture checkerboard, so the data run stops instead
        // and names every texture that is not there.
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Model block/" + name + " names textures that are not in the mod's resources: " + missing);
        }
        ModelTemplate template = new ModelTemplate(Optional.of(Ids.mod("block/" + machine.blockId())), Optional.empty(),
                slots.toArray(TextureSlot[]::new));
        return template.create(Ids.mod("block/" + name), textures, output);
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
        Identifier variant = Identifier.fromNamespaceAndPath(copper.getNamespace(), base + "_" + tier.id());
        return textureExists(variant) ? variant : copper;
    }

    private boolean textureExists(Identifier texture) {
        return existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES, ".png", "textures");
    }

    /**
     * The textures the delivered first-tier model of a machine names, in file order. References to
     * other keys ({@code "#side"}) are skipped: the child inherits them and they follow the swap.
     */
    private Map<String, Identifier> deliveredTextures(MachineType machine) {
        Identifier location = Ids.mod("block/" + machine.blockId());
        Map<String, Identifier> textures = new LinkedHashMap<>();
        try (Reader reader = existingFileHelper.getResource(location, PackType.CLIENT_RESOURCES, ".json", "models").openAsReader()) {
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
     * The texture slot for a key of a delivered model. Vanilla keeps the slot factory private and
     * has no slot for keys such as {@code blade} or {@code sprout}, so the factory is called
     * reflectively. This only runs in the data run, never in the game.
     */
    private static TextureSlot slot(String id) {
        return SLOTS.computeIfAbsent(id, key -> {
            try {
                Method create = TextureSlot.class.getDeclaredMethod("create", String.class);
                create.setAccessible(true);
                return (TextureSlot) create.invoke(null, key);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not make a texture slot for the key " + key, e);
            }
        });
    }
}
